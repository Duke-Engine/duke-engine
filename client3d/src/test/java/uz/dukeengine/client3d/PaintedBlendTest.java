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
}
