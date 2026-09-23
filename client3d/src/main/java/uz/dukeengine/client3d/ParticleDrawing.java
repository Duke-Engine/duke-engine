package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import uz.dukeengine.core.content.ParticleSystem;

/**
 * The particle systems, drawn: one geometry a system, its shape made again each frame from where its particles
 * are — the reference game's {@code W3DParticleSystemManager}.
 *
 * <ul>
 *   <li>A particle is a square {@code size} on a side facing the camera, turned about the view by its angle and
 *       coloured by its colour and alpha; a ground-aligned system's lie flat instead.
 *   <li>A {@code STREAK} joins its system's particles, in the order they were born, into one strip as wide as
 *       each is big — a tracer.
 *   <li>Blended as the system says: added, by alpha, by an alpha test, or multiplied — which the materials
 *       handed in carry.
 * </ul>
 *
 * <p><b>Z-up, turned.</b> A system is written, and moved, in the frame the game's models were made in — x and y
 * across the ground, z up — and is turned into this client's by the same quarter turn about x those models were:
 * {@code (x, y, z)} is drawn at {@code (x, z, -y)}. Everything that is turned is turned here, and only here.
 *
 * <p>The shape is worked out apart from any GPU ({@link #squares}, {@link #streak}) so that it can be checked
 * without one; the buffers it is copied into are kept and grown, not made again every frame.
 */
final class ParticleDrawing {

    private static final Logger LOG = Logger.getLogger(ParticleDrawing.class.getName());

    private static final float TURN = FastMath.TWO_PI;

    /** What a system's particles are drawn as this frame, before it reaches a buffer. */
    record Shape(float[] positions, float[] colours, float[] uvs, int[] indices) {

        static final Shape NOTHING = new Shape(new float[0], new float[0], new float[0], new int[0]);

        int vertices() {
            return positions.length / 3;
        }
    }

    /** One system on screen: its geometry, and the buffers kept for it. */
    private static final class Drawn {
        private final Geometry geometry;
        private final Mesh mesh = new Mesh();
        private int room;
        private int indexRoom;
        private FloatBuffer positions;
        private FloatBuffer colours;
        private FloatBuffer uvs;
        private IntBuffer indices;

        Drawn(Geometry geometry) {
            this.geometry = geometry;
        }
    }

    private final Node node = new Node("particle systems");
    private final Particles particles;
    private final Function<ParticleSystem, Material> materials;
    private final Map<Emitter, Drawn> drawn = new IdentityHashMap<>();
    private final Set<String> undrawable = new HashSet<>();

    ParticleDrawing(Particles particles, Function<ParticleSystem, Material> materials) {
        this.particles = particles;
        this.materials = materials;
    }

    Node node() {
        return node;
    }

    /** Every system as it stands now, from where the camera is. */
    void draw(Camera camera) {
        var left = camera.getLeft();
        var up = camera.getUp();
        var eye = camera.getLocation();
        var live = java.util.Collections.newSetFromMap(new IdentityHashMap<Emitter, Boolean>());
        for (var emitter : particles.emitters()) {
            live.add(emitter);
            var shape = shapeOf(emitter, left, up, eye);
            var one = drawn.get(emitter);
            if (shape.vertices() == 0) {
                if (one != null) {
                    one.geometry.setCullHint(Spatial.CullHint.Always);
                }
                continue;
            }
            if (one == null) {
                one = new Drawn(new Geometry("particles " + emitter.data().name()));
                one.geometry.setMesh(one.mesh);
                one.geometry.setMaterial(materials.apply(emitter.data()));
                one.geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
                node.attachChild(one.geometry);
                drawn.put(emitter, one);
            }
            fill(one, shape);
            one.geometry.setCullHint(Spatial.CullHint.Inherit);
        }
        var gone = drawn.entrySet().iterator();
        while (gone.hasNext()) {
            var entry = gone.next();
            if (!live.contains(entry.getKey())) {
                entry.getValue().geometry.removeFromParent();
                gone.remove();
            }
        }
    }

