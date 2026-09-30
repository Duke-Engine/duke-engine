package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import java.util.BitSet;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The whole world as the player knows it, as a {@link Picture} the game draws where it likes — a map screen, a scroll —
 * with the minimap's colours: a texel for as many cells a side as keep it within the size the game asked, each drawn
 * as the most seen of its cells is — lit, then remembered, then unwalked — its floor over its stone. What changed is
 * heard from the discovery every frame and painted, so the picture is always the player's knowledge now, and costs
 * what changed; a world nobody discovers is its ground and its stone.
 */
final class WorldPicture {

    private final PathGrid grid;
    private final boolean discovered;
    private final ColorRGBA dark;
    /** How many cells a side a texel is. */
    private final int cells;
    private final Picture picture;
    /** The texels to be painted again, a bit a texel, row by row. */
    private final BitSet stale = new BitSet();
    private boolean everyTexel = true;

    /**
     * The picture of {@code grid}, at most {@code mostTexels} a side, of what {@code discovered} is told, in the dark
     * of {@code dark} — the fog's colour.
     */
    WorldPicture(PathGrid grid, int mostTexels, boolean discovered, ColorRGBA dark) {
        this.grid = grid;
        this.discovered = discovered;
        this.dark = dark == null ? ColorRGBA.Black : dark;
        int most = Math.max(1, mostTexels);
        this.cells = Math.max(1, Math.ceilDiv(Math.max(grid.getWidth(), grid.getHeight()), most));
        this.picture = new Picture(Math.ceilDiv(grid.getWidth(), cells), Math.ceilDiv(grid.getHeight(), cells));
    }

    /** The picture, as the game draws it: kept as the world is known, and said {@link Picture#changed} each time. */
    Picture picture() {
        return picture;
    }

    /** How many cells a side a texel is. */
    int cellsATexel() {
        return cells;
    }

    /** Hear what the last soften found changed, its texels to be painted. */
    void heard(Discovery seen) {
        if (seen.everythingChanged()) {
            everyTexel = true;
            return;
        }
        var changed = seen.changed();
        int width = grid.getWidth();
        for (int i = 0; i < changed.size(); i++) {
            int cell = changed.get(i);
            stale.set(cell / width / cells * picture.width() + cell % width / cells);
        }
    }

    /** Paint the texels whose cells changed since, every texel the first time; whether any was painted. */
    boolean paint(Discovery seen) {
        boolean painted = false;
        if (everyTexel) {
            for (int texel = 0; texel < picture.width() * picture.height(); texel++) {
                painted |= paintTexel(seen, texel);
            }
            everyTexel = false;
            stale.clear();
        } else {
            for (int texel = stale.nextSetBit(0); texel >= 0; texel = stale.nextSetBit(texel + 1)) {
                painted |= paintTexel(seen, texel);
            }
            stale.clear();
        }
        if (painted) {
            picture.changed();
        }
        return painted;
    }

    /** One texel, as the most seen of its cells is, its floor over its stone; whether it changed. */
    private boolean paintTexel(Discovery seen, int texel) {
        int x0 = texel % picture.width() * cells;
        int y0 = texel / picture.width() * cells;
        var best = Discovery.State.UNSEEN;
        boolean floorAtBest = false;
        int storey = 0;
        for (int cy = y0; cy < Math.min(y0 + cells, grid.getHeight()); cy++) {
            for (int cx = x0; cx < Math.min(x0 + cells, grid.getWidth()); cx++) {
                var state = discovered && seen != null ? seen.stateAt(cx, cy) : Discovery.State.VISIBLE;
                boolean floor = !grid.isTerrainBlocked(cx, cy);
                if (state.ordinal() > best.ordinal() || state == best && floor && !floorAtBest) {
                    best = state;
                    floorAtBest = floor;
                    storey = grid.level(cx, cy);
                } else if (state == best && floor && grid.level(cx, cy) > storey) {
                    storey = grid.level(cx, cy); // the highest floor of the most seen, as a plan shows it
                }
            }
        }
        var colour = discovered && seen != null ? MinimapPicture.colourOf(best, !floorAtBest, storey, dark)
                : MinimapPicture.colourOf(!floorAtBest);
        int argb = 0xFF000000 | (MinimapPicture.channel(colour.r) & 0xFF) << 16
                | (MinimapPicture.channel(colour.g) & 0xFF) << 8 | MinimapPicture.channel(colour.b) & 0xFF;
        int[] pixels = picture.argb();
        if (pixels[texel] == argb) {
            return false;
        }
        pixels[texel] = argb;
        return true;
    }
}
