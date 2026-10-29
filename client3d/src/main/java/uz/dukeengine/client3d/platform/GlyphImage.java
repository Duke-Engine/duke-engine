package uz.dukeengine.client3d.platform;

/**
 * One drawn glyph: its box relative to the pen, how far the pen moves after it, and its pixels as ARGB, white on clear.
 */
public record GlyphImage(int left, int top, int width, int height, float advance, int[] argb) {
}
