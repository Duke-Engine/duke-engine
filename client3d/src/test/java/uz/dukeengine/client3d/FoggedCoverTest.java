package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.instancing.InstancedGeometry;
import com.jme3.scene.shape.Box;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.MapTerrain;

/**
 * Ground cover grown on a map whose pieces are drawn fogged — the window's death the first chunk, once (E100): drawn
 * instanced in the fogged material, and a material that cannot be instanced drawn as it is.
 */
class FoggedCoverTest {

    private static final String GRASS = "textures/ground/grass.png";
    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    private static Node grow(String matDef) {
        var cells = new ArrayList<String>();
        var paint = new ArrayList<String>();
        var relief = new ArrayList<String>();
        for (int y = 0; y < 16; y++) {
            cells.add(".".repeat(32));
            paint.add("g".repeat(32));
        }
        for (int y = 0; y <= 16; y++) {
            relief.add(String.join(" ", java.util.Collections.nCopies(33, "0")));
        }
        var map = new PaintedGroundTest.Shore("meadow", 10f, cells, relief, paint, List.of(), List.of(),
                Map.of("g", GRASS), Map.of("g", 4f));
        TileSource tiles = path -> {
            var tuft = new Geometry("tuft", new Box(0.2f, 0.5f, 0.2f));
            tuft.setMaterial(new Material(ASSETS, matDef));
            var node = new Node(path);
            node.attachChild(tuft);
            return node;
        };
        var root = new Node("root");
        var scene = new TerrainScene(root, (colour, texture) -> null, true, null, tiles, true);
        scene.streamWithin(1000);
        scene.rebuild(MapTerrain.of(map, 10f, 0f), null, GroundPaint.of(map));
        scene.cover(new GroundCover(Map.of(GRASS, List.of(new GroundCover.Kind("models/grass.glb", 1, 1f, 0xFFFFFF))),
                Map.of(GRASS, 1.2f), 7L, null, 24f, 1f));
        for (int frame = 0; frame < 20; frame++) {
            scene.stream(50f, 50f);
        }
        return root;
    }

    @Test
    void coverOnAFoggedMapIsDrawnInstancedInTheFoggedMaterial() {
        var root = grow("MatDefs/duke/FoggedTerrain.j3md");
        assertFalse(root.descendantMatches(InstancedGeometry.class).isEmpty(), "grown, instanced, no exception");
    }

    @Test
    void aMaterialThatCannotBeInstancedIsDrawnAsItIs() {
        var root = grow("MatDefs/duke/Gamma.j3md");
        assertTrue(root.descendantMatches(InstancedGeometry.class).isEmpty());
        assertFalse(root.descendantMatches(Geometry.class).stream().filter(g -> g.getName().equals("tuft")).toList()
                .isEmpty(), "grown all the same, drawn plainly");
    }
}
