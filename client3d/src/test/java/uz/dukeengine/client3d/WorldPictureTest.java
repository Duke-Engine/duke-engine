package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.ColorRGBA;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.UnitView;

/**
 * The whole world as the player knows it, a picture the game shows: at a texel a cell the minimap's own colours, and
 * at more cells a texel each texel its most seen cell's, its floor over its stone — kept as the player walks, and said
 * changed only when it did.
 */
class WorldPictureTest {

    private static final ColorRGBA TINT = new ColorRGBA(0.06f, 0.12f, 0.19f, 1f);

    private static PathGrid rooms() {
        var random = new SplittableRandom(21);
        var grid = new PathGrid(120, 90);
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                grid.setBlocked(cx, cy, random.nextInt(5) == 0);
                if (random.nextInt(9) == 0) {
                    grid.setLevel(cx, cy, 1 + random.nextInt(2));
                }
            }
        }
        return grid;
    }

    private static UnitView at(float x, float y) {
        return new UnitView(1, "Hero", 0, x, y, 0f, 4f, 4f, false, true, false, false, -1);
    }

    private static int rgb(Picture picture, int x, int y) {
        return picture.argb()[y * picture.width() + x] & 0xFFFFFF;
    }

    @Test
    void aTexelACellIsTheMinimapsOwnPictureAndIsKeptAsThePlayerWalks() {
        var grid = rooms();
        var seen = new Discovery(grid, new Fog(true, 0f, 0.3f, 1f, 1, 7f, 64, 0x102030));
        var minimap = new MinimapPicture();
        minimap.rebuild(grid, true, TINT);
        var world = new WorldPicture(grid, 512, true, TINT);
        assertEquals(1, world.cellsATexel());
        assertEquals(120, world.picture().width());
        var random = new SplittableRandom(4);
        float x = 300f;
        float y = 300f;
        for (int frame = 0; frame < 200; frame++) {
            x = Math.clamp(x + (float) random.nextDouble(-6, 9), 0f, 1199f);
            y = Math.clamp(y + (float) random.nextDouble(-6, 7), 0f, 899f);
            seen.reveal(List.of(at(x, y)), 0, 55f, null);
            seen.soften(1f / 30f);
            minimap.paint(seen);
            world.heard(seen);
            world.paint(seen);
        }
        for (int cy = 0; cy < 90; cy++) {
            for (int cx = 0; cx < 120; cx++) {
                assertEquals(minimap.colourAt(cx, cy), rgb(world.picture(), cx, cy), "cell " + cx + "," + cy);
            }
        }
    }

    @Test
    void manyCellsATexelShowTheMostSeenOfThemItsFloorOverItsStone() {
        var grid = new PathGrid(40, 40);
        grid.setBlocked(0, 0, true);
        var seen = new Discovery(grid);
        var world = new WorldPicture(grid, 10, true, ColorRGBA.Black);
        assertEquals(4, world.cellsATexel());
        assertEquals(10, world.picture().width());
        seen.reveal(List.of(at(5f, 5f)), 0, 12f, null); // opens cells round the corner, stone and floor
        seen.soften(1f / 30f);
        world.heard(seen);
        world.paint(seen);
        var lit = MinimapPicture.colourOf(Discovery.State.VISIBLE, false, 0, ColorRGBA.Black);
        int litFloor = (MinimapPicture.channel(lit.r) & 0xFF) << 16 | (MinimapPicture.channel(lit.g) & 0xFF) << 8
                | MinimapPicture.channel(lit.b) & 0xFF;
        assertEquals(litFloor, rgb(world.picture(), 0, 0), "the corner texel: lit, and its floor over its stone");
        assertEquals(0, rgb(world.picture(), 9, 9), "far over there, unwalked, in the fog's black");
    }

    @Test
    void itIsSaidChangedOnlyWhenItDid() {
        var grid = rooms();
        var seen = new Discovery(grid);
        var world = new WorldPicture(grid, 60, true, TINT);
        seen.reveal(List.of(at(300f, 300f)), 0, 50f, null);
        seen.soften(1f / 30f);
        world.heard(seen);
        assertTrue(world.paint(seen), "the first painting");
        int version = world.picture().version();
        seen.reveal(List.of(at(300f, 300f)), 0, 50f, null);
        seen.soften(1f / 30f);
        world.heard(seen);
        assertFalse(world.paint(seen), "standing still, nothing to paint");
        assertEquals(version, world.picture().version());
        seen.reveal(List.of(at(700f, 500f)), 0, 50f, null);
        seen.soften(1f / 30f);
        world.heard(seen);
        assertTrue(world.paint(seen));
        assertTrue(world.picture().version() > version, "a walk somewhere new, painted and said");
    }

    @Test
    void aWorldNobodyDiscoversIsItsGroundAndItsStone() {
        var grid = new PathGrid(8, 8);
        grid.setBlocked(3, 3, true);
        var world = new WorldPicture(grid, 8, false, ColorRGBA.Black);
        world.paint(null);
        int stone = rgb(world.picture(), 3, 3);
        int ground = rgb(world.picture(), 5, 5);
        assertEquals(0x595242, stone);
        assertEquals(0x1A2414, ground);
    }
}
