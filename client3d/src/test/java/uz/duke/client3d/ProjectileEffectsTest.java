package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;

/**
 * What burning costs, and that it gives it all back.
 *
 * <p>Three things are worth holding still here and none of them is how it looks.
 * A fight is fifty arrows, so an effect that keeps anything — a node, an emitter,
 * a light — is a scene that grows for as long as the session lasts and a frame
 * rate that falls with it. And dynamic lights are the expensive kind, so the
 * budget has to be a ceiling rather than a hope.
 *
 * <p>No window is opened. Emitters and lights are ordinary scene objects until
 * something renders them, which is what lets this be a build failure instead of a
 * slow afternoon.
 */
class ProjectileEffectsTest {

    private static final int LIGHTS = 4;

    /** Somewhere for a shot to be drawn: up off the ground and out at its head. */
    private static final float BOW_HEIGHT = 7f;
    private static final float TO_THE_HEAD = 6f;

    /** One burning thing and the projectile that wears it. */
    private static Visuals burning() {
        return Visuals.create()
                .effect("Fire", recipe -> recipe
                        .kind(Visuals.EffectVisual.FLAME_TRAIL)
                        .kind(Visuals.EffectVisual.IMPACT_BURST)
                        .colours(java.awt.Color.ORANGE, java.awt.Color.RED)
                        .light(java.awt.Color.ORANGE, 2f, 50f)
                        .particles(12, 1.5f, 0.4f, 5f)
                        .burst(10, 2f, 0.3f))
                .unit("Shot", unit -> unit.effect("Fire")
                        .yOffset(BOW_HEIGHT).effectAt(TO_THE_HEAD));
    }

    private record Scene(ProjectileEffects effects, Node root, Visuals visuals) {

        Visuals.UnitVisual shot() {
            return visuals.of("Shot");
        }

        /** A fresh node for one shot, hung where the client hangs them. */
        Node fly(String named) {
            var node = new Node(named);
            root.attachChild(node);
            return node;
        }
    }

    private static Scene scene(Visuals visuals) {
        return scene(visuals, 0f);
    }

    private static Scene scene(Visuals visuals, float distance) {
        var root = new Node("root");
        return new Scene(new ProjectileEffects(new DesktopAssetManager(true), root, visuals,
                LIGHTS, 8, 8, distance), root, visuals);
    }

    /** A shot in the air carries a light; the same shot landed gives it back. */
    @Test
    void aShotTakesALightAndGivesItBack() {
        var scene = scene(burning());

        scene.effects().appeared(1, scene.shot(), scene.fly("shot"), Vector3f.ZERO, null);
        assertEquals(1, scene.effects().litCount(), "it should be burning");

        scene.effects().gone(1);
        assertEquals(0, scene.effects().litCount(), "and it should not still be");
    }

    /**
     * The fire is where the thing is, not under it.
     *
     * <p>Which was wrong in both directions at once. A trail hung on a unit's root
     * comes out of the ground the unit stands on, because the model is lifted off
     * that root; and one at the root's middle comes out of the middle of a shaft
     * twelve units long. A burning arrow burns at the head, in the air.
     */
    @Test
    void theFireIsAtTheHeadAndInTheAir() {
        var scene = scene(burning());
        var node = scene.fly("shot");
        node.setLocalTranslation(100f, 0f, 50f);
        scene.root().updateGeometricState();

        scene.effects().appeared(1, scene.shot(), node, node.getWorldTranslation(), null);

        var fire = node.getChildren().get(0).getWorldTranslation();
        assertEquals(BOW_HEIGHT, fire.y, 0.01f, "the fire is dragging along the floor");
        assertEquals(100f + TO_THE_HEAD, fire.x, 0.01f,
                "the fire is coming out of the middle of the shaft");
    }

    /**
     * More things burning than there are lights is the ordinary case, not the
     * exception: the fifth one flies dark.
     *
     * <p>Dark rather than unlit — it keeps its trail, so what is lost is the glow
     * on the floor and not the shot. Which is the whole reason for a ceiling: past
     * it, things go on working and cost less.
     */
    @Test
    void theLightBudgetIsACeilingAndNotASuggestion() {
        var scene = scene(burning());
        for (int shot = 0; shot < LIGHTS * 3; shot++) {
            scene.effects().appeared(shot, scene.shot(), scene.fly("shot" + shot),
                    Vector3f.ZERO, null);
            assertTrue(scene.effects().litCount() <= LIGHTS,
                    "after " + (shot + 1) + " shots, " + scene.effects().litCount()
                            + " lights are burning and only " + LIGHTS + " were allowed");
        }
    }

