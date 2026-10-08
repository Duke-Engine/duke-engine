package uz.dukeengine.core.map;

import java.util.List;

/**
 * How much of each surface a painted map's ground shows at a corner of its cells, from the paint alone — the rule a
 * client blends its ground by and an editor previews it with, as Warcraft III blends its tiles: no blend is written in
 * the map.
 *
 * <p>The corner ({@code cx}, {@code cy}), for {@code 0 <= cx <= width} and {@code 0 <= cy <= height}, is shared by the
 * up to four cells round it — ({@code cx - 1}, {@code cy - 1}), ({@code cx}, {@code cy - 1}), ({@code cx - 1},
 * {@code cy}) and ({@code cx}, {@code cy}) — those of them on the map, and painted with a character the palette names.
 * Each surface's weight is how many of those cells it paints over how many were counted; so at most four surfaces show
 * at a corner, the weights add up to one, and a corner with none counted shows none. A cell is drawn as its four
 * corners' weights blended across it. Pure and deterministic; for the drawing, and nothing in the logic reads it.
 */
public final class PaintWeights {

    private PaintWeights() {
    }

    /**
     * The weight of each surface at corner ({@code cx}, {@code cy}), in the order of {@code keys} — the palette's
     * characters, in the palette's own order — zero for one that paints none of the cells round it.
     *
     * @param rows   the map's paint rows, one character a cell, the first row the map's first
     * @param width  the map's width in cells
     * @param height the map's height in cells
     */
    public static float[] at(List<String> rows, List<String> keys, int width, int height, int cx, int cy) {
        var weights = new float[keys.size()];
        int counted = 0;
        for (int y = cy - 1; y <= cy; y++) {
            for (int x = cx - 1; x <= cx; x++) {
                if (x < 0 || y < 0 || x >= width || y >= height || y >= rows.size() || x >= rows.get(y).length()) {
                    continue;
                }
                int surface = keys.indexOf(String.valueOf(rows.get(y).charAt(x)));
                if (surface >= 0) {
                    weights[surface]++;
                    counted++;
                }
            }
        }
        for (int i = 0; counted > 0 && i < weights.length; i++) {
            weights[i] /= counted;
        }
        return weights;
    }
}
