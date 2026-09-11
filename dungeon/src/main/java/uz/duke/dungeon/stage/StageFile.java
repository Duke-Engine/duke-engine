package uz.duke.dungeon.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.ini.IniException;
import uz.duke.dungeon.gen.GeneratedDungeon;
import uz.duke.dungeon.gen.GeneratedDungeon.Link;
import uz.duke.dungeon.gen.GeneratedDungeon.Monster;
import uz.duke.dungeon.gen.GeneratedDungeon.Placement;
import uz.duke.dungeon.gen.GeneratedDungeon.Prop;
import uz.duke.dungeon.gen.GeneratedDungeon.Room;

/**
 * A stage, as text and back again.
 *
 * <p>Text on purpose, and the engine's own INI at that — the same reader that
 * already reads every creature, every theme and every tuning number this game
 * has. A binary level format is a level nobody can look at: a stage that behaves
 * oddly is a file somebody has to be able to open, read down, and see the
 * mistake in. So a map is a picture of a map, a monster is a line saying what it
 * is and where it stands, and every number in the file is the number that was
 * meant.
 *
 * <p>Positions are written in <b>cells</b> rather than in world units, which
 * loses nothing: a dungeon stands everything it places at the centre of a cell,
 * so the two are the same fact and only one of them can be counted along a row
 * of the map above it by eye.
 *
 * <p>The one thing the format cannot carry is a semicolon: it starts a comment,
 * everywhere, and that is the reader's rule rather than this file's. Writing one
 * fails loudly instead of quietly truncating a description halfway.
 */
public final class StageFile {

    private static final String HEADER = """
            ; Duke Dungeon — a stage: a dungeon that has stopped changing.
            ;
            ; Written by the world builder (./gradlew :worldbuilder:run) and read by the
            ; game (--stage=<this file>). Hand-editing is expected — every position is a
            ; cell, counted from the top-left of the maps below — and every edit is
            ; checked on load: a monster inside a wall or a room nothing can walk to
            ; stops the game with a list of what is wrong rather than starting anyway.
            ;
            ; A ';' begins a comment on any line, so no name or description may contain one.

            """;

    private StageFile() {
    }

    // ---- writing ----

    /** The stage as the text of a {@code .stage} file. */
    public static String write(Stage stage) {
        var floor = stage.floor();
        var out = new StringBuilder(HEADER);

        out.append("Stage ").append(stage.id()).append('\n');
        field(out, "Name", stage.name());
        field(out, "Description", stage.description());
        field(out, "Difficulty", String.valueOf(stage.difficulty()));
        field(out, "Players", String.valueOf(stage.players()));
        out.append("""
                  ; The seed this floor was cut from. The loot, the level-up cards and the
                  ; look of the place are still drawn from it, so a stage plays the same
                  ; way every time rather than merely having the same shape.
                """);
        field(out, "Seed", String.valueOf(stage.seed()));
        if (floor.hero() != null) {
            out.append("  ; Where the hero comes in.\n");
            field(out, "Entrance", cells(floor.hero()));
        }
        out.append("End\n\n");

        mapBlock(out, "StageTerrain", "'#' is stone and '.' is floor", floor.asciiMap());
        mapBlock(out, "StageStoreys",
                "a digit is the storey a cell stands on, '/' is a stair", floor.levelMap());

        out.append("StageRooms\n  ; x y width height storey\n");
        for (int i = 0; i < floor.rooms().size(); i++) {
            var room = floor.rooms().get(i);
            int storey = i < floor.roomStoreys().size() ? floor.roomStoreys().get(i) : 0;
            field(out, "Room", room.x() + " " + room.y() + " " + room.w() + " " + room.h()
                    + " " + storey);
        }
        out.append("End\n\n");

        out.append("StageLinks\n  ; the two rooms a corridor joins, by their order above\n");
        for (var link : floor.links()) {
            field(out, "Link", link.from() + " " + link.to());
        }
        out.append("End\n\n");

        if (floor.boss() != null) {
            out.append("StageBoss ").append(floor.boss().kind()).append('\n')
                    .append("  ; Killing it wins the stage.\n");
            if (floor.boss().at() != null) {
                field(out, "Cell", cells(floor.boss().at()));
            }
            field(out, "Room", String.valueOf(floor.bossRoom()));
            out.append("End\n\n");
        }

        out.append("StageMonsters\n  ; kind, then the cell it stands in\n");
        for (var monster : floor.monsters()) {
            field(out, "Monster", monster.kind() + " " + cells(monster.at()));
        }
        out.append("End\n\n");

        out.append("StageProps\n  ; solid, and not alive — see props.ini\n");
        for (var prop : floor.props()) {
            field(out, "Prop", prop.kind() + " " + cells(prop.at()));
        }
        out.append("End\n");

        return out.toString();
    }

