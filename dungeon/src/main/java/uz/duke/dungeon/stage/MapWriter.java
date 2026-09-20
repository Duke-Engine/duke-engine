package uz.duke.dungeon.stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;
import uz.duke.dungeon.gen.Layout;

/**
 * A map drawn once, from a seed, into a file: the generator draws the place and fills it, and an author
 * moves what stands in it afterwards, on the Map tab of the IDE. Run from the repository root:
 *
 * <pre>{@code
 * ./gradlew :dungeon:newMap --args="crypt 42"             # crypt, from seed 42, at depth 1, a floor's size
 * ./gradlew :dungeon:newMap --args="crypt 42 3 60 40 12"  # at depth 3, 60 by 40 cells, 12 rooms
 * ./gradlew :dungeon:writeExampleMaps                     # the two the game ships, written again
 * ./gradlew :dungeon:writeExampleMaps --args=first        # only the first of them
 * }</pre>
 *
 * <p>Drawn by the generator rather than by hand because a floor carries a guarantee a hand would break:
 * every room can be walked to. Its size and depth are asked before it is drawn, because neither can be
 * applied afterwards — a bigger floor is other rooms, and the depth decides which monsters are in them.
 * A map the game's own checks refuse is not written.
 */
public final class MapWriter {

    /** Where the game's own maps live: one folder a map, named for it, as every map is kept. */
    static final Path FOLDER = Path.of("dungeon", "src", "main", "resources", "maps");

    /**
     * One map to draw.
     *
     * @param seed       chosen by looking — any seed makes a valid map, and the examples' make good ones
     * @param difficulty the depth it is fought at
     */
    record Drawn(String name, String displayName, String description, long seed, int difficulty, int width,
            int height, int rooms) {
    }

    // The two the game ships, as a pair on purpose: one the size and danger of an ordinary floor, the other what
    // a map can be that a generated floor never is -- kept to four times the floor, because the 3D client builds
    // one Geometry per stone cell and batches none of them.
    static final Drawn FIRST = new Drawn("first", "The First Descent",
            "One floor, drawn once and never again — learn it, then win it.", 20260911L, 1, 50, 36, 9);
    static final Drawn DEEP = new Drawn("deep", "The Long Dark",
            "Four times the floor and eight times down. Bring everything.", 20260912L, 8, 100, 76, 28);

    private MapWriter() {
    }

    public static void main(String[] args) {
        var settings = DungeonSettings.load();
        if (args.length == 0) {
            write(settings, FIRST, true);
            write(settings, DEEP, true);
            return;
        }
        // One of the two by its name, the other left as it is.
        if (args.length == 1 && (args[0].equals(FIRST.name()) || args[0].equals(DEEP.name()))) {
            write(settings, args[0].equals(FIRST.name()) ? FIRST : DEEP, true);
            return;
        }
        if (args.length != 2 && args.length != 3 && args.length != 6) {
            throw new IllegalArgumentException(
                    "a new map is: name seed [difficulty [width height rooms]] — not " + String.join(" ", args));
        }
        var floor = Layout.of(settings);
        boolean sized = args.length == 6;
        write(settings, new Drawn(args[0], args[0], "", Long.parseLong(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1,
                sized ? Integer.parseInt(args[3]) : floor.width(), sized ? Integer.parseInt(args[4]) : floor.height(),
                sized ? Integer.parseInt(args[5]) : floor.maxRooms()), false);
    }

    private static void write(DungeonSettings settings, Drawn drawn, boolean again) {
        var path = FOLDER.resolve(drawn.name()).resolve(drawn.name() + uz.duke.core.map.MapPackage.SUFFIX);
        if (!again && Files.exists(path)) {
            // A map an author has already filled is a morning's work: drawing over it is never what was meant.
            throw new IllegalStateException(path + " is there already: choose another name, or delete it first");
        }
        var stage = stage(settings, drawn);
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, StageFile.write(stage), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + path, e);
        }
        // The folder is the map, and its picture is part of it: a map drawn into a folder with no preview in it
        // is a map the screen it is chosen on has nothing to show of.
        MapPicture.write(stage, path.toAbsolutePath().getParent());
        System.out.println("wrote " + path.toAbsolutePath());
    }

    /** The file of {@code drawn}: its floor drawn from its seed, at its depth and size, and checked as the game checks it. */
    static String text(DungeonSettings settings, Drawn drawn) {
        return StageFile.write(stage(settings, drawn));
    }

    /** The stage {@code drawn} draws, refused if the game's own checks would refuse it. */
    private static Stage stage(DungeonSettings settings, Drawn drawn) {
        int depth = Math.max(1, drawn.difficulty());
        var floor = DungeonGenerator.generate(drawn.seed(), settings, depth,
                Layout.sized(settings, drawn.width(), drawn.height(), drawn.rooms()));
        var stage = new Stage(drawn.name(), drawn.displayName(), drawn.description(), depth, 1, drawn.seed(), floor);
        var problems = StageCheck.problems(stage, settings);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(drawn.name() + " is broken: " + problems);
        }
        return stage;
    }
}
