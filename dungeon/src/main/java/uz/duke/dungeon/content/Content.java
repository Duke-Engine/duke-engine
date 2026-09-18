package uz.duke.dungeon.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;

/**
 * The game's data files, read off the classpath.
 *
 * <p>The creature definitions and the tuning used to be Java text blocks, which
 * meant changing how much a skeleton hurts was a code change and a rebuild. They
 * are data, so they live in files, and the engine's own INI loader reads them —
 * the same loader every duke-engine game uses.
 *
 * <p>{@link #SETTINGS} is the world, and its {@code DungeonContent} block names every
 * other file: one unit, projectile, effect or prop to a file, and the sounds by
 * channel. A file may hold any blocks, so what reaches each loader is sorted out
 * here: the templates go to the engine's unit loader, everything else to
 * {@link DungeonSettings} — neither has to know the files were ever split. A unit's
 * block ({@code Monster}, {@code Hero}, {@code Projectile}, {@code Prop}) goes to both,
 * each reading its own fields of it; see {@link DungeonTemplates}.
 */
public final class Content {

    /** The world, and the list of every other file the game is made of. */
    public static final String SETTINGS = "ini/dungeon.ini";

    /** The hand-drawn room's own frozen creatures, independent of the game's. */
    public static final String FIXTURE_CREATURES = "ini/fixture-creatures.ini";

    /** The hand-drawn room itself, as ASCII terrain. */
    public static final String FIXTURE_ROOM = "ini/fixture-room.txt";

    private static final String OBJECT = "Object";
    private static final java.util.Set<String> UNITS = java.util.Set.of("monster", "hero", "projectile", "prop");
    private static final String MANIFEST = "DungeonContent";

    // A file listed twice would be read twice, and DungeonSettings keeps its blocks in
    // lists: every sound, skill and monster kind in it would be there twice over.
    private static final FieldParseTable<List<String>> MANIFEST_FIELDS = new FieldParseTable<List<String>>()
            .add("File", Ini.string((files, file) -> {
                if (files.contains(file)) {
                    throw new IllegalStateException(file + " is listed twice");
                }
                files.add(file);
            }));

    private Content() {
    }

    /**
     * Every template the game is made of — its units, projectiles and props — in the
     * order the files are read: the text the engine's unit loader takes.
     */
    public static String units() {
        return gather(type -> type.equalsIgnoreCase(OBJECT) || UNITS.contains(type.toLowerCase(java.util.Locale.ROOT)));
    }

    /** Everything but the engine's own templates, for {@link DungeonSettings}: the world, the units and the rest of every file. */
    public static String settings() {
        return gather(type -> !type.equalsIgnoreCase(OBJECT) && !type.equalsIgnoreCase(MANIFEST));
    }

    /**
     * The files {@link #SETTINGS} names, in the order they are read. The order is the
     * game's: monster kinds are drawn in it and heroes offered in it.
     */
    public static List<String> files() {
        var manifest = new StringBuilder();
        for (var block : blocks(SETTINGS)) {
            if (block.type().equalsIgnoreCase(MANIFEST)) {
                manifest.append(block.text());
            }
        }
        var files = new ArrayList<String>();
        Ini.of(manifest.toString(), Map.of(MANIFEST, ini -> {
            ini.getNextToken(); // the block's name
            ini.initFromIni(files, MANIFEST_FIELDS);
        }), SETTINGS).load();
        return List.copyOf(files);
    }

    private static String gather(Predicate<String> wanted) {
        var files = new ArrayList<String>();
        files.add(SETTINGS);
        files.addAll(files());
        var text = new StringBuilder();
        for (var file : files) {
            for (var block : blocks(file)) {
                if (wanted.test(block.type())) {
                    text.append(block.text());
                }
            }
        }
        return text.toString();
    }

    /** One top-level block of a file, with the comments that lead into it. */
    private record Block(String type, String text) {
    }

    /**
     * A file cut into its top-level blocks. A block opens on a line that starts in the
     * first column and closes on the {@code End} that does; everything nested inside a
     * block is indented. Every file here is written that way, and one that is not is
     * refused, naming the line, rather than cut in the wrong place.
     */
    private static List<Block> blocks(String file) {
        var blocks = new ArrayList<Block>();
        var text = new StringBuilder();
        String open = null;
        int number = 0;
        for (var line : read(file).split("\n", -1)) {
            number++;
            text.append(line).append('\n');
            var word = firstColumnWord(line);
            if (word == null) {
                // Between blocks only comments may stand. Anything else would ride along
                // into the next block's text, or off the end of the file with nobody told.
                if (open == null && !line.isBlank() && !line.strip().startsWith(";")) {
                    throw new IllegalStateException(file + ":" + number + " '" + line.strip()
                            + "' stands outside any block");
                }
                continue;
            }
            boolean end = word.equalsIgnoreCase("End");
            if (open == null && end) {
                throw new IllegalStateException(file + ":" + number + " End with no block open");
            }
            if (open != null && !end) {
                throw new IllegalStateException(file + ":" + number + " " + word
                        + " starts in the first column inside " + open + ", which has no End");
            }
            if (end) {
                blocks.add(new Block(open, text.toString()));
                text.setLength(0);
                open = null;
            } else {
                open = word;
            }
        }
        if (open != null) {
            throw new IllegalStateException(file + ": " + open + " has no End");
        }
        return blocks;
    }

    /** The token a line starts with, cut where the INI reader cuts it, if it starts in the first column. */
    private static String firstColumnWord(String line) {
        if (line.isEmpty() || !Character.isLetter(line.charAt(0))) {
            return null;
        }
        int end = 0;
        while (end < line.length() && (Ini.SEPS + ";").indexOf(line.charAt(end)) < 0) {
            end++;
        }
        return line.substring(0, end);
    }

    /**
     * The text of a data file, by its whole path from the resource root, or an error
     * naming what is missing. Lines end with {@code \n}, whatever they ended with on disk.
     *
     * <p>★ The normalising is not tidiness. These files are checked out, and what
     * a checkout does to their line endings is a property of the MACHINE rather
     * than of the game: git is commonly set to hand Windows a working copy with
     * CRLF, so the same commit is LF here and CRLF there. Every reader that looks
     * for a line — {@code indexOf("Object Skeleton\n")}, a {@code split} that is
     * then compared against a word — then works on one machine and not the other,
     * and the way it shows up is a test that is green for everybody except
     * whoever is on Windows.
     *
     * <p>The INI parser itself never cared, because it tokenises. It is the
     * things that read the file AS TEXT that did, so the fix belongs here, at the
     * one door all of them come through, rather than in each of them.
     */
    public static String read(String name) {
        try (var stream = Content.class.getResourceAsStream("/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("missing dungeon data file: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\r", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("could not read dungeon data file: " + name, e);
        }
    }
}