    private static void field(StringBuilder out, String name, String value) {
        var text = value == null ? "" : value;
        if (text.indexOf(';') >= 0) {
            throw new IllegalArgumentException(
                    "a stage's " + name + " cannot contain ';' — it begins a comment: " + text);
        }
        out.append("  ").append(name).append(" = ").append(text).append('\n');
    }

    private static void mapBlock(StringBuilder out, String block, String what, String rows) {
        out.append(block).append("\n  ; ").append(what).append('\n');
        for (var row : rows.strip().split("\n")) {
            out.append("  ").append(row).append('\n');
        }
        out.append("End\n\n");
    }

    private static String cells(Placement at) {
        return at.cellX() + " " + at.cellY();
    }

    // ---- reading ----

    /** The stage in this text, or an error saying where the file stops making sense. */
    public static Stage read(String text, String sourceName) {
        var draft = new Draft();
        Ini.of(text, Map.ofEntries(
                Map.entry("Stage", (Ini.BlockParser) reader -> {
                    draft.id = reader.getNextToken();
                    reader.initFromIni(draft, HEAD);
                }),
                Map.entry("StageTerrain", (Ini.BlockParser) reader ->
                        draft.terrain = rowsOf(reader, "StageTerrain")),
                Map.entry("StageStoreys", (Ini.BlockParser) reader ->
                        draft.storeys = rowsOf(reader, "StageStoreys")),
                Map.entry("StageRooms", (Ini.BlockParser) reader ->
                        reader.initFromIni(draft, ROOMS)),
                Map.entry("StageLinks", (Ini.BlockParser) reader ->
                        reader.initFromIni(draft, LINKS)),
                Map.entry("StageBoss", (Ini.BlockParser) reader -> {
                    draft.bossKind = reader.getNextToken();
                    reader.initFromIni(draft, BOSS);
                }),
                Map.entry("StageMonsters", (Ini.BlockParser) reader ->
                        reader.initFromIni(draft, MONSTERS)),
                Map.entry("StageProps", (Ini.BlockParser) reader ->
                        reader.initFromIni(draft, PROPS))),
                sourceName).load();
        return draft.build(sourceName);
    }

    /**
     * A block whose lines are a picture rather than fields.
     *
     * <p>Read by hand because that is what it is: {@code initFromIni} would take
     * the first run of stone on a row for a field name and say it had never heard
     * of it. Nothing else about these lines is special — the reader has already
     * taken any comment off the end of each of them.
     */
    private static String rowsOf(Ini reader, String block) {
        var rows = new StringBuilder();
        while (reader.readLine()) {
            var row = reader.getRestOfLine();
            if (row.isEmpty()) {
                continue;
            }
            if (row.equalsIgnoreCase("End")) {
                return rows.toString();
            }
            rows.append(row).append('\n');
        }
        throw new IniException(reader.getSourceName() + ": '" + block + "' has no 'End'");
    }

