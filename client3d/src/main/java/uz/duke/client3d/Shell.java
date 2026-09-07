package uz.duke.client3d;

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

    private final Map<Entry, String> items = new LinkedHashMap<>();
    private final boolean startsImmediately;

    private Shell(boolean startsImmediately) {
        this.startsImmediately = startsImmediately;
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
