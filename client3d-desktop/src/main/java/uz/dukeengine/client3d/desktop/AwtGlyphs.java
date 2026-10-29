package uz.dukeengine.client3d.desktop;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import uz.dukeengine.client3d.platform.Face;
import uz.dukeengine.client3d.platform.GlyphImage;
import uz.dukeengine.client3d.platform.GlyphRasterizer;
import uz.dukeengine.client3d.platform.Typeface;

/**
 * Fonts and glyphs from AWT: the machine's own font renderer, antialiased, white on nothing. A face measures one of two
 * ways, as it was asked for fractional metrics or not, and each is exactly what the canvas's text and the bitmap baker
 * measured with before they went through here.
 */
final class AwtGlyphs implements GlyphRasterizer {

    @Override
    public Typeface load(byte[] fontFile) {
        try {
            return new AwtTypeface(Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(fontFile)));
        } catch (IOException | FontFormatException _) {
            return null;
        }
    }

    @Override
    public Typeface system(String family) {
        return new AwtTypeface(new Font(family == null ? Font.DIALOG : family, Font.PLAIN, 1));
    }

    private record AwtTypeface(Font font) implements Typeface {

        @Override
        public Face at(float size, boolean bold, float scaleX, boolean fractionalMetrics) {
            var sized = font.deriveFont(bold ? Font.BOLD : Font.PLAIN, size);
            if (scaleX != 1f) {
                sized = sized.deriveFont(AffineTransform.getScaleInstance(scaleX, 1.0));
            }
            return new AwtFace(sized, fractionalMetrics);
        }
    }

    private static final class AwtFace implements Face {

        /** Antialiased edges, whole-pixel advances: the canvas's text is measured and drawn with this alike. */
        private static final FontRenderContext WHOLE = new FontRenderContext(null, true, false);

        private final Font font;
        private final boolean fractional;
        /** Fractional metrics: the baker's way, through a graphics' font metrics, and kerning measured on a kerned font. */
        private final FontMetrics metrics;
        private final FontRenderContext context;
        private final Font kerned;
        private final int ascent;
        private final int descent;
        private final int leading;

        AwtFace(Font font, boolean fractional) {
            this.font = font;
            this.fractional = fractional;
            if (fractional) {
                var scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
                hints(scratch, true);
                metrics = scratch.getFontMetrics(font);
                context = scratch.getFontRenderContext();
                scratch.dispose();
                kerned = font.deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));
                ascent = metrics.getAscent();
                descent = metrics.getDescent();
                leading = metrics.getLeading();
            } else {
                var line = font.getLineMetrics("Hg", WHOLE);
                metrics = null;
                context = WHOLE;
                kerned = null;
                ascent = (int) Math.ceil(line.getAscent());
                descent = (int) Math.ceil(line.getDescent());
                leading = (int) Math.ceil(line.getLeading());
            }
        }

        @Override
        public String family() {
            return font.getFamily();
        }

        @Override
        public float size() {
            return font.getSize2D();
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
            return font.canDisplay(codePoint);
        }

        @Override
        public double width(String text) {
            if (fractional) {
                return kerned.getStringBounds(text, context).getWidth();
            }
            var vector = font.createGlyphVector(context, text);
            double sum = 0;
            for (int glyph = 0; glyph < vector.getNumGlyphs(); glyph++) {
                sum += vector.getGlyphMetrics(glyph).getAdvanceX();
            }
            return sum;
        }

        @Override
        public GlyphImage draw(int codePoint) {
            var text = new String(Character.toChars(codePoint));
            var vector = font.createGlyphVector(context, text);
            // Fractional: the whole vector's ink about the baseline, the advance the whole pixels of the metrics.
            // Otherwise the glyph's own ink about a baseline `ascent` down, and its advance as it is.
            var ink = fractional ? vector.getPixelBounds(context, 0, 0)
                    : vector.getGlyphPixelBounds(0, context, 0f, ascent);
            float advance = fractional ? metrics.charWidth(codePoint) : vector.getGlyphMetrics(0).getAdvanceX();
            int top = fractional ? ink.y + ascent : ink.y;
            if (ink.width <= 0 || ink.height <= 0) {
                return new GlyphImage(0, 0, 0, 0, advance, new int[0]);
            }
            var image = new BufferedImage(ink.width, ink.height, BufferedImage.TYPE_INT_ARGB);
            var pen = image.createGraphics();
            try {
                hints(pen, fractional);
                pen.setColor(Color.WHITE);
                if (fractional) {
                    pen.setFont(font);
                    pen.drawString(text, -ink.x, -ink.y);
                } else {
                    pen.drawGlyphVector(vector, -ink.x, -ink.y + ascent);
                }
            } finally {
                pen.dispose();
            }
            return new GlyphImage(ink.x, top, ink.width, ink.height, advance,
                    image.getRGB(0, 0, ink.width, ink.height, null, 0, ink.width));
        }

        private static void hints(Graphics2D pen, boolean fractional) {
            pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (fractional) {
                pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                pen.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            } else {
                pen.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                pen.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            }
        }
    }
}
