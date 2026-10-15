package uz.dukeengine.client3d;

/**
 * A skill aimed with no pointer — the action scheme's: from the hero along the camera's forward, at a distance the held
 * mouse's motion stretches and shortens within the skill's reach. See {@code Hotkeys.aimBy}.
 */
final class ForwardAim {

    /** How far a pixel of the held mouse's motion up or down moves the aim, in world units. */
    static final float UNITS_A_PIXEL = 0.25f;

    private final float reach;
    private float distance;

    /** Armed for a skill reaching {@code reach}: at all of it down a lane, two thirds of it at a spot. */
    ForwardAim(float reach, SkillRange.Shape shape) {
        this.reach = Math.max(0f, reach);
        this.distance = shape == SkillRange.Shape.DOWN_A_LANE ? this.reach : this.reach * 2f / 3f;
    }

    /** The held mouse moved {@code dy} pixels — up, away from him; down, toward him — within 0 and its reach. */
    void nudge(float dy) {
        distance = Math.clamp(distance - dy * UNITS_A_PIXEL, 0f, reach);
    }

    float distance() {
        return distance;
    }

    /**
     * The point it aims at, from ({@code x}, {@code y}) along the forward of a camera turned {@code yaw} — up the
     * screen, as the pan keys and the walking keys have it: in the world's x and y.
     */
    float[] point(float x, float y, float yaw) {
        return new float[] {x - (float) Math.sin(yaw) * distance, y - (float) Math.cos(yaw) * distance};
    }
}
