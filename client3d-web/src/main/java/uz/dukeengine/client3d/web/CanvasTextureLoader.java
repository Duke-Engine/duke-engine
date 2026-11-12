package uz.dukeengine.client3d.web;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.TextureKey;
import com.jme3.texture.Image;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.io.IOException;

/** A picture file as a jME image, decoded by the browser (see {@link CanvasImages}): what AWT does on a desktop. */
public final class CanvasTextureLoader implements AssetLoader {

    @Override
    public Object load(AssetInfo info) throws IOException {
        byte[] file;
        try (var in = info.openStream()) {
            file = in.readAllBytes();
        }
        var pixels = CanvasImages.pixels(file);
        if (pixels == null) {
            throw new IOException("the browser cannot decode " + info.getKey().getName());
        }
        int width = pixels[0];
        int height = pixels[1];
        // A picture file's first row is its top; a texture's is its bottom unless the key says to leave it.
        boolean flip = info.getKey() instanceof TextureKey key && key.isFlipY();
        var data = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            int row = 2 + (flip ? height - 1 - y : y) * width;
            for (int x = 0; x < width; x++) {
                int argb = pixels[row + x];
                data.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
            }
        }
        data.flip();
        return new Image(Image.Format.RGBA8, width, height, data, ColorSpace.sRGB);
    }
}
