package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.combat.module.PursueUpdate;
import uz.dukeengine.combat.module.WeaponUpdate;

/** What closing on a target costs: a route planned when one is needed, and a crowd of chasers a frame can afford. */
class PursuitCostTest {

    private static CombatTest.CombatLogic world(int sides, int repathFrames, PathGrid grid) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(ThingTemplate.named("Chaser")
                .module(new ActiveBody.Data(1_000_000f))
                .module(new MoveUpdate.Data(30f))
                .module(new WeaponUpdate.Data(1f, 30f, 15, DamageType.NORMAL))
                .module(new PursueUpdate.Data(0f, repathFrames))
                .build());
        factory.addTemplate(ThingTemplate.named("Target").module(new ActiveBody.Data(1000f)).build());
        var world = new CombatTest.CombatLogic(factory);
        world.init();
        world.setPathGrid(grid);
        for (int n = 0; n < sides; n++) {
            world.getPlayerList().addPlayer("Side " + n);
        }
        for (int a = 0; a < sides; a++) {
            for (int b = 0; b < sides; b++) {
                if (a != b) {
                    world.getPlayerList().getPlayer(a).setRelationshipTo(world.getPlayerList().getPlayer(b),
                            Relationship.ENEMIES);
                }
            }
        }
        return world;
    }

    private static GameObject spawn(CombatTest.CombatLogic world, String template, int side, float x, float y) {
        var thing = world.createObject(world.getThingFactory().findTemplate(template));
        thing.setPlayerIndex(side);
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    @Test
    void aChaserOfATargetStandingStillPlansOnceAndAgainOnlyWhenItMoves() {
        var world = world(2, 0, new PathGrid(60, 60));
        var chaser = spawn(world, "Chaser", 0, 20f, 300f);
        var target = spawn(world, "Target", 1, 500f, 300f);
        chaser.findModule(WeaponUpdate.class).attack(target.getId());
        var pursuit = chaser.findModule(PursueUpdate.class);

        for (int frame = 0; frame < 150; frame++) {
            world.update();
        }
        assertTrue(chaser.getPosition().x() > 50f, "on its way: " + chaser.getPosition());
        assertEquals(1, pursuit.plans(), "one route, walked, for something that stands still");

        target.setPosition(new Coord3D(500f, 400f, 0f));
        world.update();
        assertEquals(2, pursuit.plans(), "and one more once it has moved further than a cell");
    }

    /**
     * Eighty units in four groups across a field of walls, each group chasing the next. The numbers are printed for
     * whoever wants them; the bound is loose, so the test fails on a search gone wrong, not on a busy machine.
     */
    @Test
    void eightyChasersInFourGroupsKeepTheFrameCheap() {
        var grid = new PathGrid(80, 80);
        for (int n = 0; n < 12; n++) {
            grid.setBlocked(40, 25 + n, true);
            grid.setBlocked(25 + n, 40, true);
            grid.setBlocked(55 - n, 45, true);
            grid.setBlocked(45, 55 - n, true);
        }
        var world = world(4, 30, grid);
        float[][] corners = {{100f, 100f}, {700f, 100f}, {700f, 700f}, {100f, 700f}};
        var groups = new ArrayList<List<GameObject>>();
        for (int side = 0; side < 4; side++) {
            var group = new ArrayList<GameObject>();
            for (int n = 0; n < 20; n++) {
                group.add(spawn(world, "Chaser", side, corners[side][0] + n % 5 * 20f,
                        corners[side][1] + n / 5 * 20f));
            }
            groups.add(group);
        }
        for (int side = 0; side < 4; side++) {
            var prey = groups.get((side + 1) % 4);
            for (int n = 0; n < 20; n++) {
                groups.get(side).get(n).findModule(WeaponUpdate.class).attack(prey.get(n).getId());
            }
        }

        for (int frame = 0; frame < 60; frame++) {
            world.update(); // warm the code up before timing it
        }
        int frames = 300;
        int mostCells = 0;
        long cells = 0;
        long started = System.nanoTime();
        for (int frame = 0; frame < frames; frame++) {
            world.update();
            mostCells = Math.max(mostCells, world.getCellsExaminedLastFrame());
            cells += world.getCellsExaminedLastFrame();
        }
        double meanMillis = (System.nanoTime() - started) / 1e6 / frames;
        System.out.printf("80 chasers: %.3f ms a frame, %d cells a frame on average, %d at most%n", meanMillis,
                cells / frames, mostCells);

        assertTrue(meanMillis < 10.0, "a frame of eighty chasers in well under a frame's time: " + meanMillis);
        assertTrue(mostCells < world.getPathfindBudget() + 2 * 80 * 80,
                "no frame searching past its budget by more than the one search it may finish: " + mostCells);
    }
}
