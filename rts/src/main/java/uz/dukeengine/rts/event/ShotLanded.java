package uz.dukeengine.rts.event;

import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A shot's damage landed — where it struck, on the frame it did. Posted alike for a shot that hits the instant
 * it is fired, at its victim, and for one a launcher carried, where it came down.
 *
 * <p>The other half of {@link WeaponFired}. Firing and landing are the same frame for a rifle and seconds apart
 * for a shell, and a client drawing the burst has no way to tell which from a snapshot: a shell that lands where
 * its target used to stand has no target to point at.
 *
 * @param shooter what fired it; may be gone by the time a carried shot lands
 * @param victim  what it struck, or {@code null} for a shot that came down on open ground
 * @param weapon  the name of the weapon that fired it, or {@code null} for one with no name of its own
 * @param where   where it struck: the middle of what it hit, for a shot that hit at once — halfway up it, not at
 *                its feet — and where it came down, for one a launcher carried
 * @param from    where it came from, so what is drawn there can face the way it travelled
 * @param radius  its blast's radius, or 0 for a shot with none
 */
public record ShotLanded(int frame, ObjectId shooter, ObjectId victim, String weapon, Coord3D where, Coord3D from,
        float radius) implements WorldEvent {

    @Override
    public Coord3D where() {
        return where;
    }
}
