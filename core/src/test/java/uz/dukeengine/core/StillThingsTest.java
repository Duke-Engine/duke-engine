package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.ObstacleRules;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * What still things lay on the ground is laid again only when one of them changed what it lays — and comes out the
 * same, cell for cell and version for version, as a ground laid whole at every laying: a world that makes and loses
 * movers and sparks by the hundred never lays its ground for them.
 */
class StillThingsTest {

    private static final class Stage extends GameLogic {
        Stage(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final Kind LOW = Kind.of("LOW");

    private static final List<ThingTemplate> KINDS = List.of(
            ThingTemplate.named("Hut").geometry(new Geometry.Box(14f, 9f, 8f)).module(new ActiveBody.Data(50f)).build(),
            ThingTemplate.named("Tower").geometry(new Geometry.Cylinder(7f, 20f)).module(new ActiveBody.Data(50f))
                    .build(),
            ThingTemplate.named("Stone").geometry(new Geometry.Sphere(5f)).kindOf(LOW).build(),
            ThingTemplate.named("Walker").geometry(new Geometry.Cylinder(3f, 8f)).module(new ActiveBody.Data(20f))
                    .module(new MoveUpdate.Data(30f)).build(),
            ThingTemplate.named("Spark").module(new ActiveBody.Data(1f)).build());

    private static Stage stage(int cellsPerCell) {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        KINDS.forEach(things::addTemplate);
        var world = new Stage(things);
        world.init();
        world.setPathGrid(new PathGrid(40, 40), cellsPerCell);
        world.setObstacleRules(new ObstacleRules(Set.of(), Set.of(), 6f,
                List.of(new ObstacleRules.Laid("RUBBLE", null, "rubble"), new ObstacleRules.Laid(null, LOW, "gravel"))));
        return world;
    }

    /** One step, the same in both worlds: {@code pick} chooses among a world's things alike in each. */
    private static void step(Stage world, int op, double a, double b, double c, int pick) {
        var things = world.getObjects();
        GameObject some = things.isEmpty() ? null : things.get(pick % things.size());
        switch (op) {
            case 0, 1, 2 -> {
                var thing = world.createObject(KINDS.get(pick % KINDS.size()));
                thing.setPosition(new Coord3D((float) a * 400f, (float) b * 400f, (float) c * 10f));
                thing.setOrientation((float) (c * 6.2));
            }
            case 3 -> {
                if (some != null) {
                    world.destroyObject(some);
                }
            }
            case 4 -> {
                if (some != null && some.getBody() != null) {
                    some.getBody().damage(1000f);
                }
            }
            case 5 -> {
                if (some != null) {
                    some.setPosition(new Coord3D((float) a * 400f, (float) b * 400f, (float) c * 10f));
                }
            }
            case 6 -> {
                if (some != null) {
                    some.setOrientation((float) (a * 6.2));
                }
            }
            case 7 -> {
                if (some != null) {
                    some.setContained(!some.isContained());
                }
            }
            case 8 -> {
                if (some != null) {
                    if (some.hasCondition("RUBBLE")) {
                        some.clearCondition("RUBBLE");
                    } else {
                        some.setCondition("RUBBLE");
                    }
                }
            }
            case 9 -> {
                if (some != null) {
                    var legs = some.findModule(MoveUpdate.class);
                    if (legs != null) {
                        some.removeModule(legs);
                    } else {
                        some.addModule(new MoveUpdate(some, new MoveUpdate.Data(20f)));
                    }
                }
            }
            case 10 -> world.findPath(new Coord3D(5f, 5f, 0f), new Coord3D((float) a * 400f, (float) b * 400f, 0f));
            default -> world.update();
        }
    }

    private static void assertLaidAlike(Stage laidWhole, Stage laid, String when) {
        var whole = laidWhole.getPathGrid();
        var grid = laid.getPathGrid();
        assertEquals(whole.getObstacleVersion(), grid.getObstacleVersion(), when + ": the ground's version");
        assertEquals(whole.getShapeVersion(), grid.getShapeVersion(), when + ": what the zones are made of");
        for (int y = 0; y < whole.getHeight(); y++) {
            for (int x = 0; x < whole.getWidth(); x++) {
                assertEquals(whole.isBlocked(x, y), grid.isBlocked(x, y), when + ": cell " + x + "," + y);
                assertEquals(whole.groundClassAt(x, y), grid.groundClassAt(x, y), when + ": class " + x + "," + y);
            }
        }
        for (int i = 0; i < 64; i++) {
            float x = i * 6.1f;
            float y = (i * 37 % 64) * 6.1f;
            assertEquals(whole.clearOfCircles(x, y, 4f), grid.clearOfCircles(x, y, 4f), when + ": round things");
        }
    }

    private static void playAlike(int cellsPerCell, long seed) {
        var laidWhole = stage(cellsPerCell);
        var laid = stage(cellsPerCell);
        var random = new SplittableRandom(seed);
        for (int i = 0; i < 1500; i++) {
            int op = random.nextInt(14);
            double a = random.nextDouble();
            double b = random.nextDouble();
            double c = random.nextDouble();
            int pick = random.nextInt(1 << 20);
            laidWhole.forgetWhatWasLaid(); // this one lays its ground whole whenever it lays it, as it always did
            step(laidWhole, op, a, b, c, pick);
            step(laid, op, a, b, c, pick);
            assertLaidAlike(laidWhole, laid, "step " + i + " (" + op + ")");
        }
        assertEquals(laidWhole.checksum(), laid.checksum());
    }

    @Test
    void theGroundComesOutAsOneLaidWholeEveryTime() {
        playAlike(1, 20260930L);
    }

    @Test
    void andOnAGroundWalkedFiner() {
        playAlike(3, 17L);
    }

    @Test
    void aWorldOfMoversIsNeverLaidForThem() {
        var world = stage(1);
        var hut = world.createObject(KINDS.get(0));
        hut.setPosition(new Coord3D(105f, 105f, 0f));
        world.update();
        int version = world.getPathGrid().getObstacleVersion();
        for (int i = 0; i < 200; i++) {
            var walker = world.createObject(KINDS.get(3));
            walker.setPosition(new Coord3D(20f + i, 300f, 0f));
            world.createObject(KINDS.get(4)).setPosition(new Coord3D(200f, 20f + i, 0f));
            world.update();
        }
        assertEquals(version, world.getPathGrid().getObstacleVersion(), "movers and sparks lay nothing");
        hut.setPosition(new Coord3D(205f, 105f, 0f));
        world.update();
        assertEquals(version + 1, world.getPathGrid().getObstacleVersion(), "a still thing moved lays its ground again");
    }
}
