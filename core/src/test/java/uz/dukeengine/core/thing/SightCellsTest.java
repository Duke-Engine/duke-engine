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
        factory.addTemplate(ObjectTemplate.named("Lamp").visionRange(30f).module(new ActiveBody.Data(10f)).build());
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

    /** Every cell of {@code player}'s view, counted by what he has of it. */
    private java.util.Map<Sight, Integer> counted(int player) {
        var counts = new java.util.EnumMap<Sight, Integer>(Sight.class);
        for (var state : world.getSightCells().view(player).states()) {
            counts.merge(Sight.values()[state], 1, Integer::sum);
        }
        return counts;
    }

    /**
     * The reference's reveal at the start of a match whose option leaves the shroud out: every one of the 25 by 25
     * cells seen and none in sight; a looker of his opens what it covers — its own cell and the four a cell off — and
     * the rest stays seen.
     */
    @Test
    void aPlayerMarkedSeenHasEveryCellSeenAndHisLookerPutsInSightOnlyWhatItCovers() {
        world.markMapSeen(viewer);
        run(1);
        assertEquals(java.util.Map.of(Sight.SEEN, 625), counted(viewer), "every cell seen, none unseen or in sight");
        assertFalse(world.canSee(viewer, new Coord3D(700f, 700f, 0f)), "seen is not in sight");
        assertEquals(java.util.Map.of(Sight.NEVER_SEEN, 625), counted(enemy), "and it is his alone");

        world.spawn(world.findTemplate("Lamp"), new Coord3D(500f, 500f, 0f), viewer); // cell (12, 12)
        run(1);
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, 12, 12));
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, 13, 12));
        assertEquals(java.util.Map.of(Sight.IN_SIGHT, 5, Sight.SEEN, 620), counted(viewer),
                "what the lamp covers in sight, the rest still seen");
    }

    /** Marked seen and then revealed: the reveal is the more, every cell in sight for good, whatever leaves. */
    @Test
    void aPlayerMarkedSeenAndThenRevealedHasEveryCellInSightForGood() {
        world.markMapSeen(viewer);
        world.revealMapTo(viewer);
        var lamp = world.spawn(world.findTemplate("Lamp"), new Coord3D(500f, 500f, 0f), viewer);
        run(1);
        assertEquals(java.util.Map.of(Sight.IN_SIGHT, 625), counted(viewer));

        lamp.markDestroyed();
        run(200);
        assertEquals(java.util.Map.of(Sight.IN_SIGHT, 625), counted(viewer), "a looker leaving fogs none of it");
        assertTrue(world.canSee(viewer, new Coord3D(900f, 900f, 0f)));
        assertTrue(world.isMapMarkedSeen(viewer) && world.isMapRevealedTo(viewer), "each kept as it was said");
    }

    /** A mark is the player's, not the grid's: cells laid after it, on a new grid, are marked too. */
    @Test
    void aMarkIsOnTheCellsOfAGridLaidAfterIt() {
        world.markMapSeen(viewer);
        world.setPathGrid(new PathGrid(50, 50));
        assertEquals(java.util.Map.of(Sight.SEEN, 169), counted(viewer), "13 by 13 cells of 40 over 500");
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
    @Test
    void withStoneHidingSightAWallKeepsWhatIsBehindItNeverSeen() {
        var grid = new PathGrid(100, 100);
        for (int y = 0; y < 100; y++) {
            grid.setBlocked(30, y, true); // a wall of stone at x 300..310
        }
        world.setPathGrid(grid);
        world.setSightCells(10f, 150);
        world.setSightHiddenByStone(true);
        world.spawn(world.findTemplate("Scout"), new Coord3D(255f, 505f, 0f), viewer);
        run(1);
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, new Coord3D(295f, 505f, 0f)), "this side");
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, new Coord3D(305f, 505f, 0f)), "the wall");
        assertEquals(Sight.NEVER_SEEN, world.getSightCells().sight(viewer, new Coord3D(325f, 505f, 0f)), "beyond");

        world.setSightHiddenByStone(false);
        run(1);
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, new Coord3D(325f, 505f, 0f)),
                "a disc, as the reference's, once stone hides nothing");
    }

    /** A template that says whether it blocks sight, as a game's own record does with a BlocksSight line. */
    private record Gate(String name, java.util.List<uz.dukeengine.core.module.ModuleData> modules, Geometry geometry,
            boolean blocksSight) implements Solid {
    }

    /**
     * A shut gate standing in a doorway hides what is behind it as the wall beside it does — itself seen, the court
     * not — and gone, the next look sees through: a test of the one rule a door, a curtain or a hedge says too.
     */
    @Test
    void aThingThatBlocksSightHidesWhatIsBehindItWhileItStands() {
        world.getThingFactory().addTemplate(
                new Gate("Gate", java.util.List.of(), new Geometry.Box(15f, 2f, 10f), true));
        world.getThingFactory().addTemplate(new Gate("OpenGate", java.util.List.of(), new Geometry.Box(15f, 2f, 10f),
                false));
        world.setPathGrid(new PathGrid(100, 100));
        world.setSightCells(10f, 150);
        world.setSightHiddenByStone(true);
        var gate = world.spawn(world.findTemplate("Gate"), new Coord3D(305f, 505f, 0f), enemy);
        gate.setOrientation((float) (Math.PI / 2)); // its length across the line of sight, x 303..307, y 490..520
        world.spawn(world.findTemplate("Scout"), new Coord3D(255f, 505f, 0f), viewer);
        var cells = world.getSightCells();
        run(1);
        assertEquals(Sight.IN_SIGHT, cells.sight(viewer, new Coord3D(295f, 505f, 0f)), "this side");
        assertEquals(Sight.IN_SIGHT, cells.sight(viewer, new Coord3D(305f, 505f, 0f)), "the gate's own cell");
        assertEquals(Sight.NEVER_SEEN, cells.sight(viewer, new Coord3D(325f, 505f, 0f)), "behind it");

        gate.markDestroyed();
        world.spawn(world.findTemplate("OpenGate"), new Coord3D(305f, 505f, 0f), enemy)
                .setOrientation((float) (Math.PI / 2));
        run(1);
        assertEquals(Sight.IN_SIGHT, cells.sight(viewer, new Coord3D(325f, 505f, 0f)),
                "opened — another template that says nothing — and the next look sees through");
    }

    /**
     * The Particle Uplink Cannon's reveal: a place looked at this frame by no thing is the player's alone, in sight as the
     * frame ends and for the linger after, then seen; asked again the next frame it is a look again.
     */
    @Test
    void aPlaceLookedAtWithNoThingThereIsInSightToThatPlayerAloneForTheLingerAfter() {
        var at = new Coord3D(420f, 420f, 0f);
        var near = new Coord3D(480f, 420f, 0f); // 60 off
        world.lookAt(viewer, at, 100f);
        run(1);
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, near));
        assertEquals(Sight.NEVER_SEEN, world.getSightCells().sight(enemy, near), "and not the enemy's");
        assertEquals(Sight.NEVER_SEEN, world.getSightCells().sight(ally, near), "nor the ally's");

        run(149);
        assertEquals(Sight.IN_SIGHT, world.getSightCells().sight(viewer, near), "still in sight 149 frames later");
        run(1);
        assertEquals(Sight.SEEN, world.getSightCells().sight(viewer, near), "seen once the linger is out");
    }

    /** Where the game keeps no cells, a look at a place is asked of {@code canSee} until the frame ends, and no longer. */
    @Test
    void whereNoCellsAreKeptAPlaceLookedAtIsSeenOnlyUntilTheFrameEnds() {
        world.setSightCells(0f, 150);
        var near = new Coord3D(480f, 420f, 0f);
        world.lookAt(viewer, new Coord3D(420f, 420f, 0f), 100f);
        assertTrue(world.canSee(viewer, near));
        assertFalse(world.canSee(enemy, near));
        run(1);
        assertFalse(world.canSee(viewer, near), "gone with the frame");
    }
}
