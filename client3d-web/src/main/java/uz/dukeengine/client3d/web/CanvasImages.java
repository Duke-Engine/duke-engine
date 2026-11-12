package uz.dukeengine.client3d.web;

import java.io.IOException;
import java.io.InputStream;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Int8Array;
import uz.dukeengine.client3d.Picture;
import uz.dukeengine.client3d.platform.ImageDecoder;

/** Picture files the browser decodes itself (PNG, JPEG, GIF, BMP, WebP), read back off a canvas. */
public final class CanvasImages implements ImageDecoder {

    @JSFunctor
    interface Got extends JSObject {
        void got(Int32Array pixels);
    }

    /** Width, height, then each pixel as ARGB, unpremultiplied and as the file has it; null where it is no picture. */
    @JSBody(params = {"bytes", "got"}, script = "createImageBitmap(new Blob([bytes]),"
            + " {premultiplyAlpha: 'none', colorSpaceConversion: 'none'}).then(function(b) {"
            + " var c = document.createElement('canvas'); c.width = b.width; c.height = b.height;"
            + " var g = c.getContext('2d', {willReadFrequently: true}); g.drawImage(b, 0, 0);"
            + " var d = g.getImageData(0, 0, b.width, b.height).data, n = b.width * b.height;"
            + " var out = new Int32Array(2 + n); out[0] = b.width; out[1] = b.height;"
            + " for (var i = 0; i < n; i++)"
            + "   out[2 + i] = (d[4 * i + 3] << 24) | (d[4 * i] << 16) | (d[4 * i + 1] << 8) | d[4 * i + 2];"
            + " b.close(); got(out); }, function() { got(null); });")
    private static native void decode(Int8Array bytes, Got got);

    @Async
    private static native int[] pixels(Int8Array bytes);

    private static void pixels(Int8Array bytes, AsyncCallback<int[]> callback) {
        decode(bytes, pixels -> callback.complete(pixels == null ? null : pixels.copyToJavaArray()));
    }

    /** Width, height and ARGB pixels of a picture file, or null where the browser cannot decode it. */
    public static int[] pixels(byte[] file) {
        return pixels(Int8Array.copyFromJavaArray(file));
    }

    @Override
    public Picture decode(InputStream in) throws IOException {
        var decoded = pixels(in.readAllBytes());
        if (decoded == null) {
            return null;
        }
        var picture = new Picture(decoded[0], decoded[1]);
        System.arraycopy(decoded, 2, picture.argb(), 0, picture.argb().length);
        return picture;
    }
}
