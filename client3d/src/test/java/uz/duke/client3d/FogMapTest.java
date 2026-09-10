package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.game.view.UnitView;

/**
 * The dark, as a picture of the map rather than a shade per cell.
 *
 * <p>This is where the squares died. The fog was correct before — the light was
 * blurred over its neighbours and eased over time — and it still came out as a
 * field of ten-unit tiles, because a cell got one number and that number was
 * painted flat across every tile in it. The softening was real and it was
 * happening at the wrong size.
 *
 * <p>So what is worth holding still is not that the fog is soft in principle but
 * that <b>no two neighbouring texels are far apart</b>: an edge that climbs from
 * dark to clear in small steps is a gradient, and one that does it in a single
 * step is a square, however many texels are either side of it. The second thing is
 * the picture's alignment — laid down the wrong way up it is a fog that opens
 * where the hero is not, and it is the sort of mistake that looks plausible in
 * every still picture of it.
 *
 * <p>What is <em>not</em> here is how the picture reaches the screen. The terrain's
 * material samples it by world x and z, in a shader, and a shader is not something
 * a test without a window can compile. What a test can do is make sure the numbers
 * going into it are right and that the mapping this file writes out is the same
 * one {@code FoggedTerrain.vert} reads.
 */
class FogMapTest {

    /** A long open corridor, so the light can run out somewhere along it. */
    private static final String CORRIDOR = """
            ##############
            #............#
            #............#
            #............#
            ##############
            """;

    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static Fog fog(int textureSize) {
        return new Fog(false, 0f, 0.3f, 1f, 2, 7f, textureSize, 0x121821);
    }

    private static FogMap fogMap(int textureSize, PathGrid grid) {
        var map = new FogMap(fog(textureSize));
        map.resize(grid);
        return map;
    }

    /** What the hero has opened up, run on until the fog has stopped moving. */
    private static Discovery seenFrom(PathGrid grid, float x, float y, float radius) {
        var seen = new Discovery(grid, fog(64));
        seen.reveal(List.of(unit(x, y)), 0, radius);
        for (int frame = 0; frame < 120; frame++) {
            seen.soften(1f / 30f);
        }
        return seen;
    }

    private static UnitView unit(float x, float y) {
        return new UnitView(1, "Hero", 0, x, y, 0f, 10f, 10f, false, true, false, false, -1);
    }

    // ---- the picture itself ----

    /**
     * How big the picture is, is the game's answer and nobody else's.
     *
     * <p>The point of the whole change: the dark used to be drawn at whatever
     * resolution the pathfinder happened to want its cells at, which is a number
     * chosen for how the hero walks. Two maps of very different sizes get the same
     * picture, and asking for a bigger one gets a bigger one.
     */
    @Test
    void theTextureIsTheSizeTheGameAskedForWhateverTheMapIs() {
        var map = fogMap(128, MapLoader.fromText(CORRIDOR));
        assertEquals(128, map.size());

        map.resize(new PathGrid(200, 200));
        assertEquals(128, map.size(), "a map sixteen times the area, and the same picture");

        assertEquals(256, fogMap(256, MapLoader.fromText(CORRIDOR)).size(),
                "and the number in the file is the one that decides");
    }

    /**
     * How wide the map is, is what a world position divides by — and it follows
     * the map rather than being fixed.
     *
     * <p>The shader turns a vertex's world x and z into a place on the picture with
     * exactly this pair of numbers. Handed the wrong map's size, every wall in the
     * dungeon reads the dark from somewhere else entirely.
     */
    @Test
    void theWorldSizeFollowsTheMap() {
        var grid = MapLoader.fromText(CORRIDOR);
        var map = fogMap(64, grid);

        assertEquals(grid.getWidth() * CELL, map.worldSize().x, 0.001f);
        assertEquals(grid.getHeight() * CELL, map.worldSize().y, 0.001f);

        map.resize(new PathGrid(30, 20));
        assertEquals(30 * CELL, map.worldSize().x, 0.001f);
        assertEquals(20 * CELL, map.worldSize().y, 0.001f);
    }

    /** A floor nobody has walked is dark to the last texel. */
    @Test
    void anUnwalkedFloorIsWhollyDark() {
        var grid = MapLoader.fromText(CORRIDOR);
        var map = fogMap(64, grid);

        map.update(new Discovery(grid, fog(64)));

        for (int ty = 0; ty < map.size(); ty++) {
            for (int tx = 0; tx < map.size(); tx++) {
                assertEquals(1f, map.darknessAt(tx, ty), 0.001f);
            }
        }
    }

    /** A new floor is a floor nobody has walked, whatever the last one had opened. */
    @Test
    void aNewMapForgetsTheLastOnesLight() {
        var grid = MapLoader.fromText(CORRIDOR);
        var map = fogMap(64, grid);
        map.update(seenFrom(grid, 1.5f * CELL, 2.5f * CELL, 3f * CELL));
        assertTrue(map.darknessAt(texelX(map, grid, 1.5f * CELL),
                texelY(map, grid, 2.5f * CELL)) < 0.2f, "he opened this much of it");

        map.resize(MapLoader.fromText(CORRIDOR));

        assertEquals(1f, map.darknessAt(texelX(map, grid, 1.5f * CELL),
                texelY(map, grid, 2.5f * CELL)), 0.001f, "and the next floor is black again");
    }

