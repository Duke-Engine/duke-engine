package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import uz.dukeengine.core.view.UnitView;

/**
 * Marks left on the ground behind wheels and treads — the reference's {@code W3DTreadMarks}: while a thing of a
 * template the game names moves faster than its look's least speed, off water, a piece of track is laid behind each of
 * its sides every so far, lying its life and fading out over its last frames. A fixed pool of pieces a look, the oldest
 * taken again when it is full. Drawing only.
 */
final class TrackMarks {

    /** Pieces each look keeps at most. */
    static final int POOL = 512;

    /** One piece of track: its four corners on the ground and the game's frame it was laid on. */
    record Piece(float[] corners, int laidOn) {
    }

    /** The pieces of one look, a ring the oldest of which is taken again. */
    static final class Ring {
        final Visuals.TrackLook look;
        final Piece[] pieces = new Piece[POOL];
        int next;

        Ring(Visuals.TrackLook look) {
            this.look = look;
        }

        void lay(Piece piece) {
            pieces[next] = piece;
            next = (next + 1) % POOL;
        }

        /** How clear piece {@code i} is at {@code frame}: 1 in its life, falling to 0 over its fade; 0 for none. */
        float alpha(int i, int frame) {
            var piece = pieces[i];
            if (piece == null) {
                return 0f;
            }
            int age = frame - piece.laidOn();
            if (age >= look.lifeFrames()) {
                return 0f;
            }
            int fadeFrom = look.lifeFrames() - look.fadeFrames();
            return age < fadeFrom ? 1f : 1f - (age - fadeFrom) / (float) Math.max(1, look.fadeFrames());
        }
    }

    /** Where a thing's sides last laid a piece, by thing. */
    private record Last(float[] sides) {
    }

    private final Node node;
    private final Function<String, Material> pictures;
    private final Map<Visuals.TrackLook, Ring> rings = new HashMap<>();
    private final Map<Visuals.TrackLook, Geometry> drawn = new HashMap<>();
    private final Map<Integer, Last> last = new HashMap<>();

    /**
     * @param node     where the marks are hung
     * @param pictures the material a look's picture is drawn with, or null for one that will not load
     */
    TrackMarks(Node node, Function<String, Material> pictures) {
        this.node = node;
        this.pictures = pictures;
    }

    /**
     * A frame of the picture: each thing of {@code looks}' templates laying its pieces, every look's pieces drawn as
     * clear as their age leaves them.
     *
     * @param height the ground's drawn height at a point
     * @param water  whether a point is on water
     */
    void see(List<UnitView> units, Map<String, Visuals.TrackLook> looks, int frame,
            java.util.function.BiFunction<Float, Float, Float> height,
            java.util.function.BiPredicate<Float, Float> water) {
        if (looks.isEmpty()) {
            return;
        }
        var seen = new java.util.HashSet<Integer>();
        for (var view : units) {
            var look = looks.get(view.templateName());
            if (look == null) {
                continue;
            }
            seen.add(view.id());
            layBehind(view, look, frame, height, water);
        }
        last.keySet().retainAll(seen);
        rings.forEach((look, ring) -> draw(look, ring, frame));
    }

