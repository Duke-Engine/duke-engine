package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectHurt;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Bones;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ObjectId;
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

    /**
     * A carrier with a rider and nothing else, told to evacuate, keeps it on at its bone, and an exit naming the rider
     * does nothing: the reference's Overlord keeps its add-on through an evacuate.
     */
    @Test
    void anEvacuateOrAnExitLeavesARiderOn() {
        var overlordType = RtsTemplate.named("Overlord").model("models/bones/overlord.gltf")
                .module(new ActiveBody.Data(1100f)).module(new MoveUpdate.Data(30f))
                .module(new ContainModule.Data(1, null, false, "GUNNER")).build();
        var logic = world(overlordType, RANGER);
        var overlord = logic.spawn(overlordType, new Coord3D(0f, 0f, 0f), 1);
        var gattling = logic.spawn(RANGER, new Coord3D(0f, 0f, 0f), 1);
        overlord.findModule(ContainModule.class).load(gattling);

        assertFalse(ContainModule.evacuate(logic,
                new uz.dukeengine.rts.message.GameMessage.Evacuate(1, overlord.getId())), "nobody to let out");
        assertFalse(ContainModule.exit(logic,
                new uz.dukeengine.rts.message.GameMessage.ExitContainer(1, gattling.getId())), "nor the rider");
        logic.update();

        assertTrue(gattling.isContained(), "still on");
        assertEquals(Bones.inWorld(overlord, "GUNNER"), gattling.getPosition(), "at its bone");
        overlord.findModule(ContainModule.class).unload(gattling);
        assertFalse(gattling.isContained(), "let off by the game's own unload");
    }

    /**
     * The reference's Helix, riding one kind: its add-on on top at the bone, two soldiers inside. The add-on fires from
     * its bone and the two hold fire, until the bunker's upgrade lets them; an evacuate lets the two out and keeps the
     * add-on on.
     */
    @Test
    void aHoldRidingOneKindSeatsItOnTopAndTheRestInside() {
        var portable = Kind.of("PORTABLE_STRUCTURE");
        var helixType = RtsTemplate.named("Helix").model("models/bones/overlord.gltf")
                .module(new ActiveBody.Data(1100f)).module(new MoveUpdate.Data(30f))
                .module(new ContainModule.Data(6, null, false, "GUNNER", false, null, null, null, false,
                        List.of(portable))).build();
        var gattlingType = RtsTemplate.named("Gattling").kindOf(portable).module(new ActiveBody.Data(100f))
                .module(new WeaponUpdate.Data(5f, 100f, 10, DamageType.NORMAL, 0f)).build();
        var logic = world(helixType, gattlingType, RANGER, TANK);
        var helix = logic.spawn(helixType, new Coord3D(0f, 0f, 0f), 1);
        var gattling = logic.spawn(gattlingType, new Coord3D(0f, 0f, 0f), 1);
        var soldiers = List.of(logic.spawn(RANGER, new Coord3D(-30f, 0f, 0f), 1),
                logic.spawn(RANGER, new Coord3D(-40f, 0f, 0f), 1));
        var tank = logic.spawn(TANK, new Coord3D(40f, 60f, 0f), 2);
        var hold = helix.findModule(ContainModule.class);
        hold.load(gattling);
        soldiers.forEach(hold::load);
        logic.drainEvents();

        assertEquals(Set.of(gattling.getId()), shootersOver(logic, tank, 60), "the rider fires, the two hold fire");
        assertEquals(Bones.inWorld(helix, "GUNNER"), gattling.getPosition(), "from its bone");
        assertTrue(hold.rides(gattling) && !hold.rides(soldiers.get(0)), "it rides, they sit inside");

        hold.setPassengersFire(true);
        assertEquals(Set.of(gattling.getId(), soldiers.get(0).getId(), soldiers.get(1).getId()),
                shootersOver(logic, tank, 60), "all three, once the two may fire");

        assertTrue(ContainModule.evacuate(logic,
                new uz.dukeengine.rts.message.GameMessage.Evacuate(1, helix.getId())));
        assertTrue(soldiers.stream().noneMatch(GameObject::isContained), "the two come out");
        assertTrue(gattling.isContained(), "the rider stays on");
    }

    /** A game's turret, turned where the test says. */
    static final class TurnedTurret extends uz.dukeengine.core.module.Module implements Turret {
        private final float turn;

        TurnedTurret(GameObject owner, float turn) {
            super(owner);
            this.turn = turn;
        }

        @Override
        public float turretTurn() {
            return turn;
        }
    }

    /**
     * The reference's heavy tank, its battle bunker riding at FIREPOINT01 on its turret, 15 behind the turret's pivot,
     * the turret turned a quarter round at something beside it: the bunker stands a quarter round about the pivot from
     * its default seat — 21.2 from it — facing the turret's way.
     */
    @Test
    void aRiderOnATurretIsTurnedWithIt() {
        var tankType = RtsTemplate.named("HeavyTank").model("models/bones/heavy_tank.gltf")
                .module(new ActiveBody.Data(1000f)).module(new MoveUpdate.Data(30f))
                .module(new ContainModule.Data(1, null, false, "FIREPOINT01", false, null, null, null, false,
                        List.of(), false, "TURRET01")).build();
        var logic = world(tankType, RANGER);
        var tank = logic.spawn(tankType, new Coord3D(100f, 100f, 0f), 1);
        var bunker = logic.spawn(RANGER, new Coord3D(100f, 100f, 0f), 1);
        tank.addModule(new TurnedTurret(tank, (float) (Math.PI / 2)));
        tank.findModule(ContainModule.class).load(bunker);
        logic.update();

        var pivot = Bones.inWorld(tank, "TURRET01");
        var seat = Bones.inWorld(tank, "FIREPOINT01");
        assertEquals(pivot.x(), bunker.getPosition().x(), 1e-3f, "a quarter round about the pivot");
        assertEquals(pivot.y() - 15f, bunker.getPosition().y(), 1e-3f);
        assertEquals(15f, bunker.getPosition().z(), 1e-3f, "as high as its seat");
        assertEquals(21.21f, seat.distance(bunker.getPosition()), 0.01f, "21.2 from its default seat");
        assertEquals((float) (Math.PI / 2), bunker.getOrientation(), 1e-5f, "facing the turret's way");
    }

    /** Who hurt {@code victim} over the next {@code frames} frames. */
    private static Set<ObjectId> shootersOver(CombatTest.CombatLogic logic, GameObject victim, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
        }
        return logic.drainEvents().stream().filter(ObjectHurt.class::isInstance).map(ObjectHurt.class::cast)
                .filter(hurt -> hurt.object().equals(victim.getId())).map(ObjectHurt::attacker)
                .collect(Collectors.toSet());
    }
}
