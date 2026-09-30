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
import uz.dukeengine.core.view.UnitView;

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

    // ---- rising layers: lit from their first frame, and by a height the game names ----

    /** A model 48 high: a box standing on the ground, its top at 48. */
    private static Spatial tower(String path) {
        var model = new Node(path);
        var box = new com.jme3.scene.Geometry(path + "-mesh", new com.jme3.scene.shape.Box(5f, 24f, 5f));
        box.setLocalTranslation(0f, 24f, 0f);
        model.attachChild(box);
        return model;
    }

    private static UnitView built(float share) {
        return new UnitView(1, "CommandCenter", 0, 0f, 0f, 0f, 100f, 100f, true, true, false, false, 0, 0f, 0f, 0f,
                false, 0, List.of(), List.of(), share);
    }

    private static int lightsOn(Spatial body) {
        var count = new int[] {Integer.MAX_VALUE};
        body.depthFirstTraversal(spatial -> {
            if (spatial instanceof com.jme3.scene.Geometry geometry) {
                count[0] = Math.min(count[0], geometry.getWorldLightList().size());
            }
        });
        return count[0];
    }

    private static List<ModelLayer> commandCenter() {
        var look = Visuals.create().unit("CommandCenter", l -> {
            l.model("models/cc.glb");
            l.layer("dish").model("models/dish.glb").risesAsBuilt();
            l.layer("flag").model("models/flag.glb");
        }).of("CommandCenter");
        var layers = new ArrayList<ModelLayer>();
        look.layers.values().forEach(one -> layers.add(new ModelLayer(one, ModelLayerTest::tower)));
        return layers;
    }

    @Test
    void aRisingLayerWornTheFrameItsThingAppearsIsLitAsOneThatDoesNotRise() {
        var scene = new Node("scene");
        scene.addLight(new com.jme3.light.DirectionalLight());
        scene.updateGeometricState(); // the scene as it stood before the thing
        var thing = new Node("thing");
        scene.attachChild(thing); // it appears this frame, finished
        var layers = commandCenter();

        wear(layers, thing, built(1f), 0);
        scene.updateGeometricState(); // the first frame drawn

        for (var layer : layers) {
            assertEquals(1, lightsOn(layer.body()), "the scene's light on every piece, rising or not");
        }
    }

    @Test
    void aRisingLayerOfAThingAppearingMidMatchIsLitToo() {
        var scene = new Node("scene");
        scene.addLight(new com.jme3.light.DirectionalLight());
        var first = new Node("first");
        scene.attachChild(first);
        var early = commandCenter();
        wear(early, first, built(1f), 0);
        for (int frame = 0; frame < 3; frame++) {
            scene.updateGeometricState();
        }
        var later = new Node("later");
        scene.attachChild(later);
        var layers = commandCenter();

        wear(layers, later, built(0.5f), 90);
        scene.updateGeometricState();

        for (var layer : layers) {
            assertEquals(1, lightsOn(layer.body()), "lit from the frame it appears");
        }
    }

    @Test
    void aLayerRisesByTheHeightTheGameNamesNotItsModelsTop() {
        var look = Visuals.create().unit("Factory", l -> l.layer("walls").model("models/walls.glb")
                .risesAsBuilt(40f)).of("Factory");
        var layer = new ModelLayer(look.layers.get("walls"), ModelLayerTest::tower);
        var thing = new Node("thing");

        float[] tops = new float[3];
        float[] shares = {0f, 0.5f, 1f};
        for (int at = 0; at < 3; at++) {
            layer.wear(thing, built(shares[at]), Set.of(), 0, body -> { }, clip -> { });
            thing.updateGeometricState();
            var box = (com.jme3.bounding.BoundingBox) layer.body().getWorldBound();
            tops[at] = box.getCenter().y + box.getYExtent();
        }

        assertEquals(8f, tops[0], 1e-3f, "at nothing built the 48 model stands 8 out of the ground");
        assertEquals(28f, tops[1], 1e-3f, "half built, 28");
        assertEquals(48f, tops[2], 1e-3f, "whole, 48");
    }

    @Test
    void aLookChoosesOnceForTheSameWordsAndAgainTheFrameAWordIsSet() {
        var look = warFactory();
        var layers = layersOf(look);
        var root = new Node("root");

        for (int frame = 0; frame < 100; frame++) {
            wear(layers, root, holding("READY"), frame);
        }
        for (var layer : look.layers.values()) {
            assertEquals(1, layer.choicesMade, "a hundred frames of the same words: chosen once");
        }

        wear(layers, root, holding("READY", "PARTIALLY_CONSTRUCTED"), 100);
        for (var layer : look.layers.values()) {
            assertEquals(2, layer.choicesMade, "a word set on it: chosen again that frame");
        }
        assertNotNull(layers.get(1).body(), "and the scaffold its new words choose is drawn");
    }

    @Test
    void aChoiceKeptIsTheChoiceMade() {
        var look = warFactory().layers.get("scaffold");
        var words = Set.of("PARTIALLY_CONSTRUCTED");

        var first = look.modelFor(words);
        var again = look.modelFor(new java.util.HashSet<>(words));

        assertEquals("models/abwarfact_a4.glb", first);
        assertSame(first, again, "found, not worked out again");
        assertEquals(1, look.choicesMade);
        assertNull(look.modelFor(Set.of("READY")), "and other words choose afresh");
    }

    @Test
    void aLayerHungOnABoneStandsWhereTheClipMovesTheBone() {
        var look = Visuals.create().unit("Technical", l -> {
            l.model("models/technical.glb");
            l.layer("gun").model("models/gun.glb").hungOn("TURRET");
        }).of("Technical");
        var thing = new Node("thing");
        var body = new Node("technical");
        var turret = new Node("Turret");
        body.attachChild(turret);
        thing.attachChild(body);
        var swing = new AnimClip("Swing");
        swing.setTracks(new AnimTrack<?>[] {new TransformTrack(turret, new float[] {0f, 1f},
                new Vector3f[] {new Vector3f(), new Vector3f(10f, 0f, 0f)}, null, null)});
        var composer = new AnimComposer();
        body.addControl(composer);
        composer.addAnimClip(swing);
        var gun = new ModelLayer(look.layers.get("gun"), ModelLayerTest::tower);

        gun.wear(thing, holding(), Set.of(), 0, drawn -> { }, clip -> { }, name -> Bones.named(body, name));
        composer.setCurrentAction("Swing");
        thing.updateLogicalState(0.5f); // halfway through the clip: the turret 5 to the side
        thing.updateGeometricState();
        assertEquals(5f, gun.body().getWorldTranslation().x, 1e-3f, "the gun with it");

        turret.setLocalTranslation(10f, 0f, 0f); // where the clip ends
        thing.updateGeometricState();
        assertEquals(10f, gun.body().getWorldTranslation().x, 1e-3f, "10 to the side when the bone is");
    }

    @Test
    void aBoneItsModelsDoNotHaveLeavesItAtTheThingsPlace() {
        var look = Visuals.create().unit("Car", l -> l.layer("light").model("models/light.glb").hungOn("ROOF"))
                .of("Car");
        var thing = new Node("thing");
        var light = new ModelLayer(look.layers.get("light"), ModelLayerTest::tower);

        light.wear(thing, holding(), Set.of(), 0, drawn -> { }, clip -> { }, name -> null);

        assertSame(thing, light.body().getParent());
    }

    // ---- a clip played between two looks ----

    /** A model with one clip a second long, "Rise", moving its "Panel" from 0 to 10 up. */
    private static Spatial fence(String path) {
        var model = new Node(path);
        var panel = new Node("Panel");
        panel.attachChild(new com.jme3.scene.Geometry("panel", new com.jme3.scene.shape.Box(5f, 2f, 0.5f)));
        model.attachChild(panel);
        var rise = new AnimClip("Rise");
        rise.setTracks(new AnimTrack<?>[] {new TransformTrack(panel, new float[] {0f, 1f},
                new Vector3f[] {new Vector3f(), new Vector3f(0f, 10f, 0f)}, null, null)});
        var composer = new AnimComposer();
        model.addControl(composer);
        composer.addAnimClip(rise);
        return model;
    }

    /** Where the panel stands once the frame's clips are applied, as the scene applies them before drawing. */
    private static float panelHeight(ModelLayer layer) {
        var body = (Node) layer.body();
        body.updateLogicalState(0f);
        return body.getChild("Panel").getLocalTranslation().y;
    }

    private static ModelLayer siteFence() {
        var look = Visuals.create().unit("Site", l -> l.layer("fence")
                .model(Set.of("AWAITING"), "models/fence.glb")
                .transition(Set.of(), Set.of("AWAITING"), "models/fence_rise.glb", "Rise",
                        Visuals.ClipMode.ONCE, 1f)
                .transition(Set.of("AWAITING"), Set.of(), "models/fence_rise.glb", "Rise",
                        Visuals.ClipMode.ONCE_BACKWARDS, 2f)).of("Site");
        return new ModelLayer(look.layers.get("fence"), ModelLayerTest::fence);
    }

    @Test
    void aTransitionIntoALookPlaysOnceFromItsFirstFrameThenTheLooksOwn() {
        var layer = siteFence();
        var root = new Node("root");

        layer.wear(root, holding("AWAITING"), Set.of(), 100, drawn -> { }, clip -> { });
        assertEquals("models/fence_rise.glb", layer.body().getName(), "the way in first");
        assertEquals(0f, panelHeight(layer), 1e-3f, "from its first frame");

        layer.wear(root, holding("AWAITING"), Set.of(), 115, drawn -> { }, clip -> { });
        assertEquals(5f, panelHeight(layer), 1e-2f, "half a second in, halfway up");

        layer.wear(root, holding("AWAITING"), Set.of(), 130, drawn -> { }, clip -> { });
        assertEquals("models/fence.glb", layer.body().getName(), "played: the look's own from then");
        assertEquals(1, root.getQuantity(), "and the way in taken away");
    }

    @Test
    void theWordsLeavingPlayTheWayOutAtItsSpeedAndTheLayerIsGoneWhenItEnds() {
        var layer = siteFence();
        var root = new Node("root");
        layer.wear(root, holding("AWAITING"), Set.of(), 0, drawn -> { }, clip -> { });
        layer.wear(root, holding("AWAITING"), Set.of(), 40, drawn -> { }, clip -> { });

        layer.wear(root, holding(), Set.of(), 50, drawn -> { }, clip -> { });
        assertEquals("models/fence_rise.glb", layer.body().getName(), "the way out, though no look is left");
        assertEquals(10f, panelHeight(layer), 1e-2f, "backwards, from its end");

        layer.wear(root, holding(), Set.of(), 57, drawn -> { }, clip -> { });
        assertEquals(10f - 10f * 7 * 2 / 30f, panelHeight(layer), 1e-2f, "at twice its pace");

        layer.wear(root, holding(), Set.of(), 65, drawn -> { }, clip -> { });
        assertNull(layer.body(), "played out: gone");
        assertEquals(0, root.getQuantity());
    }

    // ---- a transition as the reference plays it ----

    /** A model whose one clip, "Swing", is two seconds long, moving its "Arm" 20 across. */
    private static Spatial crane(String path) {
        var model = new Node(path);
        var arm = new Node("Arm");
        arm.attachChild(new com.jme3.scene.Geometry("arm", new com.jme3.scene.shape.Box(5f, 1f, 1f)));
        model.attachChild(arm);
        var swing = new AnimClip("Swing");
        swing.setTracks(new AnimTrack<?>[] {new TransformTrack(arm, new float[] {0f, 2f},
                new Vector3f[] {new Vector3f(), new Vector3f(20f, 0f, 0f)}, null, null)});
        var composer = new AnimComposer();
        model.addControl(composer);
        composer.addAnimClip(swing);
        return model;
    }

    /**
     * A factory's crane swinging while it works, its way out the same clip in the same keep group: left at 1.5 of its
     * 2 seconds, the way out starts at 1.5 and ends half a second later — it finishes the swing it is in.
     */
    @Test
    void aTransitionInTheKeepGroupOfTheLookItLeavesStartsWhereThatLookStood() {
        var look = Visuals.create().unit("Factory", l -> l.layer("crane")
                .model("models/crane.glb")
                .model(Set.of("DONE"), "models/crane_rest.glb")
                .clip(Set.of(), "Swing", Visuals.ClipMode.LOOP, null, "swing")
                .transition(Set.of(), Set.of("DONE"), "models/crane.glb", "Swing", Visuals.ClipMode.ONCE, 1f,
                        "swing")).of("Factory");
        var layer = new ModelLayer(look.layers.get("crane"), ModelLayerTest::crane);
        var root = new Node("root");
        layer.wear(root, holding(), Set.of(), 0, drawn -> { }, clip -> { });
        layer.wear(root, holding(), Set.of(), 45, drawn -> { }, clip -> { });

        layer.wear(root, holding("DONE"), Set.of(), 45, drawn -> { }, clip -> { });
        var composer = AnimationLibrary.findControl(layer.body(), AnimComposer.class);
        assertEquals(1.5, composer.getTime(AnimComposer.DEFAULT_LAYER), 1e-4, "from where the swing stood");
        layer.wear(root, holding("DONE"), Set.of(), 59, drawn -> { }, clip -> { });
        assertEquals("models/crane.glb", layer.body().getName(), "still on its way out");
        layer.wear(root, holding("DONE"), Set.of(), 60, drawn -> { }, clip -> { });
        assertEquals("models/crane_rest.glb", layer.body().getName(), "half a second later, at rest");
    }

    /** A command centre's fence rising over burning pits: the transition's system runs while it plays, then stops. */
    @Test
    void aTransitionsSystemRunsWhileItPlaysAndStopsWhenItEnds() {
        var look = Visuals.create().unit("Site", l -> l.layer("fence")
                .model(Set.of("AWAITING"), "models/fence.glb")
                .transition(Set.of(), Set.of("AWAITING"), "models/fence_rise.glb", "Rise", Visuals.ClipMode.ONCE, 1f)
                .transitionParticles("Panel", "Fire")).of("Site");
        var scene = new Node("scene");
        var root = new Node("root");
        scene.attachChild(root);
        var fire = new uz.dukeengine.core.data.Binder().bind(uz.dukeengine.core.data.DukeText.parse("""
                ParticleSystem
                  Name = Fire
                  BurstCount = [1]
                  BurstDelay = [5]
                  Lifetime = [20]
                  SystemLifetime = 0
                End
                """, "fx.duke").getFirst(), uz.dukeengine.core.content.ParticleSystem.class);
        var particles = new Particles(name -> name.equals("Fire") ? fire : null, 7L, Particles.Ground.FLAT,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        var systems = new BoneSystems(particles, scene);
        var layer = new ModelLayer(look.layers.get("fence"), ModelLayerTest::fence, systems);

        layer.wear(root, holding("AWAITING"), Set.of(), 100, drawn -> { }, clip -> { });
        assertEquals(1, systems.emitters().size(), "burning while the fence rises");
        layer.wear(root, holding("AWAITING"), Set.of(), 130, drawn -> { }, clip -> { });
        assertEquals(0, systems.emitters().size(), "and out once it has");
    }

    /** A Chinook's crates lifted before they are carried: the new look worn once the one before played its clip. */
    @Test
    void aLookWaitingForTheOneBeforeIsWornWhenThatOnesOnceClipEnds() {
        var look = Visuals.create().unit("Chinook", l -> l.layer("crates")
                .model("models/fence_rise.glb")
                .model(Set.of("CARRIED"), "models/fence.glb")
                .clip(Set.of(), "Rise", Visuals.ClipMode.ONCE, null, null)
                .waitFor(Set.of(), Set.of("CARRIED"))).of("Chinook");
        var layer = new ModelLayer(look.layers.get("crates"), ModelLayerTest::fence);
        var root = new Node("root");
        layer.wear(root, holding(), Set.of(), 0, drawn -> { }, clip -> { });

        layer.wear(root, holding("CARRIED"), Set.of(), 15, drawn -> { }, clip -> { });
        assertEquals("models/fence_rise.glb", layer.body().getName(), "half through its lift: still lifting");
        layer.wear(root, holding("CARRIED"), Set.of(), 29, drawn -> { }, clip -> { });
        assertEquals("models/fence_rise.glb", layer.body().getName());
        layer.wear(root, holding("CARRIED"), Set.of(), 30, drawn -> { }, clip -> { });
        assertEquals("models/fence.glb", layer.body().getName(), "the lift played to its end: carried");
    }
}
