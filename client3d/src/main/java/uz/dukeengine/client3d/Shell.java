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
    private final boolean drawnByTheGame;
    private Question question;
    private List<Option> pauseRows = List.of();
    private List<Option> frontRows = List.of();
    private final Map<String, String> words = new LinkedHashMap<>();
    private MenuSide menuSide = MenuSide.MIDDLE;
    private float menuMargin;

    private Shell(boolean startsImmediately) {
        this(startsImmediately, false);
    }

    private Shell(boolean startsImmediately, boolean drawnByTheGame) {
        this.startsImmediately = startsImmediately;
        this.drawnByTheGame = drawnByTheGame;
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

    /**
     * The game's own rows on the pause menu, between Settings and Abandon, in this order. Taking one runs its
     * {@code taken} on the client's thread with the simulation paused; a row with a {@code next} question opens it,
     * Back and its last answer both coming back to the pause menu, and one without comes straight back.
     */
    public Shell pauseRows(List<Option> rows) {
        this.pauseRows = List.copyOf(rows);
        return this;
    }

    /**
     * Say one of the client's fixed menu words in the game's own: {@code word("Resume", "Davom etish")}. Keyed by the
     * English the client says when the game names nothing — the pause menu's {@code PAUSED}, {@code Resume},
     * {@code Settings}, {@code Quit to the main menu}, {@code Quit to the desktop}, {@code ESC resume}, the questions
     * they ask ({@code QUIT TO THE MAIN MENU?}, {@code QUIT TO THE DESKTOP?}, {@code everything on this floor is lost},
     * {@code Keep playing}, {@code Quit}, {@code ESC keep playing}), the questions' and the skirmish menu's
     * {@code Back}, the settings screen's every label, value and line
     * ({@code SETTINGS}, {@code Fullscreen}, {@code NO}, {@code YES}, {@code Size}, {@code MONITOR},
     * {@code Anti-aliasing}, {@code OFF}, {@code Volume}, {@code Effects}, {@code Voice}, {@code Music}, {@code Track},
     * {@code Save}, {@code Cancel}, {@code Keys} and every client control's name as it reads there ({@code Pan up},
     * {@code Select all}…), {@code anti-aliasing from the next launch}, {@code unsaved changes},
     * {@code UP DOWN choose    LEFT RIGHT change    ESC back}) and the front menu's
     * {@code UP DOWN choose    ENTER take}.
     */
    public Shell word(String english, String said) {
        words.put(english, said);
        return this;
    }

    /** One of the client's fixed words as this game says it, or as the client does where the game names none. */
    public String wordFor(String english) {
        return words.getOrDefault(english, english);
    }

    /**
     * Lay the front and pause menus' rows at a side of the screen, {@code margin} pixels in from its edge, their title
     * above them on that side, and only that side darkened: so a live backdrop ({@link Duke3D#backdrop}) is seen across
     * the rest. {@link MenuSide#MIDDLE}, the default, is the middle as ever.
     */
    public Shell menuAt(MenuSide side, float margin) {
        this.menuSide = side == null ? MenuSide.MIDDLE : side;
        this.menuMargin = Math.max(0f, margin);
        return this;
    }

    public MenuSide menuSide() {
        return menuSide;
    }

    public float menuMargin() {
        return menuMargin;
    }

    /**
     * The game's own rows at the top of the front menu, before its entries: Continue, New game, Load, Credits.
     * Taking one runs its {@code taken}; one with a {@code next} question opens it, Back coming back to the front menu
     * and its last answer starting the game as Play's does; one without comes back to the front menu — a {@code
     * taken} that starts a match ({@link Duke3D#startMatch}) is what is seen next.
     */
    public Shell frontRows(List<Option> rows) {
        this.frontRows = List.copyOf(rows);
        return this;
    }

    /** The game's rows on the front menu; see {@link #frontRows(List)}. */
    public List<Option> frontRows() {
        return frontRows;
    }

    /** The game's rows on the pause menu; see {@link #pauseRows(List)}. */
    public List<Option> pauseRows() {
        return pauseRows;
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

    /**
     * Every screen is the game's own, drawn on its {@link Canvas}: the client shows none of its menus — no front
     * menu, no pause menu, no settings — and none of its HUD, and the window opens on the game's front end with no
     * match behind it. The game starts a match with {@link Duke3D#startMatch} and comes back with
     * {@link Duke3D#frontEnd}. The client keeps the world's own controls — selecting, ordering, the camera — for
     * whatever the game's canvas does not take.
     */
    public static Shell drawnByTheGame() {
        return new Shell(false, true);
    }

    /** Whether the game draws every screen itself; see {@link #drawnByTheGame}. */
    public boolean isDrawnByTheGame() {
        return drawnByTheGame;
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
