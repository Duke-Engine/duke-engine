package uz.duke.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

/**
 * Weapons that have to be stood still to use.
 *
 * <p>Most do not, and that is the default and stays the default: an RTS unit
 * shoots as it advances, and every weapon in this engine did before this existed.
 * The exceptions are real enough to need saying — a drawn bow, a gun that has to
 * be deployed — and it has to be the weapon that knows, not whatever is steering
 * the unit.
 *
 * <p>That last part is the whole reason this lives in the module rather than in a
 * script. A weapon acquires its own target and fires in the same call, so anything
 * outside it that disarmed the weapon would be undone before it ran again. This is
 * a real bug that shipped: the dungeon's archer held fire every frame and shot
 * anyway, because his weapon updated first.
 */
class AttackOnTheMoveTest {

    private static final float RANGE = 40f;
    private static final float DAMAGE = 10f;
    private static final int RELOAD = 4;

    private ThingFactory factory;
    private CombatTest.CombatLogic logic;
    private int ours;
    private int theirs;

    @BeforeEach
    void setUp() {
        factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(shooter("Rifleman", true));
        factory.addTemplate(shooter("Archer", false));
        factory.addTemplate(ThingTemplate.named("Target")
                .module("ActiveBody", new ActiveBody.Data(1000f))
                .build());

        logic = new CombatTest.CombatLogic(factory);
        logic.init();
        ours = logic.getPlayerList().addPlayer("Ours").getIndex();
        theirs = logic.getPlayerList().addPlayer("Theirs").getIndex();
        var us = logic.getPlayerList().getPlayer(ours);
        var them = logic.getPlayerList().getPlayer(theirs);
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
    }

    private static ThingTemplate shooter(String name, boolean onTheMove) {
        return ThingTemplate.named(name)
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("MoveUpdate", new MoveUpdate.Data(90f, 0f))
                .module("WeaponUpdate", new WeaponUpdate.Data(DAMAGE, RANGE, RELOAD,
                        uz.duke.core.module.DamageType.NORMAL, 0f, onTheMove))
                .build();
    }

    private GameObject spawn(String template, int player, float x, float y) {
        var unit = logic.createObject(factory.findTemplate(template));
        unit.setPlayerIndex(player);
        unit.setPosition(new Coord3D(x, y, 0f));
        return unit;
    }

    /** A shot fired at something standing well inside reach, over a fixed window. */
    private float damageDoneWhileWalking(String template) {
        var shooter = spawn(template, ours, 0f, 0f);
        var victim = spawn("Target", theirs, 0f, 20f); // beside the line he walks
        float before = victim.getBody().getHealth();
        shooter.findModule(MoveUpdate.class).moveTo(new Coord3D(200f, 0f, 0f));

        for (int frame = 0; frame < 40; frame++) {
            logic.update();
        }

        assertTrue(shooter.getPosition().x() > 10f, "he should have been walking");
        return before - victim.getBody().getHealth();
    }

    /** Unchanged for everything that had a weapon before this existed. */
    @Test
    void aWeaponFiresOnTheMoveByDefault() {
        assertTrue(damageDoneWhileWalking("Rifleman") > 0f,
                "an ordinary unit shoots as it advances, and always has");
    }

    /** And not at all for one the file says must be stood still for. */
    @Test
    void aWeaponToldToStandStillHoldsItsShotWhileWalking() {
        assertEquals(0f, damageDoneWhileWalking("Archer"), 0.001f,
                "he loosed on the move");
    }

    /**
     * He fires the moment he stops, which means the reload ran while he walked.
     *
     * <p>Otherwise standing still would only be the start of the wait, and every
     * step would cost a full reload — which would not be a weapon you had to stand
     * still for, it would be one you could not use.
     */
    @Test
    void heShootsAsSoonAsHeStops() {
        var archer = spawn("Archer", ours, 0f, 0f);
        var victim = spawn("Target", theirs, 0f, 20f);
        float before = victim.getBody().getHealth();
        var move = archer.findModule(MoveUpdate.class);
        move.moveTo(new Coord3D(200f, 0f, 0f));

        // A short walk, cut off while the target is still well inside his reach —
        // otherwise stopping out of range would prove nothing about holding fire.
        for (int frame = 0; frame < 8; frame++) {
            logic.update();
        }
        assertEquals(before, victim.getBody().getHealth(), 0.001f, "nothing yet");
        assertTrue(archer.getPosition().distance(victim.getPosition()) < RANGE,
                "he has walked out of range, so this would prove nothing");

        move.stop();
        logic.update();

        assertTrue(victim.getBody().getHealth() < before,
                "standing still, the shot he had been holding should go");
    }

    /**
     * And he keeps what he was aimed at across the walk.
     *
     * <p>Holding the shot must not mean forgetting the order — a unit sent at
     * something across the map that arrived with nothing to shoot would need
     * telling twice.
     */
    @Test
    void heKeepsHisTargetWhileWalking() {
        var archer = spawn("Archer", ours, 0f, 0f);
        var victim = spawn("Target", theirs, 0f, 20f);
        archer.findModule(WeaponUpdate.class).attack(victim.getId());
        archer.findModule(MoveUpdate.class).moveTo(new Coord3D(200f, 0f, 0f));

        for (int frame = 0; frame < 20; frame++) {
            logic.update();
        }

        assertEquals(victim.getId(), archer.findModule(WeaponUpdate.class).getTarget(),
                "he was still pointed at it");
    }

    /** Standing still, he is an ordinary shooter and nothing about him has changed. */
    @Test
    void standingStillHeFightsLikeAnythingElse() {
        var archer = spawn("Archer", ours, 0f, 0f);
        var victim = spawn("Target", theirs, 0f, 20f);
        float before = victim.getBody().getHealth();

        for (int frame = 0; frame < 40; frame++) {
            logic.update();
        }

        assertTrue(victim.getBody().getHealth() < before,
                "he never moved, so he should have been shooting the whole time");
    }
}
