package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;

/**
 * The ring round a creature an attack was ordered on: red, and really blinking.
 *
 * <p>Two things worth pinning, and they are the two ways this quietly becomes a
 * different effect. It has to be <b>off</b> in the gaps — a "blink" that only
 * dims is a thing nobody sees across a lit room, and it is the gap the eye
 * catches rather than the light. And it has to be drawn <b>round the creature</b>
 * rather than at his feet on the floor, because the question it answers is which
 * one, not where.
 */
class AttackFlashTest {

    private static final float NOW = 100f;
    private static final OrderMark LOOK = OrderMark.DEFAULT;

    private record Scene(AttackFlash flash, Node root, OrderMarkers orders) {
    }

    private static Scene scene() {
        var root = new Node("markers");
        return new Scene(new AttackFlash(new DesktopAssetManager(true), root, LOOK),
                root, new OrderMarkers());
    }

    private static void draw(Scene scene, float now) {
        scene.flash().show(scene.orders().markers(), now, id -> null, (x, y) -> 0f);
        scene.root().updateGeometricState();
    }

    private static Spatial shown(Node root) {
        for (var child : root.getChildren()) {
            if (child.getLocalCullHint() != Spatial.CullHint.Always) {
                return child;
            }
        }
        return null;
    }

    /** A walking order is not its business; it leaves nothing behind. */
    @Test
    void itAnswersOnlyAttackOrders() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.MOVE, NOW);

        draw(scene, NOW);

        assertEquals(0, scene.flash().madeSoFar(),
                "the arrowheads answer a walk; this should not have been asked");
    }

    /** An attack is a ring, round the creature, at the radius the file named. */
    @Test
    void anAttackIsARingRoundTheCreature() {
        var scene = scene();
        scene.orders().add(160f, 240f, OrderMarkers.Kind.ATTACK, NOW);

        draw(scene, NOW);

        var ring = shown(scene.root());
        assertTrue(ring != null, "an attack should have left a ring");
        assertEquals(160f, ring.getLocalTranslation().x, 0.001f, "on the creature");
        assertEquals(240f, ring.getLocalTranslation().z, 0.001f, "in both directions");
        assertEquals(LOOK.height(), ring.getLocalTranslation().y, 0.001f,
                "and just clear of the floor under it");
    }

    /**
     * It really goes out in between, twice, and then stays out.
     *
     * <p>Measured off the scene graph rather than off {@code blinkAt}, because
     * "the number says zero" and "nothing is on screen" are two different claims
     * and it is the second one the player sees.
     */
    @Test
    void itGoesOutAndComesBackAndThenStaysOut() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.ATTACK, NOW);
        float life = LOOK.seconds();

        draw(scene, NOW);
        assertTrue(shown(scene.root()) != null, "lit the moment it is ordered");

        draw(scene, NOW + life * 0.30f);
        assertEquals(null, shown(scene.root()), "and off in the gap, not merely dimmer");

        draw(scene, NOW + life * 0.55f);
        assertTrue(shown(scene.root()) != null, "and back for the second flash");

        draw(scene, NOW + life * 0.80f);
        assertEquals(null, shown(scene.root()), "and out again");

        draw(scene, NOW + life * 1.5f);
        assertEquals(null, shown(scene.root()), "and gone for good");
    }

    /** Red, because that is what the file says an attack order is. */
    @Test
    void itIsDrawnInTheAttackColour() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.ATTACK, NOW);

        draw(scene, NOW);

        var band = (Geometry) ((Node) shown(scene.root())).getChild(1);
        var colour = (ColorRGBA) band.getMaterial().getParam("Color").getValue();
        assertTrue(colour.r > colour.g && colour.r > colour.b,
                "kill that is red, but was " + colour);
    }

    /** Two attacks at once need two rings, and a hundred more need no more. */
    @Test
    void theSceneStopsGrowingOnceItHasSeenTheBusiestMoment() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.ATTACK, NOW);
        scene.orders().add(20f, 20f, OrderMarkers.Kind.ATTACK, NOW);
        draw(scene, NOW);
        assertEquals(2, scene.flash().madeSoFar(), "two at once needs two");

        for (int click = 0; click < 100; click++) {
            float when = NOW + 10f + click;
            scene.orders().clear();
            scene.orders().add(click, click, OrderMarkers.Kind.ATTACK, when);
            draw(scene, when);
        }

        assertEquals(2, scene.flash().madeSoFar(),
                "the pool built something new for an order it had a ring for");
        assertEquals(2, scene.root().getChildren().size(), "and the scene grew with it");
    }

    /**
     * The ring goes with the creature rather than staying on the flagstone.
     *
     * <p>A skeleton is walking at him when he clicks it, so by the time the second
     * flash comes it is somewhere else. A ring left where it was marks a place
     * nothing is any more, which the player reads as the order having gone
     * somewhere else.
     */
    @Test
    void theRingFollowsTheCreatureItWasGivenTo() {
        var scene = scene();
        scene.orders().add(100f, 100f, 7, OrderMarkers.Kind.ATTACK, NOW);

        // It has walked twenty units north since the click.
        scene.flash().show(scene.orders().markers(), NOW,
                id -> id == 7 ? new uz.duke.core.math.Coord3D(100f, 120f, 0f) : null,
                (x, y) -> 0f);
        scene.root().updateGeometricState();

        var ring = shown(scene.root());
        assertEquals(120f, ring.getLocalTranslation().z, 0.001f,
                "the ring should have gone with it");
    }

    /** And when it dies it stays where the order was given, rather than vanishing. */
    @Test
    void aCreatureThatDiesLeavesTheMarkWhereItWasOrdered() {
        var scene = scene();
        scene.orders().add(100f, 100f, 7, OrderMarkers.Kind.ATTACK, NOW);

        scene.flash().show(scene.orders().markers(), NOW, id -> null, (x, y) -> 0f);
        scene.root().updateGeometricState();

        var ring = shown(scene.root());
        assertEquals(100f, ring.getLocalTranslation().x, 0.001f,
                "it is gone, so the answer is where he pointed");
    }

    /** A new world has no orders outstanding in it. */
    @Test
    void aNewWorldShowsNothing() {
        var scene = scene();
        scene.orders().add(10f, 10f, OrderMarkers.Kind.ATTACK, NOW);
        draw(scene, NOW);

        scene.flash().clear();

        assertEquals(null, shown(scene.root()));
    }
}
