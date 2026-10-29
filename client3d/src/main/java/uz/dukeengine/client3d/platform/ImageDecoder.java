package uz.dukeengine.client3d.platform;

import java.io.IOException;
import java.io.InputStream;
import uz.dukeengine.client3d.Picture;

/** Reads a picture file into pixels. */
public interface ImageDecoder {

    /** The picture in the stream, or {@code null} when it is not one. Never closes {@code in}. */
    Picture decode(InputStream in) throws IOException;
}
