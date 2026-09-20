package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Damage a <em>unit</em> earns, rather than damage its whole side is given.
 *
 * <p>There used to be two ways to hit harder and neither was the unit's own: a
 * rank with multipliers compiled into an enum, and a bonus held on the player,
 * which every unit that player owns shares whether it earned it or not. A hero
 * who had levelled and a conscript beside him hit exactly as hard.
 *
 * <p>These tests are written as a game would write them — a modifier the engine
 * has never heard of — because that is the point of the seam.
 */
class DamageModifierTest {

    /** What a game might attach to one unit: this one, personally, hits harder. */
    private static final class Sharpened extends Module implements DamageModifier {
        private float multiplier;

        Sharpened(GameObject owner, float multiplier) {
            super(owner);
            this.multiplier = multiplier;
        }

        @Override
        public float damageMultiplier() {
            return multiplier;
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
                .module(new ActiveBody.Data(1000f))
                .module(new WeaponUpdate.Data(20f, 10f, 1))
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

    /**
     * Damage from exactly one shot: the order is applied and the weapon fires
     * within the same frame, so a single step measures a single hit.
     */
    private float damageDealtBy(GameObject attacker, GameObject victim) {
        float before = victim.getBody().getHealth();
        logic.issueCommand(new GameMessage.AttackObject(attacker.getPlayerIndex(),
                java.util.List.of(attacker.getId()), victim.getId()));
        logic.update();
        return before - victim.getBody().getHealth();
    }

    @Test
    void aUnitWithNoModifiersDealsItsWeaponsDamage() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);

        assertEquals(20f, damageDealtBy(attacker, victim), 0.01f);
    }

    @Test
    void aModifierOnAUnitRaisesThatUnitsDamage() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        attacker.addModule(new Sharpened(attacker, 2f));

        assertEquals(40f, damageDealtBy(attacker, victim), 0.01f);
    }

    /** The half that the player-wide bonus could never express. */
    @Test
    void theBonusBelongsToTheUnitAndNotToItsSide() {
        // Two separate duels, far enough apart that neither soldier can
        // auto-acquire the other duel's victim and skew the measurement.
        var veteran = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        var conscript = spawn(red, 100f);
        var otherVictim = spawn(blue, 105f);
        veteran.addModule(new Sharpened(veteran, 3f));

        float byVeteran = damageDealtBy(veteran, victim);
        float byConscript = damageDealtBy(conscript, otherVictim);

        assertEquals(60f, byVeteran, 0.01f, "the one that earned it hits harder");
        assertEquals(20f, byConscript, 0.01f, "his neighbour is untouched by it");
    }

    @Test
    void severalModifiersCompound() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        attacker.addModule(new Sharpened(attacker, 2f));
        attacker.addModule(new Sharpened(attacker, 1.5f));

        assertEquals(60f, damageDealtBy(attacker, victim), 0.01f, "2 x 1.5 x 20");
    }

    /** A modifier may follow the unit's state, so long as it follows nothing else. */
    @Test
    void aModifierCanChangeDuringTheFight() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        var buff = new Sharpened(attacker, 1f);
        attacker.addModule(buff);

        float plain = damageDealtBy(attacker, victim);
        buff.multiplier = 4f;
        float buffed = damageDealtBy(attacker, victim);

        assertTrue(buffed > plain, "raising the modifier should raise the damage");
        assertEquals(80f, buffed, 0.01f);
    }

    /** Taking the modifier off puts the unit back where it started. */
    @Test
    void removingAModifierRemovesTheBonus() {
        var attacker = spawn(red, 0f);
        var victim = spawn(blue, 5f);
        var buff = new Sharpened(attacker, 5f);
        attacker.addModule(buff);
        assertEquals(100f, damageDealtBy(attacker, victim), 0.01f);

        attacker.removeModule(buff);

        assertEquals(20f, damageDealtBy(attacker, victim), 0.01f);
    }
}
