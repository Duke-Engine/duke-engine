package uz.dukeengine.game;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.player.PlayerList;

/** A game's players, and which of them this machine plays — or whether it only watches. */
final class Roster {

    private final List<GamePlayer> players = new ArrayList<>();
    private GamePlayer local;
    private boolean observing;
    /** Whether this machine has taken a watcher's seat mid-match. */
    private volatile boolean watching;

    GamePlayer add(String name, Colour colour) {
        var player = new GamePlayer(name, colour);
        players.add(player);
        if (local == null && !observing) {
            local = player;
        }
        return player;
    }

    List<GamePlayer> all() {
        return players;
    }

    int size() {
        return players.size();
    }

    /** The player seated at a 1-based engine index, or null for none (the neutral side, or a seat that is not one). */
    GamePlayer playerAt(int index) {
        return index < 1 || index > players.size() ? null : players.get(index - 1);
    }

    /** The player bound to this engine index, or null. */
    GamePlayer bound(int index) {
        for (var player : players) {
            if (player.isBound() && player.getIndex() == index) {
                return player;
            }
        }
        return null;
    }

    Colour colourOf(int index) {
        var player = bound(index);
        return player != null ? player.getColour() : index == 0 ? Colour.GRAY : Colour.MAGENTA;
    }

    /** The index of the player this machine plays, or -1. */
    int localIndex() {
        return !watching && local != null && local.isBound() ? local.getIndex() : -1;
    }

    /** Whose point of view the client draws: the local player's, or everyone's where there is none. */
    int viewer() {
        return local == null ? MatchClient.EVERYONE : local.getIndex();
    }

    /** Play as {@code player}, or watch for null. */
    void sit(GamePlayer player) {
        this.local = player;
        this.observing = player == null;
    }


    /** Play the player a network session seated this machine as. */
    void seat(MultiplayerSession session) {
        this.local = players.get(session.getLocalPlayerIndex() - 1);
    }

    void seatAndPlay(MultiplayerSession session) {
        this.observing = false;
        seat(session);
    }

    void watch() {
        watching = true;
    }

    boolean isWatching() {
        return watching || observing;
    }

    void requireNetworkPlayers(int needed) {
        if (needed < 2) {
            throw new IllegalStateException("a network game needs at least two players");
        }
        if (players.size() < needed) {
            throw new IllegalStateException("this game defines only " + players.size()
                    + " players, but the match needs " + needed);
        }
    }

    void bindAll(PlayerList list) {
        for (var player : players) {
            player.bind(list.addPlayer(player.getName()).getIndex());
        }
    }
}
