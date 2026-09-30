package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.ColorRGBA;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.UnitView;

/** The minimap's ground as one picture, a texel a cell, repainted only where the player's knowledge changed. */
class MinimapPictureTest {

    private static final int STONE = 0x595242;
    private static final int GROUND = 0x1A2414;
    private static final int LIT_FLOOR = 0x293821;

    private static PathGrid walled() {
        var grid = new PathGrid(30, 20);
        for (int cy = 0; cy < 20; cy++) {
            grid.setBlocked(15, cy, cy != 10);
        }
        return grid;
    }

    private static UnitView at(float x, float y) {
        return new UnitView(1, "Rogue", 0, x, y, 0f, 10f, 10f, false, true, false, false, -1);
    }

    @Test
    void withoutDiscoveryItIsTheGroundAndItsStonePaintedOnceAtACellATexel() {
        var grid = walled();
        var picture = new MinimapPicture();
        picture.rebuild(grid, false, ColorRGBA.Black);

        assertEquals(30, picture.texture().getImage().getWidth());
        assertEquals(20, picture.texture().getImage().getHeight());
        assertEquals(STONE, picture.colourAt(15, 3));
        assertEquals(GROUND, picture.colourAt(3, 3));
        assertEquals(GROUND, picture.colourAt(15, 10), "the gap in the wall is ground");
    }

    @Test
    void theFirstRowOfThePictureIsTheMapsFarEdgeDrawnAtTheBottom() {
        var grid = new PathGrid(4, 3);
        grid.setBlocked(0, 2, true); // the last row of the map
        var picture = new MinimapPicture();
        picture.rebuild(grid, false, ColorRGBA.Black);

        var data = picture.texture().getImage().getData(0);
        assertEquals((byte) 0x59, data.get(0), "row 0, texel 0: the stone at the far edge");
    }

    @Test
    void withDiscoveryItIsUnwalkedUntilSeenThenLitThenRemembered() {
        var grid = walled();
        var seen = new Discovery(grid);
        var picture = new MinimapPicture();
        picture.rebuild(grid, true, ColorRGBA.Black);
        int unseen = picture.colourAt(5, 5);
        assertEquals(0, unseen, "black, from the fog's black");

        seen.reveal(List.of(at(55f, 55f)), 0, 40f, null);
        seen.soften(1f / 30f);
        picture.paint(seen);
        assertEquals(LIT_FLOOR, picture.colourAt(5, 5), "the floor he stands on, lit");
        assertEquals(unseen, picture.colourAt(25, 15), "and far over there, still nothing");

        seen.reveal(List.of(at(105f, 55f)), 0, 40f, null);
        seen.soften(1f / 30f);
        picture.paint(seen);
        int remembered = picture.colourAt(5, 5);
        assertNotEquals(LIT_FLOOR, remembered, "left behind, it is remembered");
        assertNotEquals(unseen, remembered);
    }

    @Test
    void repaintingOnlyWhatChangedPaintsWhatRepaintingEverythingPaints() {
        var grid = walled();
        var seen = new Discovery(grid, new Fog(true, 0f, 0.3f, 1f, 1, 7f, 64, 0x102030));
        var kept = new MinimapPicture();
        kept.rebuild(grid, true, new ColorRGBA(0.06f, 0.12f, 0.19f, 1f));
        for (int step = 0; step < 50; step++) {
            seen.reveal(List.of(at(15f + step * 5f, 100f + (step % 5) * 4f)), 0, 35f, null);
            seen.soften(1f / 30f);
            kept.paint(seen);
        }
        var whole = new MinimapPicture();
        whole.rebuild(grid, true, new ColorRGBA(0.06f, 0.12f, 0.19f, 1f));
        whole.paint(seen);
        for (int cy = 0; cy < 20; cy++) {
            for (int cx = 0; cx < 30; cx++) {
                assertEquals(whole.colourAt(cx, cy), kept.colourAt(cx, cy), "cell " + cx + "," + cy);
            }
        }
    }

    @Test
    void aFloorAStoreyUpIsPalerThanTheOneBelowIt() {
        var grid = new PathGrid(10, 4);
        grid.setLevelHeight(10f);
        for (int cy = 0; cy < 4; cy++) {
            for (int cx = 5; cx < 10; cx++) {
                grid.setLevel(cx, cy, 1);
            }
        }
        var seen = new Discovery(grid);
        var picture = new MinimapPicture();
        picture.rebuild(grid, true, ColorRGBA.Black);
        seen.openEverything();
        seen.soften(1f / 30f);
        picture.paint(seen);

        assertTrue((picture.colourAt(7, 2) >> 8 & 0xFF) > (picture.colourAt(2, 2) >> 8 & 0xFF),
                "the storey up is paler");
    }

