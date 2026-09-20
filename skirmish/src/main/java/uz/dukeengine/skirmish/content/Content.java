package uz.dukeengine.skirmish.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import uz.dukeengine.core.content.AnimationSet;
import uz.dukeengine.core.content.Effect;
import uz.dukeengine.core.content.Game;
import uz.dukeengine.core.content.Sound;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DataException;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.rts.module.RtsModules;

/**
 * The game's own files, and the record each block of them is.
 *
 * <p>Deliberately the smallest thing that reads a game in: one manifest, one binder, one map of word to record.
 * The dungeon's equivalent is over a thousand lines because it grew a façade in front of every setting; this one
 * is here to find out what a <em>second</em> game actually needs, so it takes nothing it has not asked for.
 */
public final class Content {

    private static final String GAME = "data/game.duke";

    /** Every module a unit's block may hold: the engine's, and the RTS library's. Nothing of this game's own yet. */
    public static final List<Class<? extends ModuleData>> MODULES =
            Stream.of(ModuleFactory.ENGINE_MODULES, RtsModules.MODULES).flatMap(List::stream).toList();

    /** The record each block is, by the word it opens with. */
    private static final Map<String, Class<? extends Record>> TYPES = Map.ofEntries(
            named(Unit.class), named(Field.class), named(AnimationSet.class),
            named(Effect.class), named(Sound.class),
            named(uz.dukeengine.client3d.Sun.class), named(uz.dukeengine.client3d.Camera.class));

    /**
     * Where this game keeps its templates.
     *
     * <p>A folder rather than a list of words, and that is worth noticing: the template loader is given text and
     * refuses a block it was not told about, so a game must separate its unit blocks from the rest somehow. The
     * dungeon keeps a list of template words to filter by; this one keeps them in the folder the house layout
     * already names. Neither is the engine's answer, which is the point — see the plan.
     */
    private static final String TEMPLATES = "data/units/";

    private Content() {
    }

    private static Map.Entry<String, Class<? extends Record>> named(Class<? extends Record> type) {
        return Map.entry(type.getSimpleName().toLowerCase(Locale.ROOT), type);
    }

    /** The game's own block: the files it is made of, and the map it opens on. */
    public static Game game() {
        var blocks = DukeText.parse(read(GAME), GAME);
        if (blocks.size() != 1 || !blocks.getFirst().word().equalsIgnoreCase("Game")) {
            throw new DataException(GAME, "holds one block, the Game");
        }
        return new Binder().bind(blocks.getFirst(), Game.class);
    }

    /** Every unit block, in the order the files are read: what a world's template loader is given. */
    public static String units() {
        var text = new StringBuilder();
        for (var file : game().files()) {
            if (file.startsWith(TEMPLATES)) {
                text.append(read(file)).append("\n");
            }
        }
        return text.toString();
    }

    /** Every block of every file, as the record its word names. */
    public static List<Record> everything() {
        var records = new ArrayList<Record>();
        for (var file : game().files()) {
            records.addAll(records(read(file), file));
        }
        return records;
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

    /** A file of the game's, from the classpath — so a built game reads its own data out of its jar. */
    public static String read(String path) {
        try (var in = Content.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new DataException(path, "missing data file");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
        }
    }
}
