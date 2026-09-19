package uz.duke.dungeon.stage;

import java.util.List;
import java.util.stream.IntStream;
import uz.duke.core.data.DataException;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.gen.GeneratedDungeon;
import uz.duke.dungeon.gen.GeneratedDungeon.Link;
import uz.duke.dungeon.gen.GeneratedDungeon.Monster;
import uz.duke.dungeon.gen.GeneratedDungeon.Placement;
import uz.duke.dungeon.gen.GeneratedDungeon.Prop;
import uz.duke.dungeon.gen.GeneratedDungeon.Room;
import uz.duke.dungeon.map.StaticMap;

/**
 * A stage, as the text of a map file and back again: one {@code StaticMap} block.
 *
 * <p>Text on purpose. A binary level format is a level nobody can look at: a stage that behaves
 * oddly is a file somebody has to be able to open, read down, and see the mistake in. So the floor
 * is a picture of the floor, a monster is a line saying what it is and where it stands, and every
 * number in the file is the number that was meant.
 *
 * <p>Positions are written in <b>cells</b> rather than in world units, which loses nothing: a
 * dungeon stands everything it places at the centre of a cell, so the two are the same fact and
 * only one of them can be counted along a row of the picture by eye.
 */
public final class StageFile {

    private static final String HEADER = """
            ; Duke Dungeon — a map drawn once: a dungeon that has stopped changing.
            ;
            ; Drawn from a seed (./gradlew :dungeon:newMap), filled on the Map tab of the
            ; IDE, and read by the game (--map=<its Name, or this file>). Hand-editing is
            ; expected — every position is a cell, counted from the top-left of Cells — and
            ; every edit is checked on load: a monster inside a wall or a room nothing can
            ; walk to stops the game with a list of what is wrong rather than starting.

            """;

    private StageFile() {
    }

    // ---- writing ----

    /** The stage as the text of a map file. */
    public static String write(Stage stage) {
        var floor = stage.floor();
        var out = new StringBuilder(HEADER).append("StaticMap\n");
        field(out, "Name", stage.id());
        field(out, "DisplayName", text(stage.name()));
        field(out, "Description", text(stage.description()));
        field(out, "Difficulty", String.valueOf(stage.difficulty()));
        field(out, "Players", String.valueOf(stage.players()));
        out.append("""
                  ; The seed this floor was cut from. The loot and the look of the place
                  ; are still drawn from it, so a map plays the same way every time
                  ; rather than merely having the same shape.
                """);
        field(out, "Seed", String.valueOf(stage.seed()));
        if (floor.hero() != null) {
            out.append("  ; Where the hero comes in.\n");
            field(out, "Entrance", "[" + floor.hero().cellX() + ", " + floor.hero().cellY() + "]");
        }
        out.append("  ; '#' is stone, a digit the storey a cell stands on, '/' a stair.\n");
        list(out, "Cells", floor.levelMap().strip().lines().map(row -> "\"" + row + "\"").toList());
        out.append("  ; x y width height storey, in the order they were placed: the first is where he starts.\n");
        list(out, "Rooms", IntStream.range(0, floor.rooms().size()).mapToObj(i -> {
            var room = floor.rooms().get(i);
            int storey = i < floor.roomStoreys().size() ? floor.roomStoreys().get(i) : 0;
            return room.x() + " " + room.y() + " " + room.w() + " " + room.h() + " " + storey;
        }).toList());
        out.append("  ; The two rooms a corridor joins, by their place in Rooms.\n");
        list(out, "Links", floor.links().stream().map(link -> link.from() + " " + link.to()).toList());
        if (floor.boss() != null && floor.boss().at() != null) {
            out.append("  ; Killing it wins the map.\n");
            field(out, "Boss", placed(floor.boss().kind(), floor.boss().at()));
        }
        out.append("  ; Each a kind, then the cell it stands in.\n");
        list(out, "Monsters", floor.monsters().stream().map(monster -> placed(monster.kind(), monster.at())).toList());
        out.append("  ; Solid, and not alive — see data/props/.\n");
        list(out, "Props", floor.props().stream().map(prop -> placed(prop.kind(), prop.at())).toList());
        return out.append("End\n").toString();
    }

