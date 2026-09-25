package uz.dukeengine.client3d;

/**
 * The right button held scrolls the view, under the {@link Mouse#LEFT_COMMANDS} arrangement whose right button lets
 * the selection go — the reference's {@code SCROLL_RMB} — and lets go of the selection, or gives up an armed button,
 * only when it is let go as a click. Where it went down is an anchor: each frame the view scrolls by how far the
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
 */
public record RightDrag(float across, float along, float reach, float clickPixels, float clickMillis,
        float clickMoved) {

    /** None: the right button lets go the moment it goes down, as it always did. */
    public static final RightDrag NONE = new RightDrag(0f, 0f, 0f, 0f, 0f, 0f);

    /** Whether a game asked for it at all. */
    public boolean wanted() {
        return !equals(NONE);
    }
}