    void layBehind(UnitView view, Visuals.TrackLook look, int frame,
            java.util.function.BiFunction<Float, Float, Float> height,
            java.util.function.BiPredicate<Float, Float> water) {
        var sides = sidesOf(view, look);
        var was = last.get(view.id());
        boolean moving = view.speed() > look.minSpeed() && !water.test(view.x(), view.y());
        if (!moving) {
            last.remove(view.id()); // stopped, or afloat: the next mark starts where it sets off again
            return;
        }
        if (was == null) {
            last.put(view.id(), new Last(sides));
            return;
        }
        float dx = sides[0] - was.sides()[0];
        float dy = sides[1] - was.sides()[1];
        if (dx * dx + dy * dy < look.width() * look.width()) {
            return; // a piece a width long at least
        }
        var ring = rings.computeIfAbsent(look, Ring::new);
        float half = look.width() / 2f;
        float acrossX = -FastMath.sin(view.orientation()) * half;
        float acrossY = FastMath.cos(view.orientation()) * half;
        for (int side = 0; side < sides.length; side += 2) {
            float fx = was.sides()[side];
            float fy = was.sides()[side + 1];
            float tx = sides[side];
            float ty = sides[side + 1];
            ring.lay(new Piece(new float[] {
                fx - acrossX, height.apply(fx - acrossX, fy - acrossY), fy - acrossY,
                fx + acrossX, height.apply(fx + acrossX, fy + acrossY), fy + acrossY,
                tx + acrossX, height.apply(tx + acrossX, ty + acrossY), ty + acrossY,
                tx - acrossX, height.apply(tx - acrossX, ty - acrossY), ty - acrossY}, frame));
        }
        last.put(view.id(), new Last(sides));
    }

    /** The middles of its sides on the ground, {@code x, y, …}: one for a single line, else spread across it. */
    static float[] sidesOf(UnitView view, Visuals.TrackLook look) {
        int count = Math.max(1, look.sides());
        var sides = new float[count * 2];
        float acrossX = -FastMath.sin(view.orientation());
        float acrossY = FastMath.cos(view.orientation());
        for (int i = 0; i < count; i++) {
            float off = count == 1 ? 0f : look.apart() * (i / (float) (count - 1) - 0.5f);
            sides[i * 2] = view.x() + acrossX * off;
            sides[i * 2 + 1] = view.y() + acrossY * off;
        }
        return sides;
    }

    Ring ring(Visuals.TrackLook look) {
        return rings.get(look);
    }

    private void draw(Visuals.TrackLook look, Ring ring, int frame) {
        var geometry = drawn.computeIfAbsent(look, this::geometryFor);
        if (geometry == null) {
            return;
        }
        var mesh = geometry.getMesh();
        var positions = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();
        var colours = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Color).getData();
        positions.clear();
        colours.clear();
        for (int i = 0; i < POOL; i++) {
            float alpha = ring.alpha(i, frame);
            var corners = ring.pieces[i] == null ? new float[12] : ring.pieces[i].corners();
            for (int c = 0; c < 4; c++) {
                positions.put(corners[c * 3]).put(corners[c * 3 + 1] + 0.05f).put(corners[c * 3 + 2]);
                colours.put(1f).put(1f).put(1f).put(alpha);
            }
        }
        positions.flip();
        colours.flip();
        mesh.getBuffer(VertexBuffer.Type.Position).updateData(positions);
        mesh.getBuffer(VertexBuffer.Type.Color).updateData(colours);
        mesh.updateBound();
    }

    private Geometry geometryFor(Visuals.TrackLook look) {
        var material = pictures.apply(look.picture());
        if (material == null) {
            return null;
        }
        material.setBoolean("VertexColor", true);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(POOL * 4 * 3));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(POOL * 4 * 4));
        var uvs = BufferUtils.createFloatBuffer(POOL * 4 * 2);
        var indices = BufferUtils.createIntBuffer(POOL * 6);
        for (int i = 0; i < POOL; i++) {
            uvs.put(0f).put(0f).put(1f).put(0f).put(1f).put(1f).put(0f).put(1f);
            int at = i * 4;
            indices.put(at).put(at + 1).put(at + 2).put(at).put(at + 2).put(at + 3);
        }
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs.flip());
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices.flip());
        var geometry = new Geometry("track marks", mesh);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        node.attachChild(geometry);
        return geometry;
    }

    /** Every mark gone, for a new world. */
    void clear() {
        drawn.values().forEach(geometry -> {
            if (geometry != null) {
                geometry.removeFromParent();
            }
        });
        drawn.clear();
        rings.clear();
        last.clear();
    }
}
