package uz.dukeengine.core.pathfind;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Pathfinder.Tally;
import uz.dukeengine.core.pathfind.Pathfinder.Traffic;
import uz.dukeengine.core.pathfind.Pathfinder.Within;

/**
 * A route that ends at the nearest cell within a band of distance of a thing — the reference's {@code
 * Pathfinder::findAttackPath} — searched by the search's own {@link Steps}, toward the band itself.
 */
final class BandSearch {

    private BandSearch() {
    }

    /** See {@link Pathfinder#findPathWithin}. */
    static Path findPathWithin(PathGrid grid, Coord3D from, Coord3D toward, float clearance, Within within,
            Tally tally, Traffic traffic) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        if (grid.isBlocked(startX, startY)) {
            return Room.escape(grid, from);
        }
        int width = grid.getWidth();
        var scratch = SearchScratch.take(width * grid.getHeight());
        try {
            int startIndex = grid.index(startX, startY);
            scratch.set(startIndex, 0, bandEstimate(grid, within, toward, startX, startY), -1);
            var open = scratch.open;
            open.add(startIndex);
            float half = grid.getCellSize() / 2f;
            int examined = 0;
            // The reference's limit is in its own cells; a grid walked finer has that many to the same ground.
            int mostExamined = Pathfinder.BAND_CELL_LIMIT * grid.cellsPerMapCell() * grid.cellsPerMapCell();
            while (!open.isEmpty()) {
                int current = open.poll();
                if (scratch.closed(current)) {
                    continue;
                }
                int cx = current % width;
                int cy = current / width;
                var centre = grid.cellCenter(cx, cy);
                if (current != startIndex && within.outside(centre) <= 0f
                        && Lines.across(centre, from) >= half) {
                    return GridSearch.reconstruct(grid, scratch, current, startIndex, from, centre, clearance,
                            traffic, Integer.MAX_VALUE);
                }
                Tally.examined(tally);
                scratch.close(current);
                if (++examined > mostExamined) {
                    continue; // no more expanding: what is open is looked at, and then it gives up
                }
                for (var step : Steps.NEIGHBOURS) {
                    int nx = cx + step[0];
                    int ny = cy + step[1];
                    if (!Steps.canTake(grid, cx, cy, step, clearance)) {
                        continue;
                    }
                    int next = grid.index(nx, ny);
                    if (scratch.closed(next)) {
                        continue;
                    }
                    int tentative = Steps.costVia(grid, scratch.g(current), scratch.came(current), cx, cy, step,
                            traffic);
                    if (tentative == Traffic.CLOSED || tentative >= scratch.g(next)) {
                        continue;
                    }
                    scratch.set(next, tentative, tentative + bandEstimate(grid, within, toward, nx, ny), current);
                    open.add(next);
                }
            }
            return Path.EMPTY;
        } finally {
            scratch.release();
        }
    }

    /**
     * What a walk from cell ({@code cx}, {@code cy}) into the band is estimated to cost: the search's own estimate to
     * the goal ({@link Steps#heuristic}) for the way from the cell to the band's nearest point, straight toward {@code
     * toward} or straight away from it, as far as the cell lies outside the band.
     */
    private static int bandEstimate(PathGrid grid, Within within, Coord3D toward, int cx, int cy) {
        var centre = grid.cellCenter(cx, cy);
        float outside = within.outside(centre);
        if (outside <= 0f) {
            return 0;
        }
        float dx = toward.x() - centre.x();
        float dy = toward.y() - centre.y();
        float span = (float) Math.sqrt(dx * dx + dy * dy);
        float cells = outside / grid.getCellSize();
        if (span < 1e-3f) {
            return (int) (cells * Steps.ORTHOGONAL_COST);
        }
        float across = Math.abs(dx) / span * cells;
        float along = Math.abs(dy) / span * cells;
        return (int) (Steps.ORTHOGONAL_COST * Math.max(across, along)
                + Steps.ORTHOGONAL_COST * Math.min(across, along) / 2f);
    }
}
