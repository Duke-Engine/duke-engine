package uz.dukeengine.client3d.rpg;

import com.jme3.math.FastMath;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

/**
 * The marks cut into the slots and the cards, as line drawings in a 24-by-24
 * square.
 *
 * <p>Drawn here rather than loaded, because an icon that is four straight lines
 * is smaller as four straight lines than as a file, and because a glyph that
 * is drawn takes the panel's colours without a second copy of the palette
 * living in an image.
 *
 * <p>They are pictures of what the thing is rather than of what it hits: an
 * arrow leaving, a burst around a centre, a stride, a star. The names are a
 * small vocabulary a game picks from, the same way it picks the words — so a
 * dungeon says "clock" and gets a clock without this class knowing what a
 * cooldown is.
 */
final class PanelGlyphs {

    private PanelGlyphs() {
    }

    /** An arrow flying up and to the right — the shot that goes further. */
    private static final float[][] SHOT = {
        {4, 20, 20, 4}, {14, 4, 20, 4, 20, 10}, {9, 15, 6, 18},
    };

    /** A ring with rays off it — something going off where he stands. */
    private static final float[][] BURST = ring();

    /** A stride: a long step and the ground ahead of it. */
    private static final float[][] DASH = {
        {3, 18, 8, 18, 11, 12, 14, 21, 17, 6}, {18, 6, 21, 6},
    };

    /** A star, for the one that is not like the others. */
    private static final float[][] STAR = {
        {12, 2, 14.6f, 8.4f, 21, 11, 14.6f, 13.6f, 12, 20, 9.4f, 13.6f, 3, 11, 9.4f, 8.4f,
            12, 2},
    };

    /** A dial with a hand — time, and so a cooldown. */
    private static final float[][] CLOCK = clock();

    /** A heart — health coming back. */
    private static final float[][] HEART = {
        {12, 21, 5, 15, 3, 11, 4, 7, 8, 5.5f, 12, 8, 16, 5.5f, 20, 7, 21, 11, 19, 15, 12, 21},
    };

    /** A boot in stride — moving faster. */
    private static final float[][] BOOT = {
        {8, 3, 8, 14, 5, 17, 5, 20, 18, 20, 18, 17, 13, 14, 13, 3, 8, 3},
    };

    /** A plus — more of something. */
    private static final float[][] PLUS = {
        {12, 5, 12, 19}, {5, 12, 19, 12},
    };

    /** A doubling mark — the same thing again. */
    private static final float[][] TIMES = {
        {5, 6, 12, 13}, {12, 6, 5, 13}, {14, 18, 16, 16, 16, 20, 20, 20},
    };

    /** A skull: two sockets, a nose and a jaw. Something that is not his. */
    private static final float[][] SKULL = {
        {4, 10, 4, 16, 7, 19, 7, 21, 17, 21, 17, 19, 20, 16, 20, 10,
            17, 4, 12, 2, 7, 4, 4, 10},
        circle(9f, 12f, 2.4f, 10), circle(15f, 12f, 2.4f, 10),
        {12, 15, 10.6f, 17.5f, 13.4f, 17.5f, 12, 15},
        {9.5f, 19, 9.5f, 21}, {12, 19, 12, 21}, {14.5f, 19, 14.5f, 21},
    };

    /** A blade on the diagonal, with its guard — what he hits for. */
    private static final float[][] BLADE = {
        {4, 20, 20, 4}, {14, 4, 20, 4, 20, 10}, {6, 14, 10, 18}, {4, 16, 8, 20},
    };

    /** A shield — what gets through, and the order to stand behind one. */
    private static final float[][] SHIELD = {
        {12, 3, 20, 6, 20, 12, 12, 21, 4, 12, 4, 6, 12, 3},
    };

    /** A bolt — how fast he moves. */
    private static final float[][] BOLT = {
        {13, 2, 4, 14, 11, 14, 10, 22, 19, 10, 12, 10, 13, 2},
    };

    /** A flask — something drunk, or eaten, or otherwise used up. */
    private static final float[][] FLASK = {
        {9, 3, 9, 9, 5, 18, 6, 21, 18, 21, 19, 18, 15, 9, 15, 3},
        {8, 3, 16, 3}, {6.5f, 15, 17.5f, 15},
    };

