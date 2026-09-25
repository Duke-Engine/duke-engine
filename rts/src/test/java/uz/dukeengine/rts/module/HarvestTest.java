package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

class HarvestTest {

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

    private TestLogic logic;
    private ThingFactory thingFactory;
    private ThingTemplate harvester;
    private ThingTemplate supplyPile;
    private int usa;

    @BeforeEach
    void setUp() {
        thingFactory = new ThingFactory(RtsModules.withDefaults());
        harvester = ThingTemplate.named("Harvester")
                .module(new ActiveBody.Data(100f))
                .module(new HarvestUpdate.Data(100, 10, 0f)) // 100 per 10 frames
                .build();
        supplyPile = ThingTemplate.named("Supplies")
                .module(new SupplyModule.Data(1000))
                .build();
        thingFactory.addTemplate(harvester);
        thingFactory.addTemplate(supplyPile);

        logic = new TestLogic(thingFactory);
        logic.init();
        usa = logic.getPlayerList().addPlayer("USA").getIndex();
    }

    private GameObject spawn(ThingTemplate template) {
        var o = logic.createObject(template);
        o.setPlayerIndex(usa);
        o.setPosition(Coord3D.ZERO);
        return o;
    }

    @Test
    void harvesterDepositsMoneyEachTrip() {
        spawn(harvester);
        spawn(supplyPile);
        assertEquals(0, logic.getRtsPlayer(usa).getMoney());

        for (int i = 0; i < 10; i++) {
            logic.update();
        }
        assertEquals(100, logic.getRtsPlayer(usa).getMoney()); // one trip

        for (int i = 0; i < 20; i++) {
            logic.update();
        }
        assertEquals(300, logic.getRtsPlayer(usa).getMoney()); // three trips total
    }

    @Test
    void harvestingStopsWhenPileExhausted() {
        var smallPile = ThingTemplate.named("SmallSupplies")
                .module(new SupplyModule.Data(250)) // 100,100,50 then empty
                .build();
        thingFactory.addTemplate(smallPile);

        spawn(harvester);
        var pile = spawn(smallPile);

        for (int i = 0; i < 100; i++) {
            logic.update();
        }
        assertEquals(250, logic.getRtsPlayer(usa).getMoney()); // never exceeds the pile
        assertEquals(0, pile.findModule(SupplyModule.class).getRemaining());
    }

    @Test
    void idleWithoutSupplies() {
        spawn(harvester); // no pile present
        for (int i = 0; i < 50; i++) {
            logic.update();
        }
        assertEquals(0, logic.getRtsPlayer(usa).getMoney());
    }

    @Test
    void supplyTakeNeverGoesNegative() {
        var owner = new GameObject(new uz.dukeengine.core.thing.ObjectId(1),
                ThingTemplate.named("X").build());
        var supply = new SupplyModule(owner, new SupplyModule.Data(250));
        assertEquals(250, supply.take(1000)); // capped at remaining
        assertEquals(0, supply.getRemaining());
        assertEquals(0, supply.take(50)); // nothing left
        assertTrue(supply.getRemaining() == 0);
    }

    // ---- standing at either end ----

