package uz.dukeengine.client3d.platform;

/** Turns a font into glyphs: the one door to a platform's text drawing. */
public interface GlyphRasterizer {

    /** The font in these file bytes, or {@code null} when they are not a font. */
    Typeface load(byte[] fontFile);

    /** The machine's font of this family, or its own face for a {@code null} family. */
    Typeface system(String family);
}
