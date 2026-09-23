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
 *
 * <p>It says <b>which weapon</b>, by name, so a unit with two sounds two ways ({@code fired.<weapon>}) and a
 * client draws the right muzzle: {@code null} for a unit's one weapon written in place with no name of its
 * own, which is every weapon from before weapons had names.
 *
 * <p>And what a client needs to draw the shot where the reference draws it: <b>which slot</b> fired, so the
 * flash comes out of that slot's barrel ({@code WeaponFireFXBone = [PRIMARY Muzzle]} in the RTS this was
 * measured in, on 963 of its models' draw states); whether it is a <b>contact</b> weapon, whose flash is at
 * what it hit because it has no barrel to speak of; and the <b>radius</b> it deals its blast over, which an
 * effect list's particle systems may take as their own.
 *
 * @param slot    which of the unit's weapon slots fired, the first 0
 * @param contact whether it strikes what it touches rather than firing from a distance: the reference's
 *                {@code isContactWeapon}, a range under one pathfinding cell
 * @param radius  its blast's radius, or 0 for a shot with none
 */
public record WeaponFired(
        int frame,
        ObjectId shooter,
        ObjectId target,
        Coord3D from,
        Coord3D to,
        String weapon,
        int slot,
        boolean contact,
        float radius) implements WorldEvent {

    /** A shot from a weapon with no name of its own. */
    public WeaponFired(int frame, ObjectId shooter, ObjectId target, Coord3D from, Coord3D to) {
        this(frame, shooter, target, from, to, null);
    }

    /** A shot from the first slot, from a distance, with no blast. */
    public WeaponFired(int frame, ObjectId shooter, ObjectId target, Coord3D from, Coord3D to, String weapon) {
        this(frame, shooter, target, from, to, weapon, 0, false, 0f);
    }

    @Override
    public Coord3D where() {
        return from;
    }
}
