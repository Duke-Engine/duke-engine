package uz.dukeengine.client3d.platform;

/** A font at one size: what it measures and how it draws a character. */
public interface Face {

    int ascent();

    int descent();

    int leading();

    /** The width of the text in pixels, with each character's advance and the pairs' kerning. */
    float width(String text);

    /** The glyph for this code point, or one with no pixels where the font has none. */
    GlyphImage draw(int codePoint);
}
