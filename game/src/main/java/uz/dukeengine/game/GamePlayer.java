package uz.dukeengine.game;

import java.awt.Color;

/**
 * A player as game code sees it — a friendly handle over the engine's
 * {@code Player}, carrying presentation extras (display colour) the simulation
 * itself does not know about.
 *
 * <p>Created via {@link DukeGame#addPlayer}; the engine-side player (and its
 * index) is bound when the game starts.
 */
public final class GamePlayer {

    private final String name;
    private final Color color;
    private int index = -1;

    GamePlayer(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public Color getColor() {
        return color;
    }

    /** The engine player index. Only valid once the game has started. */
    public int getIndex() {
        if (index < 0) {
            throw new IllegalStateException("player '" + name + "' is not bound yet — start the game first");
        }
        return index;
    }

    boolean isBound() {
        return index >= 0;
    }

    void bind(int index) {
        this.index = index;
    }
}
