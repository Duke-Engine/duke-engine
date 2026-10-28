package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.MapArea;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.core.view.UnitView;

/** A preset changed in the middle of a match takes away what it switches off, and gives it back. */
class DetailPresetTest {

    private static final String SHADOW = "Common/Textures/dot.png";
    private static final String CRATE = "Common/Textures/MissingTexture.png";
    private static final BiFunction<Float, Float, Float> FLAT = (_, _) -> 0f;

    private static final Visuals.UnitVisual SOLDIER = Visuals.create().unit("Soldier", look -> look
            .shadowDecal(SHADOW, 6f, 6f)
            .groundPicture(Set.of("CRATE"), CRATE, 6f, 6f, 0)).of("Soldier");

    private final GroundPictures pictures = new GroundPictures(new DesktopAssetManager(true), new Node("ground"));

    private void frame(Set<String> words) {
        pictures.see(1, SOLDIER, words, new Coord3D(0f, 0f, 0f), 0f, 1f, FLAT);
    }

    private List<String> laid() {
        return pictures.under(1).stream().map(GroundPictures.Shown::picture).toList();
    }

    @Test
    void aShadowAddedBeforeIsGoneWhenThePresetTurnsDecalsOffAndBackWhenItTurnsThemOn() {
        frame(Set.of());
        assertEquals(List.of(SHADOW), laid(), "decals are on until a preset says otherwise");

        pictures.shadowDecals(false);
        frame(Set.of());
        assertTrue(laid().isEmpty(), "the shadow that was laid is taken up");

        pictures.shadowDecals(true);
        frame(Set.of());
        assertEquals(List.of(SHADOW), laid());
    }

    @Test
    void aPictureThatIsNotAShadowStaysWhenDecalsAreOff() {
        pictures.shadowDecals(false);

        frame(Set.of("CRATE"));

        assertEquals(List.of(CRATE), laid(), "only the look's shadow decal is the preset's");
    }

    @Test
    void thePresetDefaultsToDrawingEverythingAsItAlwaysWas() {
        assertEquals(new DetailPreset(true, true, true, true, true), DetailPreset.ALL);
    }

    @Test
    void aScaffoldLayerIsTakenOffItsThingAndWornAgainWhenScaffoldsReturn() {
        var look = Visuals.create().unit("WarFactory", visual -> {
            visual.layer("scaffold").scaffold().model(Set.of("PARTIALLY_CONSTRUCTED"), "models/scaffold.glb");
            visual.layer("door").model("models/door.glb");
        }).of("WarFactory");
        var scaffold = new ModelLayer(look.layers.get("scaffold"), Node::new);
        var door = new ModelLayer(look.layers.get("door"), Node::new);
        var root = new Node("unit");
        var view = new UnitView(1, "WarFactory", 0, 0f, 0f, 0f, 100f, 100f, true, true, false, false, 0, 0f, 0f, 0f,
                false, 0, List.of(), List.of("PARTIALLY_CONSTRUCTED"));

        scaffold.wear(root, view, Set.of(), 0, _ -> { }, _ -> { });
        assertNotNull(scaffold.body(), "going up: its scaffold");
        assertTrue(scaffold.scaffold());
        assertTrue(!door.scaffold(), "a layer not marked is not one");

        scaffold.away();
        assertNull(scaffold.body());
        assertEquals(0, root.getQuantity(), "off the thing");

        scaffold.wear(root, view, Set.of(), 1, _ -> { }, _ -> { });
        assertNotNull(scaffold.body(), "worn anew once the preset allows it");
    }

    @Test
    void theWordsOfTheNightAreTakenOutOfTheWorldWhereEmissiveNightMaterialsAreOff() {
        var visuals = Visuals.create().world("SNOW", "NIGHT").nightWords("NIGHT");

        assertEquals(Set.of("NIGHT"), visuals.getNightWords());
        assertEquals(Set.of("SNOW"), UnitScene.without(visuals.getWorldConditions(), visuals.getNightWords()));
        assertTrue(Visuals.create().getNightWords().isEmpty(), "none unless the game names them");
    }

    private record Pond(String name, boolean water, float height, List<Float> points, boolean river, int riverStart)
            implements MapArea {
    }

    private static final DrawnGround FLOOR = new DrawnGround() {
        @Override
        public float cellSize() {
            return 10f;
        }

        @Override
        public int columns() {
            return 40;
        }

        @Override
        public int rows() {
            return 40;
        }

        @Override
        public float heightAt(float x, float z) {
            return 4.5f; // half a unit under a surface of 5
        }

        @Override
        public HeightMap.Diagonal diagonal(int cx, int cy) {
            return HeightMap.Diagonal.MAIN;
        }
    };

    @Test
    void waterIsClearAtTheShoreUntilThePresetHardensItsEdgeAndTheWaterIsLaidAgain() {
        var assets = new DesktopAssetManager(true);
        var node = new Node("water");
        var water = new Water(node, (_, _) -> new Material(assets, "MatDefs/duke/Water.j3md"));
        var look = new Visuals.WaterLook("textures/water.png", 150f, 0f, 0f, null, 0f, 0f, 0f, 0xB4AFAFAF, 3f, null,
                0f, null);
        var pond = new Pond("Pond", true, 8f, List.of(2f, 2f, 5f, 2f, 5f, 5f, 2f, 5f), false, 0);
        water.rebuild(List.of(pond), FLOOR, look);

        assertEquals(0.5f / 3f, alphaOf(node), 1e-4f, "half a unit deep of a fade of three: nearly clear");

        water.softEdge(false);
        assertEquals(1f, alphaOf(node), 1e-4f, "a hard edge: whole to the shore");
        assertEquals(1, node.getQuantity(), "laid again, not added to");

        water.softEdge(true);
        assertEquals(0.5f / 3f, alphaOf(node), 1e-4f);
    }

    private static float alphaOf(Node water) {
        var mesh = ((Geometry) water.getChild(0)).getMesh();
        return mesh.getFloatBuffer(VertexBuffer.Type.Color).get(3);
    }
}
