package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.texture.Image;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** That a strip picture is read as the cube it holds, and that the identity cube gives every colour back. */
class ColorLutTest {

    /** A strip of {@code size} squares whose (r, g, b) step holds {@code grade(r, g, b)} in 8 bits, as a tool would export it. */
    private static Image strip(int size, UnaryOperator<float[]> grade) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(size * size * size * 3);
        for (int g = 0; g < size; g++) {
            for (int b = 0; b < size; b++) {
                for (int r = 0; r < size; r++) {
                    for (float c : grade.apply(new float[] {r / (size - 1f), g / (size - 1f), b / (size - 1f)})) {
                        bytes.put((byte) Math.round(c * 255f));
                    }
                }
            }
        }
        bytes.flip();
        return new Image(Image.Format.RGB8, size * size, size, bytes, ColorSpace.Linear);
    }

    @Test
    void anIdentityStripIsTheIdentityCubeAndAnyColourComesBack() {
        var read = ColorLut.fromStrip(strip(32, c -> c));
        assertEquals(32, read.size());
        var made = ColorLut.identity(32);
        for (float[] colour : new float[][] {{0f, 0f, 0f}, {1f, 1f, 1f}, {0.2f, 0.7f, 0.45f}, {0.013f, 0.5f, 0.99f}}) {
            assertArrayEquals(colour, made.at(colour[0], colour[1], colour[2]), 1e-6f);
            // The strip holds 8 bits a step, so a colour is out by at most half a level.
            assertArrayEquals(colour, read.at(colour[0], colour[1], colour[2]), 0.5f / 255f + 1e-6f);
        }
    }

    @Test
    void redRunsAcrossASquareGreenDownAndBlueFromSquareToSquare() {
        var read = ColorLut.fromStrip(strip(4, c -> new float[] {c[2], c[0], c[1]}));
        // This grade hands each colour on one place: the step (r=1, g=0, b=0) holds (0, 1, 0).
        assertArrayEquals(new float[] {0f, 1f, 0f}, read.at(1f, 0f, 0f), 1e-6f);
        assertArrayEquals(new float[] {0f, 0f, 1f}, read.at(0f, 1f, 0f), 1e-6f);
        assertArrayEquals(new float[] {1f, 0f, 0f}, read.at(0f, 0f, 1f), 1e-6f);
    }

    @Test
    void aPictureThatIsNotASquaresWideStripOfItsHeightIsRefused() {
        var notAStrip = new Image(Image.Format.RGB8, 15, 4, BufferUtils.createByteBuffer(15 * 4 * 3), ColorSpace.Linear);
        assertThrows(IllegalArgumentException.class, () -> ColorLut.fromStrip(notAStrip));
    }
}
