package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.function.BiFunction;
import uz.duke.core.math.Coord3D;

/**
 * A circle drawn flat on the floor: an unbroken line, and a faint wash inside it.
 *
 * <p>The client draws circles on the ground for two quite different reasons — how
 * far a skill reaches, and which creature an order was given to — and they want
 * exactly the same thing: a line that stays a line at any size, a wash that says
 * "this, inside here", and both readable in a dark room. Written once so the two
 * cannot drift apart, and so that the next reason to draw one costs nothing.
 *
 * <p><b>The line is rewritten, not scaled.</b> That is the whole reason this keeps
 * a buffer of its own. Scaling a band scales its thickness with it, so a ring at
 * sixty and a ring at nine would be drawn in two different weights of line — and a
 * ruler whose markings get fatter the further out they are is not a ruler. So the
 * corners are written into the buffer it already owns, a few hundred floats a
 * frame, and nothing is allocated while the game runs.
 *
 * <p>The wash is a plain disc of radius one and <em>is</em> simply scaled, because
 * a disc has no thickness for scaling to distort.
 */
final class GroundRing {

    private final float bandWidth;
    private final int segments;
    private final float brightness;
    private final Node node = new Node("ring");
    private final Geometry band;
    private final Geometry fill;
    private final FloatBuffer corners;

    GroundRing(AssetManager assets, Node parent, float bandWidth, int segments,
            float brightness) {
        this.bandWidth = Math.max(0.05f, bandWidth);
        this.segments = Math.clamp(segments, 12, 512);
        this.brightness = Math.max(0f, brightness);

        corners = BufferUtils.createFloatBuffer(this.segments * 2 * 3);
        band = Glow.inTheGlow(new Geometry("band", ring(this.segments, corners)),
                Glow.material(assets));
        fill = Glow.inTheGlow(new Geometry("wash", disc(this.segments)),
                Glow.material(assets));

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
        node.setLocalTranslation(at.x(), floorAt.apply(at.x(), at.y()) + height, at.y());
        writeBand(radius);
        fill.setLocalScale(radius, 1f, radius);
        band.getMaterial().setColor("Color", Glow.colour(colour, brightness, Math.min(1f, edge)));
        fill.getMaterial().setColor("Color", Glow.colour(colour, brightness, Math.min(1f, wash)));
    }

    /** Move the band's corners onto the circle this ring now is. */
    private void writeBand(float radius) {
        float inner = Math.max(0f, radius - bandWidth * 0.5f);
        float outer = radius + bandWidth * 0.5f;
        float step = FastMath.TWO_PI / segments;
        corners.clear();
        for (int segment = 0; segment < segments; segment++) {
            float angle = segment * step;
            put(inner, angle);
            put(outer, angle);
        }
        corners.flip();
        band.getMesh().getBuffer(VertexBuffer.Type.Position).updateData(corners);
        band.getMesh().updateBound();
    }

    private void put(float radius, float angle) {
        corners.put(FastMath.cos(angle) * radius).put(0f).put(FastMath.sin(angle) * radius);
    }

    /** Two points per segment, inner and outer, stitched into a closed strip. */
    private static Mesh ring(int segments, FloatBuffer corners) {
        var mesh = new Mesh();
        var order = BufferUtils.createShortBuffer(segments * 6);
        for (int segment = 0; segment < segments; segment++) {
            short here = (short) (segment * 2);
            short next = (short) (((segment + 1) % segments) * 2);
            order.put(here).put((short) (here + 1)).put((short) (next + 1));
            order.put(here).put((short) (next + 1)).put(next);
        }
        order.flip();
        corners.limit(corners.capacity());
        mesh.setBuffer(VertexBuffer.Type.Position, 3, corners);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, order);
        mesh.updateBound();
        return mesh;
    }

    /** A flat disc of radius 1, as a fan of triangles round its middle. */
    private static Mesh disc(int segments) {
        var mesh = new Mesh();
        var points = BufferUtils.createFloatBuffer((segments + 1) * 3);
        points.put(0f).put(0f).put(0f);
        for (int step = 0; step < segments; step++) {
            float angle = FastMath.TWO_PI * step / segments;
            points.put(FastMath.cos(angle)).put(0f).put(FastMath.sin(angle));
        }
        points.flip();
        var order = BufferUtils.createShortBuffer(segments * 3);
        for (int step = 0; step < segments; step++) {
            order.put((short) 0).put((short) (1 + step)).put((short) (1 + (step + 1) % segments));
        }
        order.flip();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, points);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, order);
        mesh.updateBound();
        return mesh;
    }
}
