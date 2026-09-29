package uz.dukeengine.client3d;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a TrueType font into the pair of files jME can actually draw with.
 *
 * <p>jME does not read TTF. It draws text from a bitmap font: a PNG holding every
 * glyph once, and an AngelCode {@code .fnt} saying where each of them is and how
 * far to move afterwards. Tools that make those exist, they are Windows-only or
 * web-only, and half of what they emit is a binary format jME's loader does not
 * parse — so a game that wants its own lettering is stuck at whatever font it
 * shipped with.
 *
 * <p>This is that step, in the repository, in Java:
 *
 * <pre>{@code
 * java -cp … uz.dukeengine.client3d.BitmapFontBaker Cinzel-Bold.ttf 32 out/ cinzel-32
 * }</pre>
 *
 * <p>A build-time tool, and it is here rather than in a game because the next
 * game will want one too; the two files it writes are what get shipped. The
 * client calls it itself only for the hero bar's lettering, baked at the size
 * each line is drawn at ({@link Lettering}).
 *
 * <p>Glyphs come out white on transparency, which is not a look but a
 * requirement: jME tints bitmap text by vertex colour, and a glyph baked in a
 * colour can only ever be multiplied darker.
 */
public final class BitmapFontBaker {

    /** ASCII, plus the few marks a menu uses that a display face still carries. */
    public static final String DEFAULT_CHARACTERS = buildDefaultCharacters();

    private static String buildDefaultCharacters() {
        var text = new StringBuilder();
        for (char c = 32; c < 127; c++) {
            text.append(c);
        }
        return text + "×·–—‹›«»";
    }

    /** The two files, as bytes, for a caller that would rather not touch a disc. */
    public record Baked(String fnt, byte[] png) {
    }

