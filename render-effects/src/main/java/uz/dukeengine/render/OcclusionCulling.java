package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.Vector3f;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.texture.FrameBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What stands behind something else is not drawn. The solid geometries the view queued are sorted into cells of
 * {@link #CELL} world units by where they stand; after the frame is drawn, each cell's box (the bound of what is in it)
 * is drawn into the scene's depth inside a hardware query, colour and depth writes off; a cell whose box had no sample
 * pass is left out of the next frame's solid bucket, and asked about again each frame until it shows. Shadow maps see
 * the scene with their own culling, so a hidden thing still casts its shadow.
 *
 * <p>Always drawn: a geometry larger than a cell (a ground, a wall: what hides the others), a cell the camera stands in
 * or that the near plane cuts, and a cell that was not in the view the frame before (its last answer is stale).
 *
 * <p>shortcut: the answer is a frame late, so what comes out from behind a wall shows one frame after it should;
 * a camera that turns fast past a corner may see a frame's gap. Draw the hidden cells' boxes before the frame
 * (two-pass, against last frame's depth) when that is seen in a game.
 */
public final class OcclusionCulling implements SceneProcessor {

    /** The side of a cell, in world units: about a room, a house, a few trees. */
    static final float CELL = 12f;
    /** How many frames a cell nothing stood in is remembered for. */
    private static final int FORGET = 120;
    private static final int QUERIES = 128;
    /** A cell that shows is asked again only every this many frames, a quarter of them each frame: it is drawn meanwhile anyway. */
    static final int SHOWING_EVERY = 4;

    static final class Cell {
        final BoundingBox box = new BoundingBox();
        final List<Geometry> geometries = new ArrayList<>();
        int query = -1;
        boolean hidden;
        long seen = -2;
        int turn;
        /** New to the view: asked at once, whatever its turn. */
        boolean fresh = true;
    }

    private final Map<Long, Cell> cells = new HashMap<>();
    private final List<Cell> live = new ArrayList<>();
    private final List<Geometry> kept = new ArrayList<>();
    private final ArrayDeque<Integer> spare = new ArrayDeque<>();
    private final AssetManager assets;
    private Geometry probe;
    private RenderManager renderManager;
    private ViewPort viewPort;
    private long frame;
    private int culled;
    private int asked;

    public OcclusionCulling(AssetManager assets) {
        this.assets = assets;
    }

    /** Whether this GPU can answer an occlusion query: desktop GL 2.0, or GLES 3.0. */
    public static boolean supported(EnumSet<Caps> caps) {
        return caps.contains(Caps.OpenGL20) || caps.contains(Caps.OpenGLES30);
    }

    /** How many geometries the last frame left out. */
    public int culled() {
        return culled;
    }

    /** How many boxes the last frame asked about. */
    public int asked() {
        return asked;
    }

    /** The key of the cell around {@code at}. */
    static long key(Vector3f at) {
        long x = (long) Math.floor(at.x / CELL) & 0x1FFFFF;
        long y = (long) Math.floor(at.y / CELL) & 0x1FFFFF;
        long z = (long) Math.floor(at.z / CELL) & 0x1FFFFF;
        return x << 42 | y << 21 | z;
    }

    static float radius(BoundingVolume bound) {
        return bound instanceof BoundingSphere sphere ? sphere.getRadius()
                : bound instanceof BoundingBox box ? box.getExtent(null).length() : Float.POSITIVE_INFINITY;
    }

    /** Whether the camera is in {@code box} or its near plane cuts it: its front faces cannot be trusted to be drawn. */
    static boolean near(BoundingBox box, Camera camera) {
        var extent = box.getExtent(null);
        // The near plane's corners reach out about its height from the eye; a margin of that is enough.
        float margin = camera.getFrustumNear() * 2f + Math.abs(camera.getFrustumTop()) * 2f;
        var d = camera.getLocation().subtract(box.getCenter());
        return Math.abs(d.x) <= extent.x + margin && Math.abs(d.y) <= extent.y + margin && Math.abs(d.z) <= extent.z + margin;
    }

    /**
     * Reads the answers that came back, sorts the queued solid geometries into cells and leaves out those of the cells
     * found hidden.
     */
    void sort(GeometryList list, Camera camera, Renderer renderer) {
        frame++;
        culled = 0;
        live.clear();
        kept.clear();
        for (int i = 0; i < list.size(); i++) {
            var geometry = list.get(i);
            var bound = geometry.getWorldBound();
            if (bound == null || radius(bound) > CELL) {
                kept.add(geometry);
                continue;
            }
            long key = key(bound.getCenter());
            var cell = cells.computeIfAbsent(key, k -> {
                var made = new Cell();
                made.turn = cells.size() % SHOWING_EVERY;
                return made;
            });
            if (cell.seen != frame) {
                answer(cell, renderer);
                // Out of the view last frame: whatever it was told then is stale.
                if (cell.seen != frame - 1) {
                    cell.hidden = false;
                    cell.fresh = true;
                }
                cell.seen = frame;
                cell.geometries.clear();
                cell.box.setCenter(bound.getCenter());
                cell.box.setXExtent(0f);
                cell.box.setYExtent(0f);
                cell.box.setZExtent(0f);
                live.add(cell);
            }
            cell.geometries.add(geometry);
            cell.box.mergeLocal(bound);
        }
        for (var cell : live) {
            if (cell.hidden && !near(cell.box, camera)) {
                culled += cell.geometries.size();
            } else {
                cell.hidden = false;
                kept.addAll(cell.geometries);
            }
        }
        if (kept.size() != list.size()) {
            list.clear();
            kept.forEach(list::add);
        }
        forget(renderer);
    }

    private void answer(Cell cell, Renderer renderer) {
        if (cell.query >= 0 && renderer.isTaskResultAvailable(cell.query)) {
            cell.hidden = !renderer.occlusionQueryPassed(cell.query);
            spare.push(cell.query);
            cell.query = -1;
        }
    }

    private void forget(Renderer renderer) {
        if (frame % FORGET != 0) {
            return;
        }
        cells.values().removeIf(cell -> {
            if (frame - cell.seen < FORGET) {
                return false;
            }
            if (cell.query >= 0) {
                // Read, or it may be reused while still running.
                if (!renderer.isTaskResultAvailable(cell.query)) {
                    return false;
                }
                spare.push(cell.query);
            }
            return true;
        });
    }

    /** Asks about each cell of this frame that has no question running yet. */
    void ask(RenderManager rm, Camera camera) {
        asked = 0;
        var renderer = rm.getRenderer();
        for (var cell : live) {
            if (cell.query >= 0 || (!cell.hidden && !cell.fresh && frame % SHOWING_EVERY != cell.turn) || near(cell.box, camera)) {
                continue;
            }
            if (spare.isEmpty()) {
                for (int id : renderer.generateProfilingTasks(QUERIES)) {
                    spare.push(id);
                }
            }
            cell.fresh = false;
            cell.query = spare.pop();
            var extent = cell.box.getExtent(null);
            probe.setLocalTranslation(cell.box.getCenter());
            probe.setLocalScale(extent.x * 1.01f + 0.01f, extent.y * 1.01f + 0.01f, extent.z * 1.01f + 0.01f);
            probe.updateGeometricState();
            renderer.startOcclusionQuery(cell.query);
            rm.renderGeometry(probe);
            renderer.stopOcclusionQuery();
            asked++;
        }
    }

    @Override
    public void initialize(RenderManager rm, ViewPort vp) {
        renderManager = rm;
        viewPort = vp;
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        var state = material.getAdditionalRenderState();
        state.setColorWrite(false);
        state.setDepthWrite(false);
        state.setDepthTest(true);
        state.setDepthFunc(RenderState.TestFunction.LessOrEqual);
        state.setFaceCullMode(RenderState.FaceCullMode.Back);
        probe = new Geometry("occlusion probe", new Box(1f, 1f, 1f));
        probe.setMaterial(material);
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
    }

    @Override
    public void postQueue(RenderQueue rq) {
        sort(rq.getList(RenderQueue.Bucket.Opaque), viewPort.getCamera(), renderManager.getRenderer());
    }

    @Override
    public void postFrame(FrameBuffer out) {
        // The scene's depth is still bound: the processors after this one (the filters) have not drawn yet.
        ask(renderManager, viewPort.getCamera());
    }

    @Override
    public void cleanup() {
        cells.clear();
        live.clear();
        spare.clear();
        viewPort = null;
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
    }
}
