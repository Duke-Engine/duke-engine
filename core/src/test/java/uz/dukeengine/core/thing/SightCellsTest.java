package uz.dukeengine.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.SightCells.Sight;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;

/**
 * What a player has seen of the map, by cells, lingering — the reference's partition cells of 40 and its
 * {@code UnlookPersistDuration} of 150 frames: a scout's trail stays open five seconds behind him, and is fogged after.
 */
class SightCellsTest {

    private GameLogic world;
    private int viewer;
    private int ally;
    private int enemy;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ObjectTemplate.named("Scout").visionRange(100f).module(new ActiveBody.Data(100f)).build());
        world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        world.setPathGrid(new PathGrid(100, 100));
        world.setSightCells(40f, 150);
        var players = world.getPlayerList();
        viewer = players.addPlayer("Viewer").getIndex();
        ally = players.addPlayer("Ally").getIndex();
        enemy = players.addPlayer("Enemy").getIndex();
        players.getPlayer(viewer).setRelationshipTo(players.getPlayer(ally), Relationship.ALLIES);
        players.getPlayer(ally).setRelationshipTo(players.getPlayer(viewer), Relationship.ALLIES);
        for (int other : new int[] {viewer, ally}) {
            players.getPlayer(other).setRelationshipTo(players.getPlayer(enemy), Relationship.ENEMIES);
            players.getPlayer(enemy).setRelationshipTo(players.getPlayer(other), Relationship.ENEMIES);
        }
    }

    private void run(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            world.update();
        }
    }

    @Test
    void aCellStaysInSightItsLingerAfterItsLookerGoesAndIsSeenAfter() {
        var stood = new Coord3D(420f, 420f, 0f);
        var scout = world.spawn(world.findTemplate("Scout"), stood, viewer);
        var near = new Coord3D(480f, 420f, 0f); // 60 off
        var far = new Coord3D(620f, 420f, 0f); // 200 off
        run(1);
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, near));

        scout.markDestroyed();
        run(149);
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, near), "in sight 149 frames later");
        assertTrue(world.canSee(viewer, near), "and canSee counts the while after");
        run(1);
        assertEquals(Sight.SEEN, world.getSightCells().sight(viewer, near), "seen, not in sight, 150 frames later");
        assertFalse(world.canSee(viewer, near));
        assertEquals(Sight.NEVER_SEEN, world.getSightCells().sight(viewer, far), "200 off was never seen");
    }

    @Test
    void anAllysLookerPutsThePlayersCellsInSightAndAnEnemysDoesNot() {
        world.spawn(world.findTemplate("Scout"), new Coord3D(200f, 200f, 0f), ally);
        world.spawn(world.findTemplate("Scout"), new Coord3D(700f, 700f, 0f), enemy);
        run(1);
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, new Coord3D(210f, 200f, 0f)));
        assertEquals(Sight.NEVER_SEEN, world.getSightCells().sight(viewer, new Coord3D(710f, 700f, 0f)));
        assertEquals(1, world.getVisibleObjects(viewer).size(), "the ally's scout, not the enemy's");
    }

    @Test
    void aViewOfAPlayersCellsReadsAsTheCellsDoAndPastTheEdgeIsNeverSeen() {
        world.spawn(world.findTemplate("Scout"), new Coord3D(20f, 20f, 0f), viewer);
        run(1);
        var view = world.getSightCells().view(viewer);
        assertEquals(Sight.IN_SIGHT, view.at(30f, 30f));
        assertEquals(Sight.NEVER_SEEN, view.at(-10f, 30f), "past the map's edge");
        assertEquals(Sight.NEVER_SEEN, view.at(900f, 900f));
    }
}
