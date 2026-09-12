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
                // Three drawings, each answering a different question. How far
                // can I throw it (the ring), what will the shot itself run into
                // (the lane, at the shot's own width), and how much does it take
                // with it when it stops (the circle where it would land).
                //
                // It used to be the lane alone, drawn at the width of the BURST --
                // so the one number the player most needed, his reach, was not on
                // screen at all, and the one that was there was a lie about how
                // wide the shot is.
                reach.show(hero, range.reach(), look.height(), look.areaColour(),
                        look.edgeAlpha() * bright, look.fillAlpha() * bright, floorAt);
                lane.show(hero, pointer, range.reach(), range.width(),
                        Glow.colour(edge, look.brightness(), look.edgeAlpha() * bright), floorAt,
                        look.height());
                if (range.area() > 0f) {
                    // Where it would land if nothing stopped it first. The shot
                    // bursts wherever it actually stops, which may be sooner -- a
                    // body, or a wall -- so this is the far end of the promise
                    // rather than a prediction.
                    area.show(range.within(hero, pointer == null ? hero : pointer),
                            range.area(), look.height(),
                            allowed ? look.areaColour() : look.denyColour(),
                            look.edgeAlpha() * bright, look.fillAlpha() * 2f * bright, floorAt);
                } else {
                    area.hide();
                }
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
     * The lane: a band out of the caster toward the cursor, sharpened to a point.
     *
     * <p>Drawn at the shot's real length and the shot's real WIDTH — what the
     * thing in flight will actually run into — because a lane that is merely
     * suggestive is worse than none: the player will trust it, stand someone in
     * it, and be wrong. It used to be drawn at the width of the BURST instead,
     * which made a fireball's lane as wide as its explosion and claimed the shot
     * would sweep a band five men across. Where the blast lands is now a circle
     * of its own, beside the ring that says how far he can throw it.
     *
     * <p><b>A pencil rather than an arrow.</b> It used to end in a head wider
     * than the shaft, which is how a diagram points at something — and at this
     * size, lying on the floor, it read as an enormous cursor rather than as the
     * path of a shot. Narrowing to a point says the same thing about direction
     * and says it the way a thrown thing looks.
     */
    private static final class Lane {

        private static final int VERTICES = 5;

        /**
         * How much of the lane is the point.
         *
         * <p>A fifth. Enough to read as sharpened from a long way up, and short
         * enough that the part a player is actually judging — where he may safely
         * stand — is still drawn at its true width.
         */
        private static final float TIP_SHARE = 0.2f;

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
            // Where the taper starts. A share of the LENGTH rather than a number
            // of its own, so a long shot and a short one are the same drawing at
            // two sizes -- a tip measured in world units is a spike on one and
            // the whole of the other.
            float shaft = length * (1f - TIP_SHARE);
            corners.clear();
            put(0f, -half);
            put(0f, half);
            put(shaft, half);
            put(shaft, -half);
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
        // The body, as two triangles...
        order.put((short) 0).put((short) 1).put((short) 2);
        order.put((short) 0).put((short) 2).put((short) 3);
        // ...and the point, which is the shaft's far edge brought together.
        order.put((short) 3).put((short) 2).put((short) 4);
        order.flip();
        corners.limit(corners.capacity());
        mesh.setBuffer(VertexBuffer.Type.Position, 3, corners);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, order);
        mesh.updateBound();
        return mesh;
    }
}
