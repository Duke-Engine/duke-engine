package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Node;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.game.view.UnitView;

/**
 * The dark, drawn as a sheet rather than as a shade per cell.
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
 * the sheet's alignment — a texture laid down the wrong way up is a fog that opens
 * where the hero is not, and it is the sort of mistake that looks plausible in
 * every still picture of it.
 *
 * <p>Nothing here needs a window: an image is a byte buffer, and the card's own
 * interpolation is the one part not being asked about.
 */
class FogOverlayTest {

    /** A long open corridor, so the light can run out somewhere along it. */
    private static final String CORRIDOR = """
            ##############
            #............#
            #............#
            #............#
            ##############
            """;

    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    /** How high the walls stand, which is where the sheet has to hang. */
    private static final float WALL_TOP = 10f;

    private static Fog fog(int textureSize) {
        return new Fog(false, 0f, 0.3f, 1f, 2, 7f, textureSize, 0x121821);
    }

    private static FogOverlay overlay(Node root, int textureSize) {
        return new FogOverlay(root, texture -> null, fog(textureSize));
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

    // ---- the sheet itself ----

    /** One sheet over the map, and a rebuild replaces it rather than adding one. */
    @Test
    void everyMapGetsOneSheetAndOnlyOne() {
        var root = new Node("fog");
        var overlay = overlay(root, 64);

        overlay.rebuild(MapLoader.fromText(CORRIDOR), WALL_TOP);
        assertEquals(1, root.getChildren().size(), "the sheet");

        for (int run = 0; run < 5; run++) {
            overlay.rebuild(MapLoader.fromText(CORRIDOR), WALL_TOP);
        }

        assertEquals(1, root.getChildren().size(),
                "six runs later there should still be one dungeon's worth of dark");
    }

    /**
     * The sheet hangs level with the wall tops, not with the floor.
     *
     * <p>A flat sheet lines up with the world at one height only, and the height
     * to pick is the one where the surfaces have edges. A roof over a piece of
     * rock is a tile with a wall along the side of it: hang the sheet at the floor
     * and the near half of every roof stays lit while the far half goes dark, and
     * a roof lit down the middle is worse to look at than the squares were.
     */
    @Test
    void theSheetHangsLevelWithTheWallTops() {
        var root = new Node("fog");
        var overlay = overlay(root, 64);

        overlay.rebuild(MapLoader.fromText(CORRIDOR), WALL_TOP);

        float hangs = root.getChild(0).getLocalTranslation().y;
        assertTrue(hangs >= WALL_TOP && hangs < WALL_TOP + 1f,
                "the sheet should sit just clear of the wall tops, not down on the floor,"
                        + " but it hangs at " + hangs);
    }

    /** A game with no map has nothing to lay a sheet over. */
    @Test
    void withoutAMapThereIsNoSheet() {
        var root = new Node("fog");
        overlay(root, 64).rebuild(null, WALL_TOP);

        assertEquals(0, root.getChildren().size());
    }

    /**
     * How big the sheet is, is the game's answer and nobody else's.
     *
     * <p>The point of the whole change: the dark used to be drawn at whatever
     * resolution the pathfinder happened to want its cells at, which is a number
     * chosen for how the hero walks. Two maps of very different sizes get the same
     * sheet, and asking for a bigger one gets a bigger one.
     */
    @Test
    void theSheetIsTheSizeTheGameAskedForWhateverTheMapIs() {
        var root = new Node("fog");
        var overlay = overlay(root, 128);

        overlay.rebuild(MapLoader.fromText(CORRIDOR), WALL_TOP);
        assertEquals(128, overlay.size());

        overlay.rebuild(new PathGrid(200, 200), WALL_TOP);
        assertEquals(128, overlay.size(), "a map sixteen times the area, and the same sheet");

        var bigger = overlay(new Node("fog"), 256);
        bigger.rebuild(MapLoader.fromText(CORRIDOR), WALL_TOP);
        assertEquals(256, bigger.size(), "and the number in the file is the one that decides");
    }

    /** A floor nobody has walked is dark to the last texel. */
    @Test
    void anUnwalkedFloorIsWhollyDark() {
        var root = new Node("fog");
        var grid = MapLoader.fromText(CORRIDOR);
        var overlay = overlay(root, 64);
        overlay.rebuild(grid, WALL_TOP);

        overlay.update(new Discovery(grid, fog(64)));

        for (int ty = 0; ty < overlay.size(); ty++) {
            for (int tx = 0; tx < overlay.size(); tx++) {
                assertEquals(1f, overlay.darknessAt(tx, ty), 0.001f);
            }
        }
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
        var root = new Node("fog");
        var grid = MapLoader.fromText(CORRIDOR);
        var overlay = overlay(root, 128);
        overlay.rebuild(grid, WALL_TOP);

        overlay.update(seenFrom(grid, 1.5f * CELL, 2.5f * CELL, 2.5f * CELL));

        int row = texelY(overlay, grid, 2.5f * CELL); // straight down the corridor
        float darkest = 0f;
        float clearest = 1f;
        float biggestStep = 0f;
        for (int tx = 1; tx < overlay.size(); tx++) {
            float here = overlay.darknessAt(tx, row);
            darkest = Math.max(darkest, here);
            clearest = Math.min(clearest, here);
            biggestStep = Math.max(biggestStep,
                    Math.abs(here - overlay.darknessAt(tx - 1, row)));
        }

        assertTrue(clearest < 0.15f, "he is standing on this row, so part of it is clear");
        assertTrue(darkest > 0.85f, "and the far end of it is dark");
        assertTrue(biggestStep < 0.12f,
                "but no one texel may carry the whole difference: that is the square");
    }

    /**
     * The sheet is laid the right way up.
     *
     * <p>A texture over a map has two chances to come out mirrored, and both look
     * entirely plausible until you notice the dark opening on the wrong side of the
     * hero. So the hero's own corner is asked directly, against the corner
     * diagonally opposite it.
     */
    @Test
    void theClearPatchIsWhereTheHeroActuallyStands() {
        var root = new Node("fog");
        var grid = MapLoader.fromText(CORRIDOR);
        var overlay = overlay(root, 64);
        overlay.rebuild(grid, WALL_TOP);
        float nearX = 1.5f * CELL;
        float nearY = 1.5f * CELL;
        float farX = 11.5f * CELL;
        float farY = 3.5f * CELL;

        overlay.update(seenFrom(grid, nearX, nearY, 2f * CELL));

        assertTrue(overlay.darknessAt(texelX(overlay, grid, nearX),
                        texelY(overlay, grid, nearY)) < 0.2f,
                "where he stands the sheet should be clear");
        assertTrue(overlay.darknessAt(texelX(overlay, grid, farX),
                        texelY(overlay, grid, farY)) > 0.9f,
                "and away at the other end of the corridor it should not be");
    }

    /** Ground he walked and left is neither lit nor black — it is in between. */
    @Test
    void groundHeHasLeftIsDrawnDimmerRatherThanHidden() {
        var root = new Node("fog");
        var grid = MapLoader.fromText(CORRIDOR);
        var overlay = overlay(root, 64);
        overlay.rebuild(grid, WALL_TOP);
        var seen = new Discovery(grid, fog(64));

        seen.reveal(List.of(unit(1.5f * CELL, 2.5f * CELL)), 0, 2f * CELL);
        seen.reveal(List.of(unit(11.5f * CELL, 2.5f * CELL)), 0, 2f * CELL); // gone off
        for (int frame = 0; frame < 120; frame++) {
            seen.soften(1f / 30f);
        }
        overlay.update(seen);

        float left = overlay.darknessAt(texelX(overlay, grid, 1.5f * CELL),
                texelY(overlay, grid, 2.5f * CELL));
        assertTrue(left > 0.4f, "he is not there any more");
        assertTrue(left < 0.95f, "but he has been, and the walls have to stay findable");
    }

    /** An update before there is a sheet to update is a no-op, not a crash. */
    @Test
    void updatingBeforeThereIsAMapDoesNothing() {
        var root = new Node("fog");
        var overlay = overlay(root, 64);

        overlay.update(new Discovery(MapLoader.fromText(CORRIDOR), fog(64)));

        assertEquals(0, overlay.size());
        assertFalse(root.hasChild(new Node("anything")));
    }

    // ---- where a point on the map lands on the sheet ----

    private static int texelX(FogOverlay overlay, PathGrid grid, float worldX) {
        float worldWidth = grid.getWidth() * grid.getCellSize();
        return Math.clamp((int) (worldX / worldWidth * overlay.size()), 0, overlay.size() - 1);
    }

    /**
     * The sheet lies like the ground plane, whose own +y runs back up the map, so
     * the image's first row is the map's <em>far</em> edge. Written out here rather
     * than borrowed from the overlay: a mapping that agreed with the code it is
     * checking would agree with it upside down too.
     */
    private static int texelY(FogOverlay overlay, PathGrid grid, float worldY) {
        float worldHeight = grid.getHeight() * grid.getCellSize();
        return Math.clamp((int) ((1f - worldY / worldHeight) * overlay.size()),
                0, overlay.size() - 1);
    }
}
