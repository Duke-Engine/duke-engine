package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;

/**
 * What a weapon may be fired at: the classes it names, and the game's rules for which classes a thing has.
 *
 * <p>The words here — GROUND, AIRBORNE_VEHICLE, MINE — are this test's, standing in for a game's. The
 * engine compares them and reads none of them.
 */
class WeaponTargetsTest {

    static final class TestLogic extends RtsSimulation {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
        }
    }

    private static final Kind VEHICLE = Kind.of("VEHICLE");
    private static final Kind MINE = Kind.of("MINE");

    /** The reference game's lines, cut down to the ones these tests need. */
    private static final List<TargetRule> RULES = List.of(
            new TargetRule(List.of(MINE), false, List.of("MINE", "GROUND")),
            new TargetRule(List.of(VEHICLE), true, List.of("AIRBORNE_VEHICLE")),
            new TargetRule(List.of(), true, List.of()),
            new TargetRule(List.of(), false, List.of("GROUND")));

    private TestLogic logic;
    private int us;
    private int them;

    private void world(List<TargetRule> rules, ThingTemplate... templates) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        for (var template : templates) {
            factory.addTemplate(template);
        }
        logic = new TestLogic(factory);
        logic.init();
        var mine = logic.getPlayerList().addPlayer("Us");
        var theirs = logic.getPlayerList().addPlayer("Them");
        mine.setRelationshipTo(theirs, Relationship.ENEMIES);
        theirs.setRelationshipTo(mine, Relationship.ENEMIES);
        us = mine.getIndex();
        them = theirs.getIndex();
        logic.setTargetRules(rules);
    }

    private static ThingTemplate gun(String name, List<String> targets) {
        return RtsTemplate.named(name)
                .geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new WeaponUpdate.Data(10f, 40f, 5, DamageType.NORMAL, 0f, true, targets))
                .build();
    }

    private static ThingTemplate thing(String name, Kind kind) {
        return RtsTemplate.named(name)
                .geometry(new Geometry.Cylinder(3f, 6f))
                .kindOf(kind)
                .module(new ActiveBody.Data(100f))
                .build();
    }

    private GameObject spawn(String template, int player, float x) {
        var object = logic.createObject(logic.getThingFactory().findTemplate(template));
        object.setPlayerIndex(player);
        object.setPosition(new Coord3D(x, 100f, 0f));
        return object;
    }

    private void run(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
        }
    }

    private static boolean hurt(GameObject thing) {
        return thing.getBody().getHealth() < thing.getBody().getMaxHealth();
    }

    @Test
    void aWeaponForTheGroundNeverAcquiresAnAircraftInTheAir() {
        world(RULES, gun("Tank", List.of("GROUND")), thing("Helicopter", VEHICLE));
        var tank = spawn("Tank", us, 100f);
        var helicopter = spawn("Helicopter", them, 110f);
        helicopter.setStatus(ObjectStatus.AIRBORNE);

        run(30);
        assertFalse(hurt(helicopter), "a tank gun does not shoot at a helicopter overhead");
        assertNull(tank.findModule(WeaponUpdate.class).getTarget());

        helicopter.clearStatus(ObjectStatus.AIRBORNE); // it lands
        run(30);
        assertTrue(hurt(helicopter), "and on its pad it is ground like anything else");
    }

    @Test
    void aMineClearingChargeIgnoresTheTankBesideItAndClearsTheMine() {
        world(RULES, gun("Dozer", List.of("MINE")), thing("EnemyTank", VEHICLE), thing("Mine", MINE));
        spawn("Dozer", us, 100f);
        var tank = spawn("EnemyTank", them, 105f);
        var mine = spawn("Mine", them, 125f);

        run(30);
        assertFalse(hurt(tank), "the nearer tank is nothing to a mine-clearing charge");
        assertTrue(hurt(mine), "the mine beyond it is what it is for");
    }

    @Test
    void aThingTwoRulesMatchTakesTheFirst() {
        world(List.of(
                new TargetRule(List.of(MINE), false, List.of("MINE", "GROUND")),
                new TargetRule(List.of(MINE), false, List.of("SOMETHING_ELSE"))),
                gun("Sweeper", List.of("SOMETHING_ELSE")), thing("Mine", MINE));
        spawn("Sweeper", us, 100f);
        var mine = spawn("Mine", them, 110f);

        assertEquals(List.of("MINE", "GROUND"), TargetRule.classesOf(logic.getTargetRules(), mine));
        run(30);
        assertFalse(hurt(mine), "the second line never gets a say");
    }

    /** A weapon that names nothing fires at anything, rules or no rules — every game before this. */
    @Test
    void aWeaponThatNamesNothingFiresAtAnything() {
        world(RULES, gun("Rifleman", List.of()), thing("Helicopter", VEHICLE));
        spawn("Rifleman", us, 100f);
        var helicopter = spawn("Helicopter", them, 110f);
        helicopter.setStatus(ObjectStatus.AIRBORNE);

        run(30);
        assertTrue(hurt(helicopter));
    }

    /**
     * An order to attack something it cannot hit is refused, and what it was doing is kept; a target that
     * takes off while it is being shot at is let go.
     */
    @Test
    void anOrderToAttackWhatItCannotHitIsRefused() {
        world(RULES, gun("Tank", List.of("GROUND")), thing("Helicopter", VEHICLE), thing("Jeep", VEHICLE));
        var tank = spawn("Tank", us, 100f);
        var helicopter = spawn("Helicopter", them, 300f);
        helicopter.setStatus(ObjectStatus.AIRBORNE);
        var jeep = spawn("Jeep", them, 110f);
        var weapon = tank.findModule(WeaponUpdate.class);

        assertTrue(weapon.attack(jeep.getId()));
        assertFalse(weapon.attack(helicopter.getId()), "refused");
        assertEquals(jeep.getId(), weapon.getTarget(), "and still on the jeep");

        jeep.setStatus(ObjectStatus.AIRBORNE); // the jeep, improbably, takes off
        run(1);
        assertNull(weapon.getTarget(), "let go: it is no longer something this can hit");
    }
}
