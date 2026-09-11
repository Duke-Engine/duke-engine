package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
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
 * The picture a skill draws on the floor while the player is deciding where to
 * put it.
 *
 * <p>Answering three questions he otherwise has to answer by spending the skill:
 * <em>how far does this reach</em>, <em>can I hit that from here</em>, and for
 * anything that flies, <em>which way will it go</em>. A cooldown makes guessing
 * expensive, so guessing is the thing to remove.
 *
 * <p>What is drawn follows from what he is being asked to point at rather than
 * from what the skill does — see {@link SkillRange.Shape}. Five shapes, one
 * visual language: a <b>ring</b> is a reach, a <b>filled disc</b> is what
 * something will cover, a <b>lane</b> is where something will fly, and
 * <b>red</b> anywhere means the click will be refused.
 *
 * <p><b>Nothing is built while the game runs.</b> Only one skill can be armed at
 * a time, so there is exactly one set of geometry here; it is made once and shown
 * or hidden. The circles themselves are {@link GroundRing}, which is also what
 * marks the creature an attack was ordered on -- one drawing, so the two cannot
 * drift into looking like different games.
 */
final class RangeRings {

    private final RangeLook look;
    private final Node root;
    private final GroundRing reach;
    private final GroundRing area;
    private final Lane lane;

    RangeRings(AssetManager assets, Node parent, RangeLook look) {
        this.look = look == null ? RangeLook.DEFAULT : look;
        this.root = new Node("skill-range");
        parent.attachChild(root);
        this.reach = ring(assets, root, this.look);
        this.area = ring(assets, root, this.look);
        this.lane = new Lane(assets, root);
        hide();
    }

    /** Nothing is armed; show nothing. */
    void hide() {
        reach.hide();
        area.hide();
        lane.hide();
    }

    /**
     * Draw the picture for an armed skill.
     *
     * @param hero    where the caster stands
     * @param pointer where the player is pointing on the floor, or null when he is
     *                not pointing at anything — a skill that needs no aiming
     * @param allowed whether the click as it stands would be obeyed
     * @param seconds the wall clock, which the ring breathes by and nothing else
     *                uses: it appears at its size and stands still
     */
    void show(SkillRange range, Coord3D hero, Coord3D pointer, boolean allowed,
            float seconds, BiFunction<Float, Float, Float> floorAt) {
        if (range == null || hero == null) {
            hide();
            return;
        }
        float bright = look.breath(seconds);
        int edge = allowed ? look.allowColour() : look.denyColour();

        switch (range.shape()) {
            case AT_A_CREATURE -> {
                reach.show(hero, range.reach(), look.height(), edge,
                        look.edgeAlpha() * bright, look.fillAlpha() * bright, floorAt);
                area.hide();
                lane.hide();
            }
            case AT_A_SPOT -> {
                // His reach stays his reach whatever the cursor is doing; it is the
                // blast that turns red, because that is the half of the picture the
                // refusal is about.
                reach.show(hero, range.reach(), look.height(), look.allowColour(),
                        look.edgeAlpha() * bright, look.fillAlpha() * 0.6f * bright, floorAt);
                // A skill that leaves nothing where it lands draws nothing there:
                // a dash puts a man on a spot, and a circle round a man-sized spot
                // is a second ring saying what the pointer already said.
                var at = range.within(hero, pointer == null ? hero : pointer);
                area.show(at, range.area(), look.height(),
                        allowed ? look.areaColour() : look.denyColour(),
                        look.edgeAlpha() * bright, look.fillAlpha() * 2f * bright, floorAt);
                lane.hide();
            }
            case DOWN_A_LANE -> {
                reach.hide();
                area.hide();
                lane.show(hero, pointer, range.reach(), range.area(),
                        Glow.colour(edge, look.brightness(), look.edgeAlpha() * bright), floorAt,
                        look.height());
            }
            case AROUND_HIM -> {
                // The ring IS the blast here, so it is drawn as one: the area's
                // colour and a stronger wash inside it.
                reach.show(hero, range.reach(), look.height(), look.areaColour(),
                        look.edgeAlpha() * bright, look.fillAlpha() * 2f * bright, floorAt);
                area.hide();
                lane.hide();
            }
            case ON_HIMSELF -> {
                reach.show(hero, range.reach(), look.height(), look.areaColour(),
                        look.edgeAlpha() * bright, look.fillAlpha() * 2.5f * bright, floorAt);
                area.hide();
                lane.hide();
            }
        }
    }

