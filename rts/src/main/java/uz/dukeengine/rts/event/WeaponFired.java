package uz.dukeengine.rts.event;

import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A weapon got a shot away — one shot, on the frame it happened.
 *
 * <p>A shot is a moment, and no amount of state describes it: "this unit is
 * attacking" is true for the whole reload cycle, so a client watching only state
 * has to guess when the trigger was actually pulled. It ends up blinking a
 * muzzle flash on a wall clock and playing the fire sound once per engagement
 * rather than once per shot.
 *
 * <p>This is the game's own event, posted on the engine's channel next to
 * {@link uz.dukeengine.core.event.ObjectDied}. The engine neither knows nor cares that
 * weapons exist — which is the point of the seam.
 */
public record WeaponFired(
        int frame,
        ObjectId shooter,
        ObjectId target,
        Coord3D from,
        Coord3D to) implements WorldEvent {

    @Override
    public Coord3D where() {
        return from;
    }
}
