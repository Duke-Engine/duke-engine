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
     * Loose an arrow <em>down a line</em>, to hit whoever is standing in the way.
     *
     * <p>The opposite bargain from {@link #loose}. That one is given a victim and
     * chases it, so it never misses and the skill is a click; this one is given a
     * direction and forgets it at once, so it can miss and the skill is a
     * judgement. Everything after the loosing is the same arrow.
     *
     * @param towards  where the player pointed — a direction, not a destination:
     *                 what matters is which way, and {@code distance} says how far
     * @param distance how far it travels before it is spent
     * @return whether one actually left
     */
    public static boolean looseAlong(GameObject shooter, Coord3D towards, float damage,
            DamageType type, String template, float speed, float muzzleOffset, float distance) {
        var world = shooter.getWorld();
        if (world == null || template == null || template.isBlank() || towards == null) {
            return false;
        }
        var thing = world.findTemplate(template);
        if (thing == null) {
            return false;
        }
        var arrow = world.spawn(thing, alongThatWay(shooter, towards, muzzleOffset),
                shooter.getPlayerIndex());
        var flight = arrow.findModule(ArrowUpdate.class);
        if (flight == null) {
            arrow.markDestroyed();
            return false;
        }
        flight.looseAlong(shooter, towards, damage, type, speed, distance);
        return true;
    }

    /** The bow again, but pointed at a place rather than at a creature. */
    private static Coord3D alongThatWay(GameObject shooter, Coord3D towards, float offset) {
        var from = shooter.getPosition();
        float reach = Math.min(offset, from.distance(towards) * 0.5f);
        float facing = (float) StrictMath.atan2(towards.y() - from.y(), towards.x() - from.x());
        return new Coord3D(
                from.x() + (float) StrictMath.cos(facing) * reach,
                from.y() + (float) StrictMath.sin(facing) * reach,
                from.z());
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
