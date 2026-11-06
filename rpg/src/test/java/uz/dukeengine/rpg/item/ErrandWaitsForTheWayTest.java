package uz.dukeengine.rpg.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rpg.RpgWorld;
import uz.dukeengine.rpg.Seal;

/**
 * A hero sent for a thing the way to which only a body shuts waits for it to open, looking again, rather than giving
 * the errand up at once; one the stone shuts, or a body that never leaves, still ends it.
 */
class ErrandWaitsForTheWayTest {

    /** A thing at work where it stands: not asked aside, and not moved off its ground. */
    private static final class AtWork extends Module {
        record Data() implements ModuleData {
        }

        AtWork(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean keepsBusy() {
            return true;
        }
    }

    private static final Loot RING = new Loot("ring", "Ring", "", "ATTACK", 1, 1, 1);
    private static final ItemErrand.Rules RULES = new ItemErrand.Rules(5f, 30, "Chest", 2, "full", "no use",
            "no way", 90, 300);

    private static RpgWorld field(int corridorFrom, int corridorTo) {
        var world = new RpgWorld(Map.of(), Seal.NONE);
        world.getThingFactory().getModuleFactory().register(AtWork.Data.class, (owner, _) -> new AtWork(owner));
        world.getThingFactory().addTemplate(ThingTemplate.named("Hero").geometry(new Geometry.Cylinder(7f, 12f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(60f)).build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Worker").geometry(new Geometry.Cylinder(7f, 12f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(20f)).module(new AtWork.Data()).build());
        world.getThingFactory().addTemplate(ThingTemplate.named("Chest").module(new GroundItem.Data()).build());
        world.getThingFactory().getModuleFactory().register(GroundItem.Data.class, (owner, _) -> new GroundItem(owner));
        world.setPathGrid(new PathGrid(80, 80));
        var grid = world.getPathGrid();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setBlocked(x, y, y < corridorFrom || y > corridorTo || x < 5 || x > 75);
            }
        }
        return world;
    }

    private static GameObject chestBeyond(RpgWorld world) {
        return GroundItem.lay(world, "Chest", RING, new Coord3D(505f, 310f, 0f), world.monsters);
    }

    private static void sent(RpgWorld world, GameObject hero, GameObject chest, LootBag bag) {
        assertTrue(ItemErrand.pickUp(hero, chest, bag, RULES));
    }

    @Test
    void aBodyInTheCorridorIsWaitedOutAndTheThingTakenOnceItLeaves() {
        var world = field(30, 31);
        var worker = world.spawn(world.findTemplate("Worker"), new Coord3D(300f, 310f, 0f), world.monsters);
        var chest = chestBeyond(world);
        world.run(16);
        var hero = world.spawn(world.findTemplate("Hero"), new Coord3D(105f, 310f, 0f), world.heroes);
        var bag = new LootBag();
        sent(world, hero, chest, bag);

        var errand = hero.findModule(ItemErrand.class);
        var legs = hero.findModule(MoveUpdate.class);
        int walked = 0;
        while (!legs.stoppedShort() && walked < 400) {
            world.run(1);
            walked++;
        }
        assertTrue(legs.stoppedShort(), "the worker shuts the way and the walk says so");
        assertTrue(hero.getPosition().x() < worker.getPosition().x(), "behind the worker");

        world.run(RULES.stuckFrames() / 2);
        assertFalse(errand.isOver(), "still waiting for the way to open, not given up");

        worker.markDestroyed();
        world.run(400);

        assertTrue(errand.isOver());
        assertEquals(List.of(RING), bag.getFound(), "taken once the way opened");
        assertTrue(chest.isDestroyed(), "and gone from the floor");
    }

    @Test
    void aBodyThatNeverLeavesEndsTheErrandAfterTheHeroHasWaitedAsLongAsTheRulesAllow() {
        var world = field(30, 31);
        world.spawn(world.findTemplate("Worker"), new Coord3D(300f, 310f, 0f), world.monsters);
        var chest = chestBeyond(world);
        world.run(16);
        var hero = world.spawn(world.findTemplate("Hero"), new Coord3D(105f, 310f, 0f), world.heroes);
        var bag = new LootBag();
        sent(world, hero, chest, bag);

        var errand = hero.findModule(ItemErrand.class);
        int frames = 0;
        while (!errand.isOver() && frames < RULES.stuckFightingFrames() + 300) {
            world.run(1);
            frames++;
        }

        assertTrue(errand.isOver(), "given up in the end");
        assertTrue(frames >= RULES.stuckFrames(), "after waiting as long as the rules allow: " + frames);
        assertTrue(bag.getFound().isEmpty());
        assertEquals("no way", bag.noteAt(world.getFrame()));
    }

    @Test
    void aWayShutByStoneEndsTheErrandAtOnce() {
        var world = field(30, 31);
        var grid = world.getPathGrid();
        for (int y = 0; y < grid.getHeight(); y++) {
            grid.setBlocked(30, y, true);
        }
        var chest = chestBeyond(world);
        var hero = world.spawn(world.findTemplate("Hero"), new Coord3D(105f, 310f, 0f), world.heroes);
        var bag = new LootBag();
        world.run(2);
        sent(world, hero, chest, bag);

        var errand = hero.findModule(ItemErrand.class);
        int frames = 0;
        while (!errand.isOver() && frames < 400) {
            world.run(1);
            frames++;
        }

        assertTrue(errand.isOver(), "ended once the walk got as near as it could: " + frames);
        assertTrue(bag.getFound().isEmpty());
        assertEquals("no way", bag.noteAt(world.getFrame()));
    }
}
