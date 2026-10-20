package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.MapTerrain;
import uz.dukeengine.core.map.PaintWeights;

/**
 * Ground blended from its paint alone, as Warcraft III blends its tiles: each corner carries the weights the core rule
 * gives it, a chunk at a time; a map that writes its own blends keeps them.
 */
class PaintedBlendTest {

    private static final int WIDE = 40;
    private static final int DEEP = 20;

    /** Three surfaces in bands with ragged edges, and a stone or two; no overlay of its own. */
    private static PaintedGroundTest.Field field() {
        var cells = new ArrayList<String>();
        var paint = new ArrayList<String>();
        for (int y = 0; y < DEEP; y++) {
            var row = new StringBuilder();
            var painted = new StringBuilder();
            for (int x = 0; x < WIDE; x++) {
                row.append((x * 5 + y * 7) % 31 == 0 ? '#' : '.');
                int band = (x + (y * 3) % 4) / 14;
                painted.append("gsr".charAt(Math.min(band, 2)));
            }
            cells.add(row.toString());
            paint.add(painted.toString());
        }
        var relief = new ArrayList<String>();
        for (int y = 0; y <= DEEP; y++) {
            var heights = new StringBuilder();
            for (int x = 0; x <= WIDE; x++) {
                heights.append(x == 0 ? "" : " ").append((x + y) % 3);
            }
            relief.add(heights.toString());
        }
        var palette = new LinkedHashMap<String, String>();
        palette.put("g", "textures/ground/grass.png");
        palette.put("s", "textures/ground/sand.png");
        palette.put("r", "#806040");
        return new PaintedGroundTest.Field("blended", 10f, cells, relief, paint, palette, Map.of("g", 4f));
    }

    private static List<Geometry> named(Node root, String name) {
        return root.descendantMatches(Geometry.class).stream().filter(g -> name.equals(g.getName())).toList();
    }

    private static TerrainScene blending(Node root, boolean chunked) {
        var scene = new TerrainScene(root, (colour, texture) -> null, false, null, null, chunked);
        scene.blendWith(Visuals.PaintBlend.SMOOTH);
        return scene;
    }

