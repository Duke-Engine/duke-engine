package uz.duke.dungeon.stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /** Where stages live, on disk and inside the game alike. */
    public static final String FOLDER = "stages";

    /** What one is called. Anything else in the folder is not a stage. */
    private static final String SUFFIX = ".stage";

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Stages.class.getName());

    private Stages() {
    }

    /** One stage found and read, and the path it was found at. */
    public record Listed(String path, Stage stage) {
    }

    /**
     * Every stage this game can offer, ready to be chosen from.
     *
     * <p>Two places, in the order {@link #load} already prefers them: the
     * {@code stages} folder beside the game, then the one inside it. A file on
     * disk with the same name as a shipped one hides it, which is what an author
     * editing a copy of a shipped stage expects.
     *
     * <p><b>Broken stages are left out, loudly.</b> A stage with a room nothing
     * can walk to cannot be played, so offering it would be offering a row that
     * stops the game — but leaving it out silently is worse for the one person it
     * matters to, the author who has just written it and is wondering where it
     * went. So it is dropped from the list and said in full in the log, and naming
     * it outright on the command line still refuses in the old loud way.
     *
     * <p>Nothing here throws. A folder that is missing, unreadable or empty is a
     * game with no stages in it, which is a game — the endless dungeon is what
     * this has always been.
     */
    public static List<Listed> all(DungeonSettings settings) {
        var found = new java.util.TreeMap<String, Listed>();
        for (var path : names()) {
            String at = FOLDER + "/" + path;
            try {
                var stage = StageFile.read(textOf(at), at);
                var problems = StageCheck.problems(stage, settings);
                if (!problems.isEmpty()) {
                    LOG.warning(() -> at + " is not offered because it cannot be played:\n  - "
                            + String.join("\n  - ", problems));
                    continue;
                }
                found.put(path, new Listed(at, stage));
            } catch (RuntimeException e) {
                LOG.warning(() -> at + " is not offered: " + e.getMessage());
            }
        }
        return List.copyOf(found.values());
    }

    /**
     * The file names in the stage folder, from disk and from inside the game.
     *
     * <p>Sorted and de-duplicated by the caller, so the same name in both places
     * is one stage and the disk one wins — which is the rule {@link #load} reads
     * them by.
     */
    private static java.util.Set<String> names() {
        var names = new java.util.TreeSet<String>();
        onDisk(names);
        inTheGame(names);
        return names;
    }

    private static void onDisk(java.util.Set<String> into) {
        var folder = Path.of(FOLDER);
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (var listing = Files.list(folder)) {
            listing.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(SUFFIX))
                    .forEach(into::add);
        } catch (IOException e) {
            LOG.warning(() -> "could not read the " + FOLDER + " folder: " + e.getMessage());
        }
    }

    /**
     * The ones shipped inside the game, which is a jar once it is installed.
     *
     * <p>A folder in a jar is not a folder and cannot be listed with a file API,
     * so the jar is opened as a file system and walked. In development the very
     * same resource is a plain directory on disk — Gradle copies resources into
     * the build folder — so both shapes have to work, and which one it is, is
     * whatever the URL says.
     */
    private static void inTheGame(java.util.Set<String> into) {
        var url = Stages.class.getResource("/" + FOLDER);
        if (url == null) {
            return; // the game ships none, which is allowed
        }
        try {
            var uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                // Opened if nobody has it open yet, and never closed if somebody
                // does: closing a file system another caller is reading from
                // would break them rather than tidy up after us.
                try (var jar = java.nio.file.FileSystems.newFileSystem(uri, java.util.Map.of())) {
                    listInto(jar.getPath("/" + FOLDER), into);
                } catch (java.nio.file.FileSystemAlreadyExistsException already) {
                    listInto(java.nio.file.FileSystems.getFileSystem(uri)
                            .getPath("/" + FOLDER), into);
                }
                return;
            }
            listInto(Path.of(uri), into);
        } catch (java.net.URISyntaxException | IOException | RuntimeException e) {
            LOG.warning(() -> "could not read the stages inside the game: " + e.getMessage());
        }
    }

    private static void listInto(Path folder, java.util.Set<String> into) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (var listing = Files.list(folder)) {
            listing.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(SUFFIX))
                    .forEach(into::add);
        }
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
