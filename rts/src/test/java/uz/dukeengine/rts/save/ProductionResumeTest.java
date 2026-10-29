package uz.dukeengine.rts.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.save.ModuleStates;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.RtsLogic;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.CapacityGate;
import uz.dukeengine.rts.module.ContainModule;
import uz.dukeengine.rts.module.PowerModule;
import uz.dukeengine.rts.module.ProductionReservation;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.player.Upgrade;

/**
 * Factories and holds saved mid-activity, loaded and played on frame for frame as the world they were saved from: a
 * queue of units and research with a door-side wait, hangar doors opening and held with a reservation's tokens, a line
 * held up by power, a hold with passengers firing, a garrison that sees out and a network of tunnels.
 */
class ProductionResumeTest {

    private static final Upgrade ARMOUR = new Upgrade("Upgrade_Armour", 400, Map.of("Armour", 1.2f), 50,
            Upgrade.Scope.PLAYER, null);

    /** Two hangars, a jet in each; the token for a job is the number of its space, which is also its door. */
    static final class Hangars extends Module implements ProductionReservation, Saveable {
        record Data() implements ModuleData {
        }

        private final boolean[] used = new boolean[2];

        Hangars(GameObject owner) {
            super(owner);
        }

        @Override
        public Object reserve(ThingTemplate unit) {
            for (int space = 0; space < used.length; space++) {
                if (!used[space]) {
                    used[space] = true;
                    return space;
                }
            }
            return null;
        }

        @Override
        public void release(Object token) {
            used[(Integer) token] = false;
        }

        @Override
        public void place(GameObject unit, Object token) {
        }

        @Override
        public int door(Object token) {
            return (Integer) token;
        }

        @Override
        public void saveToken(Object token, SaveOut out) {
            out.writeInt((Integer) token);
        }

        @Override
        public Object loadToken(SaveIn in) {
            return in.readInt();
        }

        @Override
        public void save(SaveOut out) {
            out.writeBoolean(used[0]).writeBoolean(used[1]);
        }

        @Override
        public void load(SaveIn in) {
            used[0] = in.readBoolean();
            used[1] = in.readBoolean();
        }
    }

    /** A reservation that hands out a token and cannot write it. */
    static final class Silent extends Module implements ProductionReservation, Saveable {
        record Data() implements ModuleData {
        }

        Silent(GameObject owner) {
            super(owner);
        }

        @Override
        public Object reserve(ThingTemplate unit) {
            return Boolean.TRUE;
        }

        @Override
        public void release(Object token) {
        }

        @Override
        public void place(GameObject unit, Object token) {
        }

        @Override
        public void save(SaveOut out) {
        }

        @Override
        public void load(SaveIn in) {
        }
    }

    private static ThingTemplate unit(String name, int cost, int frames) {
        return RtsTemplate.named(name).geometry(new Geometry.Cylinder(3f, 3f)).module(new ActiveBody.Data(50f))
                .buildCost(cost).buildTimeFrames(frames).build();
    }

    private static RtsLogic world(ThingTemplate... templates) {
        var logic = new RtsLogic();
        var modules = logic.getThingFactory().getModuleFactory();
        modules.register(Hangars.Data.class, (owner, data) -> new Hangars(owner));
        modules.register(Silent.Data.class, (owner, data) -> new Silent(owner));
        for (var template : templates) {
            logic.getThingFactory().addTemplate(template);
        }
        logic.init();
        logic.addUpgrades(List.of(ARMOUR));
        return logic;
    }

    private static int sides(RtsLogic logic) {
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        return red.getIndex();
    }

    private static GameObject first(RtsSimulation logic) {
        return logic.findObject(new ObjectId(1));
    }

    private static ProductionUpdate line(RtsSimulation logic) {
        return first(logic).findModule(ProductionUpdate.class);
    }

    /** A world made by {@code rules}, set up and played {@code frames}: where a save is about to be taken. */
    private static <W extends RtsSimulation> W reach(Supplier<W> rules, Consumer<W> setup,
            BiConsumer<W, Integer> orders, int frames) {
        var logic = rules.get();
        setup.accept(logic);
        for (int frame = 0; frame < frames; frame++) {
            orders.accept(logic, logic.getFrame());
            logic.update();
        }
        return logic;
    }

