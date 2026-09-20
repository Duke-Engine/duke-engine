package uz.dukeengine.core.map;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Where the maps are: the ones a game ships, and the ones whoever is playing it has made or been given.
 *
 * <p>Two places, as Generals has two — the game's own {@code Maps} folder and the player's, under his
 * documents. A map is found rather than listed: drop a folder in and it is there, which is what an editor that
 * writes maps needs and what a data file listing every map by hand cannot give. The game's own maps are read
 * off the classpath, so they work the same whether the game is run from a folder of classes or from a jar.
 *
 * <p>Names are unique across the two: a player's map of the same name as a shipped one takes its place, the way
 * a game's own block takes the kit's. Everything comes back in name order, because a list a player reads should
 * not change its order from one run to the next.
 */
public final class MapPackages {

    /** Kept open for the life of the game: a path inside a jar cannot be read once its file system is closed. */
    private static final Map<URI, FileSystem> OPENED = new LinkedHashMap<>();

    private MapPackages() {
    }

    /**
     * Every map the game ships, under a folder of the classpath — {@code maps} for {@code maps/crypt/crypt.map}.
     * A folder of that name in more than one resource root is read in classpath order, the first of a name winning.
     */
    public static synchronized List<MapPackage> shipped(String root) {
        var found = new LinkedHashMap<String, MapPackage>();
        try {
            var roots = MapPackages.class.getClassLoader().getResources(root);
            while (roots.hasMoreElements()) {
                var uri = roots.nextElement().toURI();
                var folder = "jar".equals(uri.getScheme()) ? jarPath(uri, root) : Path.of(uri);
                for (var map : under(folder)) {
                    found.putIfAbsent(map.name(), map);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new UncheckedIOException("could not look for maps in " + root,
                    e instanceof IOException io ? io : new IOException(e));
        }
        return List.copyOf(found.values());
    }

    /** Every map in a folder of the machine's own: one folder a map, each holding its own {@code .map} file. */
    public static List<MapPackage> under(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return List.of();
        }
        var found = new ArrayList<MapPackage>();
        try (Stream<Path> inside = Files.list(folder)) {
            for (var path : inside.toList()) {
                var map = MapPackage.in(path);
                if (map != null) {
                    found.add(map);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not look for maps in " + folder, e);
        }
        found.sort((one, two) -> one.name().compareTo(two.name()));
        return List.copyOf(found);
    }

    /**
     * The maps of both places, a player's own taking the place of a shipped map of the same name.
     *
     * @param root   the classpath folder the game's own maps are under
     * @param theirs a folder of the machine's own, or null for a game that keeps none
     */
    public static List<MapPackage> all(String root, Path theirs) {
        var found = new LinkedHashMap<String, MapPackage>();
        for (var map : under(theirs)) {
            found.put(map.name(), map);
        }
        for (var map : shipped(root)) {
            found.putIfAbsent(map.name(), map);
        }
        var sorted = new ArrayList<>(found.values());
        sorted.sort((one, two) -> one.name().compareTo(two.name()));
        return List.copyOf(sorted);
    }

    /** A folder inside a jar, whose file system is opened once and left open: its paths are read through it. */
    private static Path jarPath(URI uri, String root) throws IOException {
        var fs = OPENED.get(uri);
        if (fs == null || !fs.isOpen()) {
            try {
                fs = FileSystems.newFileSystem(uri, Map.of());
            } catch (FileSystemAlreadyExistsException already) {
                fs = FileSystems.getFileSystem(uri);
            }
            OPENED.put(uri, fs);
        }
        return fs.getPath("/" + root);
    }
}
