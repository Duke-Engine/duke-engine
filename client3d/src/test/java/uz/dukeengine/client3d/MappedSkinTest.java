package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;
import org.junit.jupiter.api.Test;

class MappedSkinTest {

    @Test
    void smoothMetalShinesAndRoughClothDoesNot() {
        // Occlusion, roughness, metal: a polished steel texel, then a wool one.
        var bytes = BufferUtils.createByteBuffer(6);
        bytes.put(new byte[] {(byte) 255, 26, (byte) 255, (byte) 255, (byte) 230, 0}).flip();
        var source = new Texture2D(new Image(Image.Format.RGB8, 2, 1, bytes, ColorSpace.Linear));

        var raster = ImageRaster.create(MappedSkin.gloss(source).getImage());

        assertTrue(raster.getPixel(0, 0).r > 0.7f, "steel should shine");
        assertTrue(raster.getPixel(1, 0).r < 0.05f, "wool should not");
    }

    @Test
    void aModelKeepsNoMapsUntilTheGameAsks() {
        assertFalse(new Visuals.UnitVisual().keepsMaps);
        assertTrue(new Visuals.UnitVisual().keepsMaps(true).keepsMaps);
    }
}
