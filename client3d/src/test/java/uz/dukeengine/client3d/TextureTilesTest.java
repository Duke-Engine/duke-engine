package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.renderer.Renderer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A picture's changes go to the card a tile at a time, and the whole picture where most of it changed. */
class TextureTilesTest {

    /** What a card was sent: where, how big, and the first texel's red. */
    private record Sent(int x, int y, int width, int height, int firstRed) {
    }

    /** A renderer that only writes down the tiles it is sent. */
    private static Renderer recording(List<Sent> sent) {
        return (Renderer) java.lang.reflect.Proxy.newProxyInstance(Renderer.class.getClassLoader(),
                new Class<?>[] {Renderer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("modifyTexture") && args.length == 4) {
                        var patch = (Image) args[1];
                        sent.add(new Sent((int) args[2], (int) args[3], patch.getWidth(), patch.getHeight(),
                                patch.getData(0).get(0) & 0xFF));
                    }
                    return null;
                });
    }

    @Test
    void aFewTexelsChangedGoAsTheirTilesAndAPictureMostlyChangedGoesWhole() {
        int width = 300;
        int height = 200;
        var texels = BufferUtils.createByteBuffer(width * height * 4);
        var image = new Image(Image.Format.RGBA8, width, height, texels, ColorSpace.Linear);
        var texture = new Texture2D(image);
        var tiles = new TextureTiles();
        tiles.resize(width, height);
        var sent = new ArrayList<Sent>();
        var renderer = recording(sent);

        tiles.send(renderer, texture, image, texels);
        assertTrue(image.isUpdateNeeded(), "never sent: the whole picture");
        assertTrue(sent.isEmpty());
        image.clearUpdateNeeded();

        texels.put((130 * width + 70) * 4, (byte) 200);
        tiles.changed(70, 130, 70, 130);
        tiles.send(renderer, texture, image, texels);
        assertFalse(image.isUpdateNeeded(), "not the whole picture");
        assertEquals(List.of(new Sent(64, 128, 64, 64, 0)), sent, "the one tile the texel stands in");
        assertFalse(tiles.waiting());

        sent.clear();
        texels.put((192 * width + 256) * 4, (byte) 77);
        tiles.changed(256, 192, 256, 192);
        tiles.send(renderer, texture, image, texels);
        assertEquals(List.of(new Sent(256, 192, 44, 8, 77)), sent, "a tile at the picture's corner, cut to it");

        sent.clear();
        tiles.changed(0, 0, 299, 150);
        tiles.send(renderer, texture, image, texels);
        assertTrue(image.isUpdateNeeded() && sent.isEmpty(), "most of it changed: the whole picture");
    }
}
