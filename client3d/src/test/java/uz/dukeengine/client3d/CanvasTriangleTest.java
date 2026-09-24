package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A filled triangle on the canvas — what a build button's clock is swept out of — checked pixel by pixel: the
 * triangles the frame records are laid over a white square at the centres of its pixels and blended by their alpha,
 * as the card blends them.
 */
class CanvasTriangleTest {

    private static final int WHITE = 0xFFFFFFFF;

    /** The frame's plain triangles over a white square, each pixel blended source over what is under it. */
    private static int[] laidOver(CanvasFrame frame, int size) {
        int[] pixels = new int[size * size];
        java.util.Arrays.fill(pixels, WHITE);
        for (var triangle : frame.triangles()) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (inside(triangle, x + 0.5f, y + 0.5f)) {
                        pixels[y * size + x] = over(triangle.argb()[0], pixels[y * size + x]);
                    }
                }
            }
        }
        return pixels;
    }

    private static boolean inside(CanvasFrame.Triangle t, float px, float py) {
        float d1 = side(px, py, t.x()[0], t.y()[0], t.x()[1], t.y()[1]);
        float d2 = side(px, py, t.x()[1], t.y()[1], t.x()[2], t.y()[2]);
        float d3 = side(px, py, t.x()[2], t.y()[2], t.x()[0], t.y()[0]);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
        boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    private static float side(float px, float py, float ax, float ay, float bx, float by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

    private static int over(int source, int under) {
        float alpha = ((source >>> 24) & 0xFF) / 255f;
        int red = Math.round(((source >>> 16) & 0xFF) * alpha + ((under >>> 16) & 0xFF) * (1 - alpha));
        int green = Math.round(((source >>> 8) & 0xFF) * alpha + ((under >>> 8) & 0xFF) * (1 - alpha));
        int blue = Math.round((source & 0xFF) * alpha + (under & 0xFF) * (1 - alpha));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static CanvasFrame frame() {
        return new CanvasFrame(10, 10, new CanvasText(path -> null), Map.<String, int[]>of()::get);
    }

    @Test
    void aTriangleOverTheLowerLeftHalfFillsThatSideOfTheDiagonalAndNoneOfTheOther() {
        var frame = frame();

        frame.fillTriangle(0f, 0f, 0f, 10f, 10f, 10f, 0xFF000000);
        var pixels = laidOver(frame, 10);

        int filled = 0;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                if (x < y) {
                    assertEquals(0xFF000000, pixels[y * 10 + x], "below the diagonal at " + x + ", " + y);
                    filled++;
                } else if (x > y) {
                    assertEquals(WHITE, pixels[y * 10 + x], "above it at " + x + ", " + y);
                }
            }
        }
        assertEquals(45, filled);
    }

    @Test
    void aHalfAlphaTriangleOverWhiteIsGrey() {
        var frame = frame();

        frame.fillTriangle(0f, 0f, 0f, 10f, 10f, 10f, 0x80000000);

        assertEquals(0xFF7F7F7F, laidOver(frame, 10)[8 * 10 + 1]);
        assertEquals(List.of(Canvas.Blend.ALPHA), frame.triangles().stream().map(CanvasFrame.Triangle::blend)
                .distinct().toList(), "blended by the colour's alpha");
    }

    @Test
    void aClipCutsIt() {
        var frame = frame();
        frame.clip(0f, 0f, 5f, 10f);

        frame.fillTriangle(0f, 0f, 0f, 10f, 10f, 10f, 0xFF000000);
        var pixels = laidOver(frame, 10);

        assertEquals(0xFF000000, pixels[8 * 10 + 2], "inside the clip, drawn");
        assertEquals(WHITE, pixels[8 * 10 + 7], "inside the triangle but outside the clip: not");
    }
}
