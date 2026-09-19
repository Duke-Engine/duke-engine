package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The marks are drawn once and then lent out again.
 *
 * <p>What this is really guarding is a leak. The marker this replaced emptied its
 * node and built a fresh mesh and a fresh material every frame for every mark —
 * fine at one mark and exactly the shape of a problem at twenty, which is what a
 * minute of ordinary clicking produces. A pool is only a pool if it stops
 * growing, and nothing about a scene graph will say so out loud: it just gets
 * slower.
 *
 * <p>Runs headless. {@code DesktopAssetManager} loads a material definition
 * without a window, so everything here is the real thing rather than a stand-in.
 */
class ChevronsTest {

    private static final float NOW = 100f;

    private record Scene(Chevrons chevrons, Node root, OrderMarkers orders) {
    }

    private static Scene scene() {
        var root = new Node("markers");
        return new Scene(new Chevrons(new DesktopAssetManager(true), root, OrderMark.DEFAULTS),
                root, new OrderMarkers());
    }

    private static void draw(Scene scene, float now) {
        scene.chevrons().show(scene.orders().markers(), now, (x, y) -> 0f);
    }

    /** Every mark on screen is three arrowheads, points inward. */
    @Test
    void aMarkIsThreeArrowheads() {
        var scene = scene();
        scene.orders().add(50f, 50f, OrderMarkers.Kind.MOVE, NOW);

        draw(scene, NOW);

        assertEquals(1, scene.root().getChildren().size(), "one mark for one order");
        var mark = (Node) scene.root().getChild(0);
        assertEquals(3, mark.getChildren().size(), "Warcraft's three, a third of a turn apart");
    }