    @Test
    void everyCornerCarriesTheWeightsTheCoreRuleGivesIt() {
        var map = field();
        var grid = MapTerrain.of(map, 10f, 0f);
        var root = new Node("terrain");
        blending(root, false).rebuild(grid, null, GroundPaint.of(map));
        root.updateGeometricState();

        var meshes = named(root, "blended");
        assertEquals(6, meshes.size(), "a mesh a chunk of 16: three across, two down");
        assertTrue(named(root, "painted").isEmpty(), "and no mesh a surface");
        var keys = List.of("g", "s", "r");
        int checked = 0;
        for (var geometry : meshes) {
            var mesh = geometry.getMesh();
            var place = geometry.getWorldTranslation();
            var positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
            var first = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord2);
            var second = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord3);
            // which surfaces this chunk's slots are: those any of its corners shows, in palette order
            var slots = new ArrayList<Integer>();
            var shown = new boolean[3];
            for (int v = 0; v < mesh.getVertexCount(); v++) {
                int cx = Math.round((positions.get(v * 3) + place.x) / 10f);
                int cy = Math.round((positions.get(v * 3 + 2) + place.z) / 10f);
                var w = PaintWeights.at(map.paint(), keys, WIDE, DEEP, cx, cy);
                for (int s = 0; s < 3; s++) {
                    shown[s] |= w[s] > 0f;
                }
            }
            for (int s = 0; s < 3; s++) {
                if (shown[s]) {
                    slots.add(s);
                }
            }
            for (int v = 0; v < mesh.getVertexCount(); v++) {
                int cx = Math.round((positions.get(v * 3) + place.x) / 10f);
                int cy = Math.round((positions.get(v * 3 + 2) + place.z) / 10f);
                var w = PaintWeights.at(map.paint(), keys, WIDE, DEEP, cx, cy);
                for (int slot = 0; slot < 8; slot++) {
                    float carried = slot < 4 ? first.get(v * 4 + slot) : second.get(v * 4 + slot - 4);
                    float expected = slot < slots.size() ? w[slots.get(slot)] : 0f;
                    assertEquals(expected, carried, 1e-6f, "corner (" + cx + ", " + cy + ") slot " + slot);
                }
                checked++;
            }
        }
        assertEquals(WIDE * DEEP * 4, checked, "four corners a cell, every cell");
    }

    @Test
    void builtAChunkAtATimeItIsWhatTheWholeBuildMakes() {
        var map = field();
        var grid = MapTerrain.of(map, 10f, 0f);
        var whole = new Node("whole");
        blending(whole, false).rebuild(grid, null, GroundPaint.of(map));
        var chunked = new Node("chunked");
        var streamed = blending(chunked, true);
        streamed.streamWithin(1000);
        streamed.rebuild(grid, null, GroundPaint.of(map));
        for (int frame = 0; frame < 30; frame++) {
            streamed.stream(200f, 100f);
        }
        whole.updateGeometricState();
        chunked.updateGeometricState();

        assertEquals(weightsAndCorners(whole), weightsAndCorners(chunked));
    }

    private static List<String> weightsAndCorners(Node root) {
        var all = new ArrayList<String>();
        for (var geometry : named(root, "blended")) {
            var place = geometry.getWorldTranslation();
            var mesh = geometry.getMesh();
            var p = mesh.getFloatBuffer(VertexBuffer.Type.Position);
            var a = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord2);
            var b = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord3);
            for (int v = 0; v < mesh.getVertexCount(); v++) {
                all.add(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f",
                        p.get(v * 3) + place.x, p.get(v * 3 + 1), p.get(v * 3 + 2) + place.z,
                        a.get(v * 4), a.get(v * 4 + 1), a.get(v * 4 + 2), a.get(v * 4 + 3), b.get(v * 4)));
            }
        }
        all.sort(null);
        return all;
    }

    @Test
    void aMapThatWritesItsOwnBlendsKeepsThem() {
        var shore = PaintedGroundTest.shoreForBlending();
        var root = new Node("terrain");
        blending(root, false).rebuild(MapTerrain.of(shore, 10f, 0f), null, GroundPaint.of(shore));

        assertTrue(named(root, "blended").isEmpty(), "its @Overlay and @Fade, as written");
        assertTrue(!named(root, "painted").isEmpty() && !named(root, "overlay").isEmpty());
    }

    /** A surface is drawn by the look the game gave its palette value: tinted, and varied by the blend's picture. */
    @Test
    void aSurfacesLookTintsItsPictureAndSaysHowMuchItIsVaried() {
        var visuals = Visuals.create().surface("textures/ground/grass.png", new Visuals.SurfaceLook(0x80FF00, 0.6f));

        var tinted = GroundLooks.tinted(com.jme3.math.ColorRGBA.White, visuals.getSurface("textures/ground/grass.png"));
        assertEquals(new com.jme3.math.ColorRGBA(0x80 / 255f, 1f, 0f, 1f), tinted);
        assertEquals(0.6f, visuals.getSurface("textures/ground/grass.png").variation());
        assertEquals(Visuals.SurfaceLook.PLAIN, visuals.getSurface("textures/ground/sand.png"), "never named: plain");
        assertEquals(com.jme3.math.ColorRGBA.White,
                GroundLooks.tinted(com.jme3.math.ColorRGBA.White, Visuals.SurfaceLook.PLAIN));
        assertEquals(null, new Visuals.PaintBlend("noise.png", 0.3f, 2f, 8f).variation(), "borders alone: none");
    }

    /** A blended map's stone is its relief and its cliffs: its # cells draw nothing; a plain map keeps its blocks. */
    @Test
    void aBlendedMapsStoneDrawsNoBlocks() {
        var map = field();
        var blended = new Node("blended");
        blending(blended, false).rebuild(MapTerrain.of(map, 10f, 0f), null, GroundPaint.of(map));
        assertTrue(named(blended, "rock").isEmpty());

        var plain = new Node("plain");
        new TerrainScene(plain, (colour, texture) -> null)
                .rebuild(MapTerrain.of(map, 10f, 0f), null, GroundPaint.of(map));
        assertTrue(!named(plain, "rock").isEmpty(), "this map has stone, or the check above proves nothing");
    }

    /**
     * Cut in quarters, blended ground has four times the corners, every corner and every triangle's middle on the
     * relief the pathfinder walks — the ground drawn is the ground walked — lit by the relief round it, and a cell's
     * middle carrying the average of its corners' weights.
     */
    @Test
    void cutInQuartersEveryCornerAndEveryTriangleLiesOnTheReliefAndIsLitSmoothly() {
        var map = field();
        var grid = MapTerrain.of(map, 10f, 0f);
        var whole = new Node("whole");
        blending(whole, false).rebuild(grid, null, GroundPaint.of(map));
        var quartered = new Node("quartered");
        var scene = blending(quartered, false);
        scene.quarterWith(true);
        scene.rebuild(grid, null, GroundPaint.of(map));
        whole.updateGeometricState();
        quartered.updateGeometricState();

        int plain = named(whole, "blended").stream().mapToInt(g -> g.getMesh().getVertexCount()).sum();
        int cut = named(quartered, "blended").stream().mapToInt(g -> g.getMesh().getVertexCount()).sum();
        assertEquals(4 * plain, cut);
        var keys = List.of("g", "s", "r");
        boolean middleChecked = false;
        for (var geometry : named(quartered, "blended")) {
            var place = geometry.getWorldTranslation();
            var mesh = geometry.getMesh();
            var p = mesh.getFloatBuffer(VertexBuffer.Type.Position);
            var n = mesh.getFloatBuffer(VertexBuffer.Type.Normal);
            var w = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord2);
            var idx = mesh.getIndexBuffer();
            for (int v = 0; v < mesh.getVertexCount(); v++) {
                float x = p.get(v * 3) + place.x;
                float z = p.get(v * 3 + 2) + place.z;
                assertEquals(grid.reliefHeight(new uz.dukeengine.core.math.Coord3D(x, z, 0f)), p.get(v * 3 + 1), 1e-4f);
                var smooth = TerrainScene.smoothNormal(grid, x, z);
                assertEquals(smooth.y, n.get(v * 3 + 1), 1e-5f, "lit by the relief round it");
                if (!middleChecked && Math.abs(x % 10f - 5f) < 1e-3f && Math.abs(z % 10f - 5f) < 1e-3f) {
                    int cx = (int) (x / 10f);
                    int cy = (int) (z / 10f);
                    float average = 0f;
                    for (int[] c : new int[][] {{cx, cy}, {cx + 1, cy}, {cx + 1, cy + 1}, {cx, cy + 1}}) {
                        average += PaintWeights.at(map.paint(), keys, WIDE, DEEP, c[0], c[1])[0] / 4f;
                    }
                    if (geometry.getMesh().getFloatBuffer(VertexBuffer.Type.TexCoord2) != null && average > 0f) {
                        // slot 0 is the chunk's first surface; grass, where any corner of this cell shows it
                        assertEquals(average, w.get(v * 4), 1e-5f, "the middle: its corners' average");
                        middleChecked = true;
                    }
                }
            }
            for (int t = 0; t + 2 < idx.size(); t += 3) {
                int a = idx.get(t);
                int b = idx.get(t + 1);
                int c = idx.get(t + 2);
                float x = (p.get(a * 3) + p.get(b * 3) + p.get(c * 3)) / 3f + place.x;
                float z = (p.get(a * 3 + 2) + p.get(b * 3 + 2) + p.get(c * 3 + 2)) / 3f + place.z;
                float y = (p.get(a * 3 + 1) + p.get(b * 3 + 1) + p.get(c * 3 + 1)) / 3f;
                // the relief answers a point in 256ths of a cell, in whole numbers: a middle is within its rounding
                assertEquals(grid.reliefHeight(new uz.dukeengine.core.math.Coord3D(x, z, 0f)), y, 0.02f,
                        "a triangle in one of the relief's own");
            }
        }
        assertTrue(middleChecked, "a cell middle with grass round it was found");
    }
}
