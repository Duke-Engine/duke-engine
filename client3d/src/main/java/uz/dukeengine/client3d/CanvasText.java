package uz.dukeengine.client3d;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import uz.dukeengine.client3d.platform.ClientPlatform;
import uz.dukeengine.client3d.platform.Face;
import uz.dukeengine.client3d.platform.GlyphRasterizer;
import uz.dukeengine.client3d.platform.Typeface;

/**
 * Text for the {@link Canvas}: faces made from the game's {@link Canvas.Font}s, each glyph drawn once into a page of
 * glyphs and kept, and a line laid out glyph by glyph — the same layout for drawing it as for measuring it, so what a
 * game measures is what it gets.
 *
 * <p>Drawn by the machine's own font renderer, antialiased, white on nothing: the colour is laid on when a glyph is
 * drawn. Advances are whole pixels, as the reference's are, so a measured line is a sum of whole numbers.
 */
final class CanvasText {

    private static final Logger LOG = Logger.getLogger(CanvasText.class.getName());

    /** How big a page of glyphs is, a side. */
    static final int PAGE = 1024;

    /** Where the first wide code point starts: the reference draws from its Unicode face from here up. */
    private static final int WIDE = 256;

    /**
     * How often each lower-case letter and the space turns up in a thousand characters of English — the weights a
     * font's average character width was defined by ({@code xAvgCharWidth}, OS/2 table versions 0 to 2), which is
     * what Windows condenses a face to when it is asked for a width. For Arial it gives 904 of 2048, the value in its
     * table.
     */
    private static final int[] LETTER_WEIGHTS = {
            64, 14, 27, 35, 100, 20, 14, 42, 63, 3, 6, 35, 20, 56, 56, 17, 4, 49, 56, 71, 31, 10, 18, 3, 18, 2};
    private static final int SPACE_WEIGHT = 166;

    /** Where a glyph is on its page, and where it is drawn from the pen at the top of the line. */
    record Glyph(int page, int x, int y, int width, int height, int left, int top, int advance) {

        boolean blank() {
            return width <= 0 || height <= 0;
        }
    }

    /** A glyph laid out, {@code x} from the line's left edge, and the family it was drawn from. */
    record Placed(Glyph glyph, int x, String family) {
    }

    /** A line laid out: its glyphs, its advance and its height. */
    record Line(List<Placed> glyphs, int width, int lineHeight) {
    }

    /** A page of glyphs, and a number that moves whenever a glyph is added to it. */
    static final class Page {
        /** ARGB, {@link #PAGE} to a row. */
        final int[] argb = new int[PAGE * PAGE];
        int version;
        private int penX;
        private int penY;
        private int rowHeight;
    }

    /** One face at one size, and the glyphs of it drawn so far. */
    private final class Sized {
        final Face face;
        final int ascent;
        final int lineHeight;
        final Map<Integer, Glyph> glyphs = new HashMap<>();

        Sized(Face face) {
            this.face = face;
            this.ascent = face.ascent();
            this.lineHeight = ascent + face.descent();
        }

        Glyph glyph(int codePoint) {
            return glyphs.computeIfAbsent(codePoint, this::draw);
        }

        private Glyph draw(int codePoint) {
            var drawn = face.draw(codePoint);
            int advance = Math.round(drawn.advance());
            if (drawn.width() <= 0 || drawn.height() <= 0) {
                return new Glyph(0, 0, 0, 0, 0, 0, 0, advance);
            }
            var page = room(drawn.width() + 2, drawn.height() + 2);
            int x = page.penX + 1;
            int y = page.penY + 1;
            for (int row = 0; row < drawn.height(); row++) {
                System.arraycopy(drawn.argb(), row * drawn.width(), page.argb, (y + row) * PAGE + x, drawn.width());
            }
            page.penX += drawn.width() + 2;
            page.rowHeight = Math.max(page.rowHeight, drawn.height() + 2);
            page.version++;
            return new Glyph(pages.indexOf(page), x, y, drawn.width(), drawn.height(), drawn.left(), drawn.top(),
                    advance);
        }
    }

