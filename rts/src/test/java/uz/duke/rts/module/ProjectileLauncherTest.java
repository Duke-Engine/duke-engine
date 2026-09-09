package uz.duke.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.Module;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.rts.event.WeaponFired;
import uz.duke.rts.message.GameMessage;

/**
 * A shot that leaves the weapon and arrives later.
 *
 * <p>Weapons here hit the instant they fire, which is right for a rifle and
 * leaves no way at all to express an arrow, a shell or a missile. A game wanting
 * one had to abandon the weapon entirely — and with it the targeting, the reload,
 * the command routing and the fired event that everything else hangs off.
 *
 * <p>So the weapon keeps all of that and hands over only the last step. These
 * tests are written the way a game would write one: a launcher this library has
 * never heard of, doing something it knows nothing about.
 */
class ProjectileLauncherTest {

    /** What a game might attach: shots are collected rather than landed. */
    private static final class Quiver extends Module implements ProjectileLauncher {
        private final List<Float> caught = new ArrayList<>();
        private final List<DamageType> types = new ArrayList<>();
        private boolean accepting = true;

        Quiver(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean launch(GameObject shooter, GameObject victim, float damage, DamageType type) {
            if (!accepting) {
                return false;
            }
            caught.add(damage);
            types.add(type);
            return true;
        }
    }

    /** A unit that hits harder, so the launcher can be shown the final figure. */
    private static final class Sharpened extends Module implements DamageModifier {
        Sharpened(GameObject owner) {
            super(owner);
        }

        @Override
        public float damageMultiplier() {
            return 3f;
        }
    }

    private CombatTest.CombatLogic logic;
    private ThingTemplate soldier;
    private int red;
    private int blue;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        soldier = ThingTemplate.named("Soldier")
                .module("ActiveBody", new ActiveBody.Data(1000f))
                .module("WeaponUpdate", new WeaponUpdate.Data(20f, 10f, 1))
                .build();
        thingFactory.addTemplate(soldier);

        logic = new CombatTest.CombatLogic(thingFactory);
        logic.init();
        red = logic.getPlayerList().addPlayer("Red").getIndex();
        blue = logic.getPlayerList().addPlayer("Blue").getIndex();
        var a = logic.getPlayerList().getPlayer(red);
        var b = logic.getPlayerList().getPlayer(blue);
        a.setRelationshipTo(b, Relationship.ENEMIES);
        b.setRelationshipTo(a, Relationship.ENEMIES);
    }

    private GameObject spawn(int player, float x) {
        var unit = logic.createObject(soldier);
        unit.setPlayerIndex(player);
        unit.setPosition(new Coord3D(x, 0f, 0f));
        return unit;
    }

    /** One shot: the order is applied and the weapon fires in the same frame. */
    private void fireOnce(GameObject attacker, GameObject victim) {
        logic.issueCommand(new GameMessage.AttackObject(attacker.getPlayerIndex(),
                List.of(attacker.getId()), victim.getId()));
        logic.update();
    }

    /** A unit with no launcher hits the moment it fires, exactly as before. */
    @Test
    void withoutALauncherTheShotStillLandsAtOnce() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        float before = victim.getBody().getHealth();

        fireOnce(attacker, victim);

        assertEquals(20f, before - victim.getBody().getHealth(), 0.01f);
    }

    /** With one, nothing is hit — the shot has been handed over. */
    @Test
    void aLauncherTakesTheShotInsteadOfTheVictim() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        var quiver = new Quiver(attacker);
        attacker.addModule(quiver);
        float before = victim.getBody().getHealth();

        fireOnce(attacker, victim);

        assertEquals(1, quiver.caught.size(), "the launcher should have been given the shot");
        assertEquals(before, victim.getBody().getHealth(), 0.01f,
                "and nothing should have been hit yet");
    }

    /**
     * The figure it is given is the final one.
     *
     * <p>Every modifier and player bonus is applied before the hand-over, so a
     * projectile carries a number rather than having to work the sum out again on
     * arrival — by which time the unit that fired it may have levelled, or died.
     */
    @Test
    void theLauncherIsGivenTheDamageAfterEveryModifier() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        var quiver = new Quiver(attacker);
        attacker.addModule(quiver);
        attacker.addModule(new Sharpened(attacker));

        fireOnce(attacker, victim);

        assertEquals(60f, quiver.caught.get(0), 0.01f, "20 doubled and again by three");
        assertEquals(DamageType.NORMAL, quiver.types.get(0), "and the weapon's own damage type");
    }

    /**
     * A shot was still fired, whoever is carrying it.
     *
     * <p>The reload runs and the moment is announced, because both are true: the
     * trigger was pulled. A client drawing a muzzle flash should not have to know
     * whether the game models the flight.
     */
    @Test
    void firingIsStillAnnouncedAndStillCostsTheReload() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        attacker.addModule(new Quiver(attacker));

        fireOnce(attacker, victim);

        var fired = logic.drainEvents().stream()
                .filter(WeaponFired.class::isInstance)
                .map(WeaponFired.class::cast)
                .findFirst().orElse(null);
        assertNotNull(fired, "a shot was fired and should have been said so");
        assertEquals(attacker.getId(), fired.shooter());
        assertEquals(victim.getId(), fired.target());
    }

    /**
     * A launcher that declines leaves the shot to the weapon.
     *
     * <p>So a unit that cannot get a projectile away — nowhere to put it, nothing
     * left to fire — is a unit whose shot lands the old way, rather than one that
     * has quietly become harmless.
     */
    @Test
    void aDeclinedShotIsLandedByTheWeaponAfterAll() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        var quiver = new Quiver(attacker);
        quiver.accepting = false;
        attacker.addModule(quiver);
        float before = victim.getBody().getHealth();

        fireOnce(attacker, victim);

        assertTrue(quiver.caught.isEmpty(), "it refused the shot");
        assertEquals(20f, before - victim.getBody().getHealth(), 0.01f,
                "so the weapon should have landed it");
    }

    /**
     * A kill is not credited for a shot still in the air.
     *
     * <p>The victim is alive when the weapon lets go of it, so there is nothing to
     * be credited for. Whatever carries the shot has to hand out the experience if
     * it turns out to kill — a real difference, and the reason it is stated here.
     */
    @Test
    void nothingIsCreditedForAShotThatHasNotArrived() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        attacker.addModule(new Quiver(attacker));
        victim.getBody().damage(999f); // one hit from death, if it landed

        fireOnce(attacker, victim);

        assertTrue(victim.getBody().getHealth() > 0f,
                "it should still be standing, with the shot in the air");
    }
}