    /**
     * A second mark reuses nothing; a second <em>wave</em> reuses everything.
     *
     * <p>The whole point. Two marks at once need two sets of furniture, and the
     * pool must never build a third set for the next two.
     */
    @Test
    void theSceneStopsGrowingOnceItHasSeenTheBusiestMoment() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.MOVE, NOW);
        scene.orders().add(20f, 20f, OrderMarkers.Kind.MOVE, NOW);
        draw(scene, NOW);
        assertEquals(2, scene.chevrons().madeSoFar(), "two at once needs two");

        // A hundred more clicks, none of them overlapping.
        for (int click = 0; click < 100; click++) {
            float when = NOW + 10f + click;
            scene.orders().clear();
            scene.orders().add(click, click, OrderMarkers.Kind.MOVE, when);
            draw(scene, when);
        }

        assertEquals(2, scene.chevrons().madeSoFar(),
                "the pool built something new for a click it had furniture for");
        assertEquals(2, scene.root().getChildren().size(), "and the scene grew with it");
    }

    /** The same geometry really does come back, rather than an equal-looking one. */
    @Test
    void aFinishedMarkIsHiddenAndItsFurnitureComesBack() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.MOVE, NOW);
        draw(scene, NOW);
        var first = scene.root().getChild(0);
        var firstHead = ((Node) first).getChild(0);

        // Its life runs out, and nothing is on screen.
        scene.orders().clear();
        draw(scene, NOW + 10f);
        assertEquals(Spatial.CullHint.Always, first.getLocalCullHint(),
                "a spent mark should be out of sight");

        scene.orders().add(80f, 80f, OrderMarkers.Kind.MOVE, NOW + 10f);
        draw(scene, NOW + 10f);

        assertSame(first, scene.root().getChild(0), "the next order should reuse that mark");
        assertSame(firstHead, ((Node) scene.root().getChild(0)).getChild(0),
                "and its arrowheads, mesh and all");
        assertEquals(Spatial.CullHint.Inherit, first.getLocalCullHint(), "and show it again");
    }

    /** One mesh for every arrowhead there will ever be. */
    @Test
    void everyArrowheadSharesTheOneMesh() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.MOVE, NOW);
        scene.orders().add(20f, 20f, OrderMarkers.Kind.MOVE, NOW);
        draw(scene, NOW);

        var meshes = scene.root().getChildren().stream()
                .flatMap(mark -> ((Node) mark).getChildren().stream())
                .map(head -> ((Geometry) head).getMesh())
                .distinct()
                .toList();

        assertEquals(1, meshes.size(), "six arrowheads should be one mesh drawn six times");
    }

    /**
     * An order to attack leaves no arrowheads at all.
     *
     * <p>They are answers to different questions. A walk is answered where the
     * player pointed; an attack is given to somebody, and arrowheads closing on a
     * creature that is about to walk out from under them say "which one" badly. So
     * that one is drawn as a ring round the creature instead -- see AttackFlash --
     * and this is where the two are kept apart.
     */
    @Test
    void onlyWalkingOrdersLeaveArrowheads() {
        var move = scene();
        move.orders().add(10f, 10f, OrderMarkers.Kind.MOVE, NOW);
        draw(move, NOW);
        assertEquals(1, move.root().getChildren().size(), "a walk is three arrowheads");
        assertTrue(colourOf(move.root()).g > colourOf(move.root()).r, "go there is green");

        var attack = scene();
        attack.orders().add(10f, 10f, OrderMarkers.Kind.ATTACK, NOW);
        draw(attack, NOW);

        assertEquals(0, attack.chevrons().madeSoFar(),
                "an attack should not have asked this for anything");
    }

    /**
     * ★ An attack pointed at the FLOOR leaves arrowheads too — in red.
     *
     * <p>The third order and the one that made the split above stop being a
     * two-way choice. It is given to a place, so it is answered where the player
     * pointed exactly as a walk is; what he has asked for on the way there is a
     * fight, so it is answered in the other colour. Nothing else tells him apart
     * the two things his one attack key can mean.
     */
    @Test
    void anAttackOnTheFloorLeavesRedArrowheads() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.ATTACK_MOVE, NOW);

        draw(scene, NOW);

        assertEquals(1, scene.root().getChildren().size(),
                "an attack pointed at the floor left nothing where the player pointed");
        var red = colourOf(scene.root());
        assertTrue(red.r > red.g, "kill your way there should not be drawn in the colour of"
                + " a walk: " + red);
    }

    /** And it really does go out: the colour it is drawn in loses its alpha. */
    @Test
    void theColourFadesAsTheMarkArrives() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.MOVE, NOW);

        draw(scene, NOW);
        float atBirth = colourOf(scene.root()).a;
        draw(scene, NOW + OrderMark.DEFAULTS.seconds() * 0.95f);
        float nearlyDone = colourOf(scene.root()).a;

        assertEquals(1f, atBirth, 0.001f);
        assertTrue(nearlyDone < 0.2f, "it should be all but gone, but was at " + nearlyDone);
    }

    /** The arrowheads sit round the click, and close on it. */
    @Test
    void theArrowheadsCloseOnTheSpotThatWasClicked() {
        var scene = scene();
        scene.orders().add(160f, 240f, OrderMarkers.Kind.MOVE, NOW);

        draw(scene, NOW);
        float wide = spread(scene.root());
        draw(scene, NOW + OrderMark.DEFAULTS.seconds() * 0.95f);
        float gathered = spread(scene.root());

        var mark = scene.root().getChild(0);
        assertEquals(160f, mark.getLocalTranslation().x, 0.001f, "over the spot he clicked");
        assertEquals(240f, mark.getLocalTranslation().z, 0.001f, "in both directions");
        assertEquals(OrderMark.DEFAULTS.height(), mark.getLocalTranslation().y, 0.001f,
                "and just clear of the floor under it");
        assertEquals(OrderMark.DEFAULTS.startRadius(), wide, 0.01f, "set wide to begin with");
        assertTrue(gathered < wide * 0.5f,
                "and gathered by the end, but they were still " + gathered + " out");
    }

    /**
     * Every arrowhead points at the spot, not away from it.
     *
     * <p>The single thing most worth pinning here, because getting it backwards
     * costs nothing at compile time, throws nothing at run time, and turns the
     * effect into three shapes fleeing the place the player clicked. The point of
     * each is its own origin, so this asks whether its back edge is further out
     * than its tip.
     */
    @Test
    void everyArrowheadPointsInward() {
        var scene = scene();
        scene.orders().add(0f, 0f, OrderMarkers.Kind.MOVE, NOW);
        draw(scene, NOW);

        var mark = (Node) scene.root().getChild(0);
        for (var head : mark.getChildren()) {
            var tip = head.getLocalTranslation();
            // The mesh's back edge, put where the arrowhead actually is.
            var back = head.getLocalRotation()
                    .mult(new Vector3f(OrderMark.DEFAULTS.size(), 0f, 0f))
                    .add(tip);
            assertTrue(Math.hypot(back.x, back.z) > Math.hypot(tip.x, tip.z) + 0.5f,
                    "this one has its back to the click: tip " + Math.hypot(tip.x, tip.z)
                            + " out, back edge " + Math.hypot(back.x, back.z));
        }
    }

    /** And they are set evenly round it — a third of a turn apart. */
    @Test
    void theyAreSetAThirdOfATurnApart() {
        var scene = scene();
        scene.orders().add(0f, 0f, OrderMarkers.Kind.MOVE, NOW);
        draw(scene, NOW);

        var heads = List.copyOf(((Node) scene.root().getChild(0)).getChildren());
        var angles = heads.stream()
                .map(head -> Math.atan2(head.getLocalTranslation().z,
                        head.getLocalTranslation().x))
                .sorted()
                .toList();

        assertEquals(2 * Math.PI / 3, angles.get(1) - angles.get(0), 0.001);
        assertEquals(2 * Math.PI / 3, angles.get(2) - angles.get(1), 0.001);
    }

    /** A mark on an upper floor is drawn on that floor, not on the ground. */
    @Test
    void aMarkSitsOnTheFloorItWasOrderedOn() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.MOVE, NOW);

        scene.chevrons().show(scene.orders().markers(), NOW, (x, y) -> 40f);

        assertEquals(40f + OrderMark.DEFAULTS.height(),
                scene.root().getChild(0).getLocalTranslation().y, 0.001f);
    }

    /** A new world starts with nothing showing, whatever was on screen. */
    @Test
    void aNewWorldShowsNothing() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.MOVE, NOW);
        draw(scene, NOW);

        scene.chevrons().clear();

        assertEquals(Spatial.CullHint.Always, scene.root().getChild(0).getLocalCullHint());
    }

    private static ColorRGBA colourOf(Node root) {
        var head = (Geometry) ((Node) root.getChild(0)).getChild(0);
        var colour = (ColorRGBA) head.getMaterial().getParam("Color").getValue();
        assertNotNull(colour);
        return colour;
    }

    /** How far the arrowheads are from the middle of their mark. */
    private static float spread(Node root) {
        var mark = (Node) root.getChild(0);
        var heads = List.copyOf(mark.getChildren());
        float total = 0f;
        for (var head : heads) {
            var at = head.getLocalTranslation();
            total += (float) Math.hypot(at.x, at.z);
        }
        return total / heads.size();
    }
}
