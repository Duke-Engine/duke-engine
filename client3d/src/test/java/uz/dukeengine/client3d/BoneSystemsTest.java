package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Node;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.ParticleSystem;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;

/** Particle systems at a look's bones: running while its words choose that look, and let go when they do not. */
class BoneSystemsTest {

    private static final Map<String, ParticleSystem> SYSTEMS = Map.of(
            "Sparks", system("Sparks"), "Smoke", system("Smoke"));

    private static ParticleSystem system(String name) {
        return new Binder().bind(DukeText.parse("""
                ParticleSystem
                  Name = %s
                  BurstCount = [1]
                  BurstDelay = [5]
                  Lifetime = [100]
                  SystemLifetime = 0
                End
                """.formatted(name), "fx.duke").getFirst(), ParticleSystem.class);
    }

    /** A scaffold with two bones its sparks and smoke come from, while it is partly built. */
    private static final Visuals.UnitVisual SCAFFOLD = Visuals.create().unit("Factory", look -> look
            .particles(Set.of("PARTIALLY_CONSTRUCTED"), "SPARKS01", "Sparks")
            .particles(Set.of("PARTIALLY_CONSTRUCTED"), "SMOKE01", "Smoke")).of("Factory");

    @Test
    void whileTheThingHoldsItsWordBothSystemsRunAtTheirBonesAndWithoutItTheyStop() {
        var scene = new Node("scene");
        var model = new Node("factory");
        var sparks = new Node("SPARKS01");
        sparks.setLocalTranslation(10f, 20f, 0f);
        var smoke = new Node("SMOKE01");
        smoke.setLocalTranslation(-10f, 30f, 5f);
        model.attachChild(sparks);
        model.attachChild(smoke);
        scene.attachChild(model);
        scene.updateGeometricState();
        var particles = new Particles(SYSTEMS::get, 7L, Particles.Ground.FLAT, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var systems = new BoneSystems(particles, scene);

        systems.choose(SCAFFOLD.particlesFor(Set.of("PARTIALLY_CONSTRUCTED")), model);
        particles.update();

        var running = systems.emitters();
        assertEquals(2, running.size(), "both, while it is partly built");
        var where = running.stream().map(Emitter::where).toList();
        assertTrue(where.stream().anyMatch(at -> close(at, 10f, 0f, 20f)), "the sparks at their bone");
        assertTrue(where.stream().anyMatch(at -> close(at, -10f, -5f, 30f)), "the smoke at its (the scene's z its y)");

        systems.choose(SCAFFOLD.particlesFor(Set.of()), model);

        assertEquals(0, systems.emitters().size(), "the word gone: both let go");
        assertTrue(running.stream().allMatch(Emitter::isDestroyed));
    }

    @Test
    void theSystemsOfAModelThatLeftTheSceneEndWithIt() {
        var scene = new Node("scene");
        var model = new Node("factory");
        model.attachChild(new Node("SPARKS01"));
        scene.attachChild(model);
        var particles = new Particles(SYSTEMS::get, 7L, Particles.Ground.FLAT, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var systems = new BoneSystems(particles, scene);
        systems.choose(SCAFFOLD.particlesFor(Set.of("PARTIALLY_CONSTRUCTED")), model);
        var emitter = systems.emitters().getFirst();

        model.removeFromParent();
        particles.update();

        assertTrue(emitter.isDestroyed(), "no bone in the scene to follow");
    }

    /**
     * A system at a bone its model lacks runs all the same, at the thing's own place and unturned, following it — as
     * the reference's damaged airfield names Smoke01 on a model whose bone is SmokeS01, its smoke rising from its
     * middle.
     */
    @Test
    void aSystemAtABoneTheModelLacksRunsAtTheThingsPositionWhileOneAtABoneItHasRidesTheBone() {
        var scene = new Node("scene");
        var thing = new Node("unit");
        thing.setLocalTranslation(100f, 0f, 50f);
        thing.setLocalRotation(new com.jme3.math.Quaternion().fromAngles(0f, 1f, 0f));
        var model = new Node("factory");
        var sparks = new Node("SPARKS01");
        sparks.setLocalTranslation(10f, 20f, 0f);
        model.attachChild(sparks); // no SMOKE01 on this model
        thing.attachChild(model);
        scene.attachChild(thing);
        scene.updateGeometricState();
        var particles = new Particles(SYSTEMS::get, 7L, Particles.Ground.FLAT, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var systems = new BoneSystems(particles, scene);

        systems.choose(SCAFFOLD.particlesFor(Set.of("PARTIALLY_CONSTRUCTED")), model);
        particles.update();

        var running = systems.emitters();
        assertEquals(2, running.size(), "both run");
        var boneAt = sparks.getWorldTranslation();
        assertTrue(running.stream().anyMatch(e -> close(e.where(), boneAt.x, -boneAt.z, boneAt.y)),
                "the one on its bone");
        assertTrue(running.stream().anyMatch(e -> close(e.where(), 100f, -50f, 0f)), "the other at the thing's place");

        thing.setLocalTranslation(200f, 0f, 80f);
        scene.updateGeometricState();
        particles.update();
        assertTrue(systems.emitters().stream().anyMatch(e -> close(e.where(), 200f, -80f, 0f)), "and following it");
    }

    private static boolean close(float[] at, float x, float y, float z) {
        return Math.abs(at[0] - x) < 1e-3f && Math.abs(at[1] - y) < 1e-3f && Math.abs(at[2] - z) < 1e-3f;
    }
}
