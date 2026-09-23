package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.Grid;
import uz.dukeengine.core.data.Paint;
import uz.dukeengine.core.data.Relief;
import uz.dukeengine.core.map.MapTemplate;
import uz.dukeengine.core.map.MapTerrain;
import uz.dukeengine.core.map.Painted;
import uz.dukeengine.core.map.Scaled;

/**
 * Painted ground, from a map record written here and nowhere else.
 *
 * <p>No game's data is involved on purpose. The engine's claim is that any game's map record can carry
 * paint, and a test that borrowed the dungeon's or the skirmish's would only prove that one of them can.
 */
class PaintedGroundTest {

    /** A field with a road down it, grass either side, and a pond that is a colour rather than a picture. */
    record Field(String name, float cellSize,
            @Grid List<String> cells,
            @Relief List<String> relief,
            @Paint List<String> paint,
            Map<String, String> palette,
            Map<String, Float> coverage)
            implements MapTemplate, Scaled, Painted {
    }

    private static Map<String, String> threeSurfaces() {
        var palette = new LinkedHashMap<String, String>();
        palette.put("g", "textures/ground/grass.png");
        palette.put("r", "textures/ground/road.png");
        palette.put("w", "#1E3A5F"); // a colour, for a game that ships no art
        return palette;
    }

    /** Four cells by three: grass, a road down the middle, water in the last column. */
    private static Field field() {
        return new Field("field", 10f,
                List.of("....",
                        "....",
                        "...."),
                List.of("0 0 0 0 0",
                        "0 1 1 0 0",
                        "0 1 2 1 0",
                        "0 0 1 0 0"),
                List.of("grrw",
                        "grrw",
                        "ggrw"),
                threeSurfaces(),
                Map.of("g", 4f)); // one copy of the grass covers four cells
    }

    private static Node drawn(Object map) {
        var root = new Node("terrain");
        new TerrainScene(root, (colour, texture) -> null)
                .rebuild(MapTerrain.of(map, 10f, 0f), null, GroundPaint.of(map));
        return root;
    }

    private static List<Geometry> painted(Node root) {
        return root.getChildren().stream()
                .filter(Geometry.class::isInstance).map(Geometry.class::cast)
                .filter(geometry -> "painted".equals(geometry.getName()))
                .toList();
    }

    @Test
    void oneMeshForEachSurfaceTheMapUsesRatherThanOneForEachCell() {
        var root = drawn(field());
        var meshes = painted(root);

        assertEquals(3, meshes.size(), "three palette entries in use, three meshes");
        assertEquals(0, root.getChildren().stream()
                .filter(child -> "ground".equals(child.getName())).count(),
                "and no flat quad underneath them");

        // 12 cells in all, 4 vertices each, spread over the three surfaces by how many cells they paint.
        int vertices = meshes.stream()
                .mapToInt(mesh -> mesh.getMesh().getVertexCount()).sum();
        assertEquals(12 * 4, vertices);
        assertEquals(12 * 2, meshes.stream().mapToInt(mesh -> mesh.getMesh().getTriangleCount()).sum());
    }

    /**
     * The detail that decides whether it reads as ground or as a chequerboard. A picture laid 0..1 inside
     * every cell shows the whole of itself in each one and draws the grid with its own edges.
     */
    @Test
    void theTextureRunsAcrossTheWorldAndAtTheSizeTheMapAsksFor() {
        var meshes = painted(drawn(field()));
        // The grass: the first palette entry, and the one the map says covers four cells.
        var uvs = meshes.getFirst().getMesh().getFloatBuffer(VertexBuffer.Type.TexCoord);

        // Cell (0,0) is grass. Four cells of 10 world units is 40, so its far corner is a quarter across.
        assertEquals(0f, uvs.get(0), 1e-5f);
        assertEquals(0f, uvs.get(1), 1e-5f);
        assertEquals(10f / 40f, uvs.get(2), 1e-5f, "one cell is a quarter of one copy of the grass");

        // The road covers one cell, the default, so its texture coordinates step by a whole unit a cell.
        var road = meshes.get(1).getMesh().getFloatBuffer(VertexBuffer.Type.TexCoord);
        assertEquals(1f, Math.abs(road.get(2) - road.get(0)), 1e-5f);
    }

    /** The ground follows the relief the map already supplies — the same corners the pathfinder walks. */
    @Test
    void everyCornerStandsOnTheReliefUnderIt() {
        var grid = MapTerrain.of(field(), 10f, 0f);
        var positions = painted(drawn(field())).getFirst().getMesh()
                .getFloatBuffer(VertexBuffer.Type.Position);

        boolean lifted = false;
        for (int i = 0; i + 2 < positions.limit(); i += 3) {
            float expected = grid.reliefHeight(
                    new uz.dukeengine.core.math.Coord3D(positions.get(i), positions.get(i + 2), 0f));
            assertEquals(expected, positions.get(i + 1), 1e-4f, "corner " + i / 3);
            lifted |= positions.get(i + 1) != 0f;
        }
        assertTrue(lifted, "and this map's relief is not flat, or the check above proves nothing");
    }