    /** Banded armour — the heavier of the two things that stop a blow. */
    private static final float[][] PLATE = {
        {12, 3, 20, 6, 20, 12, 12, 21, 4, 12, 4, 6, 12, 3},
        {5, 10, 19, 10}, {5.5f, 14, 18.5f, 14},
    };

    /** An arrow coming down onto a line — "walk to there". */
    private static final float[][] MARCH = {
        {12, 3, 12, 15}, {7, 11, 12, 16, 17, 11}, {5, 20, 19, 20},
    };

    /** A filled-looking square — "stop". */
    private static final float[][] HALT = {
        {6, 6, 18, 6, 18, 18, 6, 18, 6, 6},
        {8.5f, 8.5f, 15.5f, 8.5f, 15.5f, 15.5f, 8.5f, 15.5f, 8.5f, 8.5f},
    };

    /** A cut stone — the sort of thing found in a chest. */
    private static final float[][] GEM = {
        {12, 3, 20, 10, 12, 21, 4, 10, 12, 3}, {4, 10, 20, 10}, {12, 3, 8, 10, 12, 21},
        {12, 3, 16, 10, 12, 21},
    };

    /** Anything the design did not draw: a plain lozenge, so nothing is blank. */
    private static final float[][] PLAIN = {
        {12, 4, 19, 12, 12, 20, 5, 12, 12, 4},
    };

    private static float[][] ring() {
        var paths = new float[9][];
        paths[0] = circle(12f, 12f, 4f, 16);
        float[][] rays = {
            {12, 3, 12, 6}, {12, 18, 12, 21}, {3, 12, 6, 12}, {18, 12, 21, 12},
            {5.6f, 5.6f, 7.7f, 7.7f}, {16.3f, 16.3f, 18.4f, 18.4f},
            {18.4f, 5.6f, 16.3f, 7.7f}, {7.7f, 16.3f, 5.6f, 18.4f},
        };
        System.arraycopy(rays, 0, paths, 1, rays.length);
        return paths;
    }

    private static float[][] clock() {
        return new float[][] {circle(12f, 12f, 9f, 20), {12, 7, 12, 12, 15, 14}};
    }

    private static float[] circle(float x, float y, float radius, int steps) {
        var points = new float[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            float angle = FastMath.TWO_PI * i / steps;
            points[i * 2] = x + radius * FastMath.cos(angle);
            points[i * 2 + 1] = y + radius * FastMath.sin(angle);
        }
        return points;
    }

    /**
     * One glyph as a line mesh, centred on the origin and scaled to {@code size}.
     * The drawings are in screen order — y downwards, as a designer writes
     * them — and the flip to the client's upward y happens once, here.
     */
    static Mesh of(String named, float size) {
        var paths = switch (named) {
            case "Q", "shot" -> SHOT;
            case "W", "burst" -> BURST;
            case "E", "dash" -> DASH;
            case "R", "star" -> STAR;
            case "clock" -> CLOCK;
            case "heart" -> HEART;
            case "boot" -> BOOT;
            case "plus" -> PLUS;
            case "times" -> TIMES;
            case "skull" -> SKULL;
            case "blade" -> BLADE;
            case "shield" -> SHIELD;
            case "bolt" -> BOLT;
            case "flask" -> FLASK;
            case "plate" -> PLATE;
            case "march" -> MARCH;
            case "halt" -> HALT;
            case "gem" -> GEM;
            default -> PLAIN;
        };
        int segments = 0;
        for (var path : paths) {
            segments += path.length / 2 - 1;
        }
        var positions = new float[segments * 2 * 3];
        float scale = size / 24f;
        int at = 0;
        for (var path : paths) {
            for (int i = 0; i + 3 < path.length; i += 2) {
                at = put(positions, at, path[i], path[i + 1], scale);
                at = put(positions, at, path[i + 2], path[i + 3], scale);
            }
        }
        var mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3,
                BufferUtils.createFloatBuffer(positions));
        mesh.updateBound();
        return mesh;
    }

    private static int put(float[] positions, int at, float x, float y, float scale) {
        positions[at] = (x - 12f) * scale;
        positions[at + 1] = (12f - y) * scale;
        positions[at + 2] = 0f;
        return at + 3;
    }
}
