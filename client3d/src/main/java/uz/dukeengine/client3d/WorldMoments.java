package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.rts.event.ShotLanded;
import uz.dukeengine.rts.event.WeaponFired;

/**
 * Where the world's own moments are played — {@code fired.<weapon>}, {@code landed.<weapon>} and {@code
 * died.<template>} — and for what, as the reference places its weapons' and deaths' effect lists. The
 * simulation's places are Z-up and the client's Y-up; nothing is ever played under the floor.
 */
final class WorldMoments {

    /** The height of the floor under a place, in the client's frame. */
    @FunctionalInterface
    interface Floor {
        float at(float x, float z);
    }

    private WorldMoments() {
    }

    /**
     * A shot: at the barrel it left from, turned with it; with no barrel, a contact weapon at what it struck,
     * anything else at the middle of the thing that fired, at its own height — {@code handleWeaponFireFX}, and its
     * fallback in {@code WeaponTemplate::fireWeaponTemplate}. Handed the shooter, what it was fired at, and its
     * blast's radius.
     *
     * @param bone    the barrel's bone, or null
     * @param shooter the shooter's node, or null where the client is not drawing it
     */
    static EffectLists.Cue fired(WeaponFired shot, Spatial bone, Spatial shooter, Floor floor) {
        var target = shot.to() == null ? null : standing(shot.to(), floor);
        if (bone != null) {
            return new EffectLists.Cue(bone.getWorldTranslation().clone(), bone.getWorldRotation().clone(), shooter,
                    target, shot.radius());
        }
        var turn = shooter == null ? null : shooter.getWorldRotation().clone();
        if (shot.contact() && target != null) {
            return new EffectLists.Cue(target, turn, shooter, target, shot.radius());
        }
        var middle = standing(shot.from(), floor).addLocal(0f, halfHeightOf(shooter), 0f);
        return new EffectLists.Cue(middle, turn, shooter, target, shot.radius());
    }

    /**
     * A shot landing: where it struck — the middle of what it hit, or where a carried shot came down — turned
     * along the way it came. Handed what it hit, where it came from, and its blast's radius.
     *
     * @param victim what it hit's node, or null
     */
    static EffectLists.Cue landed(ShotLanded shot, Spatial victim, Floor floor) {
        var where = standing(shot.where(), floor);
        var from = shot.from() == null ? null : standing(shot.from(), floor);
        return new EffectLists.Cue(where, from == null ? null : along(where.subtract(from)), victim, from,
                shot.radius());
    }

    /**
     * A blow: where on the thing it landed — its middle, or the point of it nearest a blast — turned to face the
     * way the blow came. Handed the thing, and where the blow came from.
     */
    static EffectLists.Cue hurt(uz.dukeengine.core.event.ObjectHurt hurt, Spatial victim, Floor floor) {
        var where = standing(hurt.where(), floor);
        var from = hurt.from() == null ? null : standing(hurt.from(), floor);
        return new EffectLists.Cue(where, from == null ? null : along(where.subtract(from)), victim, from, 0f);
    }

    /** A death: where the thing was, at its own height — a helicopter dies in the air — turned the way it faced. */
    static EffectLists.Cue died(ObjectDied death, Spatial thing, Floor floor) {
        return new EffectLists.Cue(standing(death.position(), floor),
                new Quaternion().fromAngleAxis(-death.orientation(), Vector3f.UNIT_Y), thing, null, 0f);
    }

    /**
     * An effect the simulation played: on the thing it rides, where the thing is drawn now, or at its point; turned
     * the way it says.
     */
    static EffectLists.Cue played(uz.dukeengine.core.event.EffectPlayed played, Spatial riding, Floor floor) {
        var at = riding != null ? riding.getWorldTranslation().clone() : standing(played.where(), floor);
        return new EffectLists.Cue(at, new Quaternion().fromAngleAxis(-played.facing(), Vector3f.UNIT_Y), riding,
                null, 0f);
    }

    /** A place of the simulation's in the client's frame, at its own height or on the floor, whichever is higher. */
    static Vector3f standing(Coord3D at, Floor floor) {
        return new Vector3f(at.x(), Math.max(floor.at(at.x(), at.y()), at.z()), at.y());
    }

    /** A turn that points an effect's forward — its x — along {@code way}, tipped up or down with it. */
    static Quaternion along(Vector3f way) {
        float across = (float) Math.sqrt(way.x * way.x + way.z * way.z);
        if (across == 0f && way.y == 0f) {
            return null;
        }
        var yaw = new Quaternion().fromAngleAxis(FastMath.atan2(-way.z, way.x), Vector3f.UNIT_Y);
        var pitch = new Quaternion().fromAngleAxis(FastMath.atan2(way.y, across), Vector3f.UNIT_Z);
        return yaw.mult(pitch);
    }

    /** How far above where a thing stands its middle is drawn: the middle of its bound, over its feet. */
    private static float halfHeightOf(Spatial thing) {
        return thing != null && thing.getWorldBound() instanceof BoundingBox box
                ? box.getCenter().y - thing.getWorldTranslation().y : 0f;
    }
}