    /** A harvester with legs, a pile and a depot of its side, 150 apart on open ground. */
    private GameObject walkingHarvesterBetween(int framesAtDepot) {
        logic.setPathGrid(new PathGrid(40, 40));
        var walker = RtsTemplate.named("Walker")
                .geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f))
                .module(new HarvestUpdate.Data(100, 10, 0f, framesAtDepot, 0, 0))
                .build();
        var depot = RtsTemplate.named("Depot")
                .geometry(new Geometry.Box(10f, 10f, 8f))
                .module(new SupplyDepot.Data())
                .build();
        var pile = RtsTemplate.named("Pile")
                .geometry(new Geometry.Cylinder(5f, 5f))
                .module(new SupplyModule.Data(1000))
                .build();
        thingFactory.addTemplate(walker);
        thingFactory.addTemplate(depot);
        thingFactory.addTemplate(pile);
        spawnAt(depot, 100f, 100f);
        spawnAt(pile, 250f, 100f);
        return spawnAt(walker, 150f, 100f);
    }

    private GameObject spawnAt(ThingTemplate template, float x, float y) {
        var o = logic.createObject(template);
        o.setPlayerIndex(usa);
        o.setPosition(new Coord3D(x, y, 0f));
        return o;
    }

    /** What one frame left behind: where the harvester stood, whether it was at the depot, and the purse. */
    private record Frame(Coord3D position, boolean atDepot, int carrying, int money) {
    }

    /** Every frame until the first load is banked, and ten more. */
    private java.util.List<Frame> untilTheFirstLoadIsBanked(GameObject harvester) {
        var depot = logic.getObjects().stream()
                .filter(o -> o.findModule(SupplyDepot.class) != null).findFirst().orElseThrow();
        var frames = new java.util.ArrayList<Frame>();
        for (int frame = 0; frame < 3000; frame++) {
            logic.update();
            frames.add(new Frame(harvester.getPosition(), logic.isBeside(harvester, depot),
                    harvester.findModule(HarvestUpdate.class).getCarrying(), logic.getRtsPlayer(usa).getMoney()));
            if (frames.getLast().money() > 0 && frames.size() > 10 && frames.get(frames.size() - 11).money() > 0) {
                return frames;
            }
        }
        throw new AssertionError("nothing was ever banked");
    }

    /**
     * The frame it came to the depot with a load — beside it, having carried one the frame before: the first
     * frame of its wait there.
     */
    private static int arrival(java.util.List<Frame> frames) {
        for (int at = 1; at < frames.size(); at++) {
            if (frames.get(at).atDepot() && frames.get(at - 1).carrying() > 0) {
                return at;
            }
        }
        throw new AssertionError("it never came back to the depot with a load");
    }

    private static int firstMoney(java.util.List<Frame> frames) {
        for (int at = 0; at < frames.size(); at++) {
            if (frames.get(at).money() > 0) {
                return at;
            }
        }
        return -1;
    }

    /**
     * Ten frames at the depot: it is seen standing beside it for ten frames, and the money rises on the tenth —
     * the frame it arrived in the first of them, as at the pile — not the moment it arrives.
     */
    @Test
    void aHarvesterStandsItsFramesAtTheDepotBeforeTheMoneyArrives() {
        var frames = untilTheFirstLoadIsBanked(walkingHarvesterBetween(10));
        int arrived = arrival(frames);

        assertEquals(arrived + 9, firstMoney(frames), "banked on its tenth frame at the depot");
        assertEquals(100, frames.get(arrived + 9).money());
        for (int at = arrived; at <= arrived + 9; at++) {
            assertEquals(frames.get(arrived).position(), frames.get(at).position(), "standing still, frame " + at);
            assertTrue(frames.get(at).atDepot());
        }
        assertTrue(frames.get(arrived + 10).position().distance(frames.get(arrived).position()) > 0f,
                "and off again the frame after");
    }

    /** Nothing at the depot is what there always was: the money on the frame it arrives. */
    @Test
    void aHarvesterWithNoWaitAtTheDepotBanksTheMomentItArrives() {
        var frames = untilTheFirstLoadIsBanked(walkingHarvesterBetween(0));

        assertEquals(arrival(frames), firstMoney(frames));
    }

    /**
     * A load a unit at a time, as the reference docks a truck: four boxes worth 75 each, 30 frames an act. It
     * is handed a box on each of the first four acts and leaves on the fifth, the one that finds it full — five
     * acts, 150 frames, 5.0 s at the pile for four boxes.
     */
    @Test
    void aLoadHandedOverAUnitAtATimeTakesOneActMoreThanItHasUnits() {
        var trucker = ThingTemplate.named("Truck")
                .module(new ActiveBody.Data(100f))
                .module(new HarvestUpdate.Data(300, 0, 0f, 0, 30, 75))
                .build();
        thingFactory.addTemplate(trucker);
        var truck = spawn(trucker);
        spawn(supplyPile);

        var carried = new java.util.ArrayList<Integer>();
        int banked = -1;
        for (int frame = 1; frame <= 150 && banked < 0; frame++) {
            logic.update();
            if (frame % 30 == 0 && frame < 150) {
                carried.add(truck.findModule(HarvestUpdate.class).getCarrying());
            }
            if (logic.getRtsPlayer(usa).getMoney() > 0) {
                banked = frame;
            }
        }
        assertEquals(java.util.List.of(75, 150, 225, 300), carried, "a box an act");
        assertEquals(150, banked, "and the act that finds it full is the fifth");
        assertEquals(300, logic.getRtsPlayer(usa).getMoney());
    }

    /** A pile that runs out part-way ends the wait at the act that finds it empty, and what was loaded goes home. */
    @Test
    void aPileThatRunsOutPartWayEndsTheWaitEarly() {
        var trucker = ThingTemplate.named("Truck")
                .module(new ActiveBody.Data(100f))
                .module(new HarvestUpdate.Data(4, 0, 0f, 0, 30, 0))
                .build();
        var twoBoxes = ThingTemplate.named("TwoBoxes")
                .module(new SupplyModule.Data(2))
                .build();
        thingFactory.addTemplate(trucker);
        thingFactory.addTemplate(twoBoxes);
        spawn(trucker);
        spawn(twoBoxes);

        for (int frame = 1; frame < 90; frame++) {
            logic.update();
        }
        assertEquals(0, logic.getRtsPlayer(usa).getMoney(), "two boxes handed over, the third act not yet come");
        logic.update();
        assertEquals(2, logic.getRtsPlayer(usa).getMoney(), "the third act found the pile empty: 90 frames, not 150");
    }

    /**
     * The truck of a real map: the straight line from its pile toward its depot runs onto a cliff face just
     * north-west of the depot. It used to be sent there, find no route, stand still, and flicker between
     * arrived and on its way until the end of the game with its load on board. It walks round the rock to the
     * depot's side instead, waits its frames there, and banks.
     */
    @Test
    void aHarvesterWhoseDepotStandsBesideACliffStillBanksAfterItsWait() {
        var grid = new PathGrid(30, 30);
        for (int cx = 12; cx <= 13; cx++) {
            for (int cy = 16; cy <= 18; cy++) {
                grid.setBlocked(cx, cy, true);
            }
        }
        logic.setPathGrid(grid);
        var truck = RtsTemplate.named("Truck")
                .geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f))
                .module(new HarvestUpdate.Data(100, 10, 0f, 10, 0, 0))
                .build();
        var depot = RtsTemplate.named("CliffDepot")
                .geometry(new Geometry.Box(10f, 10f, 8f))
                .module(new SupplyDepot.Data())
                .build();
        var pile = RtsTemplate.named("CliffPile")
                .geometry(new Geometry.Cylinder(5f, 5f))
                .module(new SupplyModule.Data(1000))
                .build();
        thingFactory.addTemplate(truck);
        thingFactory.addTemplate(depot);
        thingFactory.addTemplate(pile);
        spawnAt(depot, 155f, 155f);
        spawnAt(pile, 45f, 265f);
        spawnAt(truck, 60f, 250f);

        int frame = 0;
        while (logic.getRtsPlayer(usa).getMoney() == 0 && frame < 3000) {
            logic.update();
            frame++;
        }
        assertEquals(100, logic.getRtsPlayer(usa).getMoney(), "banked, after " + frame + " frames");
    }

    // ---- told where to work, paid on arrival, and flying ----

    private ThingTemplate kind(String name, uz.dukeengine.core.module.ModuleData... modules) {
        var builder = RtsTemplate.named(name).geometry(new Geometry.Cylinder(4f, 6f));
        for (var module : modules) {
            builder.module(module);
        }
        var made = builder.build();
        thingFactory.addTemplate(made);
        return made;
    }

    @Test
    void aTruckToldToWorkWarehouseBGoesBackToItAfterEveryDeliveryThoughANearerOneStands() {
        logic.setPathGrid(new PathGrid(60, 60));
        var truck = spawnAt(kind("Truck", new ActiveBody.Data(100f), new MoveUpdate.Data(60f),
                new HarvestUpdate.Data(100, 10, 0f)), 130f, 100f);
        spawnAt(kind("Centre", new SupplyDepot.Data()), 100f, 100f);
        var a = spawnAt(kind("WarehouseA", new SupplyModule.Data(1000)), 170f, 100f);
        var b = spawnAt(kind("WarehouseB", new SupplyModule.Data(1000)), 100f, 400f);

        truck.findModule(HarvestUpdate.class).workAt(b);
        for (int frame = 0; frame < 6000 && logic.getRtsPlayer(usa).getMoney() < 300; frame++) {
            logic.update();
        }
        assertEquals(300, logic.getRtsPlayer(usa).getMoney(), "three deliveries");
        assertEquals(700, b.findModule(SupplyModule.class).getRemaining(), "every load from B");
        assertEquals(1000, a.findModule(SupplyModule.class).getRemaining(), "none from the nearer A");
    }

    @Test
    void aTruckOrderedAwayOnItsWayHomeKeepsItsLoadAndTheMoneyIsUntouched() {
        logic.setPathGrid(new PathGrid(60, 60));
        var truck = spawnAt(kind("Truck", new ActiveBody.Data(100f), new MoveUpdate.Data(30f),
                new HarvestUpdate.Data(300, 10, 0f)), 450f, 100f);
        spawnAt(kind("Centre", new SupplyDepot.Data()), 100f, 100f);
        spawnAt(kind("Warehouse", new SupplyModule.Data(1000)), 480f, 100f);
        var harvest = truck.findModule(HarvestUpdate.class);
        for (int frame = 0; frame < 500 && harvest.getCarrying() < 300; frame++) {
            logic.update();
        }
        for (int frame = 0; frame < 20; frame++) {
            logic.update(); // on its way home with 300
        }
        assertEquals(300, harvest.getCarrying());

        truck.getLocomotor().moveTo(new Coord3D(450f, 500f, 0f)); // the player sends it elsewhere
        logic.update();
        for (int frame = 0; frame < 2000 && truck.getLocomotor().isMoving(); frame++) {
            logic.update();
        }
        assertTrue(truck.getPosition().distance(new Coord3D(100f, 100f, 0f)) > 300f, "stopped far from the centre");
        assertEquals(300, harvest.getCarrying(), "it still carries 300");
        assertEquals(0, logic.getRtsPlayer(usa).getMoney(), "and nothing was paid where it stopped");
    }

    @Test
    void aFlyingHarvesterFliesItsTripsAndIsPaidBesideTheCentre() {
        var chinook = spawnAt(kind("Chinook", new ActiveBody.Data(100f),
                new uz.dukeengine.core.module.FlyUpdate.Data(uz.dukeengine.core.module.FlyUpdate.Kind.HOVERING,
                        60f, 0f, 0f, 0f, 0f, 50f, 0f),
                new HarvestUpdate.Data(100, 10, 0f)), 300f, 100f);
        var centre = spawnAt(kind("Centre", new SupplyDepot.Data()), 100f, 100f);
        var warehouse = spawnAt(kind("Warehouse", new SupplyModule.Data(1000)), 500f, 100f);

        float nearestToThePile = Float.MAX_VALUE;
        boolean paidAwayFromTheCentre = false;
        int money = 0;
        for (int frame = 0; frame < 2000 && money == 0; frame++) {
            logic.update();
            nearestToThePile = Math.min(nearestToThePile,
                    uz.dukeengine.core.thing.World.reachBetween(chinook, warehouse));
            int now = logic.getRtsPlayer(usa).getMoney();
            if (now > money && !logic.isBeside(chinook, centre)) {
                paidAwayFromTheCentre = true;
            }
            money = now;
        }
        assertTrue(nearestToThePile <= logic.cellSize(), "it flew to the warehouse: " + nearestToThePile);
        assertEquals(100, money, "and back, and was paid");
        assertTrue(!paidAwayFromTheCentre, "only beside the centre");
    }

    // ---- loaded only beside its pile, idle once ordered, one place to work ----

    @Test
    void aHarvesterMovedOffItsPileMidLoadTakesNothingMoreUntilItIsBackBesideIt() {
        logic.setPathGrid(new PathGrid(60, 60));
        var truck = spawnAt(kind("Truck", new ActiveBody.Data(100f), new MoveUpdate.Data(30f),
                new HarvestUpdate.Data(300, 0, 0f, 0, 10, 75)), 120f, 100f);
        spawnAt(kind("Centre", new SupplyDepot.Data()), 450f, 450f);
        var pile = spawnAt(kind("Warehouse", new SupplyModule.Data(1000)), 100f, 100f);
        var harvest = truck.findModule(HarvestUpdate.class);
        for (int frame = 0; frame < 200 && harvest.getCarrying() < 75; frame++) {
            logic.update();
        }
        assertEquals(75, harvest.getCarrying(), "a box handed over");

        truck.setPosition(new Coord3D(120f, 350f, 0f)); // 250 off the pile, mid-load
        int frames = 0;
        while (!logic.isBeside(truck, pile) && frames < 2000) {
            logic.update();
            frames++;
            assertEquals(75, harvest.getCarrying(), "nothing taken away from the pile, frame " + frames);
        }
        assertTrue(logic.isBeside(truck, pile), "it went back to it");
        for (int frame = 0; frame < 200 && harvest.getCarrying() < 300; frame++) {
            logic.update();
        }
        assertEquals(300, harvest.getCarrying(), "and filled up there");
    }

    @Test
    void aLoadedTruckOrderedAwayStandsThereWithItsLoadUntilItIsToldWhereToWork() {
        logic.setPathGrid(new PathGrid(60, 60));
        var truck = spawnAt(kind("Truck", new ActiveBody.Data(100f), new MoveUpdate.Data(30f),
                new HarvestUpdate.Data(300, 10, 0f)), 450f, 100f);
        var centre = spawnAt(kind("Centre", new SupplyDepot.Data()), 100f, 100f);
        spawnAt(kind("Warehouse", new SupplyModule.Data(1000)), 480f, 100f);
        var harvest = truck.findModule(HarvestUpdate.class);
        for (int frame = 0; frame < 500 && harvest.getCarrying() < 300; frame++) {
            logic.update();
        }
        assertEquals(300, harvest.getCarrying());

        var away = new Coord3D(450f, 400f, 0f);
        truck.getLocomotor().moveTo(away); // the order, as the engine carries it out
        harvest.onOrder(new GameMessage.MoveTo(usa, java.util.List.of(truck.getId()), away)); // and then tells it
        for (int frame = 0; frame < 700; frame++) {
            logic.update();
        }
        assertTrue(harvest.isPaused());
        // On the block of cells round where it was sent, as every move to a place ends.
        assertTrue(truck.getPosition().distance(away) < 10f, "it stands where it was sent: " + truck.getPosition());
        assertEquals(300, harvest.getCarrying(), "with its load");
        assertEquals(0, logic.getRtsPlayer(usa).getMoney(), "and nothing paid");

        harvest.workAt(centre);
        for (int frame = 0; frame < 2000 && logic.getRtsPlayer(usa).getMoney() == 0; frame++) {
            logic.update();
        }
        assertEquals(300, logic.getRtsPlayer(usa).getMoney(), "told where to work, it goes and is paid");
    }

    @Test
    void aTruckWithAPartLoadToldItsDepotDeliversItAtOnce() {
        logic.setPathGrid(new PathGrid(60, 60));
        var truck = spawnAt(kind("Truck", new ActiveBody.Data(100f), new MoveUpdate.Data(30f),
                new HarvestUpdate.Data(300, 0, 0f, 0, 10, 100)), 470f, 100f);
        var centre = spawnAt(kind("Centre", new SupplyDepot.Data()), 100f, 100f);
        spawnAt(kind("Warehouse", new SupplyModule.Data(1000)), 500f, 100f);
        var harvest = truck.findModule(HarvestUpdate.class);
        for (int frame = 0; frame < 300 && harvest.getCarrying() < 100; frame++) {
            logic.update();
        }
        assertEquals(100, harvest.getCarrying(), "100 of 300");

        harvest.workAt(centre);
        for (int frame = 0; frame < 2000 && logic.getRtsPlayer(usa).getMoney() == 0; frame++) {
            logic.update();
        }
        assertEquals(100, logic.getRtsPlayer(usa).getMoney(), "paid its 100 on arrival");
    }

    @Test
    void aPileNamedAfterADepotIsWorkedAndItsLoadsGoToTheNearestDepot() {
        logic.setPathGrid(new PathGrid(60, 60));
        var truck = spawnAt(kind("Truck", new ActiveBody.Data(100f), new MoveUpdate.Data(60f),
                new HarvestUpdate.Data(100, 10, 0f)), 150f, 100f);
        spawnAt(kind("NearCentre", new SupplyDepot.Data()), 100f, 100f);
        var far = spawnAt(kind("FarCentre", new SupplyDepot.Data()), 500f, 500f);
        var pile = spawnAt(kind("Warehouse", new SupplyModule.Data(1000)), 100f, 300f);
        var harvest = truck.findModule(HarvestUpdate.class);

        harvest.workAt(far);
        harvest.workAt(pile); // the last named: the depot told before it is forgotten
        boolean everAtFar = false;
        for (int frame = 0; frame < 3000 && logic.getRtsPlayer(usa).getMoney() < 200; frame++) {
            logic.update();
            everAtFar |= logic.isBeside(truck, far);
        }
        assertEquals(200, logic.getRtsPlayer(usa).getMoney(), "two loads delivered");
        assertEquals(800, pile.findModule(SupplyModule.class).getRemaining(), "both from the pile it was told");
        assertTrue(!everAtFar, "to the nearest depot, never the far one told before");
    }

    // ---- beside where its move ends ----

    /**
     * The truck of the measurement, 34 by 14, bringing its first load to a depot 104 by 94 from a pile 330 off one of
     * the depot's corners, on legs that ease to a stop: beside the depot where its move there ends, the first time, and
     * paid FramesAtDepot after it first stopped. Its move used to end its close-enough distance short of a spot planned
     * for how it stood at the pile — more than a cell off — and it came again a second later, and again.
     */
    @Test
    void aLoadedTruckFromEachCornerOfABoxDepotIsPaidItsWaitAfterItFirstStops() {
        for (int corner = 0; corner < 4; corner++) {
            setUp();
            float sx = corner % 2 == 0 ? 1f : -1f;
            float sy = corner < 2 ? 1f : -1f;
            logic.setPathGrid(new PathGrid(90, 90));
            var truckKind = RtsTemplate.named("Truck")
                    .geometry(new Geometry.Box(17f, 7f, 10f))
                    .module(new ActiveBody.Data(100f))
                    .module(new MoveUpdate.Data(45f, 180f, 60f, 50f, 0f, 0f, 0.1f, 0f, 0f, 1f, false,
                            MoveUpdate.Gait.LEGS))
                    .module(new HarvestUpdate.Data(300, 30, 0f, 30, 0, 0))
                    .build();
            var depotKind = RtsTemplate.named("Depot")
                    .geometry(new Geometry.Box(52f, 47f, 20f))
                    .module(new SupplyDepot.Data())
                    .build();
            var pileKind = RtsTemplate.named("Pile")
                    .geometry(new Geometry.Cylinder(10f, 10f))
                    .module(new SupplyModule.Data(1000))
                    .build();
            thingFactory.addTemplate(truckKind);
            thingFactory.addTemplate(depotKind);
            thingFactory.addTemplate(pileKind);
            var depot = spawnAt(depotKind, 450f, 450f);
            float off = 330f / (float) Math.sqrt(2);
            float pileX = 450f + sx * (52f + off);
            float pileY = 450f + sy * (47f + off);
            spawnAt(pileKind, pileX, pileY);
            var truck = spawnAt(truckKind, pileX - sx * 25f, pileY - sy * 25f);
            // Standing a little turned at the pile, it loads there without a step: turned 22 degrees off its way home, it
            // reaches furthest toward the depot, and square to it at the end, 1.4 less far.
            truck.setOrientation((float) StrictMath.atan2(-sy, -sx) + (float) Math.toRadians(22));
            var harvest = truck.findModule(HarvestUpdate.class);

            int frame = 0;
            while (harvest.getCarrying() == 0 && frame < 600) {
                logic.update();
                frame++;
            }
            int stopped = -1;
            float reach = Float.NaN;
            while (logic.getRtsPlayer(usa).getMoney() == 0 && frame < 3000) {
                logic.update();
                frame++;
                if (stopped < 0 && !truck.getLocomotor().isMoving()
                        && uz.dukeengine.core.thing.World.reachBetween(truck, depot) < 40f) {
                    stopped = frame;
                    reach = uz.dukeengine.core.thing.World.reachBetween(truck, depot);
                }
            }
            assertTrue(stopped > 0, "corner " + corner + ": it came to the depot");
            assertTrue(reach <= logic.cellSize() * (1f + 1e-4f),
                    "corner " + corner + ": beside the depot where it first stopped, " + reach + " off");
            assertEquals(stopped + 29, frame, "corner " + corner + ": paid its 30 frames after it first stopped");
        }
    }

    // ---- needing a depot ----

    /**
     * A loaded harvester whose side has no depot, in a game that says it needs one: it banks nothing and waits by its
     * command centre; a depot put down, it goes and delivers there — the reference's truck regrouping.
     */
    @Test
    void aLoadedHarvesterWithNoDepotBanksNothingAndWaitsUntilOneIsPutDown() {
        logic.setPathGrid(new PathGrid(60, 60));
        var commandCentre = uz.dukeengine.core.thing.Kind.of("COMMANDCENTER");
        var truck = spawnAt(kind("Truck", new ActiveBody.Data(100f), new MoveUpdate.Data(30f),
                new HarvestUpdate.Data(100, 10, 0f, 0, 0, 0, true,
                        java.util.List.of(commandCentre, uz.dukeengine.rts.thing.RtsKinds.STRUCTURE))), 280f, 100f);
        spawnAt(kind("Warehouse", new SupplyModule.Data(1000)), 300f, 100f);
        var barracks = RtsTemplate.named("Barracks").kindOf(uz.dukeengine.rts.thing.RtsKinds.STRUCTURE)
                .geometry(new Geometry.Box(10f, 10f, 8f)).build();
        var centre = RtsTemplate.named("CommandCentre").kindOf(commandCentre)
                .kindOf(uz.dukeengine.rts.thing.RtsKinds.STRUCTURE).geometry(new Geometry.Box(15f, 15f, 10f)).build();
        thingFactory.addTemplate(barracks);
        thingFactory.addTemplate(centre);
        spawnAt(barracks, 300f, 250f); // nearer, but not the kind it waits by first
        var cc = spawnAt(centre, 100f, 400f);
        var harvest = truck.findModule(HarvestUpdate.class);

        for (int frame = 0; frame < 600; frame++) {
            logic.update();
        }
        assertEquals(100, harvest.getCarrying(), "it keeps its load");
        assertEquals(0, logic.getRtsPlayer(usa).getMoney(), "and banks nothing in 600 frames");
        assertTrue(harvest.isWaiting());
        assertTrue(logic.isBeside(truck, cc), "waiting by its command centre");

        var depot = spawnAt(kind("Depot", new SupplyDepot.Data()), 450f, 450f);
        boolean besideTheDepot = false;
        for (int frame = 0; frame < 2000 && logic.getRtsPlayer(usa).getMoney() == 0; frame++) {
            logic.update();
            besideTheDepot = logic.isBeside(truck, depot);
        }
        assertEquals(100, logic.getRtsPlayer(usa).getMoney(), "a depot put down, it delivers there");
        assertTrue(besideTheDepot, "beside it");
        assertTrue(!harvest.isWaiting());
    }

    /** A depot still going up is none to deliver to: the reference's truck does not dock at a site. */
    @Test
    void aDepotStillGoingUpIsNoneToDeliverTo() {
        logic.setPathGrid(new PathGrid(60, 60));
        var truck = spawnAt(kind("Truck", new ActiveBody.Data(100f), new MoveUpdate.Data(30f),
                new HarvestUpdate.Data(100, 10, 0f, 0, 0, 0, true, java.util.List.of())), 280f, 100f);
        spawnAt(kind("Warehouse", new SupplyModule.Data(1000)), 300f, 100f);
        var site = spawnAt(kind("Depot", new SupplyDepot.Data()), 100f, 100f);
        site.setStatus(uz.dukeengine.core.thing.ObjectStatus.UNDER_CONSTRUCTION);

        for (int frame = 0; frame < 600; frame++) {
            logic.update();
        }
        assertEquals(0, logic.getRtsPlayer(usa).getMoney(), "nothing delivered to the site");
        assertTrue(truck.findModule(HarvestUpdate.class).isWaiting());

        site.clearStatus(uz.dukeengine.core.thing.ObjectStatus.UNDER_CONSTRUCTION);
        for (int frame = 0; frame < 2000 && logic.getRtsPlayer(usa).getMoney() == 0; frame++) {
            logic.update();
        }
        assertEquals(100, logic.getRtsPlayer(usa).getMoney(), "finished, it is delivered to");
    }
}
