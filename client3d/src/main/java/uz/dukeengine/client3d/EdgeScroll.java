package uz.dukeengine.client3d;

/**
 * Pushing the camera by shoving the cursor against the edge of the screen.
 *
 * <p>The oldest control in the genre and still the one a hand reaches for: the
 * mouse is already where the player is looking, so moving the view costs him
 * nothing. It sits beside the keys rather than replacing them — both feed the
 * same camera, so a player may pan with one hand and shove with the other.
 *
 * <p>Off unless a game asks, because it is a matter of taste rather than of
 * correctness and three other games use this client. A margin of zero is off.
 *
 * @param marginPixels how close to the edge the cursor has to be, in window
 *                     pixels; zero turns it off
 * @param speedPercent how fast, as a percentage of what the keys move the camera
 *                     at — a share rather than a figure, so shoving and panning
 *                     stay in step at every zoom instead of one of them being
 *                     right only at one height
 */
public record EdgeScroll(float marginPixels, float speedPercent) {

    /** What the client did before any game asked: nothing. */
    public static final EdgeScroll NONE = new EdgeScroll(0f, 100f);

    public EdgeScroll {
        marginPixels = Math.max(0f, marginPixels);
        speedPercent = Math.max(0f, speedPercent);
    }

    /** Whether a game asked for it at all. */
    public boolean wanted() {
        return marginPixels > 0f && speedPercent > 0f;
    }
}
