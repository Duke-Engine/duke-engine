package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One frame of the game's drawing, as it was called: every primitive turned into triangles, clipped to the clip
 * rectangle as it stood when it was drawn, in the order it was drawn. What {@link CanvasDrawing} puts on the screen,
 * and what a test reads without one.
 *
 * <p>Clipping is exact rather than done by the card: a quad is cut at the clip rectangle and its picture with it, in
 * proportion, as the reference cuts it ({@code W3DDisplay::drawImage}); a line or a triangle is cut the same way,
 * polygon against rectangle, so a clip holds whatever is drawn under it.
 */
final class CanvasFrame implements Canvas {

    /** What a triangle is filled from. */
    sealed interface Source permits Plain, Picture, Glyphs, Pixels {
    }

    /** Its colour alone. */
    record Plain() implements Source {
    }

    /** A picture's file, in grey or not. */
    record Picture(String path, boolean gray) implements Source {
    }

    /** A page of {@link CanvasText}'s glyphs. */
    record Glyphs(int page) implements Source {
    }

    /** A picture the game made, the very one — see {@link uz.dukeengine.client3d.Picture}. */
    record Pixels(uz.dukeengine.client3d.Picture picture) implements Source {
    }

    private static final Plain PLAIN = new Plain();

    /**
     * One triangle, in screen pixels from the top left, with its picture's coordinates from 0 to 1 from the picture's
     * top left and a colour at each corner.
     */
    record Triangle(Source source, Blend blend, float[] x, float[] y, float[] u, float[] v, int[] argb) {
    }

    private final int width;
    private final int height;
    private final CanvasText text;
    /** A picture's size in pixels, {width, height}, or null for one that will not load. */
    private final Function<String, int[]> pictureSize;
    private final List<Triangle> triangles = new ArrayList<>();
    private float[] clip;

    CanvasFrame(int width, int height, CanvasText text, Function<String, int[]> pictureSize) {
        this(width, height, text, pictureSize, World.NONE);
    }

    /** The same, asking {@code world} where things are on the screen this frame. */
    CanvasFrame(int width, int height, CanvasText text, Function<String, int[]> pictureSize, World world) {
        this.width = width;
        this.height = height;
        this.text = text;
        this.pictureSize = pictureSize;
        this.world = world;
    }

    /** Where the world's points and things' bars are on the screen this frame — see {@link Canvas#screenOf}. */
    interface World {
        World NONE = new World() {
            @Override
            public Canvas.Point screenOf(float x, float y, float height) {
                return null;
            }

            @Override
            public Canvas.Box barOf(int id) {
                return null;
            }
        };

        Canvas.Point screenOf(float x, float y, float height);

        Canvas.Box barOf(int id);
    }

    private final World world;

    @Override
    public Canvas.Point screenOf(float x, float y, float height) {
        return world.screenOf(x, y, height);
    }

    @Override
    public Canvas.Box barOf(int id) {
        return world.barOf(id);
    }

    /**
     * A point of the world on the screen through {@code camera}, in the canvas's pixels from the top left — the map's y
     * the scene's z, its height the scene's y — or null behind the eye.
     */
    static Canvas.Point onScreen(com.jme3.renderer.Camera camera, float x, float y, float height) {
        var at = camera.getScreenCoordinates(new com.jme3.math.Vector3f(x, height, y));
        if (at.z < 0f || at.z > 1f) {
            return null;
        }
        return new Canvas.Point(at.x, camera.getHeight() - at.y);
    }

    /** A bar placed in the window's pixels from the bottom left — left, bottom, width, height — in the canvas's. */
    static Canvas.Box fromTheBottom(float[] placed, int windowHeight) {
        return placed == null ? null
                : new Canvas.Box(placed[0], windowHeight - placed[1] - placed[3], placed[2], placed[3]);
    }

    /** Everything drawn, in the order it was drawn. */
    List<Triangle> triangles() {
        return triangles;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void drawImage(Image image, float x0, float y0, float x1, float y1, int argb, Blend blend) {
        if (image == null || image.path() == null) {
            return;
        }
        var size = pictureSize.apply(image.path());
        if (size == null || size[0] <= 0 || size[1] <= 0) {
            return;
        }
        float left = image.whole() ? 0f : image.left() / (float) size[0];
        float right = image.whole() ? 1f : image.right() / (float) size[0];
        float top = image.whole() ? 0f : image.top() / (float) size[1];
        float bottom = image.whole() ? 1f : image.bottom() / (float) size[1];
        var source = new Picture(image.path(), blend == Blend.GRAYSCALE);
        if (image.turned()) {
            // Held a quarter turn clockwise: the screen's top left is the part's top right, as the reference maps
            // a ROTATED_90_CLOCKWISE image.
            polygon(source, blend, new float[][]{
                    vertex(x0, y0, right, top, argb), vertex(x1, y0, right, bottom, argb),
                    vertex(x1, y1, left, bottom, argb), vertex(x0, y1, left, top, argb)});
        } else {
            polygon(source, blend, new float[][]{
                    vertex(x0, y0, left, top, argb), vertex(x1, y0, right, top, argb),
                    vertex(x1, y1, right, bottom, argb), vertex(x0, y1, left, bottom, argb)});
        }
    }

    @Override
    public void drawPicture(uz.dukeengine.client3d.Picture picture, float x0, float y0, float x1, float y1, int argb,
            Blend blend) {
        if (picture == null) {
            return;
        }
        polygon(new Pixels(picture), blend, new float[][]{
                vertex(x0, y0, 0f, 0f, argb), vertex(x1, y0, 1f, 0f, argb),
                vertex(x1, y1, 1f, 1f, argb), vertex(x0, y1, 0f, 1f, argb)});
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        polygon(PLAIN, Blend.ALPHA, new float[][]{
                vertex(x, y, 0f, 0f, argb), vertex(x + w, y, 0f, 0f, argb),
                vertex(x + w, y + h, 0f, 0f, argb), vertex(x, y + h, 0f, 0f, argb)});
    }

    @Override
    public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
        float edge = Math.min(lineWidth, Math.min(w, h) / 2f);
        if (edge <= 0f) {
            return;
        }
        fillRect(x, y, w, edge, argb);
        fillRect(x, y + h - edge, w, edge, argb);
        fillRect(x, y + edge, edge, h - 2f * edge, argb);
        fillRect(x + w - edge, y + edge, edge, h - 2f * edge, argb);
    }

