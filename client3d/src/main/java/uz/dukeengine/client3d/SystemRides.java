package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.HashMap;
import java.util.Map;

/**
 * The game's particle systems standing in an effect's place: where {@link LayeredEffects} is
 * asked for a name that is a system rather than an effect, one starts at the spot or rides a
 * thing's node until it is gone.
 */
final class SystemRides {

    private final Visuals visuals;
    private Particles systems;
    /** A system riding a projectile, by its id, let go of when that is gone. */
    private final Map<Integer, Emitter> riders = new HashMap<>();

    SystemRides(Visuals visuals) {
        this.visuals = visuals;
    }

    void drawsWith(Particles systems) {
        this.systems = systems;
    }

    /** A particle system standing at a spot, where a name is one. */
    void startAt(String name, Vector3f spot) {
        if (!can(name, spot)) {
            return;
        }
        systems.startAt(name, LayeredEffects.placementAt(spot, 0f));
    }

    /** A particle system of that name riding a thing's node till it is gone; false where the game has none by it. */
    boolean rides(String name, Spatial node) {
        if (!can(name, node)) {
            return false;
        }
        ride(LayeredEffects.NOBODY, name, node, null);
        return true;
    }

    /** A particle system riding a thing's node — its place and its turn — until the thing is gone. */
    void ride(int id, String name, Spatial node, Vector3f offset) {
        if (!can(name, node)) {
            return;
        }
        var local = offset == null ? new Vector3f() : offset.clone();
        var rider = systems.start(name, () -> node.getParent() == null ? null
                : LayeredEffects.placementAt(node.localToWorld(local, null),
                        node.getWorldRotation().toAngles(null)[1]));
        if (rider != null && id != LayeredEffects.NOBODY) {
            var before = riders.put(id, rider);
            if (before != null) {
                before.destroy();
            }
        }
    }

    /** The projectile left: what it let out burns out where it is. */
    void release(int id) {
        var rider = riders.remove(id);
        if (rider != null) {
            rider.destroy();
        }
    }

    void clear() {
        riders.clear();
    }

    private boolean can(String name, Object where) {
        return systems != null && where != null && visuals.particleSystemNamed(name) != null;
    }
}
