package uz.dukeengine.core.network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** The channels of lines the engine makes itself: a pair in memory, and a socket's. */
final class LineChannels {

    private LineChannels() {
    }

    static LineChannel[] pair() {
        var toSecond = new LinkedBlockingQueue<Optional<String>>();
        var toFirst = new LinkedBlockingQueue<Optional<String>>();
        var shut = new AtomicBoolean();
        return new LineChannel[] {new InMemory(toSecond, toFirst, shut), new InMemory(toFirst, toSecond, shut)};
    }

    static LineChannel over(Socket socket) throws IOException {
        return new Sockets(socket);
    }

    /** One end of a pair: lines out on one queue, in on the other; the end of the channel an empty mark on both. */
    private static final class InMemory implements LineChannel {
        private final LinkedBlockingQueue<Optional<String>> out;
        private final LinkedBlockingQueue<Optional<String>> in;
        private final AtomicBoolean shut;

        private InMemory(LinkedBlockingQueue<Optional<String>> out, LinkedBlockingQueue<Optional<String>> in,
                AtomicBoolean shut) {
            this.out = out;
            this.in = in;
            this.shut = shut;
        }

        @Override
        public boolean send(String line) {
            if (shut.get()) {
                return false;
            }
            out.add(Optional.of(line));
            return true;
        }

        @Override
        public String receive() {
            try {
                var line = in.take();
                if (line.isEmpty()) {
                    in.add(line); // the end, for whoever reads again
                    return null;
                }
                return line.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        @Override
        public void close() {
            if (shut.compareAndSet(false, true)) {
                out.add(Optional.empty());
                in.add(Optional.empty());
            }
        }
    }

    /** A socket's lines: a send that finds it reset is its end, and any other failure keeps its error. */
    private static final class Sockets implements LineChannel {
        private final Socket socket;
        private final BufferedWriter out;
        private final BufferedReader in;

        private Sockets(Socket socket) throws IOException {
            this.socket = socket;
            socket.setTcpNoDelay(true);
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public boolean send(String line) {
            try {
                synchronized (out) {
                    out.write(line);
                    out.write('\n');
                    out.flush();
                }
                return true;
            } catch (java.net.SocketException gone) {
                return false;
            } catch (IOException e) {
                throw new UncheckedIOException("failed to send " + line, e);
            }
        }

        @Override
        public String receive() {
            try {
                return in.readLine();
            } catch (IOException e) {
                return null; // closed or errored: the end of the channel either way
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing best-effort
            }
        }
    }
}
