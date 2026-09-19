package uz.duke.dungeon.stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * Which stage is being played, and where it came from.
 *
 * <p>Two ways to ask for one, and they answer the same question at different distances. The
 * game's own file is where a build says what it ships — the map its {@code StartMap} names is what
 * the game opens on. The command line beats it, because that is what an author does twenty times
 * an hour while he is building one:
 *
 * <pre>{@code ./gradlew :dungeon:run --args="--map=first"}</pre>
 *
 * <p>Neither of them is a mode switch. A stage named nowhere is the endless dungeon this game has
 * always been, unchanged and reached by exactly the same road; naming one swaps where the floors
 * come from and nothing else.
 *
 * <p>A stage is asked for by its map's {@code Name}, or by the path of a map file an author is
 * still drawing — looked for on disk first and on the classpath second, in that order and for
 * that reason: the file an author is editing is a file on his disk.
 */
public final class Stages {

    private static final String ARG = "--map=";

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Stages.class.getName());

    private Stages() {
    }

    /** One stage offered, and the name it is asked for by. */
    public record Listed(String name, Stage stage) {
    }

    /**
     * Every stage this game can offer, ready to be chosen from: each map drawn once that the game's
     * files list, in their order.
     *
     * <p><b>Broken stages are left out, loudly.</b> A stage with a room nothing can walk to cannot be
     * played, so offering it would be offering a row that stops the game — but leaving it out
     * silently is worse for the one person it matters to, the author who has just written it and is
     * wondering where it went. So it is dropped from the list and said in full in the log, and
     * naming it outright on the command line still refuses in the old loud way.
     */
    public static List<Listed> all(DungeonSettings settings) {
        var found = new java.util.ArrayList<Listed>();
        for (var map : settings.staticMaps()) {
            try {
                var stage = StageFile.stage(map, map.name());
                var problems = StageCheck.problems(stage, settings);
                if (!problems.isEmpty()) {
                    LOG.warning(() -> map.name() + " is not offered because it cannot be played:\n  - "
                            + String.join("\n  - ", problems));
                    continue;
                }
                found.add(new Listed(map.name(), stage));
            } catch (RuntimeException e) {
                LOG.warning(() -> map.name() + " is not offered: " + e.getMessage());
            }
        }
        return List.copyOf(found);
    }

    /**
     * The stage the player asked for, by name or path, or {@code null} for the endless dungeon.
     *
     * <p>Only the name. Reading it is {@link #load} and is separate on purpose: asking which stage
     * was wanted is a question that cannot fail, and opening a file is a question that can.
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

    /**
     * The stage by this name — one the game's files list — or at this path, read and checked.
     *
     * <p>Checked here rather than by the caller so that there is no way to load a broken stage by
     * forgetting to ask. A file with a room nobody can walk to is a file that stops the game with a
     * list of what is wrong — never a run that begins and turns out, twenty minutes in, to have had
     * no way through.
     */
    public static Stage load(String nameOrPath, DungeonSettings settings) {
        var stage = settings.staticMaps().stream()
                .filter(map -> map.name().equals(nameOrPath))
                .findFirst()
                .map(map -> StageFile.stage(map, map.name()))
                .orElseGet(() -> StageFile.read(textOf(nameOrPath), nameOrPath));
        var problems = StageCheck.problems(stage, settings);
        if (problems.isEmpty()) {
            return stage;
        }
        var said = new StringBuilder(nameOrPath).append(" cannot be played:");
        for (var problem : problems) {
            said.append("\n  - ").append(problem);
        }
        throw new IllegalStateException(said.toString());
    }

    /** A map file's text. Its line endings are the parser's business: DukeText reads CRLF as LF. */
    private static String textOf(String path) {
        var file = Path.of(path);
        if (Files.isRegularFile(file)) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the map at " + path, e);
            }
        }
        var resource = path.startsWith("/") ? path : "/" + path;
        try (var stream = Stages.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("no map is called " + path
                        + ", and there is no file at that path — neither on disk nor inside the game");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the map at " + path, e);
        }
    }
}