    private BitmapFontBaker() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: BitmapFontBaker <font.ttf> <size> <outDir> <name>");
            System.exit(2);
        }
        var font = Font.createFont(Font.TRUETYPE_FONT, new File(args[0]))
                .deriveFont(Font.PLAIN, Float.parseFloat(args[1]));
        var out = Path.of(args[2]);
        Files.createDirectories(out);
        var baked = bake(font, args[3], DEFAULT_CHARACTERS);
        Files.writeString(out.resolve(args[3] + ".fnt"), baked.fnt(), StandardCharsets.UTF_8);
        Files.write(out.resolve(args[3] + ".png"), baked.png());
        System.out.println("wrote " + args[3] + ".fnt and " + args[3] + ".png to " + out);
    }

    /**
     * Draw every character once, pack them, and describe where they landed.
     *
     * @param name  what the {@code .fnt} will call its page, which must be the
     *     name the PNG is saved under — the loader reads the one to find the other
     */
    public static Baked bake(Font font, String name, String characters) throws IOException {
        return bake(font, name, characters, Kerning.of(font, characters));
    }

    /** The same, with the kerning already measured — at this size or another, which it is scaled from. */
    static Baked bake(Font font, String name, String characters, Kerning kerning) throws IOException {
        var scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var measure = scratch.createGraphics();
        applyQuality(measure);
        var metrics = measure.getFontMetrics(font);
        var context = measure.getFontRenderContext();

        var glyphs = new ArrayList<Glyph>();
        for (int i = 0; i < characters.length(); i++) {
            char c = characters.charAt(i);
            if (font.canDisplay(c)) {
                glyphs.add(measureOne(font, context, metrics, c));
            }
        }
        measure.dispose();

        int width = atlasSideFor(glyphs);
        var wide = new BufferedImage(width, width, BufferedImage.TYPE_INT_ARGB);
        var paint = wide.createGraphics();
        applyQuality(paint);
        paint.setFont(font);
        paint.setColor(Color.WHITE);
        int used = shelfPack(glyphs, width, paint);
        paint.dispose();

        // Cropped to the shelves that were filled. Square was only ever the
        // starting guess; half a texture of nothing costs memory on the card for
        // as long as the game is open.
        int height = Integer.highestOneBit(Math.max(1, used - 1)) * 2;
        var atlas = wide.getSubimage(0, 0, width, Math.min(width, height));

        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(atlas, "png", png);
        return new Baked(describe(font, name, metrics, glyphs, width, atlas.getHeight(), kerning),
                png.toByteArray());
    }

    /**
     * One character's ink, measured tight.
     *
     * <p>Tight rather than by the font's own box, because a display face leaves a
     * great deal of air around a capital and packing the air wastes most of the
     * atlas. The offsets put it back where it belongs when it is drawn.
     */
    private static Glyph measureOne(Font font, FontRenderContext context,
            java.awt.FontMetrics metrics, char c) {
        var text = String.valueOf(c);
        var vector = font.createGlyphVector(context, text);
        var ink = vector.getPixelBounds(context, 0, 0);
        var glyph = new Glyph();
        glyph.code = c;
        glyph.advance = metrics.charWidth(c);
        if (ink.width <= 0 || ink.height <= 0) {
            return glyph; // a space has no ink, only a width
        }
        // One pixel of air on each side: bilinear filtering samples across a
        // glyph's edge, and a neighbour packed flush against it bleeds in.
        glyph.width = ink.width + 2;
        glyph.height = ink.height + 2;
        glyph.left = ink.x - 1;
        glyph.top = ink.y - 1;
        return glyph;
    }

    /** How tall a line of {@code font} is, ascent and descent: the size {@link #bake} says it was drawn at. */
    static int renderedSize(Font font) {
        var measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        applyQuality(measure);
        var metrics = measure.getFontMetrics(font);
        measure.dispose();
        return metrics.getAscent() + metrics.getDescent();
    }

    private static void applyQuality(java.awt.Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** The smallest power of two that holds the ink with room for shelves. */
    private static int atlasSideFor(List<Glyph> glyphs) {
        long area = 0;
        int widest = 1;
        for (var glyph : glyphs) {
            area += (long) glyph.width * glyph.height;
            widest = Math.max(widest, glyph.width);
        }
        // A third again, for the ends of shelves that no glyph quite fits.
        int side = 16;
        while ((long) side * side < area * 4 / 3 || side < widest) {
            side *= 2;
        }
        return side;
    }

    /** Rows of glyphs, each row as tall as its tallest. Simple, and tight enough. */
    private static int shelfPack(List<Glyph> glyphs, int side, java.awt.Graphics2D paint) {
        int x = 0;
        int y = 0;
        int shelfHeight = 0;
        for (var glyph : glyphs) {
            if (glyph.width == 0) {
                continue;
            }
            if (x + glyph.width > side) {
                x = 0;
                y += shelfHeight;
                shelfHeight = 0;
            }
            glyph.x = x;
            glyph.y = y;
            // Drawn at the baseline, offset so the ink lands in the cell.
            paint.drawString(String.valueOf(glyph.code), x - glyph.left, y - glyph.top);
            x += glyph.width;
            shelfHeight = Math.max(shelfHeight, glyph.height);
        }
        return y + shelfHeight;
    }

    /**
     * The {@code .fnt}, in the plain-text AngelCode format jME's loader reads.
     *
     * <p>Only the fields it actually looks at are written, which is fewer than a
     * real BMFont file carries. Anything else would be decoration for a parser
     * that skips it.
     */
    private static String describe(Font font, String name, java.awt.FontMetrics metrics,
            List<Glyph> glyphs, int width, int height, Kerning kerning) {
        var out = new StringBuilder();
        out.append("info face=\"").append(font.getFamily())
                .append("\" size=").append(metrics.getAscent() + metrics.getDescent())
                .append(" bold=0 italic=0 unicode=1 smooth=1 aa=1\n");
        out.append("common lineHeight=").append(metrics.getHeight())
                .append(" base=").append(metrics.getAscent())
                .append(" scaleW=").append(width).append(" scaleH=").append(height)
                .append(" pages=1 packed=0\n");
        out.append("page id=0 file=\"").append(name).append(".png\"\n");
        out.append("chars count=").append(glyphs.size()).append('\n');
        for (var glyph : glyphs) {
            out.append("char id=").append((int) glyph.code)
                    .append(" x=").append(glyph.x).append(" y=").append(glyph.y)
                    .append(" width=").append(glyph.width).append(" height=").append(glyph.height)
                    .append(" xoffset=").append(glyph.left)
                    // From the top of the line rather than from the baseline,
                    // which is what the format means by yoffset.
                    .append(" yoffset=").append(metrics.getAscent() + glyph.top)
                    .append(" xadvance=").append(glyph.advance)
                    .append(" page=0 chnl=0\n");
        }
        appendKerning(out, kerning, font.getSize2D());
        return out.toString();
    }

    /** The pairs at {@code size}, in whole pixels, those that come to nothing left out. */
    private static void appendKerning(StringBuilder out, Kerning kerning, float size) {
        float scale = size / kerning.measuredAt();
        var pairs = new StringBuilder();
        int count = 0;
        for (var pair : kerning.pairs()) {
            int amount = (int) Math.round(pair.amount() * scale);
            if (amount != 0) {
                pairs.append("kerning first=").append((int) pair.first())
                        .append(" second=").append((int) pair.second())
                        .append(" amount=").append(amount).append('\n');
                count++;
            }
        }
        out.append("kernings count=").append(count).append('\n').append(pairs);
    }

    /**
     * What each pair of letters owes the pair before it, at the size it was measured at.
     *
     * <p>AWT will not hand over a font's kerning table, so it is measured: the
     * width of two letters together against the two apart. Worth it on a display
     * face — an unkerned "AV" at title size is the thing that makes lettering look
     * pasted together — and it is nearly all a bake costs, so a face baked at many
     * sizes is measured once: its kerning is in its own units and grows with the size.
     */
    record Kerning(float measuredAt, List<Pair> pairs) {

        record Pair(char first, char second, double amount) {
        }

        static Kerning of(Font font, String characters) {
            var measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            applyQuality(measure);
            var context = measure.getFontRenderContext();
            measure.dispose();
            var kerned = font.deriveFont(java.util.Map.of(TextAttribute.KERNING,
                    TextAttribute.KERNING_ON));
            var alone = new double[characters.length()];
            for (int i = 0; i < characters.length(); i++) {
                alone[i] = width(kerned, context, String.valueOf(characters.charAt(i)));
            }
            var pairs = new ArrayList<Pair>();
            for (int i = 0; i < characters.length(); i++) {
                char first = characters.charAt(i);
                if (!font.canDisplay(first)) {
                    continue;
                }
                for (int j = 0; j < characters.length(); j++) {
                    char second = characters.charAt(j);
                    if (!font.canDisplay(second)) {
                        continue;
                    }
                    double amount = width(kerned, context, "" + first + second) - (alone[i] + alone[j]);
                    if (amount != 0.0) {
                        pairs.add(new Pair(first, second, amount));
                    }
                }
            }
            return new Kerning(font.getSize2D(), List.copyOf(pairs));
        }
    }

    private static double width(Font font, FontRenderContext context, String text) {
        return font.getStringBounds(text, context).getWidth();
    }

    private static final class Glyph {
        char code;
        int x;
        int y;
        int width;
        int height;
        int left;
        int top;
        int advance;
    }
}
