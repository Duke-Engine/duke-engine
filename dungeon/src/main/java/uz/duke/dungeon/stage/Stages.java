package uz.duke.dungeon.stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * Which stage is being played, and where its file came from.
 *
 * <p>Two ways to ask for one, and they answer the same question at different
 * distances. The settings file is where a build says what it ships — a stage
 * named there is what the game opens on. The command line beats it, because that
 * is what an author does twenty times an hour while he is building one:
 *
 * <pre>{@code ./gradlew :dungeon:run --args="--stage=stages/first.stage"}</pre>
 *
 * <p>Neither of them is a mode switch. A stage named nowhere is the endless
 * dungeon this game has always been, unchanged and reached by exactly the same
 * road; naming one swaps where the floors come from and nothing else.
 *
 * <p>A path is looked for on disk first and on the classpath second, in that
 * order and for that reason: the file an author is editing is a file on his disk,
 * and the file a player installed is inside the jar.
 */
public final class Stages {

    private static final String ARG = "--stage=";

    private Stages() {
    }

    /**
     * The stage the player asked for, or {@code null} for the endless dungeon.
     *
     * <p>Only the path. Reading it is {@link #load} and is separate on purpose:
     * asking which stage was wanted is a question that cannot fail, and opening a
     * file is a question that can.
     */
    public static String chosen(String[] args, DungeonSettings settings) {
        for (var arg : args) {
            if (arg.startsWith(ARG)) {
                var path = arg.substring(ARG.length()).strip();
                return path.isEmpty() ? null : path;
            }
        }
        var shipped = settings.stageFile();
        return shipped == null || shipped.isBlank() ? null : shipped;
    }

    /**
     * The stage at this path, read and checked.
     *
     * <p>Checked here rather than by the caller so that there is no way to load a
     * broken stage by forgetting to ask. A file with a room nobody can walk to is
     * a file that stops the game with a list of what is wrong — never a run that
     * begins and turns out, twenty minutes in, to have had no way through.
     */
    public static Stage load(String path, DungeonSettings settings) {
        var stage = StageFile.read(textOf(path), path);
        var problems = StageCheck.problems(stage, settings);
        if (problems.isEmpty()) {
            return stage;
        }
        var said = new StringBuilder(path).append(" cannot be played:");
        for (var problem : problems) {
            said.append("\n  - ").append(problem);
        }
        throw new IllegalStateException(said.toString());
    }

    private static String textOf(String path) {
        var file = Path.of(path);
        if (Files.isRegularFile(file)) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the stage at " + path, e);
            }
        }
        var resource = path.startsWith("/") ? path : "/" + path;
        try (var stream = Stages.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("no stage at " + path
                        + " — neither on disk nor inside the game");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the stage at " + path, e);
        }
    }
}
