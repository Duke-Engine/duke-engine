package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** A movie of thirty pictures at thirty a second: which picture is up when, when it ends, holding, stopping. */
class MovieTest {

    /** Thirty small pictures in a zip, each a shade of grey that says which it is. */
    private static byte[] thirtyPictures() throws Exception {
        var zipped = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(zipped)) {
            for (int index = 0; index < 30; index++) {
                var picture = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
                var pen = picture.createGraphics();
                pen.setColor(new java.awt.Color(index * 8, index * 8, index * 8));
                pen.fillRect(0, 0, 8, 8);
                pen.dispose();
                zip.putNextEntry(new ZipEntry("frame_%03d.jpg".formatted(index)));
                ImageIO.write(picture, "jpg", zip);
                zip.closeEntry();
            }
        }
        return zipped.toByteArray();
    }

    /** A movie's sound: how far it says it has got, and whether it was stopped. */
    private static final class Sound implements SoundSink.Playing {
        float at = Float.NaN;
        boolean stopped;

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public float seconds() {
            return at;
        }
    }

    /** The pictures, every one read before the clock starts, so reading is not what is timed. */
    private static MovieFrames read() throws Exception {
        var zip = thirtyPictures();
        var frames = new MovieFrames(() -> new ByteArrayInputStream(zip), 64);
        long giveUp = System.nanoTime() + 20_000_000_000L;
        while (frames.count() < 0) {
            assertTrue(System.nanoTime() < giveUp, "the pictures were never read");
            Thread.sleep(5);
        }
        assertEquals(30, frames.count());
        return frames;
    }

    @Test
    void frame15IsUpAtHalfASecondAndTheEndIsToldAfterOne() throws Exception {
        var sound = new Sound();
        int[] ends = {0};
        var player = new MoviePlayer(Movie.of("movies/test.zip", 30f), read(), sound, () -> ends[0]++);

        var up = player.update(0.5f);
        assertNotNull(up);
        assertEquals(15, up.index());
        assertEquals(29, player.update(0.46875f).index(), "the last picture, just before a second");
        assertEquals(0, ends[0]);

        player.update(0.03125f);

        assertEquals(1, ends[0], "told at a second");
        assertTrue(player.isOver());
        assertTrue(sound.stopped);
    }

    @Test
    void heldItStaysOnItsLastPictureUntilItIsStopped() throws Exception {
        var sound = new Sound();
        int[] ends = {0};
        var player = new MoviePlayer(Movie.of("movies/test.zip", 30f).holdingItsLastFrame(), read(), sound,
                () -> ends[0]++);

        player.update(1f);
        player.update(3f);

        assertEquals(1, ends[0], "told once that its last picture's time is up");
        assertFalse(player.isOver(), "and still up");
        assertEquals(29, player.shown().index());
        assertFalse(sound.stopped);
        player.stop();
        assertTrue(player.isOver());
        assertTrue(sound.stopped);
    }

    @Test
    void stoppingItEndsItAndItsSoundAtOnce() throws Exception {
        var sound = new Sound();
        int[] ends = {0};
        var player = new MoviePlayer(Movie.of("movies/test.zip", 30f), read(), sound, () -> ends[0]++);
        player.update(0.25f);

        player.stop();

        assertTrue(player.isOver());
        assertTrue(sound.stopped, "its sound with it");
        assertEquals(0, ends[0], "the game that stopped it knows");
        assertEquals(null, player.update(1f), "and nothing more is shown");
    }

    @Test
    void theSoundKeepsTheTime() throws Exception {
        var sound = new Sound();
        var player = new MoviePlayer(Movie.of("movies/test.zip", 30f), read(), sound, () -> { });

        sound.at = 0.5f; // the sound is half a second in, whatever the window's frames came to
        var up = player.update(0.1f);

        assertEquals(15, up.index());
    }
}
