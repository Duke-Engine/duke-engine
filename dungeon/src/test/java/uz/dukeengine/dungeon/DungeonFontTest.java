package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import org.junit.jupiter.api.Test;

/**
 * The lettering the menus are drawn in really loads, and really has letters in it.
 *
 * <p>jME cannot read a TrueType font, so the game ships a bitmap one: a PNG of
 * every glyph and an AngelCode {@code .fnt} saying where each of them sits. Both
 * were baked here rather than downloaded — see {@code BitmapFontBaker} — and
 * every way that can go wrong is quiet. A page the {@code .fnt} names but nobody
 * saved gives text that draws as nothing; a glyph left out of the set draws as a
 * hole in the middle of a word; a wrong baseline draws a line that sits a little
 * too low and looks, unaccountably, cheap.
 *
 * <p>No window is opened. A font is an image and a table.
 */
class DungeonFontTest {

    private static final String[] SIZES = {"cinzel-17", "cinzel-22", "cinzel-40"};

    private static com.jme3.font.BitmapFont load(String name) {
        return new DesktopAssetManager(true).loadFont("fonts/" + name + ".fnt");
    }

    /** Every size the menus ask for is shipped and opens. */
    @Test
    void everySizeLoads() {
        for (var name : SIZES) {
            var font = load(name);
            assertNotNull(font, name + " is named but not shipped");
            assertTrue(font.getCharSet().getLineHeight() > 0,
                    name + " loaded with no line height, so nothing would be spaced");
        }
    }

    /**
     * And carries the characters a menu is written from.
     *
     * <p>Every word the game puts on a menu is upper-case Latin with the odd
     * digit and apostrophe. A missing glyph is not an error anywhere — the letter
     * is simply absent from the word.
     */
    @Test
    void everyLetterAMenuNeedsIsInIt() {
        var set = load("cinzel-22").getCharSet();
        for (char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 '×".toCharArray()) {
            assertNotNull(set.getCharacter(c),
                    "cinzel-22 has no '" + c + "', which a menu would draw as a gap");
        }
    }

    /**
     * Text measures wider the more of it there is, and taller the bigger it is.
     *
     * <p>Which sounds like nothing and is the whole of what a font is for. A
     * {@code .fnt} whose advances are all zero loads, reports a line height, and
     * draws every letter of a word on top of the first one.
     */
    @Test
    void textHasWidthAndTheSizesDiffer() {
        var small = new com.jme3.font.BitmapText(load("cinzel-17"));
        var large = new com.jme3.font.BitmapText(load("cinzel-40"));
        small.setText("DUKE DUNGEON");
        large.setText("DUKE DUNGEON");

        assertTrue(small.getLineWidth() > 0f, "the small font measures nothing");
        assertTrue(large.getLineWidth() > small.getLineWidth() * 1.5f,
                "forty should be a good deal wider than seventeen: "
                        + small.getLineWidth() + " against " + large.getLineWidth());

        var one = new com.jme3.font.BitmapText(load("cinzel-22"));
        one.setText("I");
        var many = new com.jme3.font.BitmapText(load("cinzel-22"));
        many.setText("IIIIIIIIII");
        assertTrue(many.getLineWidth() > one.getLineWidth() * 5f,
                "ten letters should be about ten times one");
    }

    /** The page each .fnt names is the file that shipped beside it. */
    @Test
    void everyPageNamedIsShipped() throws Exception {
        for (var name : SIZES) {
            var text = new String(DungeonFontTest.class.getClassLoader()
                    .getResourceAsStream("fonts/" + name + ".fnt").readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            var page = text.lines().filter(line -> line.startsWith("page "))
                    .findFirst().orElseThrow();
            var file = page.replaceAll(".*file=\"([^\"]+)\".*", "$1");
            assertEquals(name + ".png", file, "the page must be named after its own font");
            assertNotNull(DungeonFontTest.class.getClassLoader().getResource("fonts/" + file),
                    file + " is named by " + name + ".fnt but is not shipped");
        }
    }
}