    // ---- a queue, research, a wait at the exit, a rally point, a cancel ----

    private static RtsLogic lineRules() {
        return world(unit("Soldier", 100, 12), unit("Tank", 300, 40),
                RtsTemplate.named("Factory").geometry(new Geometry.Box(20f, 20f, 10f))
                        .module(new ActiveBody.Data(500f))
                        .module(new ProductionUpdate.Data(List.of("Soldier", "Tank"), List.of("Upgrade_Armour"),
                                new ProductionUpdate.Exit(new Coord3D(-10f, -30f, 0f), new Coord3D(53f, -30f, 0f), 20,
                                        1),
                                List.of(), new ProductionUpdate.Words("BUSY", "MADE", 15), false))
                        .build());
    }

    private static void lineSetup(RtsLogic logic) {
        int red = sides(logic);
        logic.getRtsPlayer(red).give(5000);
        var line = logic.spawn(logic.findTemplate("Factory"), new Coord3D(200f, 200f, 0f), red)
                .findModule(ProductionUpdate.class);
        var soldier = logic.findTemplate("Soldier");
        line.queue(soldier);
        line.queue(soldier);
        line.queueResearch(ARMOUR);
        line.queue(logic.findTemplate("Tank"));
        line.queue(soldier);
    }

    private static void lineOrders(RtsLogic logic, int frame) {
        var line = line(logic);
        switch (frame) {
            case 40 -> line.setRallyPoint(new Coord3D(400f, 400f, 0f));
            case 55 -> line.cancel(1); // the tank, behind the research
            case 60 -> line.queue(logic.findTemplate("Soldier"));
            default -> {
            }
        }
    }

    @Test
    void aQueueOfUnitsAndResearchKeepsItsJobsItsWaitAtTheExitItsRallyPointAndARefund() {
        var seen = reach(ProductionResumeTest::lineRules, ProductionResumeTest::lineSetup,
                ProductionResumeTest::lineOrders, 28);
        var queued = line(seen).getEntries();
        assertEquals(4, queued.size(), "the first soldier is out, the second finished and waiting at the exit");
        assertEquals("Soldier", queued.getFirst().name());
        assertEquals(1f, queued.getFirst().progress(), "done, and waiting for the way out");
        assertTrue(queued.get(1).isResearch());

        var saved = SaveLoadCheck.resumesOnlyWithItsModules(ProductionResumeTest::lineRules,
                ProductionResumeTest::lineSetup, ProductionResumeTest::lineOrders, 28, 250);
        assertTrue(saved.contains("Upgrade_Armour"), saved);
    }

    // ---- hangar doors, held open, with a reservation's tokens, and a cancel that frees a space ----

    private static RtsLogic hangarRules() {
        var first = new ProductionUpdate.Door(10, 15, 10, "OPENING_1", "OPEN_1", "CLOSING_1");
        var second = new ProductionUpdate.Door(10, 15, 10, "OPENING_2", "OPEN_2", "CLOSING_2");
        return world(unit("Raptor", 100, 12), RtsTemplate.named("Airfield").geometry(new Geometry.Box(20f, 20f, 10f))
                .module(new ActiveBody.Data(500f))
                .module(new ProductionUpdate.Data(List.of("Raptor"), List.of(), null, List.of(first, second), null, false))
                .module(new Hangars.Data()).build());
    }

    private static void hangarSetup(RtsLogic logic) {
        int red = sides(logic);
        logic.getRtsPlayer(red).give(5000);
        var line = logic.spawn(logic.findTemplate("Airfield"), new Coord3D(200f, 200f, 0f), red)
                .findModule(ProductionUpdate.class);
        line.queue(logic.findTemplate("Raptor"));
        line.queue(logic.findTemplate("Raptor"));
    }

    private static void hangarOrders(RtsLogic logic, int frame) {
        var line = line(logic);
        switch (frame) {
            case 5 -> line.holdDoorOpen(1, true);
            case 19 -> line.cancel(1);
            case 50 -> line.holdDoorOpen(1, false);
            case 60 -> line.queue(logic.findTemplate("Raptor"));
            default -> {
            }
        }
    }

