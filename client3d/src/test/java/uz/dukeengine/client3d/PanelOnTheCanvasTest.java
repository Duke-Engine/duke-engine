package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Where the bottom panel stands, told to a game's painter: the bar across the foot of the window, as tall as it is
 * drawn — squeezed into a window too narrow for its blocks or not — and nowhere while it is hidden.
 */
class PanelOnTheCanvasTest {

    /** The slab's height in the design's pixels — the band of 172 and a pad of 10 over and under it. */
    private static final float SLAB = 192f;

    private static HeroPanel panel(float width, float height) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var hero = new HeroPanel(assets, font, null, new Node("gui"), width, height, PanelSkin.NONE, RangeLook.DEFAULT,
                IconLook.DEFAULT, StatLook.DEFAULT, PanelLook.DEFAULTS);
        assertTrue(hero.show(PanelLayoutTest.LINE, 0f), "the panel should have taken the line");
        return hero;
    }

    /** What a game working the height out for itself takes the bar's scale to be: the design's share of the width. */
    private static float legible(float width) {
        var look = PanelLook.DEFAULTS;
        return Math.clamp(width / look.designWidth(), look.minScale(), look.maxScale());
    }

    @Test
    void theBarStandsAcrossTheFootOfTheWindowAsTallAsItIsDrawn() {
        var hero = panel(2560f, 1600f);

        var stands = hero.standing();

        assertEquals(new Canvas.Box(0f, 1600f - hero.heightPixels(), 2560f, hero.heightPixels()), stands);
        assertEquals(SLAB * legible(2560f), stands.height(), 0.01f, "nothing squeezes it in a wide window");
    }

    /** The design's own bar is too wide for 1600 pixels at its legible scale: squeezed, it is drawn lower than that. */
    @Test
    void squeezedIntoANarrowerWindowItSaysHowTallItIsDrawnWhichTheDesignsFigureDoesNot() {
        for (float width : new float[] {1600f, 640f}) {
            var hero = panel(2560f, 1600f);
            hero.resize(width, 900f);

            var stands = hero.standing();

            assertEquals(hero.heightPixels(), stands.height(), 0.001f, "as tall as it is drawn");
            assertTrue(stands.height() < SLAB * legible(width) - 1f, width + " wide, lower than the design's figure "
                    + "would put it: " + stands.height() + " against " + SLAB * legible(width));
            assertEquals(900f - stands.height(), stands.y(), 0.001f, "its top edge, from the top of the window");
            assertEquals(width, stands.width(), 0.001f, "across the whole window");
        }
    }

    @Test
    void hiddenItStandsNowhere() {
        var hero = panel(1600f, 900f);

        hero.hide();

        assertNull(hero.standing());
    }

    @Test
    void aPainterIsToldWhereTheClientSaysItStandsAndNowhereWithNoClientBehindIt() {
        var stands = new Canvas.Box(0f, 708f, 1600f, 192f);
        var client = new CanvasFrame.World() {
            @Override
            public Canvas.Point screenOf(float x, float y, float height) {
                return null;
            }

            @Override
            public Canvas.Box barOf(int id) {
                return null;
            }

            @Override
            public Canvas.Box bottomPanel() {
                return stands;
            }
        };
        var text = new CanvasText(path -> null);

        assertEquals(stands, new CanvasFrame(1600, 900, text, Map.<String, int[]>of()::get, client).bottomPanel());
        assertNull(new CanvasFrame(1600, 900, text, Map.<String, int[]>of()::get).bottomPanel());
    }
}
