package uz.dukeengine.dungeon.ai;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;

/**
 * Turning to look at what you are fighting.
 *
 * <p>The engine only ever sets a heading while something is walking — the
 * locomotor points a unit at its next waypoint. That leaves a standing fighter
 * facing wherever he last happened to be going, so the hero would swing at a
 * skeleton behind his shoulder, and a skeleton that had been chased down would
 * hit him without looking at him.
 *
 * <p>Which way a fighter looks is presentation, not simulation — a shot lands the
 * same either way, and the engine's weapon does not consult facing at all. But it
 * is presentation the simulation has to produce, because the client only draws
 * what the snapshot says. So the game turns its own creatures, in the same
 * deterministic step everything else happens in.
 *
 * <p>{@code StrictMath} for the same reason the engine's locomotor uses it:
 * {@code Math.atan2} may use platform intrinsics and differ in the last bit, and
 * orientation is part of the world's checksum.
 */
public final class Facing {

    private Facing() {
    }

    /** Turn {@code fighter} to look at {@code target}, if they are not on the same spot. */
    public static void turnToward(GameObject fighter, GameObject target) {
        turnToward(fighter, target.getPosition());
    }

    /** Turn {@code fighter} to look at a spot — where he was sent, rather than at whom. */
    public static void turnToward(GameObject fighter, Coord3D spot) {
        float dx = spot.x() - fighter.getPosition().x();
        float dy = spot.y() - fighter.getPosition().y();
        if (dx == 0f && dy == 0f) {
            return; // nothing to aim at; keep the heading we have
        }
        fighter.setOrientation((float) StrictMath.atan2(dy, dx));
    }
}