    @Override
    public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        polygon(PLAIN, Blend.ALPHA, new float[][]{
                vertex(x0, y0, 0f, 0f, argb), vertex(x1, y1, 0f, 0f, argb), vertex(x2, y2, 0f, 0f, argb)});
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        line(x0, y0, x1, y1, width, argb, argb);
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb, int argbEnd) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0f || width <= 0f) {
            return;
        }
        float nx = -dy / length * width / 2f;
        float ny = dx / length * width / 2f;
        polygon(PLAIN, Blend.ALPHA, new float[][]{
                vertex(x0 + nx, y0 + ny, 0f, 0f, argb), vertex(x1 + nx, y1 + ny, 0f, 0f, argbEnd),
                vertex(x1 - nx, y1 - ny, 0f, 0f, argbEnd), vertex(x0 - nx, y0 - ny, 0f, 0f, argb)});
    }

    @Override
    public void clip(float x0, float y0, float x1, float y1) {
        clip = new float[]{Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1)};
    }

    @Override
    public void noClip() {
        clip = null;
    }

    @Override
    public void drawText(Font font, String line, float x, float y, int argb) {
        if (font == null || line == null || line.isEmpty()) {
            return;
        }
        for (var placed : text.layout(font, line).glyphs()) {
            var glyph = placed.glyph();
            if (glyph.blank()) {
                continue;
            }
            float left = x + placed.x() + glyph.left();
            float top = y + glyph.top();
            float u0 = glyph.x() / (float) CanvasText.PAGE;
            float v0 = glyph.y() / (float) CanvasText.PAGE;
            float u1 = (glyph.x() + glyph.width()) / (float) CanvasText.PAGE;
            float v1 = (glyph.y() + glyph.height()) / (float) CanvasText.PAGE;
            polygon(new Glyphs(glyph.page()), Blend.ALPHA, new float[][]{
                    vertex(left, top, u0, v0, argb), vertex(left + glyph.width(), top, u1, v0, argb),
                    vertex(left + glyph.width(), top + glyph.height(), u1, v1, argb),
                    vertex(left, top + glyph.height(), u0, v1, argb)});
        }
    }

    @Override
    public Measure measure(Font font, String line) {
        if (font == null) {
            return new Measure(0, 0);
        }
        var laid = text.layout(font, line == null ? "" : line);
        return new Measure(laid.width(), laid.lineHeight());
    }

    // ---- polygons, clipped ----

    /** A corner: where, its picture's coordinates, and its colour's four channels. */
    private static float[] vertex(float x, float y, float u, float v, int argb) {
        return new float[]{x, y, u, v,
                (argb >>> 24) & 0xFF, (argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF};
    }

    /** A convex polygon, clipped to the clip rectangle and laid down as a fan of triangles. */
    private void polygon(Source source, Blend blend, float[][] corners) {
        var kept = corners;
        if (clip != null) {
            kept = cut(kept, 0, clip[0], true);
            kept = cut(kept, 1, clip[1], true);
            kept = cut(kept, 0, clip[2], false);
            kept = cut(kept, 1, clip[3], false);
        }
        for (int at = 1; at + 1 < kept.length; at++) {
            var a = kept[0];
            var b = kept[at];
            var c = kept[at + 1];
            triangles.add(new Triangle(source, blend,
                    new float[]{a[0], b[0], c[0]}, new float[]{a[1], b[1], c[1]},
                    new float[]{a[2], b[2], c[2]}, new float[]{a[3], b[3], c[3]},
                    new int[]{argb(a), argb(b), argb(c)}));
        }
    }

    /**
     * One edge of the clip: the part of a polygon on the kept side of {@code axis = at}, every attribute of a corner
     * made on the edge carried in proportion — which is what cuts a picture where its quad is cut.
     */
    private static float[][] cut(float[][] polygon, int axis, float at, boolean keepAbove) {
        var kept = new ArrayList<float[]>();
        for (int i = 0; i < polygon.length; i++) {
            var from = polygon[i];
            var to = polygon[(i + 1) % polygon.length];
            boolean fromIn = keepAbove ? from[axis] >= at : from[axis] <= at;
            boolean toIn = keepAbove ? to[axis] >= at : to[axis] <= at;
            if (fromIn) {
                kept.add(from);
            }
            if (fromIn != toIn) {
                float share = (at - from[axis]) / (to[axis] - from[axis]);
                var made = new float[from.length];
                for (int k = 0; k < made.length; k++) {
                    made[k] = from[k] + (to[k] - from[k]) * share;
                }
                made[axis] = at;
                kept.add(made);
            }
        }
        return kept.toArray(new float[0][]);
    }

    private static int argb(float[] corner) {
        return channel(corner[4]) << 24 | channel(corner[5]) << 16 | channel(corner[6]) << 8 | channel(corner[7]);
    }

    private static int channel(float value) {
        return Math.clamp(Math.round(value), 0, 255);
    }
}