    private final GlyphRasterizer glyphs = ClientPlatform.get().glyphs();
    private final Function<String, InputStream> open;
    private final Map<Canvas.Font, Sized> faces = new HashMap<>();
    private final Map<String, Typeface> families = new HashMap<>();
    private final List<Page> pages = new ArrayList<>();
    private final Set<String> missing = new HashSet<>();

    /** @param open reads a font file by its whole path from the resource root, or answers null */
    CanvasText(Function<String, InputStream> open) {
        this.open = open;
    }

    /** Every page drawn into so far. */
    List<Page> pages() {
        return pages;
    }

    /** A line of text laid out in {@code font}: every glyph, wide ones from the wide face, and the line's size. */
    Line layout(Canvas.Font font, String text) {
        var face = face(font);
        var wideFont = font.wideFamily() == null ? font
                : new Canvas.Font(font.wideFamily(), font.pixelHeight(), font.bold(), 0, null);
        var wide = face(wideFont);
        var placed = new ArrayList<Placed>();
        int x = 0;
        for (int at = 0; at < text.length(); ) {
            int codePoint = text.codePointAt(at);
            at += Character.charCount(codePoint);
            boolean isWide = codePoint >= WIDE;
            var from = isWide ? wide : face;
            var glyph = from.glyph(codePoint);
            // Set on the line's own baseline, whichever face it came from.
            placed.add(new Placed(glyph.blank() ? glyph : shifted(glyph, face.ascent - from.ascent), x,
                    (isWide ? wideFont : font).family()));
            x += glyph.advance();
        }
        return new Line(placed, x, face.lineHeight);
    }

    private static Glyph shifted(Glyph glyph, int down) {
        return down == 0 ? glyph : new Glyph(glyph.page(), glyph.x(), glyph.y(), glyph.width(), glyph.height(),
                glyph.left(), glyph.top() + down, glyph.advance());
    }

    private Sized face(Canvas.Font font) {
        return faces.computeIfAbsent(font, wanted -> {
            var typeface = family(wanted.family());
            float scale = 1f;
            if (wanted.glyphWidth() > 0) {
                float natural = averageWidth(typeface.at(wanted.pixelHeight(), wanted.bold(), 1f, false));
                if (natural > 0f) {
                    scale = wanted.glyphWidth() / natural;
                }
            }
            return new Sized(typeface.at(wanted.pixelHeight(), wanted.bold(), scale, false));
        });
    }

    /** The average character width a face is condensed by: see {@link #LETTER_WEIGHTS}. */
    static float averageWidth(Face face) {
        float sum = SPACE_WEIGHT * advanceOf(face, ' ');
        for (int letter = 0; letter < LETTER_WEIGHTS.length; letter++) {
            sum += LETTER_WEIGHTS[letter] * advanceOf(face, (char) ('a' + letter));
        }
        return sum / 1000f;
    }

    private static float advanceOf(Face face, char c) {
        return (float) face.width(String.valueOf(c));
    }

    private Typeface family(String family) {
        if (family == null) {
            return glyphs.system(null);
        }
        return families.computeIfAbsent(family, name -> {
            var lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
                return glyphs.system(name);
            }
            try (var in = open.apply(name)) {
                var typeface = in == null ? null : glyphs.load(in.readAllBytes());
                if (typeface != null) {
                    return typeface;
                }
            } catch (IOException _) {
                // said below, once, like a file that is not there
            }
            if (missing.add(name)) {
                LOG.warning("no font file " + name + "; drawing its text in the machine's own face");
            }
            return glyphs.system(null);
        });
    }

    /** A page with a free spot this big: the current one, the next row of it, or a new page. */
    private Page room(int width, int height) {
        var page = pages.isEmpty() ? null : pages.getLast();
        if (page != null && page.penX + width > PAGE) {
            page.penX = 0;
            page.penY += page.rowHeight;
            page.rowHeight = 0;
        }
        if (page == null || page.penY + height > PAGE) {
            page = new Page();
            pages.add(page);
        }
        return page;
    }
}