    /** A colour is a surface too, so a game that ships no textures gets painted ground for nothing. */
    @Test
    void aPaletteEntryMayBeAColourInsteadOfAPicture() {
        assertEquals(new com.jme3.math.ColorRGBA(0x1E / 255f, 0x3A / 255f, 0x5F / 255f, 1f),
                GroundPaint.colourOf("#1E3A5F"));
        assertEquals(GroundPaint.colourOf("#1E3A5F"), GroundPaint.colourOf("#1e3a5f"), "either case");
        assertEquals(GroundPaint.colourOf("#FFCC00"), GroundPaint.colourOf("#FC0"), "and the short way");
        assertNull(GroundPaint.colourOf("textures/ground/grass.png"), "a path is not a colour");
        assertNull(GroundPaint.colourOf("#nothex"));

        var asked = new java.util.ArrayList<String>();
        var root = new Node("terrain");
        new TerrainScene(root, (colour, texture) -> {
            asked.add(texture == null ? "colour " + colour : texture);
            return null;
        }).rebuild(MapTerrain.of(field(), 10f, 0f), null, GroundPaint.of(field()));

        assertTrue(asked.contains("textures/ground/grass.png"), "the path exactly as the map wrote it");
        assertTrue(asked.stream().anyMatch(what -> what.startsWith("colour ")),
                "and the water asked for no picture at all: " + asked);
    }

    /** A map with no paint is the ground it always was: one quad, one colour, nothing else changed. */
    @Test
    void aMapThatSaysNothingOfPaintIsDrawnExactlyAsBefore() {
        record Bare(String name, @Grid List<String> cells) implements MapTemplate {
        }
        var bare = new Bare("bare", List.of("....", "....", "...."));
        assertNull(GroundPaint.of(bare), "nothing to read");

        var root = new Node("terrain");
        new TerrainScene(root, (colour, texture) -> null)
                .rebuild(MapTerrain.of(bare, 10f, 0f), null, GroundPaint.of(bare));

        assertTrue(painted(root).isEmpty());
        var quad = root.getChild("ground");
        assertNotNull(quad, "the flat ground quad, as it has always been");
        var shape = (com.jme3.scene.shape.Quad) ((Geometry) quad).getMesh();
        assertEquals(40f, shape.getWidth(), "four cells of ten");
        assertEquals(30f, shape.getHeight(), "by three");
    }

    /**
     * A map is a file somebody edits by hand, so what is wrong with it is said and the rest is drawn.
     * A row of the wrong length, and a character the palette forgot.
     */
    @Test
    void aRaggedRowOrAnUnnamedCharacterIsReportedAndTheRestIsStillDrawn() {
        record Broken(String name, @Grid List<String> cells, @Paint List<String> paint,
                Map<String, String> palette) implements MapTemplate, Painted {
        }
        var broken = new Broken("broken",
                List.of("....", "....", "...."),
                List.of("ggrr",
                        "gg",          // two cells short
                        "ggxx"),       // 'x' is in no palette
                threeSurfaces());

        var said = new java.util.ArrayList<String>();
        var listening = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) {
                said.add(java.text.MessageFormat.format(record.getMessage(), record.getParameters()));
            }

            @Override public void flush() {
            }

            @Override public void close() {
            }
        };
        var log = java.util.logging.Logger.getLogger(GroundPaint.class.getName());
        log.addHandler(listening);
        List<GroundPaint.Patch> patches;
        try {
            patches = GroundPaint.of(broken).patches(4, 3);
        } finally {
            log.removeHandler(listening);
        }

        assertTrue(said.stream().anyMatch(what -> what.contains("broken") && what.contains("row 2")),
                "which map and which row: " + said);
        assertTrue(said.stream().anyMatch(what -> what.contains("broken") && what.contains("'x'")),
                "which map and which character: " + said);

        // And it still draws: 6 grass, 2 road, the two 'x' cells left out and nothing thrown.
        assertEquals(2, patches.size(), "grass and road; nothing was painted with the water");
        assertEquals(6, patches.getFirst().cells().length);
        assertEquals(2, patches.get(1).cells().length);
        assertFalse(painted(drawn(broken)).isEmpty());
    }

    /**
     * A kit beats paint. A dungeon's floors are models with a look of their own, and painting over them
     * would draw the ground twice at the same height.
     */
    @Test
    void aKitOfFloorModelsWinsOverPaint() {
        var root = new Node("terrain");
        var kit = Tileset.create().floor("floor").tileSize(4f);
        new TerrainScene(root, (colour, texture) -> null, false, kit, new SquareTiles())
                .rebuild(MapTerrain.of(field(), 10f, 0f), kit, GroundPaint.of(field()));

        assertTrue(painted(root).isEmpty(), "the kit laid the floor; the paint was not drawn over it");
        assertFalse(root.getChildren().isEmpty(), "and something was laid");
    }

    /** A flat square tile with a mesh in it, so a kit has something to lay. */
    private static final class SquareTiles implements TileSource {
        @Override
        public com.jme3.scene.Spatial piece(String assetPath) {
            var node = new Node(assetPath);
            var tile = new Geometry("tile", new com.jme3.scene.shape.Quad(4f, 4f));
            tile.rotate(-com.jme3.math.FastMath.HALF_PI, 0f, 0f);
            node.attachChild(tile);
            return node;
        }
    }
}
