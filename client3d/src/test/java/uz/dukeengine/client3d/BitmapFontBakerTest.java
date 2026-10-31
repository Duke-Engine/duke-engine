package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.platform.Face;
import uz.dukeengine.client3d.platform.GlyphImage;

class BitmapFontBakerTest {

    /** One tall glyph and many flat ones: shelves waste more than the area-based guess of the atlas allows. */
    @Test
    void shelvesThatOutgrowTheGuessedAtlasGetALargerOne() {
        var chars = new StringBuilder("|");
        for (char c = 'A'; c < 'A' + 30; c++) {
            chars.append(c);
        }

        var baked = BitmapFontBaker.bake(new Face() {
            public String family() { return "Stub"; }
            public float size() { return 60; }
            public int ascent() { return 60; }
            public int descent() { return 0; }
            public int leading() { return 0; }
            public boolean canDisplay(int codePoint) { return true; }
            public double width(String text) { return text.length() * 12; }
            public GlyphImage draw(int codePoint) {
                int height = codePoint == '|' ? 60 : 1;
                var ink = new int[12 * height];
                Arrays.fill(ink, 0xFFFFFFFF);
                return new GlyphImage(0, 0, 12, height, 12, ink);
            }
        }, "stub.png", chars.toString());

        assertEquals(12 * 60 + 30 * 12, Arrays.stream(baked.argb()).filter(p -> p != 0).count(), "every glyph's ink");
    }
}
