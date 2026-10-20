package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/**
 * A painted world built a chunk at a time round the camera, as E10 builds a kit's floor: each chunk, corner for corner,
 * what the whole build makes, and a chunk far off let go.
 */
class PaintedChunksTest {

    private static final int WIDE = 80;
    private static final int DEEP = 24;

    /** Grass and sand meeting along a ragged line, a road, a few stones, rolling relief, a fade where sand meets. */
    private static PaintedGroundTest.Shore world() {
        var cells = new ArrayList<String>();
        var paint = new ArrayList<String>();
        var overlay = new ArrayList<String>();
        var fade = new ArrayList<String>();
        for (int y = 0; y < DEEP; y++) {
            var row = new StringBuilder();
            var painted = new StringBuilder();
            var over = new StringBuilder();
            var faded = new StringBuilder();
            for (int x = 0; x < WIDE; x++) {
                row.append((x * 7 + y * 3) % 23 == 0 ? '#' : '.');
                int shore = 18 + (y * 5) % 7;
                painted.append(x == 11 ? 'r' : x < shore ? 'g' : 's');
                over.append(x == shore ? 's' : '.');
                faded.append(x == shore ? '4' : '.');
            }
            cells.add(row.toString());
            paint.add(painted.toString());
            overlay.add(over.toString());
            fade.add(faded.toString());
        }
        var relief = new ArrayList<String>();
        for (int y = 0; y <= DEEP; y++) {
            var heights = new StringBuilder();
            for (int x = 0; x <= WIDE; x++) {
                heights.append(x == 0 ? "" : " ").append((x * 3 + y * 2) % 5);
            }
            relief.add(heights.toString());
        }
        var palette = new LinkedHashMap<String, String>();
        palette.put("g", "textures/ground/grass.png");
        palette.put("s", "textures/ground/sand.png");
        palette.put("r", "#806040");
        return new PaintedGroundTest.Shore("world", 10f, cells, relief, paint, overlay, fade, palette,
                Map.of("g", 4f));
    }

    /** Every corner of every painted mesh, overlay and stone, in world terms, as text, sorted. */
    private static List<String> corners(Node root) {
        var all = new ArrayList<String>();
        for (var geometry : root.descendantMatches(Geometry.class)) {
            var place = geometry.getWorldTranslation();
            var mesh = geometry.getMesh();
            var positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
            var uvs = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord);
            for (int vertex = 0; vertex < mesh.getVertexCount(); vertex++) {
                all.add(String.format(java.util.Locale.ROOT, "%s %.3f %.3f %.3f %s", geometry.getName(),
                        positions.get(vertex * 3) + place.x, positions.get(vertex * 3 + 1) + place.y,
                        positions.get(vertex * 3 + 2) + place.z,
                        uvs == null ? "" : String.format(java.util.Locale.ROOT, "%.4f %.4f", uvs.get(vertex * 2),
                                uvs.get(vertex * 2 + 1))));
            }
        }
        all.sort(null);
        return all;
    }

    private static TerrainScene scene(Node root, boolean chunked) {
        return new TerrainScene(root, (_, _) -> null, true, null, null, chunked);
    }

    @Test
    void builtAChunkAtATimeItIsCornerForCornerWhatTheWholeBuildMakes() {
        var map = world();
        var grid = MapTerrain.of(map, 10f, 0f);
        var whole = new Node("whole");
        scene(whole, false).rebuild(grid, null, GroundPaint.of(map));
        whole.updateGeometricState();

        var chunked = new Node("chunked");
        var streamed = scene(chunked, true);
        streamed.streamWithin(1000);
        streamed.rebuild(grid, null, GroundPaint.of(map));
        assertEquals(0, chunked.getQuantity(), "nothing built before the camera looks");
        for (int frame = 0; frame < 60; frame++) {
            streamed.stream(200f, 120f); // a frame's budget at a time, as the camera looks
        }
        chunked.updateGeometricState();

        assertTrue(streamed.isBuilt(0, 0) && streamed.isBuilt(4, 1), "every chunk within reach");
        var expected = corners(whole);
        assertFalse(expected.isEmpty());
        assertEquals(expected, corners(chunked));
    }

    @Test
    void onlyTheChunksRoundTheCameraAreBuiltAndThoseFarOffAreLetGo() {
        var map = world();
        var grid = MapTerrain.of(map, 10f, 0f);
        var root = new Node("chunked");
        var streamed = scene(root, true);
        streamed.streamWithin(1); // the camera's own chunk and one round it
        streamed.rebuild(grid, null, GroundPaint.of(map));

        streamed.stream(5f, 5f); // chunk (0, 0)
        assertTrue(streamed.isBuilt(0, 0) && streamed.isBuilt(1, 1));
        assertFalse(streamed.isBuilt(2, 0), "two chunks off: not yet");

        streamed.stream(795f, 235f); // chunk (4, 1)
        assertTrue(streamed.isBuilt(4, 1));
        assertFalse(streamed.isBuilt(0, 0), "let go once the camera is more than a chunk past it");
    }

    /** A game streams the maps it says: its painted world round the camera, its small floors whole. */
    @Test
    void theGameSaysWhichMapsAreBuiltRoundTheCamera() {
        var painted = world();
        var visuals = Visuals.create().streamGround(48, map -> map instanceof PaintedGroundTest.Shore);

        assertEquals(48, visuals.streamGroundFor(painted));
        assertEquals(0, visuals.streamGroundFor("a floor of the descent"), "built whole");
        assertEquals(48, Visuals.create().streamGround(48).streamGroundFor("any"), "one number streams every map");
        assertEquals(0, Visuals.create().streamGroundFor(painted), "and none, none");
    }
}
