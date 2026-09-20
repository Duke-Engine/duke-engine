package uz.duke.dungeon.stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import uz.duke.core.map.MapPackage;
import uz.duke.core.map.MapPackages;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * Which stage is being played, and where it came from.
 *
 * <p>A map is a folder rather than a line in a list — see {@link MapPackage} — and the game finds them in two
 * places: {@code maps/} inside the game, which is what it ships, and {@code maps/} beside the game on disk,
 * which is where a player's own go. A map of a name that is in both is the player's, the way a game's own block
 * takes the kit's place. So a map made in the IDE, or one downloaded and dropped in, is offered by being there.
 *
 * <p>Two ways to ask for one, and they answer the same question at different distances. The game's own file is
 * where a build says what it ships — the map its {@code StartMap} names is what the game opens on. The command
 * line beats it, because that is what an author does twenty times an hour while he is building one:
 *
 * <pre>{@code ./gradlew :dungeon:run --args="--map=first"}</pre>
 *
 * <p>Neither of them is a mode switch. A stage named nowhere is the endless dungeon this game has always been,
 * unchanged and reached by exactly the same road; naming one swaps where the floors come from and nothing else.
 *
 * <p>A stage is asked for by its map's name — the name of its folder — or by the path of a map file, or of the
 * folder holding one, which is what an author who is still drawing it has in his hands.
 */
public final class Stages {

    private static final String ARG = "--map=";

    /** Where the game's own maps are, on the classpath; and where a player's own are, beside the game. */
    private static final String SHIPPED = "maps";
    private static final Path THEIRS = Path.of("maps");

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Stages.class.getName());

    private Stages() {
    }

    /**
     * One stage offered, read from the head of its map's file alone.
     *
     * <p>The head, not the map: a screen listing twenty maps should not read twenty grids to write twenty
     * lines. What it costs to read the whole of one is paid when one is chosen — which is also when a map that
     * cannot be played says so.
     */
    public record Listed(String name, String title, String description, MapPackage map) {

        /**
         * The map's picture, as the game's own assets name it, or null where its folder holds none.
         *
         * <p>A map's folder is its name, so a map shipped inside the game has its picture where the classpath
         * finds it. A map beside the game is read from disk and its picture is not on the classpath, so it has
         * none here — a row without a picture is a row that still works.
         */
        public String picture() {
            var preview = map.preview();
            return preview == null ? null : SHIPPED + "/" + name + "/" + preview.getFileName();
        }
    }

    /** Every map this game can offer, in name order, as a screen would list them. */
    public static List<Listed> all() {
        var found = new ArrayList<Listed>();
        for (var map : MapPackages.all(SHIPPED, THEIRS)) {
            try {
                var head = map.head();
                var title = head.getOrDefault("DisplayName", "").isBlank() ? map.name() : head.get("DisplayName");
                found.add(new Listed(map.name(), title, head.getOrDefault("Description", ""), map));
            } catch (RuntimeException e) {
                LOG.warning(() -> map.name() + " is not offered: " + e.getMessage());
            }
        }
        return List.copyOf(found);
    }

    /**
     * The stage the player asked for, by name or path, or {@code null} for the endless dungeon.
     *
     * <p>Only the name. Finding it is {@link #found} and reading it is {@link #load}; they are separate on
     * purpose, because asking which stage was wanted is a question that cannot fail and opening a file can.
     */
    public static String chosen(String[] args) {
        for (var arg : args) {
            if (arg.startsWith(ARG)) {
                var name = arg.substring(ARG.length()).strip();
                return name.isEmpty() ? null : name;
            }
        }
        return Content.game().startMap();
    }

    /** The map of this name, or the one at this path — its folder, or its own file — and never null. */
    public static MapPackage found(String nameOrPath) {
        for (var map : MapPackages.all(SHIPPED, THEIRS)) {
            if (map.name().equals(nameOrPath)) {
                return map;
            }
        }
        var at = MapPackage.of(Path.of(nameOrPath));
        if (at != null) {
            return at;
        }
        throw new IllegalStateException("no map is called " + nameOrPath
                + ", and there is no map at that path — neither beside the game nor inside it");
    }

    /**
     * The map read and checked.
     *
     * <p>Checked here rather than by the caller so that there is no way to load a broken stage by forgetting to
     * ask. A file with a room nobody can walk to is a file that stops the game with a list of what is wrong —
     * never a run that begins and turns out, twenty minutes in, to have had no way through.
     */
    public static Stage load(MapPackage map, DungeonSettings settings) {
        var stage = StageFile.read(map.text(), map.name());
        var problems = StageCheck.problems(stage, settings);
        if (problems.isEmpty()) {
            return stage;
        }
        var said = new StringBuilder(map.name()).append(" cannot be played:");
        for (var problem : problems) {
            said.append("\n  - ").append(problem);
        }
        throw new IllegalStateException(said.toString());
    }

    /** The same by name or path, for a caller that has only the word. */
    public static Stage load(String nameOrPath, DungeonSettings settings) {
        return load(found(nameOrPath), settings);
    }

    /**
     * A stage chosen from the menu: the map read and checked, or null where it cannot be played.
     *
     * <p>Null rather than a thrown error, because this is a row somebody has just clicked: the game says in the
     * log what is wrong with the map and stays where it is, rather than ending the evening over a file the
     * player did not write. Naming it on the command line still refuses in the old loud way.
     */
    public static Stage offer(Listed listed, DungeonSettings settings) {
        try {
            return load(listed.map(), settings);
        } catch (RuntimeException e) {
            LOG.warning(() -> listed.name() + " cannot be played: " + e.getMessage());
            return null;
        }
    }
}
