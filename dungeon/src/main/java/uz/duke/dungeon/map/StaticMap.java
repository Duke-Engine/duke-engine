package uz.duke.dungeon.map;

import java.util.List;
import uz.duke.core.data.Grid;
import uz.duke.core.data.Relief;
import uz.duke.core.map.Described;
import uz.duke.core.map.MapTemplate;
import uz.duke.core.map.Peopled;
import uz.duke.core.thing.Layered;
import uz.duke.dungeon.content.Monster;
import uz.duke.dungeon.content.Prop;

/**
 * A map drawn once and never again: the same rooms, the same monsters in the same corners, every
 * time — so that losing teaches something. Drawn from a seed by {@code :dungeon:newMap}, filled on
 * the IDE's Map tab, and what a stage is played from.
 *
 * <p>Every position is a cell, counted from the top-left of {@link #cells}, which a dungeon
 * stands everything it places in the middle of.
 *
 * @param name        its machine name, what a command line asks for
 * @param difficulty  the depth it is fought at, and so the whole of how hard it is
 * @param players     how many it was built for
 * @param seed        the seed the floor was cut from: the loot and the look of the place are still
 *     drawn from it, so a map plays the same way every time rather than merely having the same shape
 * @param entrance    where the hero comes in; none while an author is still deciding
 * @param cells       the floor, a row of characters for each row of cells: {@code #} is stone, a
 *     digit the storey a cell stands on, {@code /} a stair
 * @param relief      how the floor rises and falls over its storeys: a row of whole numbers for every row of cell
 *     corners, each that corner's height in steps of a sixteenth of a cell; none, and every floor is flat
 * @param rooms       the rooms the floor was cut into, in the order they were placed — the first is
 *     where the hero starts
 * @param links       which rooms a corridor joins, by their place in {@code rooms}
 * @param boss        who waits in the last room; killing it wins the map
 * @param levelHeight how tall one of its storeys is, in world units; 0 leaves the world's own. How WIDE a cell
 *     is stays the engine's here: a dungeon counts sight and placement in cells of {@code PathGrid.DEFAULT_CELL_SIZE},
 *     so a map at another width would be a map its own monsters could not see across
 */
public record StaticMap(String name, String displayName, String description, int difficulty, int players, long seed,
        float levelHeight,
        Cell entrance, @Grid List<String> cells, @Relief List<String> relief, List<Room> rooms, List<Link> links,
        @uz.duke.core.data.Link(Monster.class) Placed boss, @uz.duke.core.data.Link(Monster.class) List<Placed> monsters,
        @uz.duke.core.data.Link(Prop.class) List<Placed> props)
        implements MapTemplate, Described, Peopled, Layered {

    /** What a block leaves out. */
    public static final StaticMap DEFAULTS = new StaticMap("", "", "", 1, 1, 0L, 0f, null, List.of(), List.of(),
            List.of(), List.of(), null, List.of(), List.of());

    public StaticMap {
        cells = cells == null ? List.of() : List.copyOf(cells);
        relief = relief == null ? List.of() : List.copyOf(relief);
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        links = links == null ? List.of() : List.copyOf(links);
        monsters = monsters == null ? List.of() : List.copyOf(monsters);
        props = props == null ? List.of() : List.copyOf(props);
    }

    /** A cell, written {@code [x, y]}. */
    public record Cell(int x, int y) {
    }

    /** A room: {@code x y width height storey}. */
    public record Room(int x, int y, int width, int height, int storey) {

        public static Room of(String written) {
            var numbers = numbers(written, 5, "x y width height storey");
            return new Room(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4]);
        }

        public boolean contains(int cx, int cy) {
            return cx >= x && cx < x + width && cy >= y && cy < y + height;
        }
    }

    /** A corridor: the two rooms it joins, {@code from to}. */
    public record Link(int from, int to) {

        public static Link of(String written) {
            var numbers = numbers(written, 2, "the two rooms it joins");
            return new Link(numbers[0], numbers[1]);
        }
    }

    /** One thing standing on the map: its kind, then the cell — {@code Skeleton 17 16}. */
    public record Placed(String kind, int x, int y) {

        public static Placed of(String written) {
            var words = written.strip().split("\\s+");
            if (words.length != 3) {
                throw new IllegalArgumentException("'" + written + "' is a kind, then the cell it stands in: x y");
            }
            return new Placed(words[0], Integer.parseInt(words[1]), Integer.parseInt(words[2]));
        }
    }

    private static int[] numbers(String written, int count, String what) {
        var words = written.strip().split("\\s+");
        if (words.length != count) {
            throw new IllegalArgumentException("'" + written + "' is " + what);
        }
        var numbers = new int[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = Integer.parseInt(words[i]);
        }
        return numbers;
    }
}
