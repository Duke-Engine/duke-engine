package uz.dukeengine.core.map;

/**
 * A map walked on a finer grid than it is drawn: {@link #navigationCellsPerCell} cells a side for each of its own, so a
 * body goes between two trees where their trunks leave it room, while it is still drawn a tile to a cell and every rule
 * counted in cells is counted in its own — see {@code GameLogic.setPathGrid(grid, cellsPerCell)}.
 *
 * <p>Read by the simulation, and every machine reads it from the same map.
 */
public interface Subdivided extends MapTemplate {

    /** How many cells a side it is walked at for each of its own: 2 walks a map of 10-unit cells on 5-unit ones. */
    int navigationCellsPerCell();
}
