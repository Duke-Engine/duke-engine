package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a game puts around itself before it starts: the front menu, or nothing
 * at all.
 *
 * <p>The client used to decide this on its own, and decided it badly. It offered
 * "Host LAN Game" to anything with two player slots — which in a single-player
 * dungeon means the hero and whatever owns the monsters. That is the engine
 * inferring a product decision from a structural fact, and it is not the
 * engine's decision to make: whether a game has a menu, what it says, and
 * whether it even stops to ask before starting, is the game's.
 *
 * <p>So the client keeps the mechanism — a dimmed overlay of clickable text — and
 * the game says what goes on it:
 *
 * <pre>{@code
 * Duke3D.launch(game, visuals, Shell.create()
 *         .entry(Shell.Entry.PLAY, "Enter the dungeon")
 *         .entry(Shell.Entry.SETTINGS)
 *         .entry(Shell.Entry.QUIT));
 * }</pre>
 *
 * <p>What stays the engine's is everything about the machine rather than the
 * game: the settings screen, the pause menu, quitting. Those are the same
 * whatever is being played.
 *
 * <p>Entries the game asks for are still only shown when they mean something —
 * there is no point offering a LAN game to a game that seats one player.
 */
public final class Shell {

    /** The entries the client knows how to run. A game chooses which it wants. */
    public enum Entry {
        /** Start playing. */
        PLAY("Play"),
        /** Choose the map and each player's faction first. */
        SKIRMISH("Skirmish: map & factions"),
        /** Wait for other machines to join this one. */
        HOST_LAN("Host LAN Game"),
        /** Join a game hosted elsewhere. */
        JOIN_LAN("Join LAN Game"),
        /** Resolution, fullscreen, volume — the machine, not the game. */
        SETTINGS("Settings"),
        /** Leave. */
        QUIT("Quit");

        private final String defaultLabel;

        Entry(String defaultLabel) {
            this.defaultLabel = defaultLabel;
        }

        public String defaultLabel() {
            return defaultLabel;
        }
    }

    /**
     * One thing the player may pick when the game asks him something.
     *
     * <p>Taking it does whatever the game said, and then either opens the next
     * question or starts. Which is what makes a handful of these a <em>path</em>
     * rather than a screen: how you are playing, then which stage, then who you
     * are, each one narrowing the last — and the client walks it without knowing
     * what any of the words mean.
     *
     * @param label what it is called — the word he clicks
     * @param blurb a line under it saying what picking it means, or empty. A
     *     roster of two is a decision rather than a formality, and a name on its
     *     own does not tell anybody which of them he would enjoy
     * @param taken what the game does about it, or {@code null} for a row that
     *     only leads somewhere
     * @param next  the question this opens, or {@code null} to start the game.
     *     A branch may be as long as it likes and the branches need not match:
     *     one of them can ask something the other never does
     * @param picture what this row is a row about, shown beside the list while it
     *     is the lit one — the map that would be played, the hero that would be
     *     taken — as a path the game's assets are loaded by, or {@code null}. The
     *     client never looks for one: a row with a picture is a row the game gave
     *     a picture to
     */
    public record Option(String label, String blurb, Runnable taken, Question next, String picture) {

        public Option {
            label = label == null ? "" : label;
            blurb = blurb == null ? "" : blurb;
        }

        /** A row that settles something and starts the game. */
        public Option(String label, String blurb, Runnable taken) {
            this(label, blurb, taken, null, null);
        }

        public Option(String label, String blurb, Runnable taken, Question next) {
            this(label, blurb, taken, next, null);
        }
    }

    /**
     * A question the game will not start without an answer to.
     *
     * <p>The client owns the screen — a heading and a column of clickable words,
     * the same stone the rest of the menus are cut from — and the game owns the
     * question, the options and what taking one means. Which is the bargain
     * everything else on this page keeps.
     *
     * <p><b>It has no default.</b> A question with one is not a question: the
     * player would press Play, get whatever the file happened to say, and never
     * learn there was a choice. So Play opens this instead of starting, and
     * nothing starts until one of these is taken.
     *
     * @param title  the heading over the column
     * @param hint   the line along the bottom, in the game's own words
     * @param options what he may pick, in the order he should see them
     */
    public record Question(String title, String hint, List<Option> options) {

        public Question {
            options = List.copyOf(options);
        }

        /** Whether there is anything here worth stopping to ask. */
        public boolean worthAsking() {
            return !options.isEmpty();
        }
    }

    private final Map<Entry, String> items = new LinkedHashMap<>();
    private final boolean startsImmediately;
    private Question question;

    private Shell(boolean startsImmediately) {
        this.startsImmediately = startsImmediately;
    }

    /**
     * Ask this before the game starts, instead of starting.
     *
     * <p>A game that asks nothing starts on Play exactly as it always did, which
     * is every game on this client but one.
     */
    public Shell asking(Question question) {
        this.question = question;
        return this;
    }

    /** What the game wants answered first, or {@code null} if it wants nothing. */
    public Question question() {
        return question != null && question.worthAsking() ? question : null;
    }

    /** An empty shell, to be filled in. Entries appear in the order they are added. */
    public static Shell create() {
        return new Shell(false);
    }

    /**
     * Everything the client offers, which is what it showed before a game could
     * say otherwise. The default, so nothing that already worked changes.
     */
    public static Shell standard() {
        var shell = create();
        for (var entry : Entry.values()) {
            shell.entry(entry);
        }
        return shell;
    }

    /** No menu: the game begins the moment the window opens. */
    public static Shell none() {
        return new Shell(true);
    }

    /** Add an entry with the client's own wording. */
    public Shell entry(Entry entry) {
        return entry(entry, entry.defaultLabel());
    }

    /** Add an entry, worded the way this game would word it. */
    public Shell entry(Entry entry, String label) {
        items.put(entry, label == null || label.isBlank() ? entry.defaultLabel() : label);
        return this;
    }

    /** Whether to skip the front menu entirely. */
    public boolean startsImmediately() {
        return startsImmediately;
    }

    /** The chosen entries, in the order they were added. */
    public List<Map.Entry<Entry, String>> entries() {
        return List.copyOf(new ArrayList<>(items.entrySet()));
    }
}
