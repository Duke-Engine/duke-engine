package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/**
 * A factory's rally point as it is shown while the factory is selected: a line from its door through its natural rally
 * point to the rally point, round the corners of its footprint where the rally point lies behind the door.
 */
class RallyLineTest {

    private static ProductionUpdate factory(boolean withExit) {
        var things = new ThingFactory(RtsModules.withDefaults());
        var template = RtsTemplate.named("WarFactory")
                .module(new ActiveBody.Data(1000f))
                .module(new ProductionUpdate.Data(List.of(), List.of(), withExit
                        ? new ProductionUpdate.Exit(new Coord3D(-10f, -30f, 0f), new Coord3D(53f, -30f, 0f), 0, 0)
                        : null, null))
                .geometry(new Geometry.Box(40f, 40f, 20f))
                .build();
        things.addTemplate(template);
        var logic = new ProductionTest.TestLogic(things);
        logic.init();
        var factory = logic.createObject(template);
        factory.setPosition(new Coord3D(200f, 200f, 0f)); // facing east: its door at (190, 170), out to (253, 170)
        return factory.findModule(ProductionUpdate.class);
    }

    private static final Coord3D DOOR = new Coord3D(190f, 170f, 0f);
    private static final Coord3D NATURAL = new Coord3D(253f, 170f, 0f);

    @Test
    void noRallyPointNoLine() {
        assertNull(factory(true).rallyLine());
    }

    @Test
    void aRallyPointOutBeyondTheDoorIsReachedStraightFromTheDoorThroughTheNaturalRallyPoint() {
        var production = factory(true);
        var rally = new Coord3D(450f, 200f, 0f);
        production.setRallyPoint(rally);

        var line = production.rallyLine();

        assertEquals(rally, line.rallyPoint());
        assertEquals(List.of(DOOR, NATURAL, rally), line.points());
        assertEquals(List.of(NATURAL), line.nodes(), "a node at the natural rally point");
    }

    @Test
    void aRallyPointBehindTheFactoryIsReachedRoundTheCornerOfItsFootprintOnTheDoorsSide() {
        var production = factory(true);
        var rally = new Coord3D(100f, 170f, 0f);
        production.setRallyPoint(rally);

        var line = production.rallyLine();

        var corner = new Coord3D(240f, 160f, 0f);
        assertEquals(List.of(DOOR, NATURAL, corner, rally), line.points());
        assertEquals(List.of(corner, NATURAL), line.nodes(), "and one at the corner");
    }

    @Test
    void oneOnTheFarSideOfTheFirstCornerGoesRoundTheFarSidesToo() {
        var production = factory(true);
        // Short of the natural rally point in front of the door: the reference's own test (dot < 0) takes it past
        // the first corner, and round the far side's nearest one as well.
        var rally = new Coord3D(245f, 205f, 0f);
        production.setRallyPoint(rally);

        var line = production.rallyLine();

        var first = new Coord3D(240f, 240f, 0f);
        var second = new Coord3D(160f, 240f, 0f);
        assertEquals(List.of(DOOR, NATURAL, first, second, rally), line.points());
        assertEquals(List.of(first, second, NATURAL), line.nodes());
    }

    @Test
    void aFactoryWithNoExitRunsItsLineFromWhereItStandsAndAMovedRallyPointMovesIt() {
        var production = factory(false);
        production.setRallyPoint(new Coord3D(400f, 200f, 0f));
        production.setRallyPoint(new Coord3D(400f, 260f, 0f));

        var line = production.rallyLine();

        var at = new Coord3D(200f, 200f, 0f);
        assertEquals(List.of(at, new Coord3D(400f, 260f, 0f)), line.points(), "the rally point where it is now");
        assertEquals(List.of(at), line.nodes());
    }
}
