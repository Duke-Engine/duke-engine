package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import uz.dukeengine.core.view.EffectView;

/**
 * The particle systems the simulation keeps going on its things until it ends them — {@code World.effect} with a
 * number: each started once, riding its bone of the thing's model, or a point in the thing's own frame, or its place;
 * let go when the simulation ends it or stops showing it, its last particles left to finish, and with its thing when
 * the thing leaves the scene.
 */
final class RidingEffects {

    private final Particles systems;
    private final Node scene;
    private final Map<Integer, Emitter> running = new TreeMap<>();

    /**
     * @param systems where they are started, or null for a client that draws none
     * @param scene   the scene a thing has to be in for what rides it to go on
     */
    RidingEffects(Particles systems, com.jme3.scene.Node scene) {
        this.systems = systems;
        this.scene = new Node(scene);
    }

    /** The frame's effects: a new one started on its thing, drawn by {@code thingOf}, and one gone let go. */
    void show(List<EffectView> effects, IntFunction<Spatial> thingOf) {
        var wanted = new HashSet<Integer>();
        for (var effect : effects) {
            wanted.add(effect.id());
            if (systems == null || running.containsKey(effect.id())) {
                continue;
            }
            var thing = thingOf.apply(effect.thing());
            if (thing == null) {
                continue; // not drawn yet: started once it is
            }
            var emitter = systems.start(effect.name(), where(thing, effect));
            if (emitter != null) {
                running.put(effect.id(), emitter);
            }
        }
        running.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().destroy(); // lets out nothing more; what is out finishes
            return true;
        });
    }

    /** Every one let go, as a new world starts. */
    void clear() {
        running.values().forEach(Emitter::destroy);
        running.clear();
    }

    /** The systems running now, for a test. */
    List<Emitter> emitters() {
        return running.values().stream().filter(emitter -> !emitter.isDestroyed()).toList();
    }

    /** Where it stands each frame: its bone, turned with it, or its point turned with the thing; null once gone. */
    private Supplier<Particles.Placement> where(Spatial thing, EffectView effect) {
        var bone = Bones.named(thing, effect.bone());
        if (bone != null) {
            return () -> scene.holds(bone)
                    ? LayeredEffects.placementAt(bone.getWorldTranslation(), bone.getWorldRotation().toAngles(null)[1])
                    : null;
        }
        var offset = effect.offset();
        // The simulation's frame is x forward, y the ground's other way, z up; the scene's is x, up, and the ground.
        var local = offset == null ? new Vector3f() : new Vector3f(offset.x(), offset.z(), offset.y());
        return () -> scene.holds(thing)
                ? LayeredEffects.placementAt(thing.getWorldRotation().mult(local).addLocal(thing.getWorldTranslation()),
                        thing.getWorldRotation().toAngles(null)[1])
                : null; // turned with the thing, not scaled with its model: the point is in world units
    }

    /** The scene, asked whether a spatial is still in it. */
    private record Node(com.jme3.scene.Node root) {
        boolean holds(Spatial spatial) {
            var at = spatial;
            while (at.getParent() != null) {
                at = at.getParent();
            }
            return at == root;
        }
    }
}
