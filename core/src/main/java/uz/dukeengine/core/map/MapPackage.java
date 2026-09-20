package uz.dukeengine.core.map;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One map as it is kept: a folder of its own, named for the map, holding the map's own file and whatever else
 * that map needs.
 *
 * <pre>{@code
 * maps/
 *   crypt/
 *     crypt.map        the place itself: its head, its cells, and what stands on them
 *     preview.png      what the screen where a map is chosen shows (optional)
 *     monsters.duke    blocks of this map's own, read after the game's (optional)
 * }</pre>
 *
 * <p>A folder rather than a file, for the reason both engines this is drawn from keep one: a map is not one
 * thing. Generals keeps {@code Maps\Foo\Foo.map} beside its preview and its strings; a Warcraft III map is an
 * archive with the terrain, the units, the strings and the map's <em>own</em> object changes inside it. What
 * they pack, this leaves as files, because a file an editor can open is worth more here than a byte saved.
 *
 * <p>The head is read without reading the map. A map's own file opens with its plain lines — its name, what it
 * is called, how many may play — and the rows of cells come after them, so a screen listing twenty maps reads
 * twenty short heads rather than twenty grids. That is Generals' {@code MapCache.ini} and Warcraft's 512-byte
 * header, without a second file to keep in step.
 */
public final class MapPackage {

    /** What a map's own file is called, after its name: {@code crypt.map}. */
    public static final String SUFFIX = ".map";

    private final String name;
    private final Path file;
    private final List<Path> extras;
    private final Path preview;

    private MapPackage(String name, Path file, List<Path> extras, Path preview) {
        this.name = name;
        this.file = file;
        this.extras = List.copyOf(extras);
        this.preview = preview;
    }

    /**
     * The package a folder holds, or null where it holds no map file of its own name.
     *
     * <p>Named for the folder, so a map that is moved or copied is the map it is called: the name inside the
     * file is what a game shows, and this is what {@code --map=} and a saved game hold on to.
     */
    public static MapPackage in(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return null;
        }
        var name = fileName(folder);
        var file = folder.resolve(name + SUFFIX);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        var extras = new ArrayList<Path>();
        Path preview = null;
        try (Stream<Path> beside = Files.list(folder)) {
            for (var path : beside.sorted(Comparator.comparing(MapPackage::fileName)).toList()) {
                var called = fileName(path);
                if (called.endsWith(".duke")) {
                    extras.add(path);
                } else if (preview == null && (called.endsWith(".png") || called.endsWith(".jpg"))) {
                    preview = path;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the map folder " + folder, e);
        }
        return new MapPackage(name, file, extras, preview);
    }

    /**
     * The package of a map named on the command line: a folder, or a file.
     *
     * <p>A file in a folder of its own name is that whole package — its extras and its preview come with it.
     * A file anywhere else is a map on its own, which is what an author has while he is still drawing one and
     * has not decided what it is called; it brings nothing with it but itself.
     */
    public static MapPackage of(Path fileOrFolder) {
        if (fileOrFolder == null) {
            return null;
        }
        if (Files.isDirectory(fileOrFolder)) {
            return in(fileOrFolder);
        }
        if (!Files.isRegularFile(fileOrFolder)) {
            return null;
        }
        var packaged = in(fileOrFolder.getParent());
        if (packaged != null && packaged.file().equals(fileOrFolder)) {
            return packaged;
        }
        var called = fileName(fileOrFolder);
        int dot = called.lastIndexOf('.');
        return new MapPackage(dot > 0 ? called.substring(0, dot) : called, fileOrFolder, List.of(), null);
    }

    /** The name of its folder and of its file, which is what a map is asked for by. */
    public String name() {
        return name;
    }

    /** The map's own file. */
    public Path file() {
        return file;
    }

    /**
     * The blocks this map brings with it, in the order their files are named — read after the game's own, so a
     * block with a name the game already uses is this map's version of it while this map is played.
     */
    public List<Path> extras() {
        return extras;
    }

    /** The picture of the map for a screen that lists them, or null. */
    public Path preview() {
        return preview;
    }

    /** The whole map, as text: what a game binds to its own record. Line endings are the file's own machine's. */
    public String text() {
        return read(file);
    }

    /** Every block this map brings with it, one text, in file order. */
    public String extrasText() {
        var all = new StringBuilder();
        for (var extra : extras) {
            all.append(read(extra)).append('\n');
        }
        return all.toString();
    }

    /**
     * The plain lines at the head of the map, by key, as far as the first list: enough to list it on a screen
     * without reading its cells. A line that is not {@code Key = value} ends the head.
     */
    public Map<String, String> head() {
        var head = new LinkedHashMap<String, String>();
        for (var line : text().split("\n", -1)) {
            var said = line.strip();
            if (said.isEmpty() || said.startsWith(";")) {
                continue;
            }
            int is = said.indexOf('=');
            if (is < 0) {
                if (!head.isEmpty()) {
                    break; // a block's word, or its End: the head is over
                }
                continue; // the block's own word, before anything is written in it
            }
            var value = said.substring(is + 1).strip();
            if (value.startsWith("[")) {
                break; // the first list: the rows of the map, and everything after them
            }
            head.put(said.substring(0, is).strip(), value);
        }
        return head;
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\r", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the map file " + path, e);
        }
    }

    /** The last part of a path, as text — a jar's own file system does not hand back a {@code File}. */
    private static String fileName(Path path) {
        var called = path.getFileName();
        return called == null ? "" : called.toString();
    }

    @Override
    public String toString() {
        return "map " + name + " (" + file + ")";
    }
}