    /**
     * Nothing is kept. A fight's worth of arrows leaves the scene exactly as
     * large as it started.
     *
     * <p>The failure this guards against does not show up for ten minutes and then
     * shows up as the whole game being slow, which is the worst shape a bug can
     * have.
     */
    @Test
    void aFightsWorthOfShotsLeavesNothingBehind() {
        var scene = scene(burning());
        int before = scene.root().getQuantity();

        for (int shot = 0; shot < 50; shot++) {
            var node = scene.fly("shot" + shot);
            scene.effects().appeared(shot, scene.shot(), node, Vector3f.ZERO, null);
            scene.effects().moved(shot, scene.shot(), node);
            scene.effects().gone(shot);
            node.removeFromParent();
        }

        assertEquals(before, scene.root().getQuantity(),
                "the scene grew by " + (scene.root().getQuantity() - before) + " nodes");
        assertEquals(0, scene.effects().litCount(), "and something is still alight");

        // And the lights really came back, rather than merely being forgotten.
        // A pool that loses one a shot runs dry on the fifth and then everything
        // flies dark for the rest of the session — which reads as the effect
        // having been switched off, not as a leak.
        scene.effects().appeared(999, scene.shot(), scene.fly("one more"), Vector3f.ZERO, null);
        assertEquals(1, scene.effects().litCount(),
                "the fifty-first shot flew dark: the lights went out and stayed out");
    }

    /** A burst dies down on its own and takes its light with it. */
    @Test
    void aBurstBurnsOutAndGivesBackWhatItTook() {
        var scene = scene(burning());
        int before = scene.root().getQuantity();

        scene.effects().landed("Fire", Vector3f.ZERO, null);
        assertEquals(1, scene.effects().litCount(), "the flash should be alight");
        assertTrue(scene.root().getQuantity() > before, "and its sparks in the scene");

        for (int frame = 0; frame < 40; frame++) {
            scene.effects().update(0.05f);
        }

        assertEquals(0, scene.effects().litCount(), "the flash should have died down");
        assertEquals(before, scene.root().getQuantity(), "and taken its sparks with it");
    }

    /**
     * A projectile the game describes no effect for costs nothing at all.
     *
     * <p>Which is most of them, and is what keeps this whole arrangement optional:
     * a game that names no recipe draws exactly what it drew before any of it
     * existed.
     */
    @Test
    void aShotWithNoRecipeIsGivenNothing() {
        var visuals = Visuals.create().unit("Shot", unit -> unit.effect("NoSuchEffect"));
        var scene = scene(visuals);
        var node = scene.fly("shot");
        int before = scene.root().getQuantity();

        scene.effects().appeared(1, scene.shot(), node, Vector3f.ZERO, null);
        scene.effects().appeared(2, null, node, Vector3f.ZERO, null);
        scene.effects().landed("NoSuchEffect", Vector3f.ZERO, null);

        assertEquals(0, scene.effects().litCount());
        assertEquals(before, scene.root().getQuantity());
        assertNull(scene.effects().bodyFor("NoSuchEffect"));
    }

    /**
     * Changing a number in the settings changes what is drawn.
     *
     * <p>Cheap to say and worth saying: the whole point of the arrangement is that
     * a new burning thing is a block of settings, and settings that are read once
     * and then ignored look exactly like settings that work.
     */
    @Test
    void whatTheSettingsSayIsWhatIsBuilt() {
        var plain = Visuals.create().effect("Orb", recipe -> recipe
                .kind(Visuals.EffectVisual.GLOW_ORB).orb(2f));
        var noOrb = Visuals.create().effect("Orb", recipe -> recipe
                .kind(Visuals.EffectVisual.FLAME_TRAIL).orb(2f));

        assertNotNull(scene(plain).effects().bodyFor("Orb"),
                "a recipe that says it is a glowing orb should draw one");
        assertNull(scene(noOrb).effects().bodyFor("Orb"),
                "and one that does not, should not — however big it says the orb is");
    }

    /** Far-off things are not worth the budget, and are given none of it. */
    @Test
    void whatIsTooFarOffToSeeIsNotLit() {
        var scene = scene(burning(), 100f);

        scene.effects().appeared(1, scene.shot(), scene.fly("shot"),
                new Vector3f(500f, 0f, 0f), Vector3f.ZERO);

        assertEquals(0, scene.effects().litCount(), "a spark two rooms away is a pixel");
    }
}
