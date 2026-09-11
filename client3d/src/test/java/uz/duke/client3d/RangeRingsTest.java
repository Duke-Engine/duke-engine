package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;

/**
 * The ring really is the size the skill really is.
 *
 * <p>The one thing an indicator must not be is decorative. A player who has been
 * told he can reach sixty units will stand at fifty-nine and press the key, and
 * a ring drawn at any other size is worse than no ring at all — he cannot learn
 * the range by playing, because the picture keeps teaching him a different one.
 * So what is measured here is the drawn geometry against the number that came out
 * of the game's file.
 *
 * <p>Headless: {@code DesktopAssetManager} loads a material definition without a
 * window, so these are the real meshes and the real materials.
 */
class RangeRingsTest {

    private static final RangeLook LOOK = RangeLook.DEFAULT;

    private record Scene(RangeRings rings, Node root) {
    }

    private static Scene scene() {
        var root = new Node("world");
        return new Scene(new RangeRings(new DesktopAssetManager(true), root, LOOK), root);
    }

    private static Coord3D at(float x, float y) {
        return new Coord3D(x, y, 0f);
    }

    private static void draw(Scene scene, SkillRange range, Coord3D hero, Coord3D pointer,
            boolean allowed) {
        scene.rings().show(range, hero, pointer, allowed, 0f, (x, y) -> 0f);
        scene.root().updateGeometricState();
    }

    /** How wide the widest thing on screen is, measured off the scene graph. */
    private static float widthOf(Node node) {
        node.updateGeometricState();
        var bound = node.getWorldBound();
        return bound instanceof BoundingBox box ? box.getXExtent() * 2f : 0f;
    }

    private static Node shown(Node root, String name) {
        var found = find(root, name);
        return found instanceof Node node
                && node.getLocalCullHint() != Spatial.CullHint.Always ? node : null;
    }

    private static Spatial find(Spatial root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Node node) {
            for (var child : node.getChildren()) {
                var found = find(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * A ring drawn for a reach of sixty is sixty across from the middle.
     *
     * <p>Measured off the geometry rather than off the number that was passed in,
     * because the failure worth catching is a ring that is drawn at the radius as
     * though it were a diameter — which is half the reach, looks entirely
     * plausible, and teaches the player the wrong range all game.
     */
    @Test
    void theRingIsDrawnAtTheReachItWasGiven() {
        var scene = scene();
        var range = new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 60f, 0f);

        draw(scene, range, at(0f, 0f), null, true);

        // Across the whole circle, plus the band that straddles the line itself.
        assertEquals(120f + LOOK.bandWidth(), widthOf(scene.root()), 1f,
                "the ring should be its reach across in every direction");
    }

    /** And a different reach is a different ring, or the number is being ignored. */
    @Test
    void adifferentReachIsADifferentRing() {
        var small = scene();
        draw(small, new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 20f, 0f),
                at(0f, 0f), null, true);
        var large = scene();
        draw(large, new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 80f, 0f),
                at(0f, 0f), null, true);

        assertEquals(40f + LOOK.bandWidth(), widthOf(small.root()), 1f);
        assertEquals(160f + LOOK.bandWidth(), widthOf(large.root()), 1f);
    }

    /**
     * It is its full size on the first frame, and stays there.
     *
     * <p>It used to open out, which is what the genre does and which was wrong: an
     * indicator is a ruler, and a ruler you have to wait to settle is a ruler that
     * costs you the fraction of a second the skill was for. Written as a test
     * rather than left to be noticed, because an easing is the sort of thing that
     * creeps back in.
     */
    @Test
    void itIsItsFullSizeAtOnceAndDoesNotMove() {
        var scene = scene();
        var range = new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 60f, 0f);

        scene.rings().show(range, at(0f, 0f), null, true, 0f, (x, y) -> 0f);
        float atOnce = widthOf(scene.root());
        scene.rings().show(range, at(0f, 0f), null, true, 3f, (x, y) -> 0f);
        float later = widthOf(scene.root());

