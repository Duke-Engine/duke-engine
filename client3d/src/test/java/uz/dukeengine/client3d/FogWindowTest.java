package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.texture.Texture;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.UnitView;

/**
 * The fog's picture of a window round the camera holds, at the place of the picture each texel of the ground always
 * has, the dark of that ground — every texel the window holds, as the hero walks and the window follows — and the
 * ground past it is told to the shader a texel in from its edge.
 */
class FogWindowTest {

    private static PathGrid rooms() {
        var random = new SplittableRandom(3);
        var grid = new PathGrid(220, 170);
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                grid.setBlocked(cx, cy, random.nextInt(7) == 0);
            }
        }
        return grid;
    }

    private static byte expected(float light) {
        return (byte) Math.round(Math.clamp(1f - light, 0f, 1f) * 255f);
    }

    private static void walk(Fog fog, int expectedTexels) {
        var grid = rooms();
        var seen = new Discovery(grid, fog);
        seen.window(40);
        var map = new FogMap(fog);
        float x = 300f;
        float y = 400f;
        seen.follow(x, y);
        map.resize(grid, seen);
        assertTrue(map.isWindowed());
        assertEquals(expectedTexels, map.width());
        assertEquals(Texture.WrapMode.Repeat, map.texture().getWrap(Texture.WrapAxis.S));
        assertEquals(40 * grid.getCellSize(), map.worldSize().x, "the ground one copy of the picture holds");
        var random = new SplittableRandom(17);
        int version = map.windowVersion();
        for (int frame = 0; frame < 240; frame++) {
            x = Math.clamp(x + (float) random.nextDouble(-4, 9), 0f, 2199f);
            y = Math.clamp(y + (float) random.nextDouble(-6, 7), 0f, 1699f);
            seen.reveal(List.of(new UnitView(1, "Hero", 0, x, y, 0f, 4f, 4f, false, true, false, false, -1)), 0,
                    70f, "Hero");
            seen.soften(1f / 30f);
            seen.follow(x, y);
            map.update(seen);
            float texel = map.texelSize();
            for (int ty = map.liveY(); ty < map.liveY() + map.height(); ty++) {
                for (int tx = map.liveX(); tx < map.liveX() + map.width(); tx++) {
                    int column = Math.floorMod(tx, map.width());
                    int row = map.height() - 1 - Math.floorMod(ty, map.height());
                    byte wanted = expected(seen.lightAtPoint((tx + 0.5f) * texel, (ty + 0.5f) * texel));
                    assertEquals(wanted & 0xFF, Math.round(map.darknessAt(column, row) * 255f),
                            "frame " + frame + ", texel " + tx + "," + ty);
                }
            }
            var window = map.window();
            assertEquals((map.liveX() + 1) * texel, window.x, 1e-3f);
            assertEquals((map.liveY() + map.height() - 1) * texel, window.w, 1e-3f);
            // The window holds the ground round the hero, a texel in from its edges at most one cell off the middle.
            assertTrue(window.x < x && x < window.z && window.y < y && y < window.w, "the hero is in the window");
        }
        assertTrue(map.windowVersion() > version, "the window moved, and said so");
    }

    @Test
    void aSharpFogAWindowOfTwoTexelsACell() {
        walk(new Fog(true, 0f, 0.3f, 1f, 2, 7f, 256, 0x121821, 2), 80);
    }

    @Test
    void aFogOfAFixedSizeLaidOverTheWindow() {
        walk(new Fog(true, 0f, 0.3f, 1f, 1, 7f, 256, 0x121821), 256);
    }

    @Test
    void theGroundsAndTheWatersShadersAreToldWhereTheWindowHoldsAndOnlyThen() {
        var assets = new com.jme3.asset.DesktopAssetManager(true);
        for (var definition : List.of("MatDefs/duke/FoggedTerrain.j3md", "MatDefs/duke/Water.j3md")) {
            var material = new com.jme3.material.Material(assets, definition);
            assertTrue(material.getParam("FogWindow") == null, definition + ": no window unless told one");
            material.setVector4("FogWindow", new com.jme3.math.Vector4f(10f, 20f, 30f, 40f));
            var technique = material.getMaterialDef().getTechniqueDefs("Default").get(0);
            assertEquals("FOG_WINDOW", technique.getShaderParamDefine("FogWindow"), definition);
            assertTrue(material.getParam("Haze") == null, definition + ": no haze unless told one");
            material.setVector4("Haze", new com.jme3.math.Vector4f(100f, 200f, 300f, 450f));
            assertEquals("HAZE", technique.getShaderParamDefine("Haze"), definition);
        }
    }

    @Test
    void aHazeRunsFromNoFurtherThanItEndsAndNoneIsNone() {
        var visuals = Visuals.create();
        assertTrue(!visuals.hazes(), "none unless the game keeps one");
        visuals.haze(40f, 60f);
        assertTrue(visuals.hazes());
        assertEquals(40f, visuals.getHazeFrom());
        assertEquals(60f, visuals.getHazeTo());
        visuals.haze(80f, 60f);
        assertEquals(60f, visuals.getHazeFrom(), "it cannot start past where it ends");
        visuals.haze(10f, 0f);
        assertTrue(!visuals.hazes());
    }

    @Test
    void aMapNarrowerThanTheWindowIsDrawnWholeAsItAlwaysWas() {
        var grid = new PathGrid(30, 20);
        var fog = new Fog(true, 0f, 0.3f, 1f, 2, 7f, 256, 0x121821, 2);
        var seen = new Discovery(grid, fog);
        seen.window(40);
        var map = new FogMap(fog);
        map.resize(grid, seen);
        assertTrue(!seen.isWindowed() && !map.isWindowed());
        assertEquals(60, map.width());
        assertEquals(40, map.height());
        assertEquals(Texture.WrapMode.EdgeClamp, map.texture().getWrap(Texture.WrapAxis.S));
    }
}
