package uz.duke.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

/**
 * Immobile objects are terrain as far as navigation is concerned: paths route
 * around a building rather than into it.
 */
class StaticObstacleTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    /** No locomotor: it can never move, so it counts as terrain. */
    private static final ThingTemplate BUNKER = ThingTemplate.named("Bunker")
            .geometry(new Geometry.Box(20f, 20f, 10f))
            .module("ActiveBody", new ActiveBody.Data(100f))
            .build();

    /** Same shape, but it can drive away — so it must not be baked into the map. */
    private static final ThingTemplate TANK = ThingTemplate.named("Tank")
            .geometry(new Geometry.Box(20f, 20f, 10f))
            .module("ActiveBody", new ActiveBody.Data(100f))
            .module("MoveUpdate", new MoveUpdate.Data(20f))
            .build();

    /** Small and quick, for walking the length of the map in a test. */
    private static final ThingTemplate WALKER = ThingTemplate.named("Walker")
            .geometry(new Geometry.Cylinder(3f, 9f))
            .module("ActiveBody", new ActiveBody.Data(100f))
            .module("MoveUpdate", new MoveUpdate.Data(60f))
            .build();

    private static final Coord3D START = new Coord3D(25f, 105f, 0f);
    private static final Coord3D GOAL = new Coord3D(185f, 105f, 0f);

    private static TestLogic logicOn20x20Grid() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        thingFactory.addTemplate(BUNKER);
        thingFactory.addTemplate(TANK);
        thingFactory.addTemplate(WALKER);
        var logic = new TestLogic(thingFactory);
        logic.init();
        logic.setPathGrid(new PathGrid(20, 20)); // 200x200 world units
        return logic;
    }

    private static GameObject place(TestLogic logic, ThingTemplate template, float x, float y) {
        var object = logic.createObject(template);
        object.setPosition(new Coord3D(x, y, 0f));
        return object;
    }

    /** How far the route strays from the straight line between start and goal. */
    private static float maxDetour(Path path) {
        float worst = 0f;
        for (var waypoint : path.getWaypoints()) {
            worst = Math.max(worst, Math.abs(waypoint.y() - START.y()));
        }
        return worst;
    }

    @Test
    void withNothingInTheWayTheRouteIsStraight() {
        var logic = logicOn20x20Grid();

        assertTrue(maxDetour(logic.findPath(START, GOAL)) < 15f,
                "an empty corridor should not produce a detour");
    }

    @Test
    void aPathRoutesAroundABuildingRatherThanThroughIt() {
        var logic = logicOn20x20Grid();
        place(logic, BUNKER, 105f, 105f); // squarely between start and goal

        var path = logic.findPath(START, GOAL);
        var grid = logic.getPathGrid();

        assertFalse(path.isEmpty(), "there is a way round, so a route must exist");
        for (var waypoint : path.getWaypoints()) {
            assertFalse(grid.isBlocked(grid.toCellX(waypoint), grid.toCellY(waypoint)),
                    "no waypoint may sit on occupied ground: " + waypoint);
        }
        assertTrue(maxDetour(path) > 25f,
                "the route must go around the building, not through it");
    }

    @Test
    void whatCanDriveAwayIsNotBakedIntoTheMap() {
        var logic = logicOn20x20Grid();
        place(logic, TANK, 105f, 105f); // same shape and spot as the bunker

        assertTrue(maxDetour(logic.findPath(START, GOAL)) < 15f,
                "a vehicle is not terrain — pathfinding must not rewrite the map around it");
    }

    @Test
    void demolishingABuildingReopensItsGround() {
        var logic = logicOn20x20Grid();
        var bunker = place(logic, BUNKER, 105f, 105f);
        assertTrue(maxDetour(logic.findPath(START, GOAL)) > 25f, "blocked while it stands");

        bunker.getBody().damage(1000f);
        logic.update(); // reaps the wreck

        assertTrue(maxDetour(logic.findPath(START, GOAL)) < 15f,
                "once it is gone the ground is walkable again");
    }

    @Test
    void aUnitAlreadyOnItsWayRerouteWhenSomethingIsBuiltAcrossIt() {
        var logic = logicOn20x20Grid();
        var walker = place(logic, WALKER, START.x(), START.y());
        walker.findModule(MoveUpdate.class).moveTo(GOAL);

        for (int frame = 0; frame < 20; frame++) {
            logic.update(); // gets under way down a clear corridor
        }
        var bunker = place(logic, BUNKER, 105f, 105f); // built across the route, mid-journey

        boolean everInsideTheBunker = false;
        for (int frame = 0; frame < 600; frame++) {
            logic.update();
            if (uz.duke.core.thing.Footprint.of(walker)
                    .overlaps(uz.duke.core.thing.Footprint.of(bunker))) {
                everInsideTheBunker = true;
            }
        }

        assertFalse(everInsideTheBunker, "it must never end up inside the new building");
        assertTrue(walker.getPosition().distance(GOAL) < 5f,
                "it must rethink its route and still arrive, but stopped at "
                        + walker.getPosition());
    }

    @Test
    void aUnitGivesUpWhenTheWayIsSealedBehindIt() {
        var logic = logicOn20x20Grid();
        var grid = logic.getPathGrid();
        for (int cx = 0; cx < 20; cx++) { // a canyon: the only way through is row 9-11
            grid.setBlocked(cx, 8, true);
            grid.setBlocked(cx, 12, true);
        }

        var walker = place(logic, WALKER, START.x(), START.y());
        walker.findModule(MoveUpdate.class).moveTo(GOAL);
        for (int frame = 0; frame < 20; frame++) {
            logic.update();
        }
        place(logic, BUNKER, 105f, 105f); // plugs the canyon completely

        for (int frame = 0; frame < 200; frame++) {
            logic.update();
        }

        assertFalse(walker.findModule(MoveUpdate.class).isMoving(),
                "with no route left it must stop rather than walk at the wall forever");
        assertTrue(walker.getPosition().x() < 100f, "and it never got past the plug");
    }

    @Test
    void ordinaryTrafficDoesNotCountAsTheWorldChanging() {
        var logic = logicOn20x20Grid();
        place(logic, BUNKER, 105f, 105f);
        logic.update();
        int settled = logic.getNavigationVersion();

        var walker = place(logic, WALKER, 25f, 25f); // units come and go constantly
        logic.update();
        walker.getBody().damage(1000f);
        logic.update();

        assertTrue(logic.getNavigationVersion() == settled,
                "only the navigable shape counts; otherwise every unit built would "
                        + "make the whole army rethink its routes");
    }

    @Test
    void demolitionDoesNotPunchAHoleInTheTerrain() {
        var logic = logicOn20x20Grid();
        var grid = logic.getPathGrid();
        grid.setBlocked(10, 10, true); // a cliff the bunker was built against

        var bunker = place(logic, BUNKER, 105f, 105f);
        bunker.getBody().damage(1000f);
        logic.update();

        assertTrue(grid.isBlocked(10, 10), "the map's own obstacle must survive the demolition");
        assertTrue(grid.isTerrainBlocked(10, 10));
        assertFalse(grid.isTerrainBlocked(7, 7), "and the building's cells were never terrain");
    }
}
