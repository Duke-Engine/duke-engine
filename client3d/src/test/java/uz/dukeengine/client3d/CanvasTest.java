package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The game's drawing, as it is called: in order, clipped, and text laid out the one way it is measured. */
class CanvasTest {

    private static final Map<String, int[]> PICTURES = Map.of("ui/atlas.png", new int[]{200, 100});

    private static CanvasFrame frame(CanvasText text) {
        return new CanvasFrame(800, 600, text, PICTURES::get);
    }

    private static CanvasText text() {
        return new CanvasText(path -> null);
    }

    @Test
    void whatAPainterDrawsArrivesInTheOrderItWasDrawnWithTheClipApplied() {
        var frame = frame(text());
        Painter painter = canvas -> {
            canvas.fillRect(0f, 0f, 100f, 100f, 0xFFFF0000);
            canvas.clip(10f, 10f, 50f, 50f);
            canvas.drawImage(Canvas.Image.of("ui/atlas.png"), 0f, 0f, 100f, 100f, 0x80FFFFFF, Canvas.Blend.ADDITIVE);
            canvas.noClip();
            canvas.fillTriangle(0f, 0f, 10f, 0f, 0f, 10f, 0xFF00FF00);
        };

        painter.paint(frame);
        var drawn = frame.triangles();

        assertEquals(5, drawn.size(), "two triangles a quad, one for the triangle");
        assertInstanceOf(CanvasFrame.Plain.class, drawn.get(0).source());
        assertEquals(0xFFFF0000, drawn.get(0).argb()[0]);
        var picture = drawn.subList(2, 4);
        for (var triangle : picture) {
            assertEquals(new CanvasFrame.Picture("ui/atlas.png", false), triangle.source());
            assertEquals(Canvas.Blend.ADDITIVE, triangle.blend());
            for (int corner = 0; corner < 3; corner++) {
                float x = triangle.x()[corner];
                float y = triangle.y()[corner];
                assertTrue(x >= 10f && x <= 50f && y >= 10f && y <= 50f, "inside the clip: " + x + ", " + y);
                // Cut with its quad, in proportion: a tenth of the way across the quad is a tenth of the picture.
                assertEquals(x / 100f, triangle.u()[corner], 1e-5f);
                assertEquals(y / 100f, triangle.v()[corner], 1e-5f);
            }
        }
        assertEquals(0xFF00FF00, drawn.get(4).argb()[0], "and after the clip is lifted, whole again");
        assertEquals(10f, drawn.get(4).x()[1], 1e-6f);
    }

    @Test
    void aPictureHeldAQuarterTurnIsDrawnUprightAndAPartOfAnAtlasIsCutFromIt() {
        var frame = frame(text());

        frame.drawImage(new Canvas.Image("ui/atlas.png", 20, 10, 60, 90, true), 0f, 0f, 80f, 40f, 0xFFFFFFFF,
                Canvas.Blend.ALPHA);

        var first = frame.triangles().getFirst();
        // The screen's top left is the part's top right, as the reference maps a ROTATED_90_CLOCKWISE image.
        assertEquals(0f, first.x()[0]);
        assertEquals(0f, first.y()[0]);
        assertEquals(60f / 200f, first.u()[0], 1e-6f);
        assertEquals(10f / 100f, first.v()[0], 1e-6f);
        assertEquals(60f / 200f, first.u()[1], 1e-6f, "its top right is the part's bottom right");
        assertEquals(90f / 100f, first.v()[1], 1e-6f);
    }

    @Test
    void aPictureThatWillNotLoadDrawsNothing() {
        var frame = frame(text());

        frame.drawImage(Canvas.Image.of("ui/nothing.png"), 0f, 0f, 10f, 10f, 0xFFFFFFFF, Canvas.Blend.ALPHA);

        assertEquals(List.of(), frame.triangles());
    }

    @Test
    void aMeasuredLineIsTheSumOfTheAdvancesItIsDrawnWith() {
        var text = text();
        var frame = frame(text);
        var font = Canvas.Font.of("Arial", 20);

        var measured = frame.measure(font, "Hello");
        var laid = text.layout(font, "Hello");
        int sum = laid.glyphs().stream().mapToInt(placed -> placed.glyph().advance()).sum();

        assertEquals(sum, measured.width());
        assertTrue(measured.width() > 0 && measured.lineHeight() >= 20, "a line of text: " + measured);
        frame.drawText(font, "Hello", 100f, 50f, 0xFFFFFFFF);
        var glyphs = frame.triangles();
        assertEquals(10, glyphs.size(), "a quad a letter");
        var lastPlaced = laid.glyphs().getLast();
        assertEquals(100f + lastPlaced.x() + lastPlaced.glyph().left(), glyphs.get(8).x()[0], 1e-6f,
                "the last letter drawn where the advances before it put it");
    }

    @Test
    void aCodePointFrom256UpIsDrawnFromTheWideFamily() {
        var text = text();
        var font = Canvas.Font.of("Serif", 20).glyphWidth(8).wideFamily("SansSerif");

        var laid = text.layout(font, "Aж");
        var plain = text.layout(Canvas.Font.of("SansSerif", 20), "ж");

        assertEquals("Serif", laid.glyphs().get(0).family());
        assertEquals("SansSerif", laid.glyphs().get(1).family());
        assertEquals(plain.glyphs().getFirst().glyph().advance(), laid.glyphs().get(1).glyph().advance(),
                "at the wide face's own width, not the condensed one");
    }

    @Test
    void aFaceAskedForAWidthIsCondensedToIt() {
        var arial = java.awt.Font.decode("Arial").deriveFont(20f);
        float natural = CanvasText.averageWidth(arial);
        var text = text();

        var narrow = text.layout(Canvas.Font.of("Arial", 20).glyphWidth(8), "abcdefghij").width();
        var wide = text.layout(Canvas.Font.of("Arial", 20), "abcdefghij").width();

        assertTrue(natural > 8f, "Arial's own average is wider than eight pixels at 20: " + natural);
        assertTrue(narrow < wide, "condensed: " + narrow + " against " + wide);
        assertEquals(wide * 8f / natural, narrow, 3f);
    }
}