    private static void field(StringBuilder out, String name, String value) {
        out.append("  ").append(name).append(" = ").append(value).append('\n');
    }

    /** A list of values, one to a line, so a map a builder saves again changes only what changed. */
    private static void list(StringBuilder out, String name, List<String> items) {
        if (items.isEmpty()) {
            field(out, name, "[]");
            return;
        }
        out.append("  ").append(name).append(" = [\n");
        for (var item : items) {
            out.append("    ").append(item).append(",\n");
        }
        out.append("  ]\n");
    }

    /** Words as a file keeps them: quoted where a {@code ;} or a quote would otherwise be read as something else. */
    private static String text(String words) {
        var text = words == null ? "" : words;
        if (text.indexOf(';') < 0 && text.indexOf('"') < 0 && !text.startsWith("[")) {
            return text;
        }
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String placed(String kind, Placement at) {
        return kind + " " + at.cellX() + " " + at.cellY();
    }

    // ---- reading ----

    /** The stage in this text, which holds one {@code StaticMap}, or an error saying where it stops making sense. */
    public static Stage read(String text, String source) {
        var maps = Content.records(text, source).stream()
                .filter(StaticMap.class::isInstance).map(StaticMap.class::cast).toList();
        if (maps.size() != 1) {
            throw new DataException(source, "holds one StaticMap block, not " + maps.size());
        }
        return stage(maps.getFirst(), source);
    }

    /**
     * The stage a map is: its floor, exactly as the generator handed it over.
     *
     * <p>A missing entrance or a missing boss is not caught here: they are things an author has not
     * finished deciding, and a map being filled has to be able to hold one of those while he
     * decides. A missing floor is — there is no stage at all without one. See {@link StageCheck}
     * for the rest.
     */
    public static Stage stage(StaticMap map, String source) {
        if (map.cells().isEmpty()) {
            throw new DataException(source, "a map needs its Cells, and " + map.name() + " has none");
        }
        var levels = String.join("\n", map.cells()) + "\n";
        // The picture of what stands and what is floor is the storeys with every storey a floor.
        var walls = levels.replaceAll("[0-9/]", ".");
        var rooms = map.rooms().stream().map(room -> new Room(room.x(), room.y(), room.width(), room.height())).toList();
        var storeys = map.rooms().stream().map(StaticMap.Room::storey).toList();
        var links = map.links().stream().map(link -> new Link(link.from(), link.to())).toList();
        var entrance = map.entrance() == null ? null : Placement.atCell(map.entrance().x(), map.entrance().y());
        var boss = map.boss() == null ? null : new Monster(map.boss().kind(), at(map.boss()));
        var monsters = map.monsters().stream().map(placed -> new Monster(placed.kind(), at(placed))).toList();
        var props = map.props().stream().map(placed -> new Prop(placed.kind(), at(placed))).toList();
        var floor = new GeneratedDungeon(walls, levels, entrance, monsters, boss, bossRoom(map), rooms, links, storeys,
                props);
        return new Stage(map.name(), map.displayName(), map.description(), map.difficulty(), map.players(), map.seed(),
                floor);
    }

    /** Which room the boss stands in: the one its cell is inside, so it is written once, as the cell. */
    private static int bossRoom(StaticMap map) {
        if (map.boss() == null) {
            return 0;
        }
        var rooms = map.rooms();
        return IntStream.range(0, rooms.size())
                .filter(i -> rooms.get(i).contains(map.boss().x(), map.boss().y()))
                .findFirst().orElse(0);
    }

    private static Placement at(StaticMap.Placed placed) {
        return Placement.atCell(placed.x(), placed.y());
    }
}
