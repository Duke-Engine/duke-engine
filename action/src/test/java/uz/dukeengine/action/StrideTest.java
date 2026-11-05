package uz.dukeengine.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.combat.network.CombatOrderCodec;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingTemplate;

/** A held direction walked with no route: its speed a second, stopped at once, sliding along a wall, alike twice (E74). */
class StrideTest {

    /** Sixty units a second: two a frame. */
    private static final ThingTemplate HERO = ThingTemplate.named("Hero").geometry(new Geometry.Cylinder(4f, 9f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(60f)).build();

    private static ActionWorld world(boolean wall) {
        var world = new ActionWorld(Map.of()).template(HERO);
        var grid = new PathGrid(20, 20);
        if (wall) {
            for (int cx = 0; cx < 20; cx++) {
                grid.setBlocked(cx, 12, true); // a wall along x, from y 120
            }
        }
        world.setPathGrid(grid);
        return world;
    }

    private static GameObject hero(ActionWorld world) {
        return world.put("Hero", world.heroes, 100f, 50f);
    }

    @Test
    void sixtyFourWalksAlongYItsSpeedASecondAndNoneStopsAtOnce() {
        var world = world(false);
        var hero = hero(world);
        Stride.walk(hero, 64);
        world.run(30);
        assertEquals(100f, hero.getPosition().x(), 1e-3f);
        assertEquals(110f, hero.getPosition().y(), 1e-3f, "sixty units in a second, along +y");
        assertTrue(hero.getLocomotor().isMoving());

        Stride.walk(hero, ActionOrders.NONE);
        var stopped = hero.getPosition();
        world.run(1);
        assertEquals(stopped, hero.getPosition(), "none: stands where it is");
        assertFalse(hero.getLocomotor().isMoving());
    }

    @Test
    void busyAtAClipItStandsWhileTheWalkIsKeptAndGoesOnAfter() {
        var world = world(false);
        var hero = hero(world);
        Stride.walk(hero, 64);
        var legs = (MoveUpdate) hero.getLocomotor();
        assertFalse(legs.standsAtWork());
        hero.busy("Attack_L1", 10);
        assertTrue(legs.standsAtWork(), "drawn standing, not walking on the spot");
        world.run(10);
        assertEquals(50f, hero.getPosition().y(), 1e-3f, "held where it was for the ten frames");
        world.run(5);
        assertEquals(60f, hero.getPosition().y(), 1e-3f, "and walks on after");
        assertFalse(legs.standsAtWork());
    }

    @Test
    void intoAWallAtAnAngleItSlidesAlongIt() {
        var world = world(true);
        var hero = hero(world);
        Stride.walk(hero, 32); // up and right, at the wall along x
        world.run(90);
        assertTrue(hero.getPosition().y() < 120f, "kept off the wall: " + hero.getPosition());
        assertTrue(hero.getPosition().x() > 160f, "slid along it: " + hero.getPosition());
    }

    @Test
    void twoRunsWithTheSameOrdersEndAlike() {
        long[] sums = new long[2];
        for (int run = 0; run < 2; run++) {
            var world = world(true);
            var hero = hero(world);
            for (int frame = 0; frame < 120; frame++) {
                if (frame % 20 == 0) {
                    Stride.walk(hero, frame * 7);
                }
                world.run(1);
            }
            sums[run] = world.checksum();
        }
        assertEquals(sums[0], sums[1]);
    }

    @Test
    void theOrderTravelsAndWalksHisOwnSidesUnitsOnly() {
        var world = world(false);
        var hero = hero(world);
        var theirs = world.put("Hero", world.monsters, 50f, 50f);
        var order = ActionOrders.along(world.heroes, List.of(hero.getId(), theirs.getId()), 64 + 256);
        assertEquals(order, CombatOrderCodec.decode(CombatOrderCodec.encode(order)), "on the wire as it was");
        assertEquals(64, order.number(), "any value, a turn round");

        assertTrue(ActionOrders.apply(order, world));
        world.run(30);
        assertEquals(110f, hero.getPosition().y(), 1e-3f);
        assertEquals(50f, theirs.getPosition().y(), 1e-3f, "not his to order");
        assertFalse(ActionOrders.apply(new GameOrder(world.heroes, "PICKUP", List.of(), null, null, 0), world),
                "a word of the game's own is left to it");
    }
}
