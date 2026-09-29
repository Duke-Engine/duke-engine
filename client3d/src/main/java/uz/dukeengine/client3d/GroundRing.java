package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.function.BiFunction;
import uz.dukeengine.core.math.Coord3D;

/**
 * A circle drawn on the floor: an unbroken line, and a faint wash inside it.
 *
 * <p>The client draws circles on the ground for two quite different reasons — how
 * far a skill reaches, and which creature an order was given to — and they want
 * exactly the same thing: a line that stays a line at any size, a wash that says
 * "this, inside here", and both readable in a dark room. Written once so the two
 * cannot drift apart, and so that the next reason to draw one costs nothing.
 *
 * <p><b>The line is rewritten, not scaled.</b> Scaling a band scales its thickness
 * with it, so a ring at sixty and a ring at nine would be drawn in two different
 * weights of line — and a ruler whose markings get fatter the further out they are
 * is not a ruler. So the corners are written into buffers it keeps, and a buffer
 * is made again only when a larger ring needs more of them.
 *
 * <p><b>Laid over the ground, not flat.</b> Every point of the line and of the wash
 * stands on the ground under it ({@link GroundDisc#STEP} apart at most, so it
 * follows a hill between them), where one height at its middle put the half of a
 * ring on a slope that climbs under the hill.
 */
final class GroundRing {

    /** How far apart its points stand at most — see {@link GroundDisc#STEP}. */
    static final float STEP = GroundDisc.STEP;

    private final float bandWidth;
    private final float brightness;
    private final Node node = new Node("ring");
    private final Geometry band;
    private final Geometry fill;
    private final GroundDisc wash;
    private FloatBuffer bandCorners = BufferUtils.createFloatBuffer(0);
    private int bandPoints = -1;
    /**
     * Where and how large it was last laid, and on what ground — under its middle and at four points of its edge, so
     * a new floor under a ring that stood still is noticed: laid again only when one of them moves.
     */
    private float laidX = Float.NaN;
    private float laidY;
    private float laidRadius;
    private final float[] laidGround = new float[5];

    GroundRing(AssetManager assets, Node parent, float bandWidth, int segments,
            float brightness) {
        this.bandWidth = Math.max(0.05f, bandWidth);
        this.brightness = Math.max(0f, brightness);
        this.wash = new GroundDisc(Math.clamp(segments, 12, GroundDisc.MOST_POINTS));
        band = Glow.inTheGlow(new Geometry("band", new Mesh()), Glow.material(assets));
        fill = Glow.inTheGlow(new Geometry("wash", new Mesh()), Glow.material(assets));
        node.attachChild(fill);
        node.attachChild(band);
        parent.attachChild(node);
        hide();
    }

    void hide() {
        node.setCullHint(Spatial.CullHint.Always);
    }

    boolean showing() {
        return node.getLocalCullHint() != Spatial.CullHint.Always;
    }

    /** Where it is, for anything that wants to check. */
    Node node() {
        return node;
    }

    /**
     * Draw the ring round a spot.
     *
     * @param height how far above the floor it lies
     * @param edge   how strongly the line itself is drawn
     * @param wash   how strongly the inside is filled; zero for a bare circle
     */
    void show(Coord3D at, float radius, float height, int colour, float edge, float wash,
            BiFunction<Float, Float, Float> floorAt) {
        if (radius <= 0.01f || edge <= 0f && wash <= 0f) {
            hide();
            return;
        }
        node.setCullHint(Spatial.CullHint.Inherit);
        float base = floorAt.apply(at.x(), at.y());
        node.setLocalTranslation(at.x(), base + height, at.y());
        if (at.x() != laidX || at.y() != laidY || radius != laidRadius || !sameGround(at, radius, base, floorAt)) {
            laidX = at.x();
            laidY = at.y();
            laidRadius = radius;
            layBand(at, radius, base, floorAt);
            this.wash.lay(fill.getMesh(), at, radius, base, floorAt);
            band.updateModelBound();
            fill.updateModelBound();
        }
        band.getMaterial().setColor("Color", Glow.colour(colour, brightness, Math.min(1f, edge)));
        fill.getMaterial().setColor("Color", Glow.colour(colour, brightness, Math.min(1f, wash)));
    }

    /** Whether the ground under it is as it was when it was last laid, keeping what it is now. */
    private boolean sameGround(Coord3D at, float radius, float base, BiFunction<Float, Float, Float> floorAt) {
        var now = new float[] {base, floorAt.apply(at.x() + radius, at.y()), floorAt.apply(at.x() - radius, at.y()),
            floorAt.apply(at.x(), at.y() + radius), floorAt.apply(at.x(), at.y() - radius)};
        boolean same = java.util.Arrays.equals(now, laidGround);
        System.arraycopy(now, 0, laidGround, 0, now.length);
        return same;
    }

    /** The band's corners onto the circle this ring now is, each on the ground under it. */
    private void layBand(Coord3D at, float radius, float base, BiFunction<Float, Float, Float> floorAt) {
        int around = wash.pointsAround(radius);
        float inner = Math.max(0f, radius - bandWidth * 0.5f);
        float outer = radius + bandWidth * 0.5f;
        if (bandCorners.capacity() < around * 2 * 3) {
            bandCorners = BufferUtils.createFloatBuffer(around * 2 * 3);
        }
        bandCorners.clear();
        var u = wash.unitsFor(around);
        for (int point = 0; point < around; point++) {
            GroundDisc.put(bandCorners, at, u[point * 2] * inner, u[point * 2 + 1] * inner, base, floorAt);
            GroundDisc.put(bandCorners, at, u[point * 2] * outer, u[point * 2 + 1] * outer, base, floorAt);
        }
        var mesh = band.getMesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, bandCorners.flip());
        if (around != bandPoints) {
            bandPoints = around;
            var order = BufferUtils.createShortBuffer(around * 6);
            for (int point = 0; point < around; point++) {
                short here = (short) (point * 2);
                short next = (short) (((point + 1) % around) * 2);
                order.put(here).put((short) (here + 1)).put((short) (next + 1));
                order.put(here).put((short) (next + 1)).put(next);
            }
            mesh.setBuffer(VertexBuffer.Type.Index, 3, order.flip());
        }
        mesh.updateCounts();
    }
}
