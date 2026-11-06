package uz.dukeengine.render;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.instancing.InstancedGeometry;
import com.jme3.texture.FrameBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3tools.optimize.LodGenerator;

/**
 * Levels of detail the game does not have to make: each mesh seen in the view gets coarser index buffers, worked out off
 * the frame by {@link LodGenerator} once for the mesh (its clones share it), and each geometry the view queued is drawn
 * at the level its size on the screen asks for. A processor of the main view, ahead of the filters: the shadow maps and
 * {@link SceneBuffers} draw the levels it picked, so depth, velocity and shadow agree with the picture, and
 * {@link AutoInstancing} groups the geometries of one mesh at one level.
 *
 * <p>Left as they are: meshes the game gave levels of its own, skinned or morphed meshes (their shape is not their bind
 * pose's), meshes not of indexed triangles, and meshes of fewer than {@link #MINIMUM_TRIANGLES}.
 */
public final class AutoLod implements SceneProcessor {

    private static final Logger LOG = Logger.getLogger(AutoLod.class.getName());

    /** Below this many triangles a mesh costs less than the levels' bookkeeping saves. */
    static final int MINIMUM_TRIANGLES = 96;
    /** The share of triangles each coarser level takes away. */
    static final float[] REDUCTION = {0.5f, 0.75f, 0.9f};
    /**
     * The screen size (bound radius over half the view's height at the bound's distance) under which each coarser
     * level is drawn: at 1080 lines, a radius of about 54, 22 and 8 pixels.
     */
    static final float[] SIZE = {0.1f, 0.04f, 0.015f};
    /** A geometry goes coarser only once it is this much smaller than the size it went finer at, so it does not flicker. */
    static final float HYSTERESIS = 0.85f;

    /** BAKING, its levels being made; OURS, made (or none could be); LEFT, not ours to choose. */
    private enum State { BAKING, OURS, LEFT }

    private record Baked(Mesh mesh, VertexBuffer[] levels) {
    }

    private final Map<Mesh, State> meshes = new WeakHashMap<>();
    private final Queue<Baked> baked = new ConcurrentLinkedQueue<>();
    private final Executor baker;
    private ViewPort viewPort;
    private int changed;

