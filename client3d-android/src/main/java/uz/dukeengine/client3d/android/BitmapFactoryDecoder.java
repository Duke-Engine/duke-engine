package uz.dukeengine.client3d.android;

import android.graphics.BitmapFactory;
import java.io.InputStream;
import uz.dukeengine.client3d.Picture;
import uz.dukeengine.client3d.platform.ImageDecoder;

/** Reads picture files with BitmapFactory. */
final class BitmapFactoryDecoder implements ImageDecoder {

    @Override
    public Picture decode(InputStream in) {
        var bitmap = BitmapFactory.decodeStream(in); // leaves the stream open
        if (bitmap == null) {
            return null;
        }
        var picture = new Picture(bitmap.getWidth(), bitmap.getHeight());
        bitmap.getPixels(picture.argb(), 0, picture.width(), 0, 0, picture.width(), picture.height());
        bitmap.recycle();
        return picture;
    }
}