    // ---- the thing that was wrong ----

    /**
     * The edge of the light climbs. It does not step.
     *
     * <p>The whole bug in one assertion. Reading a brightness per cell and painting
     * it across the cell puts a jump of the full difference between two
     * neighbouring cells into one texel — a visible line, and a floor of squares
     * once there are lines on all four sides. Sampling the field at each texel's
     * own point spreads that same difference over as many texels as fit in a cell.
     *
     * <p>The range is asserted as well, or a fog that had simply gone out
     * everywhere would pass: it has to cross most of the way from clear to dark
     * <em>and</em> do it in small steps.
     */
    @Test
    void theEdgeOfTheLightIsARampAndNotAStep() {
        var grid = MapLoader.fromText(CORRIDOR);
        var map = fogMap(128, grid);

        map.update(seenFrom(grid, 1.5f * CELL, 2.5f * CELL, 2.5f * CELL));

        int row = texelY(map, grid, 2.5f * CELL); // straight down the corridor
        float darkest = 0f;
        float clearest = 1f;
        float biggestStep = 0f;
        for (int tx = 1; tx < map.size(); tx++) {
            float here = map.darknessAt(tx, row);
            darkest = Math.max(darkest, here);
            clearest = Math.min(clearest, here);
            biggestStep = Math.max(biggestStep, Math.abs(here - map.darknessAt(tx - 1, row)));
        }

        assertTrue(clearest < 0.15f, "he is standing on this row, so part of it is clear");
        assertTrue(darkest > 0.85f, "and the far end of it is dark");
        assertTrue(biggestStep < 0.12f,
                "but no one texel may carry the whole difference: that is the square");
    }

    /**
     * The picture is laid the right way up.
     *
     * <p>A texture over a map has two chances to come out mirrored, and both look
     * entirely plausible until you notice the dark opening on the wrong side of the
     * hero. So the hero's own corner is asked directly, against the corner
     * diagonally opposite it.
     */
    @Test
    void theClearPatchIsWhereTheHeroActuallyStands() {
        var grid = MapLoader.fromText(CORRIDOR);
        var map = fogMap(64, grid);
        float nearX = 1.5f * CELL;
        float nearY = 1.5f * CELL;
        float farX = 11.5f * CELL;
        float farY = 3.5f * CELL;

        map.update(seenFrom(grid, nearX, nearY, 2f * CELL));

        assertTrue(map.darknessAt(texelX(map, grid, nearX), texelY(map, grid, nearY)) < 0.2f,
                "where he stands the picture should be clear");
        assertTrue(map.darknessAt(texelX(map, grid, farX), texelY(map, grid, farY)) > 0.9f,
                "and away at the other end of the corridor it should not be");
    }

    /** Ground he walked and left is neither lit nor black — it is in between. */
    @Test
    void groundHeHasLeftIsDrawnDimmerRatherThanHidden() {
        var grid = MapLoader.fromText(CORRIDOR);
        var map = fogMap(64, grid);
        var seen = new Discovery(grid, fog(64));

        seen.reveal(List.of(unit(1.5f * CELL, 2.5f * CELL)), 0, 2f * CELL);
        seen.reveal(List.of(unit(11.5f * CELL, 2.5f * CELL)), 0, 2f * CELL); // gone off
        for (int frame = 0; frame < 120; frame++) {
            seen.soften(1f / 30f);
        }
        map.update(seen);

        float left = map.darknessAt(texelX(map, grid, 1.5f * CELL),
                texelY(map, grid, 2.5f * CELL));
        assertTrue(left > 0.4f, "he is not there any more");
        assertTrue(left < 0.95f, "but he has been, and the walls have to stay findable");
    }

    /** An update before there is a map to update is a no-op, not a crash. */
    @Test
    void updatingBeforeThereIsAMapDoesNothing() {
        var map = new FogMap(fog(64));

        map.update(new Discovery(MapLoader.fromText(CORRIDOR), fog(64)));

        assertEquals(1f, map.darknessAt(0, 0), 0.001f);
    }

    // ---- where a point on the map lands on the picture ----

    private static int texelX(FogMap map, PathGrid grid, float worldX) {
        float worldWidth = grid.getWidth() * grid.getCellSize();
        return Math.clamp((int) (worldX / worldWidth * map.size()), 0, map.size() - 1);
    }

    /**
     * The image's first row is the map's <em>far</em> edge; v runs the other way.
     *
     * <p>Written out here rather than borrowed from {@code FogMap}: a mapping that
     * agreed with the code it is checking would agree with it upside down too. It
     * is also the mapping {@code FoggedTerrain.vert} computes — {@code 1.0 - z/depth}
     * — and the two have to stay the same sentence in two languages.
     */
    private static int texelY(FogMap map, PathGrid grid, float worldY) {
        float worldHeight = grid.getHeight() * grid.getCellSize();
        return Math.clamp((int) ((1f - worldY / worldHeight) * map.size()), 0, map.size() - 1);
    }
}
