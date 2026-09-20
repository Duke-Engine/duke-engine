package uz.dukeengine.skirmish.content;

import java.util.List;
import uz.dukeengine.core.data.Grid;
import uz.dukeengine.core.data.Link;
import uz.dukeengine.core.map.Described;
import uz.dukeengine.core.map.MapTemplate;
import uz.dukeengine.core.map.Peopled;

/**
 * A field two sides fight over: its ground, where each of them starts, and what is standing on it.
 *
 * <p>Written from nothing rather than copied from the dungeon's map record, for the same reason the unit
 * record was: what two independently written games both needed is evidence, and what only one of them needed
 * is that game's. What this one has that the dungeon's has not is {@code starts} — a corner each, because a
 * skirmish has sides. What it has <b>not</b> got is the dungeon's rooms, corridors and boss: this is one open
 * field, not a floor cut into places.
 *
 * <p>It is not {@code Layered} either. A dungeon is built in storeys and every map is laid at the world's
 * height; a field is flat, so the idea is absent rather than set to zero.
 *
 * @param cells  the ground, a row to a line: {@code #} is rock nothing walks through, anything else is open
 * @param starts one per side, in the order the sides are added
 */
public record Battlefield(String name, String displayName, String description, int players,
        @Grid List<String> cells,
        List<Start> starts,
        @Link(Unit.class) List<Placed> things)
        implements MapTemplate, Described, Peopled {

    /** Where a side begins: its base, and the corner its first units stand in. */
    public record Start(int x, int y) {

        /** {@code 12 8} — a cell, as the row above it is counted. */
        public static Start of(String written) {
            var words = written.trim().split("\\s+");
            if (words.length != 2) {
                throw new IllegalArgumentException("a start is a cell, 'x y', not '" + written + "'");
            }
            return new Start(Integer.parseInt(words[0]), Integer.parseInt(words[1]));
        }
    }

    /** One thing standing on the field: what it is, and the cell it stands in. */
    public record Placed(String kind, int x, int y) {

        /** {@code OreNode 30 12} — the unit's name, then its cell. */
        public static Placed of(String written) {
            var words = written.trim().split("\\s+");
            if (words.length != 3) {
                throw new IllegalArgumentException("a thing is 'Kind x y', not '" + written + "'");
            }
            return new Placed(words[0], Integer.parseInt(words[1]), Integer.parseInt(words[2]));
        }
    }

    public Battlefield {
        displayName = displayName == null || displayName.isBlank() ? name : displayName;
        description = description == null ? "" : description;
        players = players <= 0 ? 2 : players;
        cells = cells == null ? List.of() : List.copyOf(cells);
        starts = starts == null ? List.of() : List.copyOf(starts);
        things = things == null ? List.of() : List.copyOf(things);
    }

    /** How wide it is, in cells: the longest row, so a ragged file is caught rather than trusted. */
    public int width() {
        return cells.stream().mapToInt(String::length).max().orElse(0);
    }

    public int height() {
        return cells.size();
    }
}
