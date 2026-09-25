package uz.dukeengine.client3d;

/**
 * The right button held scrolls the view, under either arrangement of the mouse — the reference's {@code SCROLL_RMB},
 * which never asks which button commands — and does what it does, lets go of the selection or gives up an armed button
 * under {@link Mouse#LEFT_COMMANDS}, gives its order under {@link Mouse#RIGHT_COMMANDS}, only when it is let go as a
 * click. While it is held the pointer is the scroll picture, pointing the way the view moves, or right while it does
 * not. Where it went down is an anchor: each frame the view scrolls by how far the
 * pointer is from it, so a pointer held still away from it keeps the view moving, and the anchor is dragged after the
 * pointer so that it is never further behind than {@code reach}.
 *
 * @param across      how fast the view scrolls across the screen for each pixel the pointer is to the side of the
 *                    anchor, in world units a second
 * @param along       and up and down it, along the ground, for each pixel above or below
 * @param reach       how far behind the pointer the anchor may be left, as a share of the window — across of its
 *                    width, down of its height (the reference's {@code MoveRMBScrollAnchor}: half); 0 never drags it
 * @param clickPixels how far from where it went down the button may be let go and still be a click, either way (the
 *                    reference's {@code DragTolerance}, 25)
 * @param clickMillis how long after it went down (its {@code DragToleranceMS}, 250)
 * @param clickMoved  how far the view may have moved meanwhile, in world units (its {@code DragTolerance3D}, 25)
 * @param floor       pixels the pointer counts as further from the anchor than it is, the way it is from it — the
 *                    reference adds its factors times the player's speed squared each frame, a quarter of a pixel's
 *                    worth at its middle speed and a whole pixel's at the fastest; the game changes it with that
 *                    option. At the anchor, nothing
 */
public record RightDrag(float across, float along, float reach, float clickPixels, float clickMillis,
        float clickMoved, float floor) {

    /** None: the right button acts the moment it goes down, as it always did. */
    public static final RightDrag NONE = new RightDrag(0f, 0f, 0f, 0f, 0f, 0f, 0f);

    /** A drag with no floor, as every one was before it could have one. */
    public RightDrag(float across, float along, float reach, float clickPixels, float clickMillis, float clickMoved) {
        this(across, along, reach, clickPixels, clickMillis, clickMoved, 0f);
    }

    /** Whether a game asked for it at all. */
    public boolean wanted() {
        return !equals(NONE);
    }
}