        assertEquals(120f + LOOK.bandWidth(), atOnce, 1f, "full size on the frame it appears");
        assertEquals(atOnce, later, 0.01f, "and exactly the same size a moment later");
    }

    /** The ring is unbroken, not a circle of dashes. */
    @Test
    void theRingIsOneUnbrokenLine() {
        var scene = scene();
        draw(scene, new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 60f, 0f),
                at(0f, 0f), null, true);

        var band = (Geometry) find(scene.root(), "band");
        assertEquals(LOOK.segments() * 2, band.getMesh().getVertexCount(),
                "two corners per segment and no gaps between them");
        assertEquals(LOOK.segments() * 2, band.getMesh().getTriangleCount(),
                "a closed strip, so every segment joins the next");
    }

    /**
     * A blast aimed at a spot draws two rings: his reach, and what it covers.
     *
     * <p>Warcraft's picture, and the reason it has lasted: the two questions a
     * player has — can I throw it that far, and will it catch them — are two
     * different circles, and one circle can only answer one of them.
     */
    @Test
    void aBlastAtASpotDrawsBothTheReachAndTheBlast() {
        var scene = scene();
        var range = new SkillRange('W', SkillRange.Shape.AT_A_SPOT, 60f, 15f);

        draw(scene, range, at(0f, 0f), at(30f, 0f), true);

        var rings = ((Node) find(scene.root(), "skill-range")).getChildren().stream()
                .filter(child -> "ring".equals(child.getName()))
                .filter(child -> child.getLocalCullHint() != Spatial.CullHint.Always)
                .toList();
        assertEquals(2, rings.size(), "the reach and the blast are two questions");
        assertEquals(0f, rings.get(0).getLocalTranslation().x, 0.01f, "his reach is round him");
        assertEquals(30f, rings.get(1).getLocalTranslation().x, 0.01f,
                "and the blast is under the cursor");
    }

    /** The blast follows the cursor only as far as he can throw it. */
    @Test
    void theBlastStopsAtTheEdgeOfHisReach() {
        var scene = scene();
        var range = new SkillRange('W', SkillRange.Shape.AT_A_SPOT, 60f, 15f);

        draw(scene, range, at(0f, 0f), at(500f, 0f), true);

        var blast = ((Node) find(scene.root(), "skill-range")).getChildren().stream()
                .filter(child -> "ring".equals(child.getName()))
                .filter(child -> child.getLocalCullHint() != Spatial.CullHint.Always)
                .skip(1)
                .findFirst()
                .orElseThrow();
        assertEquals(60f, blast.getLocalTranslation().x, 0.01f,
                "pointing past the edge means the edge, and the picture has to say so");
    }

    /** Ground he cannot aim at turns the picture red. */
    @Test
    void aRefusedSpotIsDrawnInTheRefusingColour() {
        var allowed = scene();
        draw(allowed, new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 60f, 0f),
                at(0f, 0f), at(10f, 0f), true);
        var refused = scene();
        draw(refused, new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 60f, 0f),
                at(0f, 0f), at(10f, 0f), false);

        var yes = ringColour(allowed.root());
        var no = ringColour(refused.root());
        assertNotEquals(yes, no, "the two answers should not look the same");
        assertTrue(no.r > no.g, "and refusal is red");
    }

    /** A shot down a lane is drawn along the line he is pointing, at its real length. */
    @Test
    void aLaneRunsFromHimTowardWhereHeIsPointing() {
        var scene = scene();
        var range = new SkillRange('E', SkillRange.Shape.DOWN_A_LANE, 70f, 6f);

        draw(scene, range, at(0f, 0f), at(100f, 0f), true);

        var lane = shown(scene.root(), "lane");
        assertTrue(lane != null, "a skillshot should draw its lane");
        assertEquals(70f, widthOf(lane), 1f, "as long as the shot really travels");
        assertEquals(35f, ((BoundingBox) lane.getWorldBound()).getCenter().x, 1f,
                "running out of him in the direction he pointed");
    }

    /** Pointing the other way turns the lane the other way. */
    @Test
    void theLaneFollowsThePointer() {
        var scene = scene();
        var range = new SkillRange('E', SkillRange.Shape.DOWN_A_LANE, 70f, 6f);

        draw(scene, range, at(0f, 0f), at(0f, 100f), true);

        var lane = shown(scene.root(), "lane");
        assertEquals(35f, ((BoundingBox) lane.getWorldBound()).getCenter().z, 1f,
                "pointed north, the lane should run north");
    }

    /** Nothing armed, nothing drawn. */
    @Test
    void nothingIsDrawnWhenNothingIsArmed() {
        var scene = scene();
        draw(scene, new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 60f, 0f),
                at(0f, 0f), null, true);

        scene.rings().hide();

        assertTrue(!scene.rings().showing(), "a disarmed key leaves nothing on the floor");
    }

    /** A ring on an upper floor lies on that floor. */
    @Test
    void aRingSitsOnTheFloorTheCasterIsStandingOn() {
        var scene = scene();
        scene.rings().show(new SkillRange('Q', SkillRange.Shape.AROUND_HIM, 40f, 0f),
                at(0f, 0f), null, true, 0f, (x, y) -> 40f);

        var ring = shown(scene.root(), "ring");
        assertEquals(40f + LOOK.height(), ring.getLocalTranslation().y, 0.001f);
    }

    private static ColorRGBA ringColour(Node root) {
        var band = (Geometry) find(root, "band");
        return (ColorRGBA) band.getMaterial().getParam("Color").getValue();
    }
}