    public AutoLod() {
        this(Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "duke-lod-baker");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }));
    }

    /** With the levels made on {@code baker}; a test passes one that runs them at once. */
    AutoLod(Executor baker) {
        this.baker = baker;
    }

    /** How many geometries changed level in the last frame. */
    public int changed() {
        return changed;
    }

    /**
     * The level of a geometry {@code size} large on the screen (see {@link #SIZE}) that is drawn at {@code current} now,
     * of {@code levels} the mesh has: coarser only below {@link #HYSTERESIS} of the step, finer as soon as it is above it.
     */
    static int level(float size, int current, int levels) {
        int finer = coarseness(size);
        int coarser = coarseness(size / HYSTERESIS);
        int level = coarser > current ? coarser : Math.min(finer, current);
        return Math.min(level, levels - 1);
    }

    private static int coarseness(float size) {
        int level = 0;
        while (level < SIZE.length && size < SIZE[level]) {
            level++;
        }
        return level;
    }

    /** How large {@code bound} is on the screen of {@code camera}: its radius over half the view's height where it stands. */
    static float screenSize(BoundingVolume bound, Camera camera) {
        float radius = bound instanceof BoundingSphere sphere ? sphere.getRadius()
                : bound instanceof BoundingBox box ? box.getExtent(null).length() : 0f;
        if (radius <= 0f) {
            return Float.POSITIVE_INFINITY;
        }
        if (camera.isParallelProjection()) {
            return radius / Math.abs(camera.getFrustumTop());
        }
        float distance = Math.max(bound.getCenter().distance(camera.getLocation()), camera.getFrustumNear());
        float halfHeight = distance * Math.abs(camera.getFrustumTop()) / camera.getFrustumNear();
        return radius / halfHeight;
    }

    /** Whether levels may be made for this mesh. */
    static boolean decimable(Mesh mesh) {
        return mesh.getMode() == Mesh.Mode.Triangles && mesh.getBuffer(VertexBuffer.Type.Index) != null
                && mesh.getBuffer(VertexBuffer.Type.Position) != null && !mesh.isAnimated() && !mesh.hasMorphTargets()
                && mesh.getTriangleCount() >= MINIMUM_TRIANGLES;
    }

    /** Sets the level of each geometry of {@code list}, as {@code camera} sees it; asks for the levels of meshes not seen before. */
    // shortcut: a caster the view does not see keeps the level it was last drawn at (full, if never seen); pick a shadow map's own levels when far cascades cost too much.
    void pick(GeometryList list, Camera camera) {
        for (int i = 0; i < list.size(); i++) {
            var geometry = list.get(i);
            if (geometry instanceof InstancedGeometry) {
                continue;
            }
            var mesh = geometry.getMesh();
            var state = meshes.get(mesh);
            if (state == null) {
                ask(mesh);
                continue;
            }
            if (state != State.OURS || mesh.getNumLodLevels() < 2 || geometry.getWorldBound() == null) {
                continue;
            }
            int level = level(screenSize(geometry.getWorldBound(), camera), geometry.getLodLevel(), mesh.getNumLodLevels());
            if (level != geometry.getLodLevel()) {
                geometry.setLodLevel(level);
                changed++;
            }
        }
    }

    private void ask(Mesh mesh) {
        // A mesh with levels of its own keeps them, and the selection leaves its geometries' level to the game.
        if (mesh.getNumLodLevels() > 0 || !decimable(mesh)) {
            meshes.put(mesh, State.LEFT);
            return;
        }
        meshes.put(mesh, State.BAKING);
        // The baker reads a copy: the mesh's own buffers are the render thread's to upload.
        var copy = new Mesh();
        copy.setBuffer(mesh.getBuffer(VertexBuffer.Type.Position).clone());
        copy.setBuffer(mesh.getBuffer(VertexBuffer.Type.Index).clone());
        var index = mesh.getBuffer(VertexBuffer.Type.Index);
        baker.execute(() -> {
            try {
                var levels = new LodGenerator(copy).computeLods(LodGenerator.TriangleReductionMethod.PROPORTIONAL, REDUCTION);
                levels[0] = index;
                baked.add(new Baked(mesh, levels));
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "no levels for a mesh", e);
                baked.add(new Baked(mesh, null));
            }
        });
    }

    /** Puts the levels made since the last frame on their meshes: on the render thread, between two frames' draws. */
    void collect() {
        Baked done;
        while ((done = baked.poll()) != null) {
            if (done.levels() != null && done.levels().length > 1) {
                done.mesh().setLodLevels(done.levels());
            }
            meshes.put(done.mesh(), State.OURS);
        }
    }

    @Override
    public void initialize(RenderManager rm, ViewPort vp) {
        viewPort = vp;
    }

    @Override
    public void reshape(ViewPort vp, int w, int h) {
    }

    @Override
    public boolean isInitialized() {
        return viewPort != null;
    }

    @Override
    public void preFrame(float tpf) {
        collect();
    }

    @Override
    public void postQueue(RenderQueue rq) {
        changed = 0;
        var camera = viewPort.getCamera();
        pick(rq.getList(RenderQueue.Bucket.Opaque), camera);
        pick(rq.getList(RenderQueue.Bucket.Transparent), camera);
        pick(rq.getList(RenderQueue.Bucket.Translucent), camera);
    }

    @Override
    public void postFrame(FrameBuffer out) {
    }

    @Override
    public void cleanup() {
        if (baker instanceof ExecutorService own) {
            own.shutdownNow();
        }
        viewPort = null;
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
    }
}
