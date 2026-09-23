package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import uz.dukeengine.core.data.Paint;
import uz.dukeengine.core.map.MapTemplate;
import uz.dukeengine.core.map.MapTerrain;
import uz.dukeengine.core.map.Painted;

/**
 * What a map's ground is painted with, read off the map and sorted into the few surfaces it actually uses.
 *
 * <p>The engine's half of this shipped in 0.4.1 and nothing answered it: {@link Painted} asks what each
 * character of the {@link Paint} rows stands for, and the ground was drawn as one coloured quad whatever the
 * answer was. This is the reading; {@link TerrainScene} is the drawing.
 *
 * <p><b>A palette value is used exactly as written.</b> No folder is put in front of it and no suffix is
 * taken off, because where a game keeps its art is that game's arrangement and a path invented here is a
 * path that game cannot move. The only thing read into it at all is whether it begins with a {@code #},
 * which makes it a colour — so a game that ships no textures still gets painted ground, and the feature is
 * not only for games that do.
 *
 * <p><b>It reads like a file, not like a renderer.</b> A map is something somebody edits by hand, so a row
 * of the wrong length or a character the palette forgot is said out loud — naming the map, the row and the
 * character — and the rest of the ground is drawn. Half a painted map beats a black one and a stack trace.
 */
final class GroundPaint {

    private static final Logger LOG = Logger.getLogger(GroundPaint.class.getName());

    /** Where the palette says nothing: one picture to a cell, which is right for a colour. */
    private static final float ONE_CELL = 1f;

    /** What one palette entry is: a colour, or a picture named the way the map named it. */
    record Surface(ColorRGBA colour, String texture) {

        /** A picture is laid over white, so the texture is what is seen; a colour has no picture. */
        static Surface of(String named) {
            var colour = colourOf(named);
            return colour == null ? new Surface(ColorRGBA.White.clone(), named) : new Surface(colour, null);
        }
    }

    /** One surface and every cell painted with it, as {@code cy * width + cx}. */
    record Patch(Surface surface, float coverage, int[] cells) {
    }

    private final String what;
    private final List<String> rows;
    private final Map<String, String> palette;
    private final Map<String, Float> coverage;

    private GroundPaint(String what, List<String> rows, Map<String, String> palette,
            Map<String, Float> coverage) {
        this.what = what;
        this.rows = rows;
        this.palette = palette;
        this.coverage = coverage;
    }

    /**
     * The paint a map carries, or {@code null} where it carries none — which is every map written before
     * this and every map that never wanted it. Both halves are needed: rows marked {@link Paint} to say
     * which cell is which, and a {@link Painted} palette to say what each of those characters is.
     */
    static GroundPaint of(Object map) {
        if (!(map instanceof Painted painted)) {
            return null;
        }
        var rows = MapTerrain.rows(map, Paint.class, "paint");
        if (rows.isEmpty() || painted.palette() == null || painted.palette().isEmpty()) {
            return null;
        }
        var coverage = painted.coverage() == null ? Map.<String, Float>of() : painted.coverage();
        return new GroundPaint(named(map), rows, painted.palette(), coverage);
    }

    /**
     * The cells of the map gathered by what they are painted with: one patch a palette entry, in the order
     * the palette was written, and none at all for an entry no cell uses.
     *
     * <p>One mesh a surface rather than one a cell, which is the whole reason to gather them: a converted
     * Command &amp; Conquer map is sixty thousand cells and a dozen pictures, and the difference between
     * twelve geometries and sixty thousand is the difference between a map that opens and one that does not.
     */
    List<Patch> patches(int width, int height) {
        var cells = new LinkedHashMap<String, List<Integer>>();
        for (var key : palette.keySet()) {
            cells.put(key, new ArrayList<>());
        }
        var unnamed = new LinkedHashMap<String, Integer>();
        for (int cy = 0; cy < height && cy < rows.size(); cy++) {
            var row = rows.get(cy);
            if (row.length() != width) {
                LOG.log(Level.WARNING, "{0}: paint row {1} is {2} cells wide where the map is {3}",
                        new Object[] {what, cy + 1, row.length(), width});
            }
            for (int cx = 0; cx < width && cx < row.length(); cx++) {
                var key = String.valueOf(row.charAt(cx));
                var painted = cells.get(key);
                if (painted == null) {
                    unnamed.merge(key, 1, Integer::sum);
                    continue;
                }
                painted.add(cy * width + cx);
            }
        }
        if (rows.size() != height) {
            LOG.log(Level.WARNING, "{0}: {1} rows of paint for {2} rows of cells",
                    new Object[] {what, rows.size(), height});
        }
        unnamed.forEach((key, count) -> LOG.log(Level.WARNING,
                "{0}: the palette does not say what ''{1}'' is, and {2} cell(s) are painted with it",
                new Object[] {what, key, count}));

        var patches = new ArrayList<Patch>(cells.size());
        cells.forEach((key, painted) -> {
            if (painted.isEmpty()) {
                return;
            }
            var of = new int[painted.size()];
            for (int i = 0; i < of.length; i++) {
                of[i] = painted.get(i);
            }
            patches.add(new Patch(Surface.of(palette.get(key)), coverageOf(key), of));
        });
        return patches;
    }

    /** How many cells one copy of this entry's picture covers; nothing sensible said means one. */
    private float coverageOf(String key) {
        var cells = coverage.get(key);
        return cells == null || cells <= 0f ? ONE_CELL : cells;
    }

    /**
     * A palette value read as a colour, or {@code null} where it is not one: {@code #3A5F2B}, and
     * {@code #RGB} for the short way of writing the same thing.
     *
     * <p>The one thing the client reads into what a game wrote. Everything else is a path, handed to the
     * asset manager exactly as it stands.
     */
    static ColorRGBA colourOf(String named) {
        if (named == null || named.isEmpty() || named.charAt(0) != '#') {
            return null;
        }
        var digits = named.substring(1);
        if (digits.length() != 3 && digits.length() != 6) {
            return null;
        }
        int packed;
        try {
            packed = Integer.parseUnsignedInt(digits, 16);
        } catch (NumberFormatException notAColour) {
            return null;
        }
        if (digits.length() == 3) {
            // #abc is #aabbcc, as everywhere else that writes a colour short.
            packed = (packed & 0xF00) * 0x1100 + (packed & 0x0F0) * 0x110 + (packed & 0x00F) * 0x11;
        }
        return new ColorRGBA((packed >> 16 & 0xFF) / 255f, (packed >> 8 & 0xFF) / 255f,
                (packed & 0xFF) / 255f, 1f);
    }

    private static String named(Object map) {
        return map instanceof MapTemplate template ? "the map '" + template.name() + "'"
                : "the map " + map.getClass().getSimpleName();
    }
}
