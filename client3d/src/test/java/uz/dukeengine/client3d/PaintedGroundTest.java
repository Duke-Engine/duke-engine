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
import uz.dukeengine.core.data.Flipped;
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

    // ---- the second layer ----

    /** Grass meeting sand down the middle, the boundary cells laying sand over the grass from its side. */
    record Shore(String name, float cellSize,
            @Grid List<String> cells,
            @Relief List<String> relief,
            @Paint List<String> paint,
            @uz.dukeengine.core.data.Overlay List<String> overlay,
            @uz.dukeengine.core.data.Fade List<String> fade,
            Map<String, String> palette,
            Map<String, Float> coverage)
            implements MapTemplate, Scaled, Painted {
    }

    private static Shore shore(List<String> fade) {
        var palette = new LinkedHashMap<String, String>();
        palette.put("g", "textures/ground/grass.png");
        palette.put("s", "textures/ground/sand.png");
        return new Shore("shore", 10f,
                List.of("....", "....", "...."),
                List.of("0 0 0 0 0", "0 1 2 1 0", "0 2 3 2 0", "0 0 1 0 0"),
                List.of("ggss", "ggss", "ggss"),
                List.of("..s.", "..s.", "..s."),
                fade,
                palette,
                Map.of("s", 2f));
    }

    private static List<Geometry> named(Node root, String name) {
        return root.getChildren().stream()
                .filter(Geometry.class::isInstance).map(Geometry.class::cast)
                .filter(geometry -> name.equals(geometry.getName()))
                .toList();
    }

    /** Where grass meets sand, the sand is laid over the grass and fades out across the cell. */
    @Test
    void anOverlayIsLaidOverTheCellsThatNameOne() {
        var map = shore(List.of("..1.", "..1.", "..1.")); // sand fading in from the right
        var root = drawn(map);
        var overlays = named(root, "overlay");

        assertEquals(1, overlays.size(), "one picture laid over, one mesh");
        var mesh = overlays.getFirst().getMesh();
        assertEquals(3 * 5, mesh.getVertexCount(), "three cells, four corners and a middle each");
        assertEquals(3 * 4, mesh.getTriangleCount(), "four triangles a cell, meeting at the middle");

        // From the right: none at the left edge, all at the right, half in the middle.
        var alpha = mesh.getFloatBuffer(VertexBuffer.Type.Color);
        assertEquals(0f, alpha.get(3), "top-left");
        assertEquals(1f, alpha.get(7), "top-right");
        assertEquals(1f, alpha.get(11), "bottom-right");
        assertEquals(0f, alpha.get(15), "bottom-left");
        assertEquals(0.5f, alpha.get(19), "the middle");
        assertEquals(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent,
                overlays.getFirst().getQueueBucket(), "drawn over the ground, after it");
    }

    /** Turning the overlay off in the data draws the step again: the ground alone, as it always was. */
    @Test
    void aMapWithNoSecondLayerBuildsNoneOfIt() {
        assertTrue(named(drawn(shore(List.of("....", "....", "...."))), "overlay").isEmpty(),
                "every fade says none");
        assertTrue(named(drawn(field()), "overlay").isEmpty(), "and a map with no fade rows at all");
    }

    /**
     * The picture laid over lines up with the same picture drawn as ground next door, rather than
     * starting again in every cell: across the world, at its own coverage.
     */
    @Test
    void anOverlayIsLaidAcrossTheWorldAtItsOwnSize() {
        var root = drawn(shore(List.of("..1.", "..1.", "..1.")));
        var uvs = named(root, "overlay").getFirst().getMesh().getFloatBuffer(VertexBuffer.Type.TexCoord);

        // The first overlay cell is column 2: x from 20 to 30, and one copy of the sand spans two cells.
        assertEquals(20f / 20f, uvs.get(0), 1e-5f);
        assertEquals(30f / 20f, uvs.get(2), 1e-5f);
    }

    /**
     * The overlay lies exactly on the ground: its middle is on the diagonal the ground is cut along, at the
     * height the ground has there — and that diagonal is the one the pathfinder cuts along too.
     */
    @Test
    void anOverlayLiesOnTheGroundAndTheGroundOnWhatIsWalked() {
        var map = shore(List.of("..6.", "..6.", "..6.")); // a corner, so the middle is not the corners' mean
        var grid = MapTerrain.of(map, 10f, 0f);
        var positions = named(drawn(map), "overlay").getFirst().getMesh()
                .getFloatBuffer(VertexBuffer.Type.Position);

        for (int vertex = 0; vertex < positions.limit() / 3; vertex++) {
            float x = positions.get(vertex * 3);
            float z = positions.get(vertex * 3 + 2);
            assertEquals(grid.reliefHeight(new uz.dukeengine.core.math.Coord3D(x, z, 0f)),
                    positions.get(vertex * 3 + 1), 1e-4f, "vertex " + vertex);
        }

        // And the ground under it is cut the way HeightMap is: corner 0 to corner 2, top-left to bottom-right.
        var indices = painted(drawn(map)).getFirst().getMesh().getIndexBuffer();
        assertEquals(0, indices.get(0));
        assertEquals(2, indices.get(2), "the first triangle ends on the bottom-right corner");
    }

    // ---- more than one layer ----

    /**
     * Where three kinds of ground meet: grass painted everywhere, sand laid over it as the first layer down
     * the middle two columns, and rock over both as the second down the right two. Column 2 carries both.
     */
    record Junction(String name, float cellSize,
            @Grid List<String> cells,
            @Paint List<String> paint,
            @uz.dukeengine.core.data.Overlay List<String> sand,
            @uz.dukeengine.core.data.Fade List<String> sandFade,
            @uz.dukeengine.core.data.Overlay List<String> rock,
            @uz.dukeengine.core.data.Fade List<String> rockFade,
            Map<String, String> palette)
            implements MapTemplate, Scaled, Painted {
    }

    private static Junction junction() {
        var palette = new LinkedHashMap<String, String>();
        palette.put("g", "textures/ground/grass.png");
        palette.put("s", "textures/ground/sand.png");
        palette.put("r", "textures/ground/rock.png");
        return new Junction("junction", 10f,
                List.of("....", "....", "...."),
                List.of("gggg", "gggg", "gggg"),
                List.of(".ss.", ".ss.", ".ss."),
                List.of(".22.", ".22.", ".22."),
                List.of("..rr", "..rr", "..rr"),
                List.of("..11", "..11", "..11"),
                palette);
    }

    /**
     * Two layers are two meshes, and where a cell carries both the second is drawn over the first — checked in
     * the order jME's own queue draws them, through the order the client installs on its view, from a camera
     * that would have put them the other way round.
     */
    @Test
    void aSecondLayerIsASecondMeshDrawnOverTheFirst() {
        var root = drawn(junction());
        var overlays = named(root, "overlay");
        assertEquals(2, overlays.size(), "one picture a layer, one mesh each");
        var sand = overlays.getFirst();
        var rock = overlays.get(1);
        assertEquals(0, (int) sand.getUserData(OverlayOrder.LAYER));
        assertEquals(1, (int) rock.getUserData(OverlayOrder.LAYER));
        assertEquals(6 * 5, sand.getMesh().getVertexCount(), "two columns of three cells");
        assertEquals(20f, rock.getMesh().getFloatBuffer(VertexBuffer.Type.Position).get(0),
                "and the rock's first cell is column 2, the one the sand covers too");

        // Something else see-through, beyond both: a spark hanging far over the map.
        var spark = new Geometry("spark", new com.jme3.scene.shape.Quad(1f, 1f));
        spark.setLocalTranslation(400f, 5f, 15f);
        spark.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
        root.attachChild(spark);
        root.updateGeometricState();

        // Standing off the sand's side of the map, the rock's mesh is the farther of the two.
        var camera = new com.jme3.renderer.Camera(640, 480);
        camera.setLocation(new com.jme3.math.Vector3f(-200f, 50f, 15f));

        assertEquals(List.of(spark, rock, sand), drawnInOrder(new com.jme3.renderer.ViewPort("jME's own", camera),
                camera, sand, rock, spark),
                "back to front by distance alone: the rock laid first, under the sand, and the spark painted over");

        var view = new com.jme3.renderer.ViewPort("the client's", camera);
        OverlayOrder.install(view);
        assertEquals(List.of(sand, rock, spark), drawnInOrder(view, camera, spark, rock, sand),
                "the layers in their order whatever the camera, and everything else after the ground");
    }

    /** What a view's transparent bucket draws, in the order it draws it: jME's queue, with no GPU behind it. */
    private static List<Geometry> drawnInOrder(com.jme3.renderer.ViewPort view, com.jme3.renderer.Camera camera,
            Geometry... queued) {
        var bucket = com.jme3.renderer.queue.RenderQueue.Bucket.Transparent;
        for (var geometry : queued) {
            view.getQueue().addToQueue(geometry, bucket);
        }
        var drawn = new java.util.ArrayList<Geometry>();
        var renderer = new com.jme3.renderer.RenderManager(new com.jme3.system.NullRenderer()) {
            @Override
            public void renderGeometry(Geometry geometry) {
                drawn.add(geometry);
            }
        };
        view.getQueue().renderQueue(bucket, renderer, camera, true);
        return drawn;
    }

    /** A map of one pair is drawn as it always was: one mesh, the first layer, and nothing said about it. */
    @Test
    void aMapOfOneLayerIsDrawnAsBefore() {
        var said = new java.util.ArrayList<String>();
        var shore = shore(List.of("..1.", "..1.", "..1."));
        List<Geometry> overlays = listening(said, () -> named(drawn(shore), "overlay"));

        assertEquals(1, overlays.size());
        assertEquals(0, (int) overlays.getFirst().getUserData(OverlayOrder.LAYER));
        assertEquals(3 * 5, overlays.getFirst().getMesh().getVertexCount());
        assertTrue(said.isEmpty(), "a whole pair is nothing to remark on: " + said);
    }

    /**
     * Two {@code @Overlay} and one {@code @Fade}: said once, naming the map, and the pair that is whole is still
     * drawn — the first overlay with the only fade.
     */
    @Test
    void aMapWithMoreOverlaysThanFadesSaysSoAndDrawsThePairThatIsWhole() {
        record Lopsided(String name, float cellSize,
                @Grid List<String> cells,
                @Paint List<String> paint,
                @uz.dukeengine.core.data.Overlay List<String> sand,
                @uz.dukeengine.core.data.Fade List<String> sandFade,
                @uz.dukeengine.core.data.Overlay List<String> rock,
                Map<String, String> palette)
                implements MapTemplate, Scaled, Painted {
        }
        var junction = junction();
        var lopsided = new Lopsided("lopsided", 10f, junction.cells(), junction.paint(), junction.sand(),
                junction.sandFade(), junction.rock(), junction.palette());

        var said = new java.util.ArrayList<String>();
        List<Geometry> overlays = listening(said, () -> named(drawn(lopsided), "overlay"));

        assertEquals(1, said.size(), "once: " + said);
        assertTrue(said.getFirst().contains("'lopsided'"), "naming the map: " + said);
        assertEquals(1, overlays.size(), "and the whole pair is drawn");
        assertEquals(10f, overlays.getFirst().getMesh().getFloatBuffer(VertexBuffer.Type.Position).get(0),
                "the sand, from column 1");
    }

    /** Runs {@code work} with every warning {@link GroundPaint} gives written into {@code said}. */
    private static <T> T listening(List<String> said, java.util.function.Supplier<T> work) {
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
        try {
            return work.get();
        } finally {
            log.removeHandler(listening);
        }
    }

    /** The mask is the engine's: five strengths a shape, and the reversed eight are one minus the rest. */
    @Test
    void theSixteenFadesAreExactRampsAndTheirReverses() {
        assertEquals(List.of(1f, 0f, 0f, 1f, 0.5f), strengths(0), "from the left");
        assertEquals(List.of(1f, 0f, 0f, 0f, 0f), strengths(4), "the top-left corner's triangle");
        assertEquals(List.of(0f, 1f, 1f, 1f, 1f), strengths(12), "and everything but it");
        for (int shape = 0; shape < 8; shape++) {
            var plain = strengths(shape);
            var reversed = strengths(shape + 8);
            for (int at = 0; at < 5; at++) {
                assertEquals(1f, plain.get(at) + reversed.get(at), 1e-6f, "shape " + shape + " at " + at);
            }
        }
        assertEquals(10, FadeShape.of('A'));
        assertEquals(10, FadeShape.of('a'), "either case");
        assertEquals(FadeShape.NONE, FadeShape.of('.'));
        assertEquals(FadeShape.UNREADABLE, FadeShape.of('x'));
    }

    private static List<Float> strengths(int shape) {
        var five = FadeShape.strengths(shape);
        return List.of(five[0], five[1], five[2], five[3], five[4]);
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

    /** One cell whose far corner stands a whole cell high, turned or not by its map. */
    record Turned(String name, float cellSize,
            @Grid List<String> cells,
            @Relief List<String> relief,
            @Paint List<String> paint,
            @Flipped List<String> flipped,
            Map<String, String> palette,
            Map<String, Float> coverage)
            implements MapTemplate, Scaled, Painted {
    }

    private static Turned turned(String flag) {
        return new Turned("turned", 10f, List.of("."), List.of("0 16", "0 0"), List.of("g"), List.of(flag),
                Map.of("g", "#3A5F2B"), Map.of());
    }

    private static int[] indicesOf(Object map) {
        var indices = painted(drawn(map)).getFirst().getMesh().getIndexBuffer();
        var read = new int[indices.size()];
        for (int i = 0; i < read.length; i++) {
            read[i] = indices.get(i);
        }
        return read;
    }

    /**
     * A cell of corners 0, 16, 0, 0 its map turns draws its two triangles along the other diagonal, while the height
     * at its middle still reads the relief's — as the reference draws some cells cut the other way and keeps its
     * heights on one.
     */
    @Test
    void aTurnedCellIsDrawnAlongTheOtherDiagonalAndItsHeightsKeepTheReliefs() {
        assertEquals(List.of(0, 3, 1, 1, 3, 2), java.util.Arrays.stream(indicesOf(turned("1"))).boxed().toList(),
                "drawn cut from the high corner to the far one");
        assertEquals(List.of(0, 3, 2, 0, 2, 1), java.util.Arrays.stream(indicesOf(turned("."))).boxed().toList(),
                "unturned, drawn exactly as before");

        var grid = MapTerrain.of(turned("1"), 10f, 0f);
        assertEquals(0f, grid.reliefHeight(new uz.dukeengine.core.math.Coord3D(5f, 5f, 0f)), 1e-6f,
                "the middle's height on the relief's own diagonal, which the drawing does not change");
    }
}
