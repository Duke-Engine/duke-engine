package uz.dukeengine.client3d.platform;

/**
 * One drawn glyph: its ink box and its pixels as ARGB, white on clear. {@code left} is from the pen and {@code top} from
 * the top of the line, whose baseline is {@link Face#ascent()} down; {@code advance} is how far the pen moves after it,
 * as the face measures it, not rounded.
 */
public record GlyphImage(int left, int top, int width, int height, float advance, int[] argb) {
}
