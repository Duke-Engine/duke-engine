package uz.dukeengine.client3d;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;

/**
 * The particle systems a model's look runs at its bones — {@link Visuals.UnitVisual#particles}, the reference's {@code
 * ParticleSysBone}: started when its words choose them, riding their bones as the model turns and animates, and let go
 * when the words choose others, when the model is swapped, or once the model has left the scene.
 */
final class BoneSystems {

    private final Particles systems;
    private final Node scene;
    private List<Visuals.UnitVisual.BoneParticles> running = List.of();
    private final List<Emitter> emitters = new ArrayList<>();
    /** The running systems that follow the thing's motion, each with the drive that says how. */
    private final java.util.Map<Emitter, ParticleDrive> driven = new java.util.LinkedHashMap<>();
    private boolean seen;
    private float lastFacing;
    private float lastSpeed;
    private Spatial on;

    /**
     * @param systems where they are started, or null for a client that draws none
     * @param scene   the scene a model has to be in for its systems to go on
     */
    BoneSystems(Particles systems, Node scene) {
        this.systems = systems;
        this.scene = scene;
    }

    /** The systems its words choose now, at their bones of {@code model}: nothing done where they are running already. */
    void choose(List<Visuals.UnitVisual.BoneParticles> wanted, Spatial model) {
        if (wanted.equals(running) && model == on) {
            return;
        }
        stop();
        running = wanted;
        on = model;
        if (systems == null || model == null) {
            return;
        }
        for (var one : wanted) {
            var bone = Bones.named(model, one.bone());
            // A bone its model does not have runs the system all the same, at the thing's own place and unturned, as
            // the reference leaves it at the drawable's origin: a damaged airfield names Smoke01 on a model whose
            // bone is SmokeS01, and its smoke rises from its middle.
            var thing = model.getParent() != null ? model.getParent() : model;
            var emitter = systems.start(one.system(), () -> !inScene(bone != null ? bone : model) ? null : bone != null
                    ? LayeredEffects.placementAt(bone.getWorldTranslation(), bone.getWorldRotation().toAngles(null)[1])
                    : LayeredEffects.placementAt(thing.getWorldTranslation(), 0f));
            if (emitter != null) {
                emitters.add(emitter);
                if (one.drive() != null) {
                    driven.put(emitter, one.drive());
                }
            }
        }
    }

    /**
     * The thing drawn again, {@code frames} of the game's after the last time: the systems that follow its motion are
     * told how fast it moved, turned and sped up or slowed since, and scaled as their drives say. Pure presentation, so
     * {@code Math} will do.
     */
    void drive(uz.dukeengine.core.view.UnitView view, float frames) {
        float facing = view.orientation() + view.yaw();
        float seconds = frames * Particles.FRAME_SECONDS;
        float turn = 0f;
        float acceleration = 0f;
        if (seen && seconds > 0f) {
            float change = facing - lastFacing;
            turn = (change - (float) (2 * Math.PI * Math.rint(change / (2 * Math.PI)))) / seconds;
            acceleration = (view.speed() - lastSpeed) / seconds;
        }
        seen = true;
        lastFacing = facing;
        lastSpeed = view.speed();
        var motion = new ParticleDrive.Motion(view, view.speed(), turn, acceleration);
        driven.forEach((emitter, drive) -> {
            var scales = drive.scales(motion);
            emitter.drive(scales.size(), scales.rate());
        });
    }

    /** Every one let go. */
    void stop() {
        emitters.forEach(Emitter::destroy);
        emitters.clear();
        driven.clear();
        running = List.of();
        on = null;
    }

    /** The systems running now, for a test. */
    List<Emitter> emitters() {
        return emitters.stream().filter(emitter -> !emitter.isDestroyed()).toList();
    }

    private boolean inScene(Spatial bone) {
        var at = bone;
        while (at.getParent() != null) {
            at = at.getParent();
        }
        return at == scene;
    }
}