    /** Everything read so far, before it is known whether the file was whole. */
    private static final class Draft {
        private String id = "stage";
        private String name = "";
        private String description = "";
        private int difficulty = 1;
        private int players = 1;
        private long seed;
        private Placement entrance;
        private String terrain;
        private String storeys;
        private String bossKind;
        private Placement bossAt;
        private int bossRoom;
        private final List<Room> rooms = new ArrayList<>();
        private final List<Integer> roomStoreys = new ArrayList<>();
        private final List<Link> links = new ArrayList<>();
        private final List<Monster> monsters = new ArrayList<>();
        private final List<Prop> props = new ArrayList<>();

        /**
         * The stage, if the file had a map in it.
         *
         * <p>A missing entrance or a missing boss is not caught here: they are
         * things an author has not finished deciding, and the world builder has to
         * be able to hold one of those while he decides. A missing map is not —
         * there is no stage at all without one. See {@link StageCheck} for the
         * rest.
         */
        Stage build(String sourceName) {
            if (terrain == null || storeys == null) {
                throw new IniException(sourceName + ": a stage needs both a StageTerrain and a"
                        + " StageStoreys block, and this one has "
                        + (terrain == null ? "neither" : "only the terrain"));
            }
            var boss = bossKind == null ? null : new Monster(bossKind, bossAt);
            var floor = new GeneratedDungeon(terrain, storeys, entrance, List.copyOf(monsters),
                    boss, bossRoom, List.copyOf(rooms), List.copyOf(links),
                    List.copyOf(roomStoreys), List.copyOf(props));
            return new Stage(id, name, description, difficulty, players, seed, floor);
        }
    }

    private static final FieldParseTable<Draft> HEAD = new FieldParseTable<Draft>()
            .add("Name", Ini.restOfLine((d, v) -> d.name = v))
            .add("Description", Ini.restOfLine((d, v) -> d.description = v))
            .add("Difficulty", Ini.integer((d, v) -> d.difficulty = v))
            .add("Players", Ini.integer((d, v) -> d.players = v))
            // Not Ini.integer: a seed is the whole of a long and the game picks one
            // off the clock, so half of them do not fit in an int.
            .add("Seed", (ini, d) -> d.seed = Long.parseLong(ini.getNextToken()))
            .add("Entrance", (ini, d) -> d.entrance = cell(ini));

    private static final FieldParseTable<Draft> ROOMS = new FieldParseTable<Draft>()
            .add("Room", (ini, d) -> {
                int x = Ini.scanInt(ini.getNextToken());
                int y = Ini.scanInt(ini.getNextToken());
                int w = Ini.scanInt(ini.getNextToken());
                int h = Ini.scanInt(ini.getNextToken());
                d.rooms.add(new Room(x, y, w, h));
                d.roomStoreys.add(Ini.scanInt(ini.getNextToken()));
            });

    private static final FieldParseTable<Draft> LINKS = new FieldParseTable<Draft>()
            .add("Link", (ini, d) -> {
                int from = Ini.scanInt(ini.getNextToken());
                d.links.add(new Link(from, Ini.scanInt(ini.getNextToken())));
            });

    private static final FieldParseTable<Draft> BOSS = new FieldParseTable<Draft>()
            .add("Cell", (ini, d) -> d.bossAt = cell(ini))
            .add("Room", Ini.integer((d, v) -> d.bossRoom = v));

    private static final FieldParseTable<Draft> MONSTERS = new FieldParseTable<Draft>()
            .add("Monster", (ini, d) -> {
                var kind = ini.getNextToken();
                d.monsters.add(new Monster(kind, cell(ini)));
            });

    private static final FieldParseTable<Draft> PROPS = new FieldParseTable<Draft>()
            .add("Prop", (ini, d) -> {
                var kind = ini.getNextToken();
                d.props.add(new Prop(kind, cell(ini)));
            });

    private static Placement cell(Ini ini) {
        int cx = Ini.scanInt(ini.getNextToken());
        return Placement.atCell(cx, Ini.scanInt(ini.getNextToken()));
    }
}
