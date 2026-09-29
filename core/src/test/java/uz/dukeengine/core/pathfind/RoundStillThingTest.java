package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * On a map walked finer than it is drawn, a round still thing is kept off by its true distance, as scenery is, and
 * closes no cell: a mage goes between a fountain and a pillar whose outlines leave him room, and comes up to either as
 * near as his own outline. On the map's own cells they close cells as they always did.
 */
class RoundStillThingTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    /** The duke-dungeon's measures: a mage of 4, a fountain of 7 and a pillar of 3, round and with no body. */
    private static final ThingTemplate MAGE = ThingTemplate.named("Mage")
            .geometry(new Geometry.Cylinder(4f, 10f))
            .module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(60f))
            .build();
    private static final ThingTemplate FOUNTAIN = ThingTemplate.named("Fountain")
            .geometry(new Geometry.Cylinder(7f, 5f))
            .build();
    private static final ThingTemplate PILLAR = ThingTemplate.named("Pillar")
            .geometry(new Geometry.Cylinder(3f, 12f))
            .build();
    private static final Coord3D FOUNTAIN_AT = new Coord3D(55f, 50f, 0f);
    private static final Coord3D PILLAR_AT = new Coord3D(55f, 71f, 0f);
    private static final Coord3D WEST = new Coord3D(20f, 62.5f, 0f);
    /** Past the wall where no straight line from {@link #WEST} goes between them: only a route's cells do. */
    private static final Coord3D BEYOND = new Coord3D(100f, 30f, 0f);

    /**
     * A wall down the map's sixth column of cells with two openings: one between y 40 and 80 with the fountain and the
     * pillar in it, their outlines 11 apart and too near the wall for a mage to pass beside either, and one far along
     * it, between 90 and 110.
     */
    private static TestLogic walled(int cellsPerCell) {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(MAGE);
        things.addTemplate(FOUNTAIN);
        things.addTemplate(PILLAR);
        var logic = new TestLogic(things);
        logic.init();
        var grid = new PathGrid(20, 12);
        for (int cy = 0; cy < 12; cy++) {
            if ((cy < 4 || cy > 7) && cy != 9 && cy != 10) {
                grid.setBlocked(5, cy, true);
            }
        }
        logic.setPathGrid(grid, cellsPerCell);
        logic.createObject(FOUNTAIN).setPosition(FOUNTAIN_AT);
        logic.createObject(PILLAR).setPosition(PILLAR_AT);
        return logic;
    }

    private static GameObject mage(TestLogic logic, Coord3D at) {
        var mage = logic.createObject(MAGE);
        mage.setPosition(at);
        return mage;
    }

    private static float highest(Path path) {
        float highest = 0f;
        for (var waypoint : path.getWaypoints()) {
            highest = Math.max(highest, waypoint.y());
        }
        return highest;
    }

    /** How far apart the mage's outline and a round thing's are: under zero where they overlap. */
    private static float gap(GameObject mage, Coord3D middle, float radius) {
        float dx = mage.getPosition().x() - middle.x();
        float dy = mage.getPosition().y() - middle.y();
        return (float) Math.sqrt(dx * dx + dy * dy) - 4f - radius;
    }

    @Test
    void walkedFinerAMageGoesBetweenAFountainAndAPillarElevenApart() {
        var logic = walled(2);
        var mage = mage(logic, WEST);

        assertTrue(highest(logic.findPath(mage, BEYOND)) < 85f, "between them, not by the far opening");
    }

    @Test
    void onTheMapsOwnCellsTheyCloseCellsAsTheyAlwaysDid() {
        var logic = walled(1);
        var mage = mage(logic, WEST);

        assertTrue(highest(logic.findPath(mage, BEYOND)) > 85f, "round by the far opening");
    }

    @Test
    void theMageWalksBetweenThemTouchingNeither() {
        var logic = walled(2);
        var mage = mage(logic, WEST);
        mage.findModule(MoveUpdate.class).moveTo(BEYOND);

        float nearest = Float.MAX_VALUE;
        float highest = 0f;
        for (int frame = 0; frame < 400; frame++) {
            logic.update();
            nearest = Math.min(nearest, Math.min(gap(mage, FOUNTAIN_AT, 7f), gap(mage, PILLAR_AT, 3f)));
            highest = Math.max(highest, mage.getPosition().y());
        }
        assertTrue(highest < 85f, "between them: " + highest);
        assertTrue(nearest >= -1e-3f, "into neither: " + nearest);
        assertTrue(mage.getPosition().distance(BEYOND) < 5f, "and through: " + mage.getPosition());
    }

    @Test
    void sentIntoTheFountainHeStopsAboutHisOwnRadiusFromIt() {
        var logic = walled(2);
        var mage = mage(logic, new Coord3D(20f, 50f, 0f));
        mage.findModule(MoveUpdate.class).moveTo(FOUNTAIN_AT);

        for (int frame = 0; frame < 300; frame++) {
            logic.update();
        }
        float gap = gap(mage, FOUNTAIN_AT, 7f);
        assertTrue(gap >= -1e-3f && gap <= 4f, "up to it, his own radius at most away: " + gap);
    }
}