    @Test
    void aWindowRoundTheCameraIsTheWholeMapsPictureWhereverItHolds() {
        var random = new java.util.SplittableRandom(12);
        var grid = new PathGrid(160, 110);
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                grid.setBlocked(cx, cy, random.nextInt(6) == 0);
                if (random.nextInt(11) == 0) {
                    grid.setLevel(cx, cy, 1);
                }
            }
        }
        var tint = new ColorRGBA(0.06f, 0.12f, 0.19f, 1f);
        var seen = new Discovery(grid, new Fog(true, 0f, 0.3f, 1f, 1, 7f, 64, 0x102030));
        var whole = new MinimapPicture();
        whole.rebuild(grid, true, tint);
        var near = new MinimapPicture();
        near.rebuild(grid, true, tint, 40);
        assertTrue(near.isWindowed() && !whole.isWindowed());
        assertEquals(42, near.texture().getImage().getWidth(), "the span, and a cell each way to slide over");
        assertEquals(com.jme3.texture.Texture.WrapMode.Repeat,
                near.texture().getWrap(com.jme3.texture.Texture.WrapAxis.S));
        float x = 200f;
        float y = 300f;
        for (int frame = 0; frame < 300; frame++) {
            x = Math.clamp(x + (float) random.nextDouble(-3, 9), 0f, 1599f);
            y = Math.clamp(y + (float) random.nextDouble(-5, 6), 0f, 1099f);
            seen.reveal(List.of(at(x, y)), 0, 60f, null);
            seen.soften(1f / 30f);
            whole.paint(seen);
            near.paint(seen);
            near.follow(x, y, seen, null);
            int fromX = (int) Math.floor(x / 10f) - 21;
            int fromY = (int) Math.floor(y / 10f) - 21;
            for (int cy = fromY; cy < fromY + 42; cy++) {
                for (int cx = fromX; cx < fromX + 42; cx++) {
                    boolean onTheMap = cx >= 0 && cy >= 0 && cx < 160 && cy < 110;
                    int wanted = onTheMap ? whole.colourAt(cx, cy) : 0x070E16; // off the map, unwalked
                    assertEquals(wanted, near.colourAt(cx, cy), "frame " + frame + ", cell " + cx + "," + cy);
                }
            }
        }
    }

    @Test
    void withoutDiscoveryAWindowPaintsTheGroundAndItsStoneAsTheyComeIntoIt() {
        var grid = new PathGrid(200, 30);
        grid.setBlocked(150, 12, true);
        var near = new MinimapPicture();
        near.rebuild(grid, false, ColorRGBA.Black, 20);
        near.follow(100f, 120f, null, null);
        assertEquals(GROUND, near.colourAt(100, 12));
        near.follow(1500f, 120f, null, null);
        assertEquals(STONE, near.colourAt(150, 12), "the rock over there, come into the window");
        assertEquals(GROUND, near.colourAt(145, 12));
    }

    @Test
    void aMapNoWiderNorDeeperThanTheSpanIsPaintedWholeAsItAlwaysWas() {
        var grid = walled();
        var picture = new MinimapPicture();
        picture.rebuild(grid, false, ColorRGBA.Black, 40);
        assertTrue(!picture.isWindowed());
        assertEquals(30, picture.texture().getImage().getWidth());
        assertEquals(STONE, picture.colourAt(15, 3));
    }

    @Test
    void theQuadsCornersFindTheCellsItShows() {
        var grid = new PathGrid(500, 500);
        var near = new MinimapPicture();
        near.rebuild(grid, false, ColorRGBA.Black, 40);
        var corners = near.corners(123.25f, 77.5f);
        // Bottom left, bottom right, top right, top left: u across, v up, a texel a cell of a picture 42 a side.
        assertEquals(123.25f / 42f, corners[0], 1e-6f);
        assertEquals(1f - (77.5f + 40f) / 42f, corners[1], 1e-6f);
        assertEquals((123.25f + 40f) / 42f, corners[2], 1e-6f);
        assertEquals(1f - 77.5f / 42f, corners[5], 1e-6f);
        // The top left corner's texel, the picture repeating, is cell 123, 77: its column and its row from the bottom.
        assertEquals(Math.floorMod(123, 42), (int) Math.floorMod((long) Math.floor(corners[6] * 42), 42L));
        int rowFromTheBottom = (int) Math.floorMod((long) Math.floor(corners[7] * 42 - 1e-4), 42L);
        assertEquals(42 - 1 - Math.floorMod(77, 42), rowFromTheBottom);
    }
}