    /** Nothing drawn: a new world. */
    void clear() {
        node.detachAllChildren();
        drawn.clear();
    }

    private Shape shapeOf(Emitter emitter, Vector3f left, Vector3f up, Vector3f eye) {
        var data = emitter.data();
        return switch (data.type()) {
            case STREAK -> streak(emitter.particles(), eye);
            case PARTICLE, VOLUME_PARTICLE -> squares(emitter.particles(), data.isGroundAligned(), left, up);
            case DRAWABLE -> {
                if (undrawable.add(data.name())) {
                    LOG.warning(() -> "the particle system '" + data.name() + "' draws a model a particle, which"
                            + " this client does not; nothing is drawn for it");
                }
                yield Shape.NOTHING;
            }
        };
    }

    /** Copy a shape into the kept buffers, growing them only when it no longer fits. */
    private static void fill(Drawn one, Shape shape) {
        int vertices = shape.vertices();
        if (vertices > one.room) {
            one.room = Math.max(vertices, one.room * 2);
            one.positions = BufferUtils.createFloatBuffer(one.room * 3);
            one.colours = BufferUtils.createFloatBuffer(one.room * 4);
            one.uvs = BufferUtils.createFloatBuffer(one.room * 2);
        }
        if (shape.indices().length > one.indexRoom) {
            one.indexRoom = Math.max(shape.indices().length, one.indexRoom * 2);
            one.indices = BufferUtils.createIntBuffer(one.indexRoom);
        }
        one.positions.clear();
        one.positions.put(shape.positions()).flip();
        one.colours.clear();
        one.colours.put(shape.colours()).flip();
        one.uvs.clear();
        one.uvs.put(shape.uvs()).flip();
        one.indices.clear();
        one.indices.put(shape.indices()).flip();
        one.mesh.setBuffer(VertexBuffer.Type.Position, 3, one.positions);
        one.mesh.setBuffer(VertexBuffer.Type.Color, 4, one.colours);
        one.mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, one.uvs);
        one.mesh.setBuffer(VertexBuffer.Type.Index, 3, one.indices);
        one.mesh.updateCounts();
        one.mesh.updateBound();
        one.geometry.updateModelBound();
    }

    // ---- the shapes ----

    /** A place in the system's Z-up frame, where it is drawn: the models' quarter turn about x. */
    static Vector3f toClient(float x, float y, float z) {
        return new Vector3f(x, z, -y);
    }

    /**
     * A particle's turn as the reference draws it: into a byte of 255ths of a turn, truncated, and drawn as
     * 256ths — a step of about 1.4 degrees, and not quite the angle written.
     */
    static float quantised(float angle) {
        int step = (int) (angle * 255f / TURN) & 0xFF;
        return step * TURN / 256f;
    }

    /**
     * Squares, one a particle still alive: facing the camera — {@code left} and {@code up} are its — or lying flat
     * on the ground, turned by the particle's angle either way.
     */
    static Shape squares(List<Particle> particles, boolean flat, Vector3f left, Vector3f up) {
        int count = 0;
        for (var particle : particles) {
            if (!particle.gone) {
                count++;
            }
        }
        var positions = new float[count * 12];
        var colours = new float[count * 16];
        var uvs = new float[count * 8];
        var indices = new int[count * 6];
        var right = left.negate();
        int at = 0;
        for (var particle : particles) {
            if (particle.gone) {
                continue;
            }
            float half = particle.size / 2f;
            float angle = quantised(particle.angle);
            float cos = FastMath.cos(angle);
            float sin = FastMath.sin(angle);
            Vector3f[] corners;
            if (flat) {
                corners = new Vector3f[4];
                float[][] offsets = {{-half, -half}, {half, -half}, {half, half}, {-half, half}};
                for (int corner = 0; corner < 4; corner++) {
                    float dx = offsets[corner][0];
                    float dy = offsets[corner][1];
                    corners[corner] = toClient(particle.x + cos * dx - sin * dy, particle.y + sin * dx + cos * dy,
                            particle.z);
                }
            } else {
                var centre = toClient(particle.x, particle.y, particle.z);
                var across = right.mult(cos).addLocal(up.mult(sin)).multLocal(half);
                var upward = up.mult(cos).subtractLocal(right.mult(sin)).multLocal(half);
                corners = new Vector3f[] {
                    centre.subtract(across).subtractLocal(upward), centre.add(across).subtractLocal(upward),
                    centre.add(across).addLocal(upward), centre.subtract(across).addLocal(upward)};
            }
            for (int corner = 0; corner < 4; corner++) {
                int vertex = at * 4 + corner;
                positions[vertex * 3] = corners[corner].x;
                positions[vertex * 3 + 1] = corners[corner].y;
                positions[vertex * 3 + 2] = corners[corner].z;
                colours[vertex * 4] = particle.red;
                colours[vertex * 4 + 1] = particle.green;
                colours[vertex * 4 + 2] = particle.blue;
                colours[vertex * 4 + 3] = particle.alpha;
            }
            System.arraycopy(new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f}, 0, uvs, at * 8, 8);
            int first = at * 4;
            System.arraycopy(new int[] {first, first + 1, first + 2, first, first + 2, first + 3}, 0,
                    indices, at * 6, 6);
            at++;
        }
        return new Shape(positions, colours, uvs, indices);
    }

    /**
     * The particles still alive joined, in the order they were born, into one strip facing the camera at
     * {@code eye} and as wide as each is big. The first point is drawn black and clear, as the reference draws
     * it, so the strip's trailing end fades out rather than stopping on a hard edge.
     */
    static Shape streak(List<Particle> particles, Vector3f eye) {
        var alive = particles.stream().filter(particle -> !particle.gone).toList();
        int count = alive.size();
        if (count < 2) {
            return Shape.NOTHING;
        }
        var points = new Vector3f[count];
        for (int at = 0; at < count; at++) {
            var particle = alive.get(at);
            points[at] = toClient(particle.x, particle.y, particle.z);
        }
        var positions = new float[count * 6];
        var colours = new float[count * 8];
        var uvs = new float[count * 4];
        var indices = new int[(count - 1) * 6];
        for (int at = 0; at < count; at++) {
            var particle = alive.get(at);
            var along = points[Math.min(at + 1, count - 1)].subtract(points[Math.max(at - 1, 0)]);
            var side = along.cross(eye.subtract(points[at]));
            if (side.lengthSquared() == 0f) {
                side.set(1f, 0f, 0f); // looked at straight down its length: any side will do
            }
            side.normalizeLocal().multLocal(particle.size / 2f);
            var one = points[at].subtract(side);
            var other = points[at].add(side);
            positions[at * 6] = one.x;
            positions[at * 6 + 1] = one.y;
            positions[at * 6 + 2] = one.z;
            positions[at * 6 + 3] = other.x;
            positions[at * 6 + 4] = other.y;
            positions[at * 6 + 5] = other.z;
            boolean trailing = at == 0;
            for (int edge = 0; edge < 2; edge++) {
                int vertex = at * 2 + edge;
                colours[vertex * 4] = trailing ? 0f : particle.red;
                colours[vertex * 4 + 1] = trailing ? 0f : particle.green;
                colours[vertex * 4 + 2] = trailing ? 0f : particle.blue;
                colours[vertex * 4 + 3] = trailing ? 0f : particle.alpha;
                uvs[vertex * 2] = (float) at / (count - 1);
                uvs[vertex * 2 + 1] = edge;
            }
            if (at < count - 1) {
                int first = at * 2;
                System.arraycopy(new int[] {first, first + 1, first + 3, first, first + 3, first + 2}, 0,
                        indices, at * 6, 6);
            }
        }
        return new Shape(positions, colours, uvs, indices);
    }
}
