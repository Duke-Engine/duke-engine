package uz.dukeengine.core.network;

import java.io.IOException;
import java.net.Socket;

/**
 * A duplex channel of text lines — a socket's, or one a game opens itself: a WebSocket's text frames through an HTTPS
 * gateway, the one way to a machine behind carrier NAT. A session runs over it exactly as over a socket: its handshake
 * line by line, then one line a message ({@link NetFraming}); the reference keeps its lock-step apart from how packets
 * travel the same way ({@code ConnectionManager} over its {@code Transport}).
 */
public interface LineChannel extends AutoCloseable {

    /** Send one line, whole: false once the channel has closed, the line not sent. */
    boolean send(String line);

    /** The next line, waiting for it; null once the channel has closed and every line sent before was read. */
    String receive();

    /** Closed from this end: the other end reads what was sent, then null. */
    @Override
    void close();

    /** Two ends of one channel in memory: what the one sends the other receives, in order, until either closes. */
    static LineChannel[] pair() {
        return LineChannels.pair();
    }

    /** A channel over a connected socket, its lines UTF-8 and its sends not held back. */
    static LineChannel over(Socket socket) throws IOException {
        return LineChannels.over(socket);
    }
}
