package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingSphere;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.ArrayList;
import java.util.List;

/**
 * Where {@link LayeredEffects}' layers happen: the places one moment gives a layer, the floor
 * under them, and whether any of it is worth starting. A world that is null knows nobody, sees
 * everywhere and has a flat floor.
 */
final class LayerSpots {

    /** One place a layer happens, and the creature it follows there, if any. */
    record Place(Vector3f at, int follows) {
    }

    private final LayeredEffects.Surroundings world;
    private final float maxDistance;

    LayerSpots(LayeredEffects.Surroundings world, float maxDistance) {
        this.world = world;
        this.maxDistance = maxDistance;
    }

    /** Where a creature stands this frame, or null once it is gone. */
    Vector3f whereIs(int unitId) {
        return world == null ? null : world.whereIs(unitId);
    }

    float floorUnder(float x, float z) {
        return world == null ? 0f : world.floorAt(x, z);
    }

    Vector3f floored(Vector3f at) {
        return new Vector3f(at.x, floorUnder(at.x, at.z), at.z);
    }

    /** Every place one layer happens for this moment — one, two, or one per enemy caught. */
    List<Place> placesFor(EffectLayer layer, LayeredEffects.Moment moment) {
        var spot = floored(moment.spot());
        return switch (layer.at()) {
            case EffectLayer.FROM -> List.of(new Place(moment.hasPath()
                    ? floored(moment.from()) : spot, LayeredEffects.NOBODY));
            case EffectLayer.TO -> List.of(new Place(moment.hasPath()
                    ? floored(moment.to()) : spot, moment.on()));
            case EffectLayer.BOTH -> moment.hasPath()
                    ? List.of(new Place(floored(moment.from()), LayeredEffects.NOBODY),
                            new Place(floored(moment.to()), moment.on()))
                    : List.of(new Place(spot, moment.on()));
            case EffectLayer.CASTER -> {
                var caster = whereIs(moment.by());
                yield List.of(new Place(caster != null ? caster : spot, moment.by()));
            }
            case EffectLayer.CAUGHT -> {
                var caught = new ArrayList<Place>();
                if (world != null) {
                    for (int id : world.enemiesNear(spot.x, spot.z, moment.radius(), moment.by())) {
                        var where = world.whereIs(id);
                        if (where != null) {
                            caught.add(new Place(where, id));
                        }
                    }
                }
                yield caught;
            }
            default -> List.of(new Place(spot, moment.on()));
        };
    }

    boolean worthStarting(EffectLayer layer, float unit, Vector3f at, Camera camera) {
        if (at == null) {
            return false;
        }
        if (world != null && !inSight(at, LayerPlay.reachOf(layer, unit) * 0.5f)) {
            return false; // under the fog: what nobody can see costs nothing
        }
        if (camera == null) {
            return true;
        }
        if (maxDistance > 0f && at.distanceSquared(camera.getLocation())
                > maxDistance * maxDistance) {
            return false; // two rooms away is a pixel, and the budget is the near fight's
        }
        if (layer.lasting()) {
            return true; // an aura may walk on screen; a burst that starts off it never will
        }
        var sphere = new BoundingSphere(LayerPlay.reachOf(layer, unit), at);
        return camera.contains(sphere) != Camera.FrustumIntersect.Outside;
    }

    /**
     * Whether any of it would be in sight: its middle, or anywhere round it about as
     * far as most of it reaches. A fireball that bursts just behind a pillar still
     * throws its fire past the pillar -- and one that bursts where nobody can see
     * any of it still costs nothing.
     */
    private boolean inSight(Vector3f at, float around) {
        if (world.canSee(at.x, at.z)) {
            return true;
        }
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            if (world.canSee(at.x + (float) Math.cos(angle) * around,
                    at.z + (float) Math.sin(angle) * around)) {
                return true;
            }
        }
        return false;
    }
}
