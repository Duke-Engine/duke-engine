package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * What grows on a painted map's ground for the look alone — grass, flowers, ferns, reeds — grown by the client a chunk
 * at a time round the camera from the map's seed and each cell, drawn instanced, a model at a time; never in the map,
 * the snapshot, the checksum or the scenery, never picked and standing in nobody's way. See {@code Duke3D.groundCover}.
 *
 * @param kinds   what grows on each surface, by the palette's picture as the map writes it ({@code
 *                ground/world/grass.png})
 * @param perCell how many stand in a cell of each surface on the average
 * @param seed    the map's seed: the same cover on every machine and every visit
 * @param bare    the cells nothing grows in — a road, water, stone, a town — by the map's cell; null for none
 * @param reach   how far round the camera it is drawn, in the map's cells: a chunk is grown once it comes this near
 *                and let go once it is a chunk further
 * @param density a share of {@code perCell} drawn — a player's setting, 0 for none
 */
public record GroundCover(Map<String, List<Kind>> kinds, Map<String, Float> perCell, long seed, Bare bare, float reach,
        float density) {

    /** Nothing grows. */
    public static final GroundCover NONE = new GroundCover(Map.of(), Map.of(), 0L, null, 0f, 0f);

    public GroundCover {
        kinds = kinds == null ? Map.of() : Map.copyOf(kinds);
        perCell = perCell == null ? Map.of() : Map.copyOf(perCell);
    }

    /** One thing that grows: its model, its share by weight, its size, and a colour multiplied over it. */
    public record Kind(String model, int weight, float scale, int tint) {
    }

    /** Whether nothing grows in the map's cell ({@code cx}, {@code cy}). */
    @FunctionalInterface
    public interface Bare {
        boolean at(int cx, int cy);
    }

    /** The same cover at another share of its density — the player's off, low or high. */
    public GroundCover atDensity(float share) {
        return new GroundCover(kinds, perCell, seed, bare, reach, Math.max(0f, share));
    }

    /** One piece grown: its kind, where in the map's cells, turned how many degrees, how large. */
    record Piece(Kind kind, float x, float y, float facing, float scale) {
    }

    /**
     * What grows in the cell ({@code cx}, {@code cy}) of the surface {@code picture}: drawn from the seed and the cell
     * alone, so a chunk grown again, or by another machine, grows the same.
     */
    List<Piece> growIn(int cx, int cy, String picture) {
        var these = picture == null ? null : kinds.get(picture);
        float share = picture == null ? 0f : perCell.getOrDefault(picture, 0f) * density;
        if (these == null || these.isEmpty() || share <= 0f || bare != null && bare.at(cx, cy)) {
            return List.of();
        }
        var random = new SplittableRandom(seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cy * 0xC2B2AE3D27D4EB4FL));
        int count = (int) share + (random.nextFloat() < share - (int) share ? 1 : 0);
        int total = 0;
        for (var kind : these) {
            total += Math.max(0, kind.weight());
        }
        var grown = new ArrayList<Piece>(count);
        for (int i = 0; i < count && total > 0; i++) {
            int pick = random.nextInt(total);
            Kind kind = these.getLast();
            for (var each : these) {
                pick -= Math.max(0, each.weight());
                if (pick < 0) {
                    kind = each;
                    break;
                }
            }
            grown.add(new Piece(kind, cx + 0.05f + random.nextFloat() * 0.9f, cy + 0.05f + random.nextFloat() * 0.9f,
                    random.nextFloat() * 360f, kind.scale() * (0.8f + random.nextFloat() * 0.4f)));
        }
        return grown;
    }
}