    @Test
    void doorsOpeningAndHeldOpenAndEachJobsHangarComeBackAndACancelFreesItsSpace() {
        var seen = reach(ProductionResumeTest::hangarRules, ProductionResumeTest::hangarSetup,
                ProductionResumeTest::hangarOrders, 17);
        assertEquals(2, line(seen).getQueueSize());
        assertTrue(first(seen).hasCondition("OPENING_1"), "the first jet's door is opening");
        assertTrue(first(seen).hasCondition("OPEN_2"), "the second's is held open");

        SaveLoadCheck.resumesOnlyWithItsModules(ProductionResumeTest::hangarRules, ProductionResumeTest::hangarSetup,
                ProductionResumeTest::hangarOrders, 17, 120);
    }

    // ---- a line held up by power ----

    private static RtsLogic powerRules() {
        return world(unit("Soldier", 100, 12), RtsTemplate.named("Factory").geometry(new Geometry.Box(20f, 20f, 10f))
                .module(new ActiveBody.Data(500f)).module(new ProductionUpdate.Data(List.of("Soldier")))
                .module(new PowerModule.Data(10, 8)).module(new CapacityGate.Data()).build());
    }

    private static void powerSetup(RtsLogic logic) {
        int red = sides(logic);
        logic.getRtsPlayer(red).give(5000);
        var line = logic.spawn(logic.findTemplate("Factory"), new Coord3D(200f, 200f, 0f), red)
                .findModule(ProductionUpdate.class);
        line.queue(logic.findTemplate("Soldier"));
        line.queue(logic.findTemplate("Soldier"));
    }

    private static void powerOrders(RtsLogic logic, int frame) {
        var power = first(logic).findModule(PowerModule.class);
        if (frame == 8) {
            power.setBonus(-10); // the grid fails mid-job
        } else if (frame == 30) {
            power.setBonus(0);
        }
    }

    @Test
    void aLineHeldUpByPowerKeepsTheFramesItHadRunAndGoesOnWhenItReturns() {
        var seen = reach(ProductionResumeTest::powerRules, ProductionResumeTest::powerSetup,
                ProductionResumeTest::powerOrders, 15);
        var head = line(seen).getEntries().getFirst();
        assertTrue(head.progress() > 0f && head.progress() < 1f, "stalled part-way: " + head.progress());

        SaveLoadCheck.resumesOnlyWithItsModules(ProductionResumeTest::powerRules, ProductionResumeTest::powerSetup,
                ProductionResumeTest::powerOrders, 15, 100);
    }

    // ---- holds ----

    private static RtsLogic holdRules() {
        var rifleman = RtsTemplate.named("Rifleman").geometry(new Geometry.Cylinder(2f, 4f))
                .module(new ActiveBody.Data(100f)).build();
        return world(rifleman,
                RtsTemplate.named("Bunker").geometry(new Geometry.Box(30f, 30f, 12f))
                        .module(new ActiveBody.Data(1000f)).module(new ContainModule.Data(6)).build(),
                RtsTemplate.named("Garrison").geometry(new Geometry.Box(30f, 30f, 12f))
                        .module(new ActiveBody.Data(1000f))
                        .module(new ContainModule.Data(4, null, false, null, false, null, null, null, true)).build(),
                RtsTemplate.named("Tunnel").geometry(new Geometry.Cylinder(10f, 8f)).module(new ActiveBody.Data(1000f))
                        .module(new ContainModule.Data(10, "Tunnel")).build(),
                RtsTemplate.named("Cave").geometry(new Geometry.Cylinder(10f, 8f)).module(new ActiveBody.Data(1000f))
                        .module(new ContainModule.Data(10, "Other")).build());
    }

