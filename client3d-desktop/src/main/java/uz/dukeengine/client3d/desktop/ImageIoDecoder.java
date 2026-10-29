package uz.dukeengine.client3d.desktop;

import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import uz.dukeengine.client3d.Picture;
import uz.dukeengine.client3d.platform.ImageDecoder;

/** Reads picture files with ImageIO. */
final class ImageIoDecoder implements ImageDecoder {

    @Override
    public Picture decode(InputStream in) throws IOException {
        // Cached in memory rather than in a file, and without touching the image reader's global switch; closing the
        // cache leaves the stream open.
        var image = ImageIO.read(new MemoryCacheImageInputStream(in));
        if (image == null) {
            return null;
        }
        var picture = new Picture(image.getWidth(), image.getHeight());
        image.getRGB(0, 0, picture.width(), picture.height(), picture.argb(), 0, picture.width());
        return picture;
    }
}
