package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.scene.Node;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.ParticleSystem;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.EffectView;

/**
 * An effect riding a thing until the simulation ends it: a damaged building's smoke column at its SMOKE bone, running
 * till stopped, and stopped by the repair.
 */
class RidingEffectsTest {

    private static final Map<String, ParticleSystem> SYSTEMS = Map.of("SmokeColumn", new Binder().bind(
            DukeText.parse("""
            ParticleSystem
              Name = SmokeColumn
              BurstCount = [1]
              BurstDelay = [2]
              Lifetime = [30]
              SystemLifetime = 0
            End
            """, "fx.duke").getFirst(), ParticleSystem.class));

    private final Node scene = new Node("scene");
    private final Node thing = new Node("tank");
    private final Particles particles = new Particles(SYSTEMS::get, 7L, Particles.Ground.FLAT, Integer.MAX_VALUE,
            Integer.MAX_VALUE);
    private final RidingEffects effects = new RidingEffects(particles, scene);

    RidingEffectsTest() {
        var smoke = new Node("SMOKE01");
        smoke.setLocalTranslation(10f, 5f, 0f);
        thing.attachChild(smoke);
        scene.attachChild(thing);
        scene.updateGeometricState();
    }

    private void show(EffectView... views) {
        effects.show(List.of(views), id -> id == 7 ? thing : null);
    }

    private static boolean close(float[] at, float x, float y, float z) {
        return Math.abs(at[0] - x) < 1e-3f && Math.abs(at[1] - y) < 1e-3f && Math.abs(at[2] - z) < 1e-3f;
    }

    @Test
    void aSystemStartedAtABoneRidesTheBoneAsTheThingMovesAndTurns() {
        show(new EffectView(1, "SmokeColumn", 7, "SMOKE01", null));
        particles.update();
        var emitter = effects.emitters().getFirst();
        assertTrue(close(emitter.where(), 10f, 0f, 5f), "at its bone: " + java.util.Arrays.toString(emitter.where()));

        thing.setLocalTranslation(100f, 0f, 50f);
        thing.setLocalRotation(new Quaternion().fromAngles(0f, -(float) Math.PI / 2f, 0f)); // a quarter turn
        scene.updateGeometricState();
        particles.update();
        assertTrue(close(emitter.where(), 100f, -60f, 5f),
                "moved and turned with it: " + java.util.Arrays.toString(emitter.where()));
    }

    @Test
    void aPointInItsOwnFrameTurnsWithTheThing() {
        show(new EffectView(1, "SmokeColumn", 7, null, new Coord3D(0f, 20f, 3f)));
        thing.setLocalRotation(new Quaternion().fromAngles(0f, -(float) Math.PI / 2f, 0f));
        scene.updateGeometricState();
        particles.update();

        var where = effects.emitters().getFirst().where();
        assertTrue(close(where, -20f, 0f, 3f), "20 to its side, turned a quarter: " + java.util.Arrays.toString(where));
    }

    @Test
    void endedItLetsNothingMoreOutAndItsLastParticlesFinish() {
        var smoke = new EffectView(1, "SmokeColumn", 7, "SMOKE01", null);
        for (int frame = 0; frame < 100; frame++) {
            show(smoke);
            particles.update();
        }
        var emitter = effects.emitters().getFirst();
        int alive = emitter.particles().size();
        assertTrue(alive > 0, "smoking");

        show(); // ended on frame 100
        assertTrue(emitter.isDestroyed(), "no more let out");
        int most = alive;
        for (int frame = 0; frame < 40; frame++) {
            particles.update();
            assertTrue(emitter.particles().size() <= most, "none let out after it was ended");
            most = emitter.particles().size();
        }
        assertEquals(0, emitter.particles().size(), "and the last have finished");
    }

    @Test
    void oneRidingAThingThatLeavesTheSceneEndsWithIt() {
        show(new EffectView(1, "SmokeColumn", 7, "SMOKE01", null));
        var emitter = effects.emitters().getFirst();

        thing.removeFromParent();
        particles.update();

        assertTrue(emitter.isDestroyed());
    }
}
