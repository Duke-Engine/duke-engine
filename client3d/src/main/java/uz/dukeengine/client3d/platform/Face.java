package uz.dukeengine.client3d.platform;

/**
 * A font at one size: what it measures and how it draws a character. Which of two measuring paths it takes is chosen
 * when it is made ({@link Typeface#at}): without fractional metrics it measures as a game's canvas does, a line from
 * the font's line metrics and a glyph by its own advance; with them, as the bitmap baker does, a line from the font's
 * metrics and a glyph by its whole-pixel width, and a text with its kerning.
 */
public interface Face {

    /** The name of the font's family. */
    String family();

    /** The size it was made at, in points. */
    float size();

    /** Pixels from the top of a line to its baseline. */
    int ascent();

    /** Pixels from the baseline to the bottom of a line. */
    int descent();

    /** Pixels between one line and the next. */
    int leading();

    /** Whether the font has a glyph of its own for this code point. */
    boolean canDisplay(int codePoint);

    /**
     * The width of the text in pixels: with fractional metrics, each character's advance and the pairs' kerning; without,
     * the sum of the characters' own advances.
     */
    double width(String text);

    /** The glyph for this code point, or one with no pixels where the font has none. */
    GlyphImage draw(int codePoint);
}
