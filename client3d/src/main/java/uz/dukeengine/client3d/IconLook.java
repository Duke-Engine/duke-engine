package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;

/**
 * Whether the game's icons are drawings to be coloured, or pictures already
 * painted.
 *
 * <p>The panel has always <em>tinted</em> the picture in a slot. That is not
 * decoration: it is how one file serves a skill that is ready, one reloading and
 * one still locked, and it works because the pictures were white on
 * transparency and a white picture becomes whatever colour it is multiplied by.
 *
 * <p>A painted picture is the other kind. Multiply a blue frost burst by the
 * torch colour and it is a gold frost burst; multiply the grey blades of a
 * whirlwind and they are brass. So a game that paints its icons says so, and the
 * panel keeps its hands off the hue — the states are then told in
 * <b>brightness</b>, which a painted picture survives: ready is the picture as
 * it was drawn, reloading is the same picture darker, locked darker still and
 * half faded.
 *
 * <p>The armed slot is the one state that changes shape rather than shade. A
 * white icon goes cold to say the next click belongs to it; a painted one is
 * left alone, because the rim, the stone and the brackets around it have already
 * gone cold and the picture is the one thing in the socket worth still being
 * itself.
 *
 * @param paintedSkills whether the skill icons carry their own colours
 */
public record IconLook(boolean paintedSkills) {

    /** White drawings, tinted as they always were. */
    public static final IconLook DEFAULT = new IconLook(false);

    /** The picture as it was painted. */
    static final ColorRGBA AS_PAINTED = new ColorRGBA(1f, 1f, 1f, 1f);

    /** Reloading: the same picture, with the light off it. */
    static final ColorRGBA PAINTED_COLD = new ColorRGBA(0.45f, 0.45f, 0.45f, 1f);

    /** Locked: darker again, and half faded, so it reads as out of reach. */
    static final ColorRGBA PAINTED_DEAD = new ColorRGBA(0.3f, 0.3f, 0.3f, 0.5f);
}
