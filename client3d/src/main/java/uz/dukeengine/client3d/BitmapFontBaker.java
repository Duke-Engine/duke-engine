package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uz.dukeengine.client3d.platform.Face;
import uz.dukeengine.client3d.platform.GlyphImage;

/**
 * Turns a font into the pair of files jME can actually draw with.
 *
 * <p>jME does not read TTF. It draws text from a bitmap font: a PNG holding every
 * glyph once, and an AngelCode {@code .fnt} saying where each of them is and how
 * far to move afterwards. Tools that make those exist, they are Windows-only or
 * web-only, and half of what they emit is a binary format jME's loader does not
 * parse — so a game that wants its own lettering is stuck at whatever font it
 * shipped with.
 *
 * <p>This is that step, in the repository, in Java. It works on a {@link Face}, which draws and measures the
 * glyphs for the platform, and hands back the {@code .fnt} text and the atlas as ARGB pixels; the desktop's
 * {@code BakeFont} tool writes the two files:
 *
 * <pre>{@code
 * java -cp … uz.dukeengine.client3d.desktop.BakeFont Cinzel-Bold.ttf 32 out/ cinzel-32
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

    /** The {@code .fnt} text and the atlas it describes, {@code width} by {@code height} pixels of ARGB. */
    public record Baked(String fnt, int[] argb, int width, int height) {
    }

    private BitmapFontBaker() {
    }

    /**
     * Draw every character once, pack them, and describe where they landed. The face is the one asked for fractional
     * metrics ({@code Typeface.at(…, true)}), which is how the baker measures.
     *
     * @param name  what the {@code .fnt} will call its page, which must be the
     *     name the PNG is saved under — the loader reads the one to find the other
     */
    public static Baked bake(Face face, String name, String characters) {
        return bake(face, name, characters, Kerning.of(face, characters));
    }

    /** The same, with the kerning already measured — at this size or another, which it is scaled from. */
    static Baked bake(Face face, String name, String characters, Kerning kerning) {
        var glyphs = new ArrayList<Glyph>();
        for (int i = 0; i < characters.length(); i++) {
            char c = characters.charAt(i);
            if (face.canDisplay(c)) {
                glyphs.add(measureOne(face, c));
            }
        }

        int width = atlasSideFor(glyphs);
        var wide = new int[width * width];
        int used = shelfPack(glyphs, width, wide);

        // Cropped to the shelves that were filled. Square was only ever the
        // starting guess; half a texture of nothing costs memory on the card for
        // as long as the game is open.
        int height = Math.min(width, Integer.highestOneBit(Math.max(1, used - 1)) * 2);
        return new Baked(describe(face, name, glyphs, width, height, kerning),
                Arrays.copyOf(wide, width * height), width, height);
    }

    /**
     * One character's ink, measured tight.
     *
     * <p>Tight rather than by the font's own box, because a display face leaves a
     * great deal of air around a capital and packing the air wastes most of the
     * atlas. The offsets put it back where it belongs when it is drawn.
     */
    private static Glyph measureOne(Face face, char c) {
        var ink = face.draw(c);
        var glyph = new Glyph();
        glyph.code = c;
        glyph.advance = Math.round(ink.advance());
        if (ink.width() <= 0 || ink.height() <= 0) {
            return glyph; // a space has no ink, only a width
        }
        // One pixel of air on each side: bilinear filtering samples across a
        // glyph's edge, and a neighbour packed flush against it bleeds in.
        glyph.ink = ink;
        glyph.width = ink.width() + 2;
        glyph.height = ink.height() + 2;
        glyph.left = ink.left() - 1;
        glyph.top = ink.top() - face.ascent() - 1; // from the baseline, as the face puts it from the line's top
        return glyph;
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
    private static int shelfPack(List<Glyph> glyphs, int side, int[] atlas) {
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
            // The ink lands in the cell, inside its pixel of air.
            for (int row = 0; row < glyph.ink.height(); row++) {
                System.arraycopy(glyph.ink.argb(), row * glyph.ink.width(), atlas, (y + 1 + row) * side + x + 1,
                        glyph.ink.width());
            }
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
    private static String describe(Face face, String name, List<Glyph> glyphs, int width, int height,
            Kerning kerning) {
        var out = new StringBuilder();
        out.append("info face=\"").append(face.family())
                .append("\" size=").append(face.ascent() + face.descent())
                .append(" bold=0 italic=0 unicode=1 smooth=1 aa=1\n");
        out.append("common lineHeight=").append(face.ascent() + face.descent() + face.leading())
                .append(" base=").append(face.ascent())
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
                    .append(" yoffset=").append(face.ascent() + glyph.top)
                    .append(" xadvance=").append(glyph.advance)
                    .append(" page=0 chnl=0\n");
        }
        appendKerning(out, kerning, face.size());
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
     * <p>A font's kerning table is not handed over, so it is measured: the
     * width of two letters together against the two apart. Worth it on a display
     * face — an unkerned "AV" at title size is the thing that makes lettering look
     * pasted together — and it is nearly all a bake costs, so a face baked at many
     * sizes is measured once: its kerning is in its own units and grows with the size.
     */
    record Kerning(float measuredAt, List<Pair> pairs) {

        record Pair(char first, char second, double amount) {
        }

        static Kerning of(Face face, String characters) {
            var alone = new double[characters.length()];
            for (int i = 0; i < characters.length(); i++) {
                alone[i] = face.width(String.valueOf(characters.charAt(i)));
            }
            var pairs = new ArrayList<Pair>();
            for (int i = 0; i < characters.length(); i++) {
                char first = characters.charAt(i);
                if (!face.canDisplay(first)) {
                    continue;
                }
                for (int j = 0; j < characters.length(); j++) {
                    char second = characters.charAt(j);
                    if (!face.canDisplay(second)) {
                        continue;
                    }
                    double amount = face.width("" + first + second) - (alone[i] + alone[j]);
                    if (amount != 0.0) {
                        pairs.add(new Pair(first, second, amount));
                    }
                }
            }
            return new Kerning(face.size(), List.copyOf(pairs));
        }
    }

    private static final class Glyph {
        char code;
        GlyphImage ink;
        int x;
        int y;
        int width;
        int height;
        int left;
        int top;
        int advance;
    }
}
