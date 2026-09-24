package uz.dukeengine.client3d;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/**
 * A movie's pictures, read ahead on a thread of their own so a slow disc never holds the picture up: each decoded into
 * the pixels the card takes — red, green, blue, bottom row first — and queued a few ahead of the one on the screen,
 * the reader waiting whenever it is that far ahead.
 */
final class MovieFrames implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MovieFrames.class.getName());

    /** How many pictures are read ahead of the one shown: a quarter of a second at thirty a second. */
    static final int AHEAD = 8;

    /** One picture, decoded. */
    record Frame(int index, int width, int height, ByteBuffer pixels) {
    }

    /** The mark the reader puts after the last picture. */
    private static final Frame END = new Frame(-1, 0, 0, null);

    private final BlockingQueue<Frame> ahead;
    private final Thread reader;
    private volatile int count = -1;
    private volatile boolean closed;
    private boolean allRead;

    /**
     * @param open   opens the zip of pictures, or answers null for one that is not there
     * @param howFar how many pictures to read ahead
     */
    MovieFrames(Supplier<InputStream> open, int howFar) {
        this.ahead = new ArrayBlockingQueue<>(Math.max(1, howFar) + 1);
        this.reader = new Thread(() -> read(open), "duke-movie");
        reader.setDaemon(true);
        reader.start();
    }

    private void read(Supplier<InputStream> open) {
        int index = 0;
        try (var raw = open.get()) {
            if (raw == null) {
                LOG.warning("no movie there; it ends at once");
            } else {
                var zip = new ZipInputStream(raw);
                for (var entry = zip.getNextEntry(); entry != null && !closed; entry = zip.getNextEntry()) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    // Cached in memory rather than in a file, and without touching the image reader's global switch;
                    // closing the cache leaves the zip open for its next entry.
                    var picture = ImageIO.read(new javax.imageio.stream.MemoryCacheImageInputStream(zip));
                    if (picture == null) {
                        LOG.warning("a movie's " + entry.getName() + " is no picture; left out");
                        continue;
                    }
                    ahead.put(frame(index++, picture));
                }
            }
        } catch (IOException e) {
            LOG.warning("a movie stopped reading after " + index + " pictures: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        count = index;
        try {
            ahead.put(END);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A picture as the card takes it: red, green, blue, the bottom row first. */
    private static Frame frame(int index, BufferedImage picture) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        var pixels = ByteBuffer.allocateDirect(width * height * 3);
        int[] row = new int[width];
        for (int y = height - 1; y >= 0; y--) {
            picture.getRGB(0, y, width, 1, row, 0, width);
            for (int rgb : row) {
                pixels.put((byte) (rgb >>> 16)).put((byte) (rgb >>> 8)).put((byte) rgb);
            }
        }
        return new Frame(index, width, height, pixels.flip());
    }

    /**
     * The picture for frame {@code wanted}: the latest read that is not after it, those before it passed over — or
     * null when there is nothing newer than what was last taken. Never waits.
     */
    Frame take(int wanted) {
        Frame latest = null;
        for (var next = ahead.peek(); next != null; next = ahead.peek()) {
            if (next == END) {
                allRead = true;
                break;
            }
            if (next.index() > wanted) {
                break;
            }
            latest = ahead.poll();
        }
        return latest;
    }

    /** Whether every picture has been read and handed out, so {@link #count} is known and nothing more is coming. */
    boolean allRead() {
        if (!allRead && ahead.peek() == END) {
            allRead = true;
        }
        return allRead;
    }

    /** How many pictures it has, once they are all read; -1 before. */
    int count() {
        return count;
    }

    @Override
    public void close() {
        closed = true;
        reader.interrupt();
        ahead.clear();
    }
}
