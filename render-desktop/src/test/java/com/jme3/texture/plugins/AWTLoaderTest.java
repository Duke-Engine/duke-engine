package com.jme3.texture.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.texture.Image;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AWTLoaderTest {

    @Test
    void anIntArgbPictureLoadsAsTheSameRgbaBytesGetRgbWouldGive() {
        int w = 37;
        int h = 11;
        var picture = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var random = new Random(7);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                picture.setRGB(x, y, random.nextInt());
            }
        }
        for (boolean flip : new boolean[] {false, true}) {
            var image = new AWTLoader().load(picture, flip);
            assertEquals(Image.Format.RGBA8, image.getFormat());
            var data = image.getData(0);
            assertEquals(0, data.position());
            assertEquals(w * h * 4, data.limit());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = picture.getRGB(x, flip ? h - y - 1 : y);
                    int at = (y * w + x) * 4;
                    assertEquals((byte) (argb >> 16), data.get(at), "r");
                    assertEquals((byte) (argb >> 8), data.get(at + 1), "g");
                    assertEquals((byte) argb, data.get(at + 2), "b");
                    assertEquals((byte) (argb >>> 24), data.get(at + 3), "a");
                }
            }
        }
    }
}
