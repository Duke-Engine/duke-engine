package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** A picture the game makes itself — a radar's layers — drawn on the canvas, and handed to the card only when changed. */
class GamePictureTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int CLEAR = 0x00FFFFFF;

    private static Picture fourColours() {
        var picture = new Picture(2, 2);
        picture.set(0, 0, RED);
        picture.set(1, 0, GREEN);
        picture.set(0, 1, BLUE);
        picture.set(1, 1, CLEAR);
        picture.changed();
        return picture;
    }

    /** The picture's colour the card shows at a screen point: the triangles' coordinates there, into its pixels. */
    private static int shownAt(CanvasFrame frame, Picture picture, float x, float y) {
        for (var t : frame.triangles()) {
            float d = (t.y()[1] - t.y()[2]) * (t.x()[0] - t.x()[2]) + (t.x()[2] - t.x()[1]) * (t.y()[0] - t.y()[2]);
            float a = ((t.y()[1] - t.y()[2]) * (x - t.x()[2]) + (t.x()[2] - t.x()[1]) * (y - t.y()[2])) / d;
            float b = ((t.y()[2] - t.y()[0]) * (x - t.x()[2]) + (t.x()[0] - t.x()[2]) * (y - t.y()[2])) / d;
            float c = 1f - a - b;
            if (a < 0f || b < 0f || c < 0f) {
                continue;
            }
            float u = a * t.u()[0] + b * t.u()[1] + c * t.u()[2];
            float v = a * t.v()[0] + b * t.v()[1] + c * t.v()[2];
            // As CanvasDrawing hands it over: the card counts rows up from the bottom, and so does its coordinate.
            int column = Math.min(picture.width() - 1, (int) (u * picture.width()));
            int rowFromBottom = Math.min(picture.height() - 1, (int) ((1f - v) * picture.height()));
            var data = CanvasDrawing.pixels(picture);
            int at = (rowFromBottom * picture.width() + column) * 4;
            return (data.get(at + 3) & 0xFF) << 24 | (data.get(at) & 0xFF) << 16 | (data.get(at + 1) & 0xFF) << 8
                    | (data.get(at + 2) & 0xFF);
        }
        throw new AssertionError("nothing drawn at " + x + ", " + y);
    }

    @Test
    void aTwoByTwoPictureStretchedOverTwentyShowsItsFourQuadrants() {
        var picture = fourColours();
        var frame = new CanvasFrame(100, 100, new CanvasText(path -> null), Map.<String, int[]>of()::get);

        frame.drawPicture(picture, 0f, 0f, 20f, 20f);

        assertEquals(RED, shownAt(frame, picture, 5f, 5f), "top left");
        assertEquals(GREEN, shownAt(frame, picture, 15f, 5f), "top right");
        assertEquals(BLUE, shownAt(frame, picture, 5f, 15f), "bottom left");
        assertEquals(CLEAR, shownAt(frame, picture, 15f, 15f), "bottom right");
        assertEquals(Canvas.Blend.ALPHA, frame.triangles().getFirst().blend());
    }

    @Test
    void aTransparentPixelLetsWhatIsUnderItShow() {
        var picture = fourColours();

        var data = CanvasDrawing.pixels(picture);

        // Its bottom-right pixel is the card's first row, second pixel: alpha nothing, so what is under shows.
        assertEquals(0, data.get(4 + 3));
    }

    @Test
    void drawingTheSameUnchangedPictureTwiceHandsItOverOnce() {
        var picture = fourColours();
        var uploads = new PictureUploads();

        assertTrue(uploads.needsUpload(picture), "the first time");
        assertFalse(uploads.needsUpload(picture), "unchanged: not again");
        picture.set(0, 0, BLUE);
        picture.changed();
        assertTrue(uploads.needsUpload(picture), "changed: once more");
        assertFalse(uploads.needsUpload(picture));
    }
}
