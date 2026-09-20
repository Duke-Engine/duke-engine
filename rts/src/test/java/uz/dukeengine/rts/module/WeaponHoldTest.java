package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.message.GameMessage;

/**
 * A unit busy with something else does not shoot.
 *
 * <p>Written as a game would write it — a module the engine has never heard of —
 * because that is the point of the seam. What a game does with it is its own:
 * the dungeon's archer holds while he draws a heavy shot, so that one keypress
 * produces one arrow instead of two.
 *
 * <p>The bug it exists for is subtle and worth stating. Holding fire from outside
 * the weapon cannot work: the weapon acquires its own target and fires in the same
 * call, and every script on a unit runs after it, so a disarm is always a frame
 * late — and by the time it happens the shot has gone.
 */
class WeaponHoldTest {

    /** What a game might attach: this unit is busy for a while. */
    private static final class Busy extends Module implements WeaponHold {
        private boolean busy = true;

        Busy(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean holdingFire() {
            return busy;
        }
    }

    private static final float DAMAGE = 20f;
    private static final int RELOAD = 6;

    private CombatTest.CombatLogic logic;
    private ThingTemplate soldier;
    private int red;
    private int blue;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        soldier = ThingTemplate.named("Soldier")
                .module(new ActiveBody.Data(1000f))
                .module(new WeaponUpdate.Data(DAMAGE, 30f, RELOAD))
                .build();
        factory.addTemplate(soldier);

        logic = new CombatTest.CombatLogic(factory);
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

    private void order(GameObject attacker, GameObject victim) {
        logic.issueCommand(new GameMessage.AttackObject(attacker.getPlayerIndex(),
                List.of(attacker.getId()), victim.getId()));
    }

    /** Nothing implementing it means nothing changes, which is most of the engine. */
    @Test
    void aUnitWithNothingHoldingItShootsAsBefore() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        float before = victim.getBody().getHealth();

        order(attacker, victim);
        logic.update();

        assertEquals(DAMAGE, before - victim.getBody().getHealth(), 0.01f);
    }

    /** A module that says it is busy silences the weapon, order or no order. */
    @Test
    void aBusyModuleSilencesTheWeapon() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        attacker.addModule(new Busy(attacker));
        float before = victim.getBody().getHealth();

        order(attacker, victim);
        for (int frame = 0; frame < 20; frame++) {
            logic.update();
        }

        assertEquals(before, victim.getBody().getHealth(), 0.01f, "it fired anyway");
    }

    /** Including one it would have found for itself. */
    @Test
    void aBusyUnitDoesNotEvenLookForATarget() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        attacker.addModule(new Busy(attacker));

        logic.update();

        assertEquals(null, attacker.findModule(WeaponUpdate.class).getTarget(),
                "acquiring is firing's first half and waits with it");
    }

    /** What it was already pointed at survives being busy. */
    @Test
    void beingBusyDoesNotLoseTheOrder() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        order(attacker, victim);
        attacker.addModule(new Busy(attacker));

        for (int frame = 0; frame < 10; frame++) {
            logic.update();
        }

        assertEquals(victim.getId(), attacker.findModule(WeaponUpdate.class).getTarget(),
                "a unit told to attack should still be, once it is free");
    }

    /**
     * And it fires the moment it stops being busy — the reload ran underneath.
     *
     * <p>Otherwise every interruption would cost a full reload on top of itself,
     * and a unit that was briefly busy would be punished twice for it.
     */
    @Test
    void itShootsAsSoonAsItIsFree() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        var busy = new Busy(attacker);
        attacker.addModule(busy);
        order(attacker, victim);
        for (int frame = 0; frame < 20; frame++) {
            logic.update();
        }
        float before = victim.getBody().getHealth();

        busy.busy = false;
        logic.update();

        assertTrue(victim.getBody().getHealth() < before,
                "the shot it had been holding should have gone at once");
    }
}
