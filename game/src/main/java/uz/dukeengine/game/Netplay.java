package uz.dukeengine.game;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;
import uz.dukeengine.core.network.ChatLine;

/** A game's network session, if it has one, and the talk and loading figures that travel beside the match. */
final class Netplay {

    private final Roster roster;
    private MultiplayerSession session;
    private volatile ServerSocket hostingSocket;

    private final Queue<ChatLine> toSay = new ConcurrentLinkedQueue<>();
    private final List<Consumer<ChatLine>> chatListeners = new CopyOnWriteArrayList<>();

    /** How far this machine has got loading, as last shared — so the same figure is not said twice. */
    private int sharedProgress = -1;
    private final List<ObjIntConsumer<GamePlayer>> peerProgress = new ArrayList<>();

    Netplay(Roster roster) {
        this.roster = roster;
    }

    MultiplayerSession session() {
        return session;
    }

    boolean active() {
        return session != null;
    }

    /** Host for {@code playerCount} players, seating this machine as the session says; blocks until they have joined. */
    MultiplayerSession host(int port, int playerCount, String spec, IntConsumer onGuestJoined) throws IOException {
        roster.requireNetworkPlayers(playerCount);
        try (var server = new ServerSocket(port)) {
            hostingSocket = server;
            session = MultiplayerSession.host(server, playerCount, spec, onGuestJoined);
        } finally {
            hostingSocket = null;
        }
        roster.seat(session);
        return session;
    }

    void cancelHosting() {
        var server = hostingSocket;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // best-effort cancel
            }
        }
    }

    MultiplayerSession join(String host, int port) throws IOException {
        roster.requireNetworkPlayers(2);
        session = MultiplayerSession.join(host, port);
        roster.requireNetworkPlayers(session.getPlayerCount());
        roster.seat(session);
        return session;
    }

    /** Play over a session made before the match. */
    void adopt(MultiplayerSession made) {
        roster.requireNetworkPlayers(made.getPlayerCount());
        this.session = made;
        roster.seatAndPlay(made);
    }

    /** Let go of the session, if there is one: the match is over for this machine, and the others are told. */
    void close() {
        if (session != null) {
            session.close();
        }
    }

    /** Wire the session into the loop: commands over the wire, frames waiting for every player, the others told. */
    void wire(LogicDriver driver, MatchEngine engine, IntConsumer playerLeft, Consumer<String> banner) {
        driver.setSession(session);  // local commands go over the wire
        engine.setSession(session);  // frames wait for every player's input
        session.onPlayerLeft(playerLeft);
        session.onPeerProgress(this::heardProgress);
        session.onChat(this::heardChat);
        // A cut-off peer and a peer waiting on a slow one look identical from
        // the outside — both stopped — so say which this is.
        session.onConnectionLost(() -> banner.accept("CONNECTION LOST"));
        // The simulation has already been stopped by the gate; all that is left
        // is to say why, so the screen does not simply freeze. The detail is
        // logged by the gate itself, which is where the two worlds were compared.
        session.onDesync(desync -> banner.accept(DukeGame.DESYNC_MESSAGE));
    }

    void say(String text, Collection<Integer> to) {
        toSay.add(new ChatLine(roster.localIndex(), List.copyOf(to), text));
    }

    void onChat(Consumer<ChatLine> listener) {
        chatListeners.add(listener);
    }

    /** Send what was said, and take in what arrived: every turn of the loop, paused or waiting or not. */
    void talk() {
        for (var line = toSay.poll(); line != null; line = toSay.poll()) {
            if (session != null) {
                session.say(line.text(), line.recipients());
            } else {
                heardChat(line);
            }
        }
        if (session != null) {
            session.listen();
        }
    }

    private void heardChat(ChatLine line) {
        chatListeners.forEach(listener -> listener.accept(line));
    }

    void loadProgress(int percent) {
        if (session == null) {
            return;
        }
        if (percent != sharedProgress) {
            sharedProgress = percent;
            session.shareProgress(percent);
        } else {
            session.listenWhileLoading();
        }
    }

    void onPeerLoadProgress(ObjIntConsumer<GamePlayer> listener) {
        peerProgress.add(listener);
    }

    private void heardProgress(int playerIndex, int percent) {
        var who = roster.playerAt(playerIndex);
        if (who == null) {
            return;
        }
        for (var listener : peerProgress) {
            listener.accept(who, percent);
        }
    }
}