    /** Whether anything at all is on screen. */
    boolean showing() {
        return root.getLocalCullHint() != Spatial.CullHint.Always
                && (reach.showing() || area.showing() || lane.showing());
    }

    Node node() {
        return root;
    }

    private static GroundRing ring(AssetManager assets, Node root, RangeLook look) {
        return new GroundRing(assets, root, look.bandWidth(), look.segments(), look.brightness());
    }

    // ---- the lane a shot flies down ----

    /**
     * The lane: a band out of the caster toward the cursor, with a head on the end.
     *
     * <p>Drawn at the shot's real length and real width, because a lane that is
     * merely suggestive is worse than none — the player will trust it, stand
     * someone in it, and be wrong. The head on the end is what tells him which way
     * it is going when he is looking at a lane that happens to lie across him.
     */
    private static final class Lane {

        private static final int VERTICES = 7;

        private final Node node = new Node("lane");
        private final Geometry band;
        private final FloatBuffer corners = BufferUtils.createFloatBuffer(VERTICES * 3);

        Lane(AssetManager assets, Node parent) {
            band = Glow.inTheGlow(new Geometry("lane-band", laneMesh(corners)),
                    Glow.material(assets));
            node.attachChild(band);
            parent.attachChild(node);
        }

        void hide() {
            node.setCullHint(Spatial.CullHint.Always);
        }

        boolean showing() {
            return node.getLocalCullHint() != Spatial.CullHint.Always;
        }

        void show(Coord3D from, Coord3D toward, float length, float width,
                com.jme3.math.ColorRGBA colour, BiFunction<Float, Float, Float> floorAt,
                float height) {
            if (length <= 0.01f) {
                hide();
                return;
            }
            node.setCullHint(Spatial.CullHint.Inherit);
            node.setLocalTranslation(from.x(), floorAt.apply(from.x(), from.y()) + height,
                    from.y());
            float heading = toward == null ? 0f
                    : (float) Math.atan2(toward.y() - from.y(), toward.x() - from.x());
            node.setLocalRotation(new Quaternion().fromAngleAxis(-heading, Vector3f.UNIT_Y));
            write(length, width);
            band.getMaterial().setColor("Color", colour);
        }

        private void write(float length, float width) {
            float half = width * 0.5f;
            // The head is a fixed share of the lane rather than a number of its
            // own: a head sized in world units is a spike on a short lane and a
            // speck on a long one, and the lane's length is the thing that varies.
            float head = Math.min(length * 0.25f, width * 1.6f);
            float shaft = length - head;
            corners.clear();
            put(0f, -half);
            put(0f, half);
            put(shaft, half);
            put(shaft, -half);
            put(shaft, -half * 1.9f);
            put(shaft, half * 1.9f);
            put(length, 0f);
            corners.flip();
            band.getMesh().getBuffer(VertexBuffer.Type.Position).updateData(corners);
            band.getMesh().updateBound();
        }

        private void put(float along, float across) {
            corners.put(along).put(0f).put(across);
        }
    }

    // ---- the meshes, built once ----

    private static Mesh laneMesh(FloatBuffer corners) {
        var mesh = new Mesh();
        var order = BufferUtils.createShortBuffer(9);
        order.put((short) 0).put((short) 1).put((short) 2);
        order.put((short) 0).put((short) 2).put((short) 3);
        order.put((short) 4).put((short) 6).put((short) 5);
        order.flip();
        corners.limit(corners.capacity());
        mesh.setBuffer(VertexBuffer.Type.Position, 3, corners);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, order);
        mesh.updateBound();
        return mesh;
    }
}
