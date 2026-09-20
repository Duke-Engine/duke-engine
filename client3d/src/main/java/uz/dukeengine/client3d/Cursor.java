package uz.dukeengine.client3d;

/**
 * What the mouse pointer looks like in one situation. Named by the situation, because the client
 * owns those and the game owns the pictures — see {@code Cursors}.
 *
 * @param hotX how far from the left of the picture the tip is, in pixels
 * @param hotY how far from the TOP of it — read the way anyone reads a file
 */
public record Cursor(String name, String image, int hotX, int hotY, int tint) {

    /** What a block leaves out. */
    public static final Cursor DEFAULTS = new Cursor("", "", 0, 0, 0xFFFFFF);
}
