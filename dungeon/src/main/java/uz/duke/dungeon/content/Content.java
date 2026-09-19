package uz.duke.dungeon.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import uz.duke.core.data.Binder;
import uz.duke.core.data.DataException;
import uz.duke.core.data.DukeText;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleFactory;
import uz.duke.dungeon.combat.ArrowUpdate;
import uz.duke.dungeon.combat.Bow;
import uz.duke.dungeon.combat.EyesOnly;
import uz.duke.dungeon.combat.FallingUpdate;
import uz.duke.dungeon.combat.Swing;
import uz.duke.dungeon.level.GrowableBody;
import uz.duke.dungeon.level.Recovery;
import uz.duke.dungeon.loot.LootUpdate;
import uz.duke.dungeon.skill.MendingUpdate;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.SummoningUpdate;
import uz.duke.game.script.ScriptModule;
import uz.duke.rts.RtsTemplate;
import uz.duke.rts.module.RtsModules;

/**
 * The game's data files, read off the classpath.
 *
 * <p>The creature definitions and the tuning used to be Java text blocks, which meant changing
 * how much a skeleton hurts was a code change and a rebuild. They are data, so they live in
 * files: {@code .duke} files, each block the record its word names — a {@code Monster} block is
 * a {@link Monster}, its fields the record's — and {@link #MANIFEST} names every one of them.
 * The {@code World} block is {@link #WORLD}, read by {@link DungeonSettings} until its sections
 * are records of their own.
 *
 * <p>A file may hold any blocks, so what reaches each reader is sorted out here: a world builds
 * the templates ({@link #units}), and the settings read everything ({@link #data}), each unit's
 * behaviour, look, face and skills included.
 */
public final class Content {

    /** The world's settings, one {@code World} block. */
    public static final String WORLD = "ini/dungeon.ini";

    /** Every other data file the game is made of, in the order it is read. */
    public static final String MANIFEST = "data/content.duke";

    /** The hand-drawn room's own frozen creatures, independent of the game's. */
    public static final String FIXTURE_CREATURES = "data/fixture-creatures.duke";

    /** The hand-drawn room itself, as ASCII terrain. */
    public static final String FIXTURE_ROOM = "data/fixture-room.txt";

    /** Every module a unit's block may hold: the words its module blocks are read with. */
    public static final List<Class<? extends ModuleData>> MODULES = Stream.of(
            ModuleFactory.ENGINE_MODULES, RtsModules.MODULES,
            List.<Class<? extends ModuleData>>of(ScriptModule.Data.class, GrowableBody.Data.class,
                    Recovery.Data.class, SkillBook.Data.class, Bow.Data.class, EyesOnly.Data.class,
                    ArrowUpdate.Data.class, FallingUpdate.Data.class, MendingUpdate.Data.class,
                    SummoningUpdate.Data.class, Swing.Data.class, LootUpdate.Data.class))
            .flatMap(List::stream).toList();

    /** The record each block of a data file is, by the word it opens with. */
    private static final Map<String, Class<? extends Record>> TYPES = Map.of(
            "object", RtsTemplate.class, "monster", Monster.class, "hero", Hero.class,
            "projectile", Projectile.class, "prop", Prop.class, "effect", Effect.class,
            "sound", Sound.class, "heavyshot", HeavyShot.class);

    /** The blocks a world builds things from. */
    private static final Set<String> TEMPLATES = Set.of("object", "monster", "hero", "projectile", "prop");

    private Content() {
    }

    /**
     * Every template the game is made of — its units, projectiles and props — in the order the
     * files are read: the text a world's template loader takes.
     */
    public static String units() {
        return gather(word -> TEMPLATES.contains(word.toLowerCase(Locale.ROOT)));
    }

    /** Every block of every data file, for {@link DungeonSettings}. */
    public static String data() {
        return gather(word -> true);
    }

    /** The {@code World} block's text. */
    public static String world() {
        return read(WORLD);
    }

    /**
     * The files {@link #MANIFEST} names, in the order they are read. The order is the game's:
     * monster kinds are drawn in it and heroes offered in it.
     */
    public static List<String> files() {
        var blocks = DukeText.parse(read(MANIFEST), MANIFEST);
        if (blocks.size() != 1 || !blocks.getFirst().word().equalsIgnoreCase("Manifest")) {
            throw new DataException(MANIFEST, "holds one block, the Manifest");
        }
        return new Binder().bind(blocks.getFirst(), Manifest.class).files();
    }

    /** Each block of {@code text} as the record its word names; {@code source} is how errors name the text. */
    public static List<Record> records(String text, String source) {
        var binder = new Binder().vocabulary(ModuleData.class, ModuleFactory.vocabularyOf(MODULES));
        var records = new ArrayList<Record>();
        for (var block : DukeText.parse(text, source)) {
            var type = TYPES.get(block.word().toLowerCase(Locale.ROOT));
            if (type == null) {
                throw new DataException(block.at(block.line()), "no block is called '" + block.word() + "'");
            }
            records.add(binder.bind(block, type));
        }
        return records;
    }

    private static String gather(Predicate<String> wanted) {
        var text = new StringBuilder();
        for (var file : files()) {
            for (var block : blocks(file)) {
                if (wanted.test(block.word())) {
                    text.append(block.text());
                }
            }
        }
        return text.toString();
    }

    /** One top-level block of a file, with the comments that lead into it. */
    private record Block(String word, String text) {
    }

    /**
     * A file cut into its top-level blocks. A block opens on a line that starts in the first
     * column and closes on the {@code End} that does; everything nested inside a block is
     * indented. Every file here is written that way, and one that is not is refused, naming the
     * line, rather than cut in the wrong place.
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

    /** The word a line starts with, if it starts in the first column. */
    private static String firstColumnWord(String line) {
        if (line.isEmpty() || !Character.isLetter(line.charAt(0))) {
            return null;
        }
        int end = 0;
        while (end < line.length() && " \t=;".indexOf(line.charAt(end)) < 0) {
            end++;
        }
        return line.substring(0, end);
    }

    /**
     * The text of a data file, by its whole path from the resource root, or an error naming
     * what is missing. Lines end with {@code \n}, whatever they ended with on disk.
     *
     * <p>★ The normalising is not tidiness. These files are checked out, and what a checkout
     * does to their line endings is a property of the MACHINE rather than of the game: git is
     * commonly set to hand Windows a working copy with CRLF, so the same commit is LF here and
     * CRLF there. Every reader that looks for a line — {@code indexOf("Name = Skeleton\n")}, a
     * {@code split} that is then compared against a word — then works on one machine and not
     * the other, and the way it shows up is a test that is green for everybody except whoever
     * is on Windows. The fix belongs here, at the one door all of them come through.
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
