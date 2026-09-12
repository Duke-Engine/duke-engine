package uz.duke.dungeon.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The game's data files, read off the classpath.
 *
 * <p>The creature definitions and the tuning used to be Java text blocks, which
 * meant changing how much a skeleton hurts was a code change and a rebuild. They
 * are data, so they live in files, and the engine's own INI loader reads them —
 * the same loader every duke-engine game uses. Nothing here parses anything; it
 * only fetches the text.
 */
public final class Content {

    private static final String ROOT = "/ini/";

    /** The creatures the real game plays with. */
    public static final String CREATURES = "creatures.ini";

    /** Generation and behaviour tuning — everything that is not a unit stat. */
    public static final String SETTINGS = "dungeon.ini";

    /** The monsters, one template per kind named in the settings file. */
    public static final String MONSTERS = "monsters.ini";

    /** The things standing about in the rooms — solid, and not alive. */
    public static final String PROPS = "props.ini";

    /** The hand-drawn room's own frozen creatures, independent of the game's. */
    public static final String FIXTURE_CREATURES = "fixture-creatures.ini";

    /** The hand-drawn room itself, as ASCII terrain. */
    public static final String FIXTURE_ROOM = "fixture-room.txt";

    private Content() {
    }

    /**
     * The text of a data file beside this class, or an error naming what is
     * missing. Lines end with {@code \n}, whatever they ended with on disk.
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
        try (var stream = Content.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) {
                throw new IllegalStateException("missing dungeon data file: " + ROOT + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\r", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("could not read dungeon data file: " + name, e);
        }
    }
}
