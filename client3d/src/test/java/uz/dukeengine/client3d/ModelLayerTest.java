package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.dukeengine.client3d.Visuals.ClipMode.HOLD;
import static uz.dukeengine.client3d.Visuals.ClipMode.ONCE;
import static uz.dukeengine.client3d.Visuals.ClipStart.FIRST;
import static uz.dukeengine.client3d.Visuals.ClipStart.LAST;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.UnitView;

/** A thing drawn by more than one model: each chosen, shown and moved by its words on its own. */
class ModelLayerTest {

    /** A war factory: its body, its door with the door's clip, and a scaffold shown only while it goes up. */
    private static Visuals.UnitVisual warFactory() {
        return Visuals.create().unit("WarFactory", look -> {
            look.model("models/abwarfact.glb");
            look.layer("door").model("models/abwarfact_a8.glb")
                    .clip(Set.of("DOOR_1_OPENING"), "ABWarFact_A8", ONCE, FIRST, null)
                    .clip(Set.of("DOOR_1_WAITING_OPEN"), "ABWarFact_A8", HOLD, LAST, null);
            look.layer("scaffold").model(Set.of("PARTIALLY_CONSTRUCTED"), "models/abwarfact_a4.glb");
        }).of("WarFactory");
    }

    /** A model as a loader gives one: the door's with its one clip, a second's swing. */
    private static Spatial load(String path) {
        var model = new Node(path);
        if (path.endsWith("a8.glb")) {
            var leaf = new Node("Leaf");
            model.attachChild(leaf);
            var swing = new AnimClip("ABWarFact_A8");
            swing.setTracks(new AnimTrack<?>[] {new TransformTrack(leaf, new float[] {0f, 1f},
                    new Vector3f[] {new Vector3f(), new Vector3f(0f, 10f, 0f)}, null, null)});
            var composer = new AnimComposer();
            model.addControl(composer);
            composer.addAnimClip(swing);
        }
        return model;
    }

    private static UnitView holding(String... words) {
        return new UnitView(1, "WarFactory", 0, 0f, 0f, 0f, 100f, 100f, true, true, false, false, 0, 0f, 0f, 0f,
                false, 0, List.of(), List.of(words));
    }

    private static List<ModelLayer> layersOf(Visuals.UnitVisual look) {
        var layers = new ArrayList<ModelLayer>();
        look.layers.values().forEach(one -> layers.add(new ModelLayer(one, ModelLayerTest::load)));
        return layers;
    }

    private static void wear(List<ModelLayer> layers, Node root, UnitView view, int frame) {
        layers.forEach(layer -> layer.wear(root, view, Set.of(), frame, body -> { }, clip -> { }));
    }

    @Test
    void aBodyAndADoorAreBothDrawnAndTheDoorsWordsMoveTheDoorAlone() {
        var root = new Node("unit");
        var body = new Node("models/abwarfact.glb"); // the thing's own look, drawn as ever
        root.attachChild(body);
        var layers = layersOf(warFactory());

        wear(layers, root, holding(), 0);
        var door = layers.getFirst().body();
        assertNotNull(door);
        assertSame(root, door.getParent(), "at the thing's place and facing");
        assertEquals(2, root.getChildren().size(), "the body and the door; the scaffold draws nothing");

        wear(layers, root, holding("DOOR_1_OPENING"), 100);
        var composer = AnimationLibrary.findControl(door, AnimComposer.class);
        assertEquals("ABWarFact_A8", composer.getCurrentAction() == null ? null : currentName(composer));
        assertEquals(0.0, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-6, "from its first frame");
        wear(layers, root, holding("DOOR_1_OPENING"), 115);
        assertEquals(0.5, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-6, "half open half a second on");
        assertEquals(new Vector3f(), body.getLocalTranslation(), "the body as it was");
    }

    @Test
    void aLayerWhoseWordsNameNoModelDrawsNothingAndComesBackWhenTheyChange() {
        var root = new Node("unit");
        var layers = layersOf(warFactory());
        var scaffold = layers.get(1);

        wear(layers, root, holding(), 0);
        assertNull(scaffold.body(), "finished: no scaffold");
        wear(layers, root, holding("PARTIALLY_CONSTRUCTED"), 1);
        assertNotNull(scaffold.body(), "going up: the scaffold");
        assertTrue(root.getChildren().contains(scaffold.body()));
        var standing = scaffold.body();
        wear(layers, root, holding(), 2);
        assertNull(scaffold.body(), "finished again: gone");
        assertNull(standing.getParent(), "and taken off the thing");
    }

    @Test
    void aThingWithNoLayersHasNone() {
        var plain = Visuals.create().unit("Ranger", l -> l.model("models/ranger.glb")).of("Ranger");
        assertTrue(plain.layers.isEmpty(), "drawn by its one model, as today");
    }

    private static String currentName(AnimComposer composer) {
        for (var name : composer.getAnimClipsNames()) {
            if (composer.getAction(name) == composer.getCurrentAction()) {
                return name;
            }
        }
        return null;
    }
}
