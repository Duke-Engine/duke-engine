package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.instancing.InstancedGeometry;
import com.jme3.scene.shape.Box;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.MapScenery;
import uz.dukeengine.core.map.MapTerrain;

/**
 * A painted world's scenery asked to be drawn instanced ({@link Visuals#instancedScenery}): a chunk's pieces of a model one
 * draw, their far meshes another, shown by how far the chunk is from the camera, and the sun's casters named by model.
 */
class InstancedSceneryTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);
    private static final int WIDE = 96;

    private record Tree(String model, float x, float y) implements MapScenery {
    }

    private final List<Material> told = new ArrayList<>();

    private Node world(boolean instanced) {
        var cells = new ArrayList<String>();
        var paint = new ArrayList<String>();
        var relief = new ArrayList<String>();
        for (int y = 0; y < 16; y++) {
            cells.add(".".repeat(WIDE));
            paint.add("g".repeat(WIDE));
        }
        for (int y = 0; y <= 16; y++) {
            relief.add(String.join(" ", Collections.nCopies(WIDE + 1, "0")));
        }
        var map = new PaintedGroundTest.Shore("wood", 10f, cells, relief, paint, List.of(), List.of(),
                Map.of("g", "textures/ground/grass.png"), Map.of("g", 4f));
        var masters = new java.util.HashMap<String, Node>();
        // As KitTiles hands them out: every copy of a model shares its mesh and its material.
        TileSource tiles = path -> masters.computeIfAbsent(path, _ -> {
            var trunk = new Geometry(path, new Box(0.2f, 0.5f, 0.2f));
            trunk.setMaterial(new Material(ASSETS, "MatDefs/duke/FoggedTerrain.j3md"));
            var node = new Node(path);
            node.attachChild(trunk);
            return node;
        }).clone(false);
        var root = new Node("root");
        var scene = new TerrainScene(root, (_, _) -> null, true, null, tiles, true);
        scene.onMadeMaterial(told::add);
        scene.streamWithin(WIDE * 2);
        if (instanced) {
            scene.instanceScenery(model -> model.equals("tree.glb") ? "tree_far.glb" : null, model -> !model.equals("bush.glb"));
        }
        var pieces = new ArrayList<MapScenery>();
        for (int i = 0; i < 6; i++) {
            pieces.add(new Tree("tree.glb", 1.5f + i, 2.5f)); // in the chunk the camera is over
            pieces.add(new Tree("tree.glb", 80.5f + i, 2.5f)); // five chunks away
        }
        pieces.add(new Tree("bush.glb", 3.5f, 5.5f));
        scene.rebuild(MapTerrain.of(map, 10f, 0f), null, GroundPaint.of(map), null, null, pieces);
        for (int frame = 0; frame < 40; frame++) {
            scene.stream(30f, 30f);
        }
        return root;
    }

    private static List<InstancedGeometry> draws(Node root, String node) {
        var out = new ArrayList<InstancedGeometry>();
        for (var draw : root.descendantMatches(InstancedGeometry.class)) {
            if (node.equals(draw.getParent().getName())) {
                out.add(draw);
            }
        }
        return out;
    }

    @Test
    void aChunksPiecesOfAModelAreOneDrawTheirFarMeshesAnother() {
        var root = world(true);
        var near = draws(root, "scenery near");
        var far = draws(root, "scenery far");
        assertEquals(2, near.size(), "the two chunks with trees in them, a draw each");
        assertEquals(2, far.size());
        assertEquals(6, near.getFirst().getNumInstances());
        assertEquals(1, draws(root, "scenery").size(), "a bush has no far mesh: it is drawn near and far alike");
        assertTrue(told.contains(near.getFirst().getMaterial()), "its material told to who keeps the fog up to date");
        assertEquals(Boolean.TRUE, near.getFirst().getMaterial().getParam("UseInstancing").getValue());
    }

    @Test
    void theModelItselfNearTheCameraItsFarMeshAwayFromIt() {
        var root = world(true);
        for (var draw : draws(root, "scenery near")) {
            boolean close = draw.getWorldBound().getCenter().x < 160f;
            assertEquals(close ? Spatial.CullHint.Inherit : Spatial.CullHint.Always, draw.getParent().getLocalCullHint());
        }
        for (var draw : draws(root, "scenery far")) {
            boolean close = draw.getWorldBound().getCenter().x < 160f;
            assertEquals(close ? Spatial.CullHint.Always : Spatial.CullHint.Inherit, draw.getParent().getLocalCullHint());
        }
    }

    @Test
    void whatCastsTheSunsShadowIsNamedByModel() {
        var root = world(true);
        assertTrue(draws(root, "scenery near").stream()
                .allMatch(draw -> draw.getShadowMode() == com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive));
        assertTrue(draws(root, "scenery").stream()
                .noneMatch(draw -> draw.getShadowMode() == com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive));
    }

    @Test
    void notAskedItIsDrawnAPieceAtATimeAsEver() {
        var root = world(false);
        assertTrue(root.descendantMatches(InstancedGeometry.class).isEmpty());
        assertFalse(root.descendantMatches(Geometry.class).isEmpty());
    }
}
