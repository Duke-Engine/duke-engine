package uz.dukeengine.client3d;

/**
 * What a game draws its own screens with: four primitives and text, in screen pixels from the top left, drawn in the
 * order they are called, over the world and over the client's own HUD. Handed to the game's {@link Painter} every
 * frame the window draws, on the window's thread.
 *
 * <p>Not a menu system. An RTS's front end is a large, exactly specified thing — windows, buttons, lists, sliders,
 * transitions — and every piece of it is the game's; the reference draws all of it with these same primitives, and
 * a game builds its window manager on them. What is here is only what that needs from a machine: pictures, filled
 * and outlined rectangles, lines and triangles, a clip rectangle, and text set in a face the game names.
 *
 * <p>Pictures and faces are loaded the first time they are drawn and kept. A picture that will not load draws
 * nothing and is said once in the log.
 */
public interface Canvas {

    /**
     * How a picture is laid over what is under it: blended by its alpha, added, laid over whole, or blended by its
     * alpha in grey — the reference's {@code DRAW_IMAGE_ALPHA}, {@code _ADDITIVE}, {@code _SOLID} and
     * {@code _GRAYSCALE}.
     */
    enum Blend { ALPHA, ADDITIVE, SOLID, GRAYSCALE }

    /**
     * A picture: a file, by its whole path from the resource root, or the part of it from {@code left, top} to
     * {@code right, bottom} in its pixels (right and bottom exclusive) — one picture of an atlas.
     *
     * @param turned whether the file holds it turned a quarter clockwise, as an atlas may to fit it in; it is drawn
     *     upright
     */
    record Image(String path, int left, int top, int right, int bottom, boolean turned) {

        /** The whole of a file. */
        public static Image of(String path) {
            return new Image(path, 0, 0, 0, 0, false);
        }

        /** Whether it is the whole file rather than a part of it. */
        public boolean whole() {
            return right <= left || bottom <= top;
        }
    }

    /**
     * A face to set text in.
     *
     * @param family     a font installed on the machine, or a font file (a path ending {@code .ttf} or
     *     {@code .otf}, whole from the resource root)
     * @param pixelHeight its em height in pixels
     * @param glyphWidth what its average character width is forced to, in pixels, as a Windows font asked for a
     *     width is condensed or widened to it — the reference's "Generals" face is Arial with this at 40% of the
     *     height; 0 for the face's own
     * @param wideFamily the face every code point from 256 up is drawn from, at the same height and weight and at
     *     its own width — the reference draws Cyrillic from plain Arial whatever a window's face is; null for none
     */
    record Font(String family, int pixelHeight, boolean bold, int glyphWidth, String wideFamily) {

        public Font {
            pixelHeight = Math.max(1, pixelHeight);
            glyphWidth = Math.max(0, glyphWidth);
        }

        public static Font of(String family, int pixelHeight) {
            return new Font(family, pixelHeight, false, 0, null);
        }

        public Font bold(boolean bold) {
            return new Font(family, pixelHeight, bold, glyphWidth, wideFamily);
        }

        public Font glyphWidth(int pixels) {
            return new Font(family, pixelHeight, bold, pixels, wideFamily);
        }

        public Font wideFamily(String family) {
            return new Font(this.family, pixelHeight, bold, glyphWidth, family);
        }
    }

    /** How much room a line of text takes: its advance, in whole pixels, and the height of its line. */
    record Measure(int width, int lineHeight) {
    }

    /** The screen's width in pixels. */
    int width();

    /** The screen's height in pixels. */
    int height();

    /**
     * A picture over {@code [x0, x1) × [y0, y1)}, stretched to fit and modulated by {@code argb}, clipped picture
     * and all to the clip rectangle. Given upside down — {@code y1} above {@code y0} — it is drawn upside down.
     */
    void drawImage(Image image, float x0, float y0, float x1, float y1, int argb, Blend blend);

    /** {@code [x, x + w) × [y, y + h)} filled, blended by the colour's alpha. */
    void fillRect(float x, float y, float w, float h, int argb);

    /** The outline of {@code [x, x + w) × [y, y + h)}, {@code lineWidth} wide, inside it. */
    void openRect(float x, float y, float w, float h, float lineWidth, int argb);

    /** A filled triangle, blended by the colour's alpha. */
    void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb);

    /** A line {@code width} wide from one point to the other. */
    void line(float x0, float y0, float x1, float y1, float width, int argb);

    /** The same, shading from {@code argb} at its start to {@code argbEnd} at its end. */
    void line(float x0, float y0, float x1, float y1, float width, int argb, int argbEnd);

    /** Whatever is drawn from now on is clipped to {@code [x0, x1) × [y0, y1)}. */
    void clip(float x0, float y0, float x1, float y1);

    /** Nothing is clipped from now on. */
    void noClip();

    /**
     * One line of text in {@code font}, the top left of its line at {@code (x, y)}, antialiased and in
     * {@code argb}. A game that wants a shadow draws the line twice.
     */
    void drawText(Font font, String text, float x, float y, int argb);

    /** How wide a line of text is as {@link #drawText} lays it out — the sum of its advances — and how tall. */
    Measure measure(Font font, String text);
}
