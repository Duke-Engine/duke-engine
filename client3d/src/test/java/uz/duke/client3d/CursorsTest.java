package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.texture.Image;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A pointer is drawn the right way up, with its tip where the file says.
 *
 * <p>Two conversions, and both of them fail <em>silently</em>. jME keeps a cursor
 * bottom-up — the last row of the buffer is the top row of the picture — so
 * getting it backwards gives a pointer that is upside down, which reads as
 * somebody having chosen a strange drawing rather than as a bug. And the hot spot
 * it hands the windowing layer is measured from the bottom, so getting that
 * backwards puts the tip at the wrong end and every click lands a pointer's
 * height away from where it was aimed — which reads as the game being imprecise.
 *
 * <p>Neither shows up in a compiler, a screenshot or a crash, and both are one
 * character to get wrong. So the arithmetic is done on a picture whose every
 * pixel is known.
 */
class CursorsTest {

    /**
     * A picture with a single opaque pixel in its top-left corner, and nothing
     * else. Wherever that pixel ends up is where the top-left corner ended up.
     */
    private static Image cornerMarked(int width, int height) {
        var bytes = BufferUtils.createByteBuffer(width * height * 4);
        for (int i = 0; i < width * height * 4; i++) {
            bytes.put((byte) 0);
        }
        bytes.rewind();
        // RGBA8, row 0 column 0: opaque red.
        bytes.put(0, (byte) 0xFF).put(1, (byte) 0).put(2, (byte) 0).put(3, (byte) 0xFF);
        return new Image(Image.Format.RGBA8, width, height, bytes, ColorSpace.sRGB);
    }

    @Test
    void thePictureIsStoredBottomUp() {
        var cursor = Cursors.build(cornerMarked(8, 6), new Cursors.Look("x", 0, 0));
        var data = cursor.getImagesData();

        // jME hands the window the buffer's LAST row first, so the picture's top
        // row has to be the buffer's last.
        assertEquals(0xFFFF0000, data.get((6 - 1) * 8),
                "the picture's top-left should be in the buffer's last row");
        assertEquals(0, data.get(0), "and nothing should be in its first");
    }

    /**
     * The tip is where the file says, counted from the top.
     *
     * <p>What jME finally passes on is {@code height - yHotSpot}, and that is what
     * has to equal the row a person reading the picture would point at — so the
     * test asserts the number the window gets rather than the one stored.
     */
    @Test
    void theTipIsTurnedRoundOnTheWayIn() {
        var cursor = Cursors.build(cornerMarked(32, 32), new Cursors.Look("x", 3, 5));

        assertEquals(3, cursor.getXHotSpot(), "across is across, either way up");
        assertEquals(5, cursor.getHeight() - cursor.getYHotSpot(),
                "five down from the top is what the file said and what the window must get");
    }

    /** A tip named outside the picture is pulled back into it rather than crashing. */
    @Test
    void aTipOffThePictureIsBroughtBack() {
        var cursor = Cursors.build(cornerMarked(16, 16), new Cursors.Look("x", 900, -4));

        assertTrue(cursor.getXHotSpot() >= 0 && cursor.getXHotSpot() < 16,
                "was " + cursor.getXHotSpot());
        int fromTop = cursor.getHeight() - cursor.getYHotSpot();
        assertTrue(fromTop >= 0 && fromTop < 16, "was " + fromTop);
    }

    /** Colour and alpha survive the trip, which is what makes it a picture. */
    @Test
    void theColourIsCarriedThrough() {
        var cursor = Cursors.build(cornerMarked(4, 4), new Cursors.Look("x", 0, 0));

        assertEquals(0xFFFF0000, cursor.getImagesData().get((4 - 1) * 4),
                "opaque red in, opaque red out");
    }

    @Test
    void aGameThatNamesNoPointersHasNone() {
        assertNotNull(new Cursors(null, null, Map.of()));
        assertTrue(!new Cursors(null, null, Map.of()).any(),
                "every game but this one names none, and must keep the system arrow");
        assertTrue(new Cursors(null, null,
                Map.of(Cursors.POINT, new Cursors.Look("a.png", 0, 0))).any());
    }

    /**
     * Asking for a situation nothing was named for changes nothing.
     *
     * <p>Deliberate: flicking back to the white arrow as the pointer crosses a
     * creature would be worse than one picture serving two situations. A game may
     * paint three of the five and the other two simply keep what is there.
     */
    @Test
    void anUnnamedSituationLeavesThePointerAlone() {
        var cursors = new Cursors(null, null,
                Map.of(Cursors.POINT, new Cursors.Look("a.png", 0, 0)));

        cursors.show(Cursors.ATTACK); // would need an input manager if it did anything
        cursors.show(null);
    }
}
