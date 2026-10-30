package uz.dukeengine.client3d.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import java.io.IOException;
import java.nio.ByteBuffer;
import uz.dukeengine.client3d.platform.Face;
import uz.dukeengine.client3d.platform.GlyphImage;
import uz.dukeengine.client3d.platform.GlyphRasterizer;
import uz.dukeengine.client3d.platform.Typeface;

/**
 * Fonts and glyphs from android.graphics: a {@link Paint} measures and draws, antialiased, white on nothing. With
 * fractional metrics a face measures as the paint does, kerning and all; without, each glyph's advance is rounded to
 * whole pixels, as the desktop's face measures then.
 */
final class PaintGlyphs implements GlyphRasterizer {

    @Override
    public Typeface load(byte[] fontFile) {
        try {
            // A font is read only from a direct buffer.
            var bytes = ByteBuffer.allocateDirect(fontFile.length);
            bytes.put(fontFile).rewind(); // exactly full, so back to the start is all a flip would do
            var family = new FontFamily.Builder(new Font.Builder(bytes).build()).build();
            return new PaintTypeface(new android.graphics.Typeface.CustomFallbackBuilder(family).build(), "font file");
        } catch (IOException | IllegalArgumentException _) {
            return null;
        }
    }

    @Override
    public Typeface system(String family) {
        return new PaintTypeface(family == null ? android.graphics.Typeface.DEFAULT
                : android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL),
                family == null ? "default" : family);
    }

    private record PaintTypeface(android.graphics.Typeface typeface, String family) implements Typeface {

        @Override
        public Face at(float size, boolean bold, float scaleX, boolean fractionalMetrics) {
            var paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(bold ? android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
                    : typeface);
            paint.setTextSize(size);
            paint.setTextScaleX(scaleX);
            paint.setSubpixelText(fractionalMetrics);
            paint.setColor(Color.WHITE);
            return new PaintFace(paint, family, fractionalMetrics);
        }
    }

    private static final class PaintFace implements Face {

        private final Paint paint;
        private final String family;
        private final boolean fractional;
        private final int ascent;
        private final int descent;
        private final int leading;

        PaintFace(Paint paint, String family, boolean fractional) {
            this.paint = paint;
            this.family = family;
            this.fractional = fractional;
            var metrics = paint.getFontMetrics();
            ascent = (int) Math.ceil(-metrics.ascent);
            descent = (int) Math.ceil(metrics.descent);
            leading = (int) Math.ceil(metrics.leading);
        }

        @Override
        public String family() {
            return family;
        }

        @Override
        public float size() {
            return paint.getTextSize();
        }

        @Override
        public int ascent() {
            return ascent;
        }

        @Override
        public int descent() {
            return descent;
        }

        @Override
        public int leading() {
            return leading;
        }

        @Override
        public boolean canDisplay(int codePoint) {
            return paint.hasGlyph(new String(Character.toChars(codePoint)));
        }

        @Override
        public double width(String text) {
            if (fractional) {
                return paint.measureText(text);
            }
            return text.codePoints().mapToDouble(this::advance).sum();
        }

        @Override
        public GlyphImage draw(int codePoint) {
            var text = new String(Character.toChars(codePoint));
            float advance = advance(codePoint);
            var ink = new Rect();
            paint.getTextBounds(text, 0, text.length(), ink);
            if (ink.isEmpty()) {
                return new GlyphImage(0, 0, 0, 0, advance, new int[0]);
            }
            // A pixel round the bounds the paint gives, which its antialiased edges reach into.
            int left = ink.left - 1;
            int top = ink.top - 1;
            int width = ink.width() + 2;
            int height = ink.height() + 2;
            var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            new Canvas(bitmap).drawText(text, -left, -top, paint);
            var argb = new int[width * height];
            bitmap.getPixels(argb, 0, width, 0, 0, width, height); // unpremultiplied, as the desktop's are
            bitmap.recycle();
            return new GlyphImage(left, ascent + top, width, height, advance, argb);
        }

        private float advance(int codePoint) {
            float measured = paint.measureText(new String(Character.toChars(codePoint)));
            return fractional ? measured : Math.round(measured);
        }
    }
}
