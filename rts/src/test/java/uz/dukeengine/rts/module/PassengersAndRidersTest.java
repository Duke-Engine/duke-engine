package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Bones;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.RtsTemplate;

/** Passengers that fire from inside where their carrier stands, and a rider on top that fires and dies with it. */
class PassengersAndRidersTest {

    private static CombatTest.CombatLogic world(ThingTemplate... templates) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        for (var template : templates) {
            factory.addTemplate(template);
        }
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        var usa = logic.getPlayerList().addPlayer("USA");
        var china = logic.getPlayerList().addPlayer("China");
        usa.setRelationshipTo(china, Relationship.ENEMIES);
        china.setRelationshipTo(usa, Relationship.ENEMIES);
        return logic;
    }

    private static final ThingTemplate RANGER = ThingTemplate.named("Ranger").module(new ActiveBody.Data(100f))
            .module(new WeaponUpdate.Data(5f, 100f, 10, DamageType.NORMAL, 0f)).build();
    private static final ThingTemplate TANK = ThingTemplate.named("Tank").module(new ActiveBody.Data(1000f)).build();

    @Test
    void aRangerInsideAHumveeHoldsFireUntilTheFlagIsOnThenShootsFromWhereItStands() {
        var humveeType = ThingTemplate.named("Humvee").module(new ActiveBody.Data(300f))
                .module(new ContainModule.Data(5)).build();
        var logic = world(humveeType, RANGER, TANK);
        var humvee = logic.spawn(humveeType, new Coord3D(0f, 0f, 0f), 1);
        var ranger = logic.spawn(RANGER, new Coord3D(500f, 500f, 0f), 1); // got in from far away, as far as it knows
        var tank = logic.spawn(TANK, new Coord3D(60f, 0f, 0f), 2);
        var hold = humvee.findModule(ContainModule.class);
        hold.load(ranger);

        for (int frame = 0; frame < 60; frame++) {
            logic.update();
        }
        assertEquals(1000f, tank.getBody().getHealth(), "held idle inside");

        hold.setPassengersFire(true); // the bunker upgrade
        for (int frame = 0; frame < 60; frame++) {
            logic.update();
        }
        assertTrue(tank.getBody().getHealth() < 1000f, "firing from the Humvee's place, with its own gun");
    }

    @Test
    void aRiderStaysOnItsBoneFiresOnItsOwnAndDiesWithItsCarrier() {
        var overlordType = RtsTemplate.named("Overlord").model("models/bones/overlord.gltf")
                .module(new ActiveBody.Data(1100f)).module(new MoveUpdate.Data(30f))
                .module(new ContainModule.Data(1, null, false, "GUNNER")).build();
        var logic = world(overlordType, RANGER, TANK);
        var overlord = logic.spawn(overlordType, new Coord3D(0f, 0f, 0f), 1);
        var gattling = logic.spawn(RANGER, new Coord3D(0f, 0f, 0f), 1);
        var tank = logic.spawn(TANK, new Coord3D(40f, 60f, 0f), 2);
        overlord.findModule(ContainModule.class).load(gattling);
        overlord.getLocomotor().moveTo(new Coord3D(80f, 0f, 0f));

        for (int frame = 0; frame < 60; frame++) {
            logic.update();
            var seat = Bones.inWorld(overlord, "GUNNER");
            assertEquals(seat, gattling.getPosition(), "on its bone at frame " + frame);
            assertEquals(overlord.getOrientation(), gattling.getOrientation(), "turned with it");
        }
        assertTrue(tank.getBody().getHealth() < 1000f, "it fired on its own");

        overlord.getBody().setHealth(0f);
        logic.update();
        assertEquals(0f, gattling.getBody().getHealth(), "and died with what it rode");
    }
}
