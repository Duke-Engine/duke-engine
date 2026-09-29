package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.font.BitmapText;
import java.awt.Font;
import org.junit.jupiter.api.Test;

/**
 * A face named for the hero's bar is baked at the pixel size each line lands at and drawn at its own size, one pixel
 * of it to one of the screen, however large the bar is drawn; with none named the bar keeps the bitmap font, drawn
 * through its scale as before.
 */
class LetteringTest {

    private final DesktopAssetManager assets = new DesktopAssetManager(true);
    private final Lettering sans = new Lettering(assets, new Font(Font.SANS_SERIF, Font.PLAIN, 12));

    @Test
    void aFaceIsBakedSoThatALineOfItStandsThePixelsAsked() {
        var font = sans.at(22);

        assertNotNull(font);
        assertEquals(22, font.getCharSet().getRenderedSize(), 1, "a line of it 22 pixels tall");
        assertSame(font, sans.at(22), "baked once a size");
    }

    /** A 17-pixel line of a bar drawn at 1.3 lands 22 pixels tall: baked there, drawn there, scaled back 1.3. */
    @Test
    void aLineInANamedFaceIsDrawnAtItsOwnSizeAndScaledBackAgainstTheBar() {
        var letters = HeroPanel.letters(sans, null, 17f, 1.3f);

        assertEquals(letters.font().getCharSet().getRenderedSize(), letters.size(), 1e-4f,
                "at the size it was baked at, a texel to a pixel");
        assertEquals(22f, letters.size(), 1f);
        assertEquals(1.3f, letters.perDesign(), 1e-4f);

        var line = new BitmapText(letters.font());
        line.setSize(letters.size());
        line.setText("Erika");
        assertTrue(line.getLineWidth() > 0f, "its glyphs read and drawn: " + line.getLineWidth());
    }

    @Test
    void withNoFaceNamedTheBitmapFontIsDrawnAtTheDesignsSizeThroughTheBarsScale() {
        var bitmap = assets.loadFont("Interface/Fonts/Default.fnt");

        var letters = HeroPanel.letters(null, bitmap, 17f, 1.3f);

        assertSame(bitmap, letters.font());
        assertEquals(17f, letters.size(), 1e-4f);
        assertEquals(1f, letters.perDesign(), 1e-4f);
    }

    @Test
    void aFontFileThatIsNotThereLeavesTheBitmapFont() {
        assertNull(Lettering.of(assets, "fonts/no-such-face.ttf"));
        assertNull(Lettering.of(assets, null), "and none named is none");
    }

    /** The bar builds with a face that will not load, in the bitmap fonts, as one naming none does. */
    @Test
    void aBarNamingAFaceThatWillNotLoadIsStillABar() throws ReflectiveOperationException {
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var look = lettered(PanelLook.DEFAULTS, "fonts/no-such-face.ttf", "fonts/no-such-bold.ttf");
        var gui = new com.jme3.scene.Node("gui");

        var panel = new HeroPanel(assets, font, font, gui, 2560f, 1600f, PanelSkin.NONE, RangeLook.DEFAULT,
                IconLook.DEFAULT, StatLook.DEFAULT, look);

        assertNotNull(panel.minimapRect());
    }

    /** The look with its lettering named and every other component as it was. */
    private static PanelLook lettered(PanelLook look, String lettering, String titleLettering)
            throws ReflectiveOperationException {
        var components = PanelLook.class.getRecordComponents();
        var types = new Class<?>[components.length];
        var values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = switch (components[i].getName()) {
                case "lettering" -> lettering;
                case "titleLettering" -> titleLettering;
                default -> components[i].getAccessor().invoke(look);
            };
        }
        return PanelLook.class.getDeclaredConstructor(types).newInstance(values);
    }
}
