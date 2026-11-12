package uz.dukeengine.client3d.web;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Int8Array;
import uz.dukeengine.client3d.platform.Face;
import uz.dukeengine.client3d.platform.GlyphImage;
import uz.dukeengine.client3d.platform.GlyphRasterizer;
import uz.dukeengine.client3d.platform.Typeface;

/**
 * Fonts and glyphs from a canvas's 2D context: it measures and draws, antialiased, white on nothing. A font file
 * becomes a {@code FontFace} under a name of its own. The browser falls back to another font for a character a face
 * lacks, so every face can display everything.
 */
final class CanvasGlyphs implements GlyphRasterizer {

    private static final String GENERIC = " serif sans-serif monospace cursive fantasy system-ui ";
    private static int loaded;

    @JSFunctor
    interface Done extends JSObject {
        void done(boolean ok);
    }

    @JSBody(params = {"name", "bytes", "done"}, script = """
            try { var f = new FontFace(name, bytes);
              f.load().then(function() { document.fonts.add(f); done(true); }, function() { done(false); });
            } catch (e) { done(false); }
            """)
    private static native void addFont(String name, Int8Array bytes, Done done);

    @Async
    private static native Boolean font(String name, Int8Array bytes);

    private static void font(String name, Int8Array bytes, AsyncCallback<Boolean> callback) {
        addFont(name, bytes, ok -> callback.complete(ok));
    }

    @Override
    public Typeface load(byte[] fontFile) {
        var name = "duke-font-" + loaded++;
        return font(name, Int8Array.copyFromJavaArray(fontFile)) ? new CanvasTypeface('"' + name + '"', "font file")
                : null;
    }

    @Override
    public Typeface system(String family) {
        if (family == null) {
            return new CanvasTypeface("sans-serif", "default");
        }
        var css = GENERIC.contains(" " + family + " ") ? family : '"' + family + "\", sans-serif";
        return new CanvasTypeface(css, family);
    }

    private record CanvasTypeface(String css, String family) implements Typeface {
        @Override
        public Face at(float size, boolean bold, float scaleX, boolean fractionalMetrics) {
            return new CanvasFace((bold ? "bold " : "") + size + "px " + css, family, size, scaleX, fractionalMetrics);
        }
    }

    private static final String CONTEXT = """
            var c = globalThis.dukeGlyphs || (globalThis.dukeGlyphs =
              document.createElement('canvas')); var g = c.getContext('2d', {willReadFrequently: true});
            """;

    /** The face's ascent and descent, whole pixels up from and down from the baseline. */
    @JSBody(params = "font", script = CONTEXT + """
            g.font = font; var m = g.measureText('Hg');
            return new Int32Array([Math.ceil(m.fontBoundingBoxAscent), Math.ceil(m.fontBoundingBoxDescent)]);
            """)
    private static native Int32Array metrics(String font);

    @JSBody(params = {"font", "scaleX", "text"}, script = CONTEXT + """
            g.font = font; return g.measureText(text).width * scaleX;
            """)
    private static native double measure(String font, float scaleX, String text);

    /**
     * {@code text} drawn white: left and top of its ink (a pixel round it, which antialiasing reaches into) from the
     * pen on the baseline, width, height, then the pixels as ARGB, unpremultiplied. Four zeros where it has no ink.
     */
    @JSBody(params = {"font", "scaleX", "text"}, script = CONTEXT + """
            g.font = font; var m = g.measureText(text);
            if (m.actualBoundingBoxLeft + m.actualBoundingBoxRight <= 0
                || m.actualBoundingBoxAscent + m.actualBoundingBoxDescent <= 0) return new Int32Array(4);
            var l = Math.floor(-m.actualBoundingBoxLeft * scaleX) - 1;
            var r = Math.ceil(m.actualBoundingBoxRight * scaleX) + 1;
            var t = Math.floor(-m.actualBoundingBoxAscent) - 1, b = Math.ceil(m.actualBoundingBoxDescent) + 1;
            var w = r - l, h = b - t;
            if (c.width < w || c.height < h) { c.width = Math.max(c.width, w); c.height = Math.max(c.height, h);
              g = c.getContext('2d', {willReadFrequently: true}); }
            g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, w, h);
            g.font = font; g.textBaseline = 'alphabetic'; g.fillStyle = '#fff';
            g.setTransform(scaleX, 0, 0, 1, -l, -t); g.fillText(text, 0, 0);
            var d = g.getImageData(0, 0, w, h).data, out = new Int32Array(4 + w * h);
            out[0] = l; out[1] = t; out[2] = w; out[3] = h;
            for (var i = 0, n = w * h; i < n; i++)
              out[4 + i] = (d[4 * i + 3] << 24) | (d[4 * i] << 16) | (d[4 * i + 1] << 8) | d[4 * i + 2];
            return out;
            """)
    private static native Int32Array ink(String font, float scaleX, String text);

    private static final class CanvasFace implements Face {

        private final String font;
        private final String family;
        private final float size;
        private final float scaleX;
        private final boolean fractional;
        private final int ascent;
        private final int descent;

        CanvasFace(String font, String family, float size, float scaleX, boolean fractional) {
            this.font = font;
            this.family = family;
            this.size = size;
            this.scaleX = scaleX;
            this.fractional = fractional;
            var metrics = metrics(font).copyToJavaArray();
            ascent = metrics[0];
            descent = metrics[1];
        }

        @Override
        public String family() {
            return family;
        }

        @Override
        public float size() {
            return size;
        }

        @Override
        public int ascent() {
            return ascent;
        }

        @Override
        public int descent() {
            return descent;
        }

        @Override
        public int leading() {
            return 0;
        }

        @Override
        public boolean canDisplay(int codePoint) {
            return true;
        }

        @Override
        public double width(String text) {
            if (fractional) {
                return measure(font, scaleX, text);
            }
            return text.codePoints().mapToDouble(this::advance).sum();
        }

        @Override
        public GlyphImage draw(int codePoint) {
            var text = new String(Character.toChars(codePoint));
            var drawn = ink(font, scaleX, text).copyToJavaArray();
            int width = drawn[2];
            int height = drawn[3];
            var argb = new int[width * height];
            System.arraycopy(drawn, 4, argb, 0, argb.length);
            int top = width == 0 ? 0 : ascent + drawn[1];
            return new GlyphImage(drawn[0], top, width, height, advance(codePoint), argb);
        }

        private float advance(int codePoint) {
            float measured = (float) measure(font, scaleX, new String(Character.toChars(codePoint)));
            return fractional ? measured : Math.round(measured);
        }
    }
}