    private static GameObject put(RtsLogic logic, String template, int side, float x, float y) {
        return logic.spawn(logic.findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    private static void evacuate(RtsLogic logic, int container) {
        var holder = logic.findObject(new ObjectId(container));
        ContainModule.evacuate(logic, new GameMessage.Evacuate(holder.getPlayerIndex(), holder.getId()));
    }

    @Test
    void aHoldKeepsItsPassengersInOrderAndWhetherTheyFireAndOneCanGetOutAfterTheLoad() {
        SaveLoadCheck.resumesOnlyWithItsModules(ProductionResumeTest::holdRules, logic -> {
            int red = sides(logic);
            var bunker = put(logic, "Bunker", red, 300f, 300f);
            var hold = bunker.findModule(ContainModule.class);
            for (int one = 0; one < 4; one++) {
                assertTrue(hold.load(put(logic, "Rifleman", red, 250f + one * 5f, 300f)));
            }
            hold.setPassengersFire(true); // an upgrade's bunker
        }, (logic, frame) -> {
            if (frame == 20) {
                var bunker = first(logic);
                var second = new ObjectId(3);
                ContainModule.exit(logic, new GameMessage.ExitContainer(bunker.getPlayerIndex(), second));
            } else if (frame == 35) {
                evacuate(logic, 1);
            }
        }, 10, 60);
    }

    @Test
    void aGarrisonThatSeesOutKeepsThemSeeingOut() {
        SaveLoadCheck.resumesOnlyWithItsModules(ProductionResumeTest::holdRules, logic -> {
            int red = sides(logic);
            var hold = put(logic, "Garrison", red, 300f, 300f).findModule(ContainModule.class);
            for (int one = 0; one < 3; one++) {
                assertTrue(hold.load(put(logic, "Rifleman", red, 250f + one * 5f, 300f)));
            }
            assertTrue(logic.findObject(new ObjectId(2)).seesOut());
        }, (logic, frame) -> {
            if (frame == 25) {
                evacuate(logic, 1);
            }
        }, 10, 50);
    }

    @Test
    void aNetworkOfTunnelsOnceItsHoldChangedComesOutAtTheOneThatJoinedItAfterTheLoad() {
        SaveLoadCheck.resumesOnlyWithItsModules(ProductionResumeTest::holdRules, logic -> {
            int red = sides(logic);
            var in = put(logic, "Tunnel", red, 100f, 100f).findModule(ContainModule.class);
            put(logic, "Tunnel", red, 500f, 500f);
            var cave = put(logic, "Cave", red, 300f, 700f).findModule(ContainModule.class);
            for (int one = 0; one < 3; one++) {
                assertTrue(in.load(put(logic, "Rifleman", red, 120f, 100f + one * 5f)));
            }
            cave.shareWith("Tunnel"); // the script gives it the network at run time
            assertEquals(3, cave.getPassengerCount());
        }, (logic, frame) -> {
            if (frame == 12) {
                evacuate(logic, 3);
            }
        }, 8, 40);
    }

    // ---- what a save refuses ----

    @Test
    void aJobWhoseReservationCannotWriteItsTokenRefusesTheSaveByTheReservationsName() {
        var logic = world(unit("Soldier", 100, 12), RtsTemplate.named("Factory").module(new ActiveBody.Data(500f))
                .module(new ProductionUpdate.Data(List.of("Soldier"))).module(new Silent.Data()).build());
        int red = sides(logic);
        logic.getRtsPlayer(red).give(500);
        var factory = put(logic, "Factory", red, 0f, 0f);
        assertTrue(factory.findModule(ProductionUpdate.class).queue(logic.findTemplate("Soldier")));

        var refused = assertThrows(UnsavedModuleException.class,
                () -> ModuleStates.save(factory, logic.getThingFactory().getModuleFactory()));
        assertEquals(Silent.class.getName(), refused.module());
    }

    @Test
    void aQueuedUnitOrUpgradeTheWorldNoLongerKnowsRefusesTheLoadByName() {
        var logic = lineRules();
        lineSetup(logic);
        var factory = first(logic);
        var line = ModuleStates.save(factory, logic.getThingFactory().getModuleFactory());

        var withoutTank = world(unit("Soldier", 100, 12),
                RtsTemplate.named("Factory").module(new ActiveBody.Data(500f))
                        .module(new ProductionUpdate.Data(List.of("Soldier", "Tank"), List.of("Upgrade_Armour"),
                                new ProductionUpdate.Exit(null, null, 20, 1), List.of(),
                                new ProductionUpdate.Words("BUSY", "MADE", 15), false))
                        .build());
        sides(withoutTank);
        var empty = put(withoutTank, "Factory", 1, 0f, 0f);
        var refused = assertThrows(UnsavedModuleException.class,
                () -> ModuleStates.load(empty, line, withoutTank.getThingFactory().getModuleFactory()));
        assertEquals(ProductionUpdate.class.getName(), refused.module());
        assertTrue(refused.getMessage().contains("Tank"), refused.getMessage());
    }
}
