package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rts.RtsTemplate;

/** A factory's units made inside it and let out through its door, one at a time, as the reference's are. */
class ProductionExitTest {

    private static final ProductionUpdate.Exit WAR_FACTORY = new ProductionUpdate.Exit(
            new Coord3D(-10f, -30f, 0f), new Coord3D(53f, -30f, 0f), 0, 0);
    private static final ProductionUpdate.Door DOOR = new ProductionUpdate.Door(5, 4, 3,
            "DOOR_1_OPENING", "DOOR_1_WAITING_OPEN", "DOOR_1_CLOSING");

    private ProductionTest.TestLogic logic;
    private ThingTemplate tank;
    private GameObject factory;

    /** A factory 80 across at (200, 200), with this exit and door, and a tank it makes in {@code frames}. */
    private void world(ProductionUpdate.Exit exit, ProductionUpdate.Door door, int frames) {
        var things = new ThingFactory(RtsModules.withDefaults());
        tank = RtsTemplate.named("Tank")
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f)) // a unit a frame
                .geometry(new Geometry.Cylinder(2f, 4f))
                .buildTimeFrames(frames)
                .build();
        things.addTemplate(tank);
        var warFactory = RtsTemplate.named("WarFactory")
                .module(new ActiveBody.Data(1000f))
                .module(new ProductionUpdate.Data(List.of(), List.of(), exit, door))
                .geometry(new Geometry.Box(40f, 40f, 20f))
                .build();
        things.addTemplate(warFactory);
        logic = new ProductionTest.TestLogic(things);
        logic.init();
        int side = logic.getPlayerList().addPlayer("USA").getIndex();
        factory = logic.createObject(warFactory);
        factory.setPlayerIndex(side);
        factory.setPosition(new Coord3D(200f, 200f, 0f));
    }

    private ProductionUpdate production() {
        return factory.findModule(ProductionUpdate.class);
    }

    private List<GameObject> tanks() {
        return logic.getObjects().stream().filter(o -> o.getTemplate() == tank).toList();
    }

    /** Frames until the {@code n}-th tank is out, the first frame 1. */
    private int frameTheTankIsOut(int n, int most) {
        for (int frame = 1; frame <= most; frame++) {
            logic.update();
            if (tanks().size() >= n) {
                return frame;
            }
        }
        return -1;
    }

    @Test
    void aFactoryTurnedAQuarterMakesItsUnitAtItsCreatePointTurnedWithItFacingItsWay() {
        world(WAR_FACTORY, null, 1);
        factory.setOrientation((float) (Math.PI / 2));
        production().queue(tank);
        logic.update();

        var made = tanks().getFirst();
        assertEquals(230f, made.getPosition().x(), 1e-3f, "(-10, -30) turned a quarter about the centre is (30, -10)");
        assertEquals(190f, made.getPosition().y(), 1e-3f);
        assertEquals((float) (Math.PI / 2), made.getOrientation(), 1e-6f, "facing the way its factory faces");
    }

    @Test
    void itWalksOutThroughTheFactorysOwnWallsToTheDoorAndOnToTheRallyPoint() {
        world(WAR_FACTORY, null, 1);
        logic.setPathGrid(new PathGrid(60, 60)); // the factory is an obstacle a route goes round
        production().setRallyPoint(new Coord3D(300f, 300f, 0f));
        production().queue(tank);
        logic.update();
        var made = tanks().getFirst();
        assertEquals(190f, made.getPosition().x(), 1e-3f, "made inside the walls");

        float x = made.getPosition().x();
        while (made.getPosition().x() < 252.9f) {
            logic.update();
            assertEquals(170f, made.getPosition().y(), 1e-3f, "straight out through the east wall");
            assertTrue(made.getPosition().x() > x, "never back the way it came");
            x = made.getPosition().x();
        }
        for (int frame = 0; frame < 400 && made.getPosition().distance(new Coord3D(300f, 300f, 0f)) > 0.5f; frame++) {
            logic.update();
        }
        assertEquals(300f, made.getPosition().x(), 0.5f, "and on to the rally point by a route");
        assertEquals(300f, made.getPosition().y(), 0.5f);
    }

    @Test
    void twoFinishedTogetherLeaveExitDelayFramesApartButForTheFirstBurst() {
        world(new ProductionUpdate.Exit(WAR_FACTORY.createPoint(), WAR_FACTORY.rallyPoint(), 9, 0), null, 1);
        production().queue(tank);
        production().queue(tank);
        int first = frameTheTankIsOut(1, 50);
        int second = frameTheTankIsOut(2, 50) + first;
        assertEquals(9, second - first, "the second waits out the delay, complete, at the head of the queue");

        world(new ProductionUpdate.Exit(WAR_FACTORY.createPoint(), WAR_FACTORY.rallyPoint(), 9, 2), null, 1);
        production().queue(tank);
        production().queue(tank);
        production().queue(tank);
        first = frameTheTankIsOut(1, 50);
        second = frameTheTankIsOut(2, 50) + first;
        int third = frameTheTankIsOut(3, 50) + second;
        assertEquals(1, second - first, "the first two are a burst: the second out the frame it is done");
        assertEquals(9, third - second, "and the third waits");
    }

    @Test
    void withADoorTheUnitIsMadeOnlyOnceItIsOpenAndTheFactoryHoldsItsWordsInTurn() {
        world(WAR_FACTORY, DOOR, 1);
        production().queue(tank);
        var held = new ArrayList<String>();
        for (int frame = 1; frame <= 14; frame++) {
            logic.update();
            held.add(word() + (tanks().isEmpty() ? "" : "+tank"));
        }
        assertEquals(List.of(
                "DOOR_1_OPENING", "DOOR_1_OPENING", "DOOR_1_OPENING", "DOOR_1_OPENING", "DOOR_1_OPENING",
                "DOOR_1_WAITING_OPEN+tank", "DOOR_1_WAITING_OPEN+tank", "DOOR_1_WAITING_OPEN+tank",
                "DOOR_1_WAITING_OPEN+tank",
                "DOOR_1_CLOSING+tank", "DOOR_1_CLOSING+tank", "DOOR_1_CLOSING+tank",
                "none+tank", "none+tank"), held, "opening 5, the tank made the frame it is open, open 4, closing 3");
    }

    @Test
    void oneFinishedWhileTheDoorWaitsOpenLeavesAtOnceAndOneWhileItClosesOpensItAgain() {
        world(WAR_FACTORY, DOOR, 2);
        production().queue(tank);
        production().queue(tank);
        int first = frameTheTankIsOut(1, 30);
        assertEquals(7, first, "done at 2, the door open at 7");
        int second = frameTheTankIsOut(2, 30) + first;
        assertEquals(9, second, "done at 9 while the door stands open: out at once");
        for (int frame = second; frame < second + 4; frame++) {
            assertFalse(factory.hasCondition("DOOR_1_CLOSING"), "the door never closed between the two");
            logic.update();
        }
        assertTrue(factory.hasCondition("DOOR_1_CLOSING"), "open its whole time after the last one left");

        production().queue(tank); // done two frames into the closing
        logic.update();
        logic.update();
        assertTrue(factory.hasCondition("DOOR_1_WAITING_OPEN"), "open again at once");
        assertFalse(factory.hasCondition("DOOR_1_CLOSING"));
        assertEquals(3, tanks().size(), "and the third straight out");
    }

    @Test
    void aFactoryThatSaysNothingLetsItsUnitOutOfItsSideAtOnce() {
        world(null, null, 1);
        production().queue(tank);
        logic.update();
        var made = tanks().getFirst();
        assertTrue(made.getPosition().y() < 160f, "beside the factory, clear of its walls, as ever");
        assertEquals(0f, made.getOrientation(), 1e-6f);
    }

    @Test
    void aFactorysExitAndDoorAreWrittenInItsBlock() {
        var loaded = RtsTemplate.register(new ThingTemplateLoader(
                new ThingFactory(RtsModules.withDefaults()))).load("""
                Object
                  Name = WarFactory
                  Modules = [
                    ProductionUpdate
                      Exit = Exit
                        CreatePoint = [-10, -30, 0]
                        RallyPoint = [53, -30, 0]
                        Delay = 9
                      End
                      Door = Door
                        OpeningFrames = 98
                        OpenFrames = 90
                        ClosingFrames = 120
                        Opening = DOOR_1_OPENING
                        Open = DOOR_1_WAITING_OPEN
                        Closing = DOOR_1_CLOSING
                      End
                    End
                  ]
                End
                """, "war_factory.duke");
        var data = loaded.getFirst().modules().stream()
                .filter(ProductionUpdate.Data.class::isInstance).map(ProductionUpdate.Data.class::cast)
                .findFirst().orElseThrow();
        assertEquals(new ProductionUpdate.Exit(new Coord3D(-10f, -30f, 0f), new Coord3D(53f, -30f, 0f), 9, 0),
                data.exit());
        assertEquals(new ProductionUpdate.Door(98, 90, 120, "DOOR_1_OPENING", "DOOR_1_WAITING_OPEN",
                "DOOR_1_CLOSING"), data.door());
    }

    private String word() {
        for (var word : List.of("DOOR_1_OPENING", "DOOR_1_WAITING_OPEN", "DOOR_1_CLOSING")) {
            if (factory.hasCondition(word)) {
                return word;
            }
        }
        return "none";
    }
}
