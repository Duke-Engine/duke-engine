package uz.duke.dungeon.combat;

import uz.duke.core.math.Coord3D;
import uz.duke.core.module.DamageType;
import uz.duke.core.thing.GameObject;

/**
 * Putting an arrow in the air.
 *
 * <p>Both the things that shoot in this dungeon do it the same way, and neither
 * of them should have to know how. The hero's bow ({@link Bow}) hands its shots
 * over through the engine's launcher seam; his heavy shot comes from a skill,
 * which is not a weapon at all and reaches this directly. What they share is
 * everything after the decision: an arrow of some template, appearing at the bow
 * rather than inside the archer, chasing what it was loosed at.
 */
public final class Shot {

    private Shot() {
    }

    /**
     * Loose an arrow of {@code template} at {@code victim}, carrying {@code damage}.
     *
     * @param muzzleOffset  how far out in front of the archer it appears
     * @return whether one actually left — false if the template is missing or is
     *     not an arrow, so a caller can still land the damage the plain way rather
     *     than swallowing the shot
     */
    public static boolean loose(GameObject shooter, GameObject victim, float damage,
            DamageType type, String template, float speed, float muzzleOffset) {
        var world = shooter.getWorld();
        if (world == null || template == null || template.isBlank()) {
            return false;
        }
        var thing = world.findTemplate(template);
        if (thing == null) {
            return false;
        }
        var arrow = world.spawn(thing, atTheBow(shooter, victim, muzzleOffset),
                shooter.getPlayerIndex());
        var flight = arrow.findModule(ArrowUpdate.class);
        if (flight == null) {
            arrow.markDestroyed();
            return false; // the template exists but is not an arrow
        }
        flight.loose(shooter, victim, damage, type, speed);
        return true;
    }

    /**
     * Where the arrow appears: out at the bow rather than in the middle of the
     * archer.
     *
     * <p>Starting it at his position puts it inside him, and what the player sees
     * is a shaft squeezing out of his chest and setting off. The bow is held out
     * in front, and in front is where he is facing — he turns to shoot, so his
     * heading is the line of the shot.
     *
     * <p>Never further out than the target is, so a shot at something almost
     * touching him does not begin behind it and have to turn round.
     */
    private static Coord3D atTheBow(GameObject shooter, GameObject victim, float offset) {
        var from = shooter.getPosition();
        float reach = Math.min(offset, from.distance(victim.getPosition()) * 0.5f);
        float facing = shooter.getOrientation();
        return new Coord3D(
                from.x() + (float) StrictMath.cos(facing) * reach,
                from.y() + (float) StrictMath.sin(facing) * reach,
                from.z());
    }
}
