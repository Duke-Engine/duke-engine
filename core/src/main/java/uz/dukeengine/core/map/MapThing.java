package uz.dukeengine.core.map;

import java.util.Map;

/**
 * One thing standing on a map before the first frame: a tree, an ore node, a bridge, somebody's tank.
 *
 * <p>Across the ground, cells; up it, steps. {@link #x} and {@link #y} are cells and {@link #z} is steps, which
 * is what the {@code @Relief} beside them is written in, and world units appear nowhere — a map that says its
 * own {@link Scaled#cellSize} is drawn at its own scale and nothing here has to be told. {@link #facing} is
 * degrees.
 *
 * <p>Fractions of a cell, because a thing is not a cell. A dungeon's monster stands in the middle of one and a
 * skirmish's ore node does too, but of the 90 098 things across Zero Hour's 65 skirmish maps hardly any sit on
 * a whole number: a bridge runs between two banks and a wall follows a road. A cell is where a whole number
 * lands.
 */
public interface MapThing {

    /** What it is, by the word its game's own template is named with. */
    String template();

    /** Cells across, from the left edge of the map's first column. */
    float x();

    /** Cells down, from the top edge of the map's first row. */
    float y();

    /** Steps up, above the relief under it; most things stand on the ground and say nothing. */
    default float z() {
        return 0f;
    }

    /** Which way it faces, in degrees. */
    default float facing() {
        return 0f;
    }

    /**
     * Whatever else the map said about it, by the game's own names — whose it is, whether it starts damaged,
     * which script knows it by what.
     *
     * <p>Core never reads a key of this, the way it never reads a word of {@code WorldSnapshot.status}: the
     * names are one game's and the moment the engine knows one of them it knows that game. It is here so that
     * a map can carry them to the game that wrote them.
     */
    default Map<String, String> properties() {
        return Map.of();
    }
}
