package uz.duke.client3d;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

/**
 * Where the light comes from and how much of it there is.
 *
 * <p>One sun and one flat ambient, which is all a scene of flat-shaded palette art
 * wants. What it is <em>worth</em> is the game's: a cellar and a wood at noon are
 * lit by the same mechanism and not by the same numbers, and the one number that
 * decides whether the picture has any depth in it at all is how far off vertical
 * the sun stands.
 *
 * <p><b>Pitch is the one that matters.</b> A sun straight overhead meets a floor
 * and the lid over a wall at exactly the same angle, so the shader shades them the
 * same and the map comes out flat — there is no relief in it because there is no
 * light to make any. Tilt it and every surface that stands up catches a different
 * share, and the floor plan turns back into a place with heights in it. Too low and
 * the far side of everything goes black, which is worse than flat.
 *
 * <p>Held here rather than in the client's own constants because the client draws
 * more than one game, and because the two places that need it — the scene's own
 * light, which lights the creatures, and the terrain shader, which lights the
 * ground — must be told the same thing. One value, two readers; they used to be two
 * constants a few hundred lines apart.
 *
 * @param pitch    degrees the sun stands above the horizon: 90 is directly
 *                 overhead and draws no relief at all, 0 is on the horizon and
 *                 throws half the world into the dark
 * @param yaw      degrees round the compass it comes from, which decides which
 *                 side of a wall is the lit one
 * @param strength how bright the sun itself is, as a multiplier on its colour
 * @param ambient  how much light everything gets whether the sun reaches it or
 *                 not. The whole of what keeps an unlit face from being black,
 *                 and the whole of what washes the relief out again if it is too
 *                 high — it is the sun's opposite and wants reading with it
 * @param colour   what colour the sun is, packed {@code 0xRRGGBB}
 * @param ambientTint what colour the shadowed side is, packed {@code 0xRRGGBB}
 */
public record Sunlight(float pitch, float yaw, float strength, float ambient,
        int colour, int ambientTint) {

    /**
     * What the client lit every scene with before a game could say, to the last
     * place the old constants had.
     *
     * <p>Worked back out of them rather than picked: the direction was written as
     * the vector {@code (-0.4, -1, -0.5)}, which normalises to 57.37 degrees above
     * the horizon and 218.66 round. The sun was {@code (1, 0.97, 0.9)}, which is
     * {@code 0xFFF7E6} exactly. The ambient was {@code (0.45, 0.45, 0.5)}, which is
     * a blue-white at half strength — and splitting it that way is the point of
     * having two fields: what colour the shadowed side is, and how much of it there
     * is, are tuned separately and were tangled in one triple before.
     */
    public static final Sunlight DEFAULT =
            new Sunlight(57.37f, 218.66f, 1f, 0.5f, 0xFFF7E6, 0xE6E6FF);

    public Sunlight {
        // Not clamped to the horizon: a sun at or below it lights nothing, and a
        // sun past the zenith is the same sun coming the other way.
        pitch = Math.clamp(pitch, 5f, 90f);
        strength = Math.max(0f, strength);
        ambient = Math.max(0f, ambient);
    }

    /**
     * The way the light travels, which is the way <em>down</em> from where the sun
     * stands — jME's directional light points along its travel, not at its source.
     */
    public Vector3f direction() {
        float up = FastMath.DEG_TO_RAD * pitch;
        float round = FastMath.DEG_TO_RAD * yaw;
        float flat = FastMath.cos(up);
        return new Vector3f(flat * FastMath.sin(round), -FastMath.sin(up),
                flat * FastMath.cos(round)).normalizeLocal();
    }

    /** The sun's colour at its strength. */
    public ColorRGBA sunColour() {
        return unpack(colour).multLocal(strength);
    }

    /** The shadowed side's colour at its strength. */
    public ColorRGBA ambientColour() {
        return unpack(ambientTint).multLocal(ambient);
    }

    private static ColorRGBA unpack(int packed) {
        return new ColorRGBA((packed >> 16 & 0xFF) / 255f, (packed >> 8 & 0xFF) / 255f,
                (packed & 0xFF) / 255f, 1f);
    }
}
