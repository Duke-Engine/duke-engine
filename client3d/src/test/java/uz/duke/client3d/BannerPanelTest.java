package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.awt.Color;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The banner is a plaque, and the three moments do not look alike.
 *
 * <p>What this guards is the half of the feature that is easy to lose: the text
 * was always drawn, so a frame that silently fails to appear leaves a working
 * banner and nobody notices for a week. And the kind is carried in front of the
 * text through a channel the engine never reads — a split that goes wrong shows
 * up as the word "won" printed on screen rather than as an error.
 */
class BannerPanelTest {

    private static final int GOLD = 0xC9A24B;
    private static final int WON = 0xF0C860;
    private static final int LOST = 0xB03A30;

    /** A picture jME itself ships, so this needs no asset of the game's to run. */
    private static final String TEXTURE = "Common/Textures/dot.png";

    private static PanelSkin skin(Map<String, Integer> tints) {
        var pieces = new java.util.LinkedHashMap<String, PanelSkin.Piece>();
        tints.forEach((name, tint) ->
                pieces.put(name, new PanelSkin.Piece(TEXTURE, 16f, 1.8f, new Color(tint))));
        return new PanelSkin(pieces);
    }

    private record Screen(BannerPanel banner, Node gui) {
    }

    private static Screen screen(PanelSkin skin) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        return new Screen(new BannerPanel(assets, font, gui, skin), gui);
    }

    private static Screen dressed() {
        return screen(skin(Map.of(PanelSkin.BANNER, GOLD,
                PanelSkin.BANNER_WON, WON, PanelSkin.BANNER_LOST, LOST)));
    }

    private static Geometry frameOf(Node gui) {
        for (var child : ((Node) gui.getChild(0)).getChildren()) {
            if (child instanceof Geometry geometry && "banner-frame".equals(child.getName())) {
                return geometry;
            }
        }
        return null;
    }

    private static ColorRGBA tintOf(Node gui) {
        var frame = frameOf(gui);
        assertNotNull(frame, "the plaque should have been cut");
        return (ColorRGBA) frame.getMaterial().getParam("Color").getValue();
    }

    private static com.jme3.font.BitmapText wordsOf(Node gui) {
        for (var child : ((Node) gui.getChild(0)).getChildren()) {
            if (child instanceof com.jme3.font.BitmapText text) {
                return text;
            }
        }
        throw new AssertionError("the banner has no lettering");
    }

    /** The kind is taken off the front and never reaches the screen. */
    @Test
    void theKindIsReadOffTheFrontAndNotPrinted() {
        var screen = dressed();

        screen.banner().show("won|Siz yutdingiz", 1600f, 900f);

        assertEquals("Siz yutdingiz", wordsOf(screen.gui()).getText(),
                "the word in front of the bar is which plaque, not part of the sentence");
    }

    /** A banner with no kind on it is the whole sentence, as every other game sends. */
    @Test
    void aBannerWithNoKindIsAllText() {
        var screen = dressed();

        screen.banner().show("Wave 4", 1600f, 900f);

        assertEquals("Wave 4", wordsOf(screen.gui()).getText());
        assertNotNull(frameOf(screen.gui()), "and it still gets the plain plaque");
    }

    /**
     * The three moments are three plaques.
     *
     * <p>The point of doing this at all: a death is an interruption, a floor is a
     * door, an ending is an ending, and the frame is the only thing on screen that
     * can tell the player which of the three just happened.
     */
    @Test
    void theThreeMomentsDoNotLookAlike() {
        var going = dressed();
        going.banner().show("depth|Chuqurlik 2", 1600f, 900f);
        var won = dressed();
        won.banner().show("won|Siz yutdingiz", 1600f, 900f);
        var lost = dressed();
        lost.banner().show("lost|Siz o'ldingiz", 1600f, 900f);

        var down = tintOf(going.gui());
        var win = tintOf(won.gui());
        var end = tintOf(lost.gui());
        assertNotEquals(win, end, "winning and dying should not be the same plaque");
        assertNotEquals(down, end, "nor dying and a door to the next floor");
        assertTrue(end.r > end.g && end.r > end.b, "and the one that went badly is red");
    }

    /** A game that names only one plaque gets it for all three rather than none. */
    @Test
    void oneNamedPlaqueServesEveryKind() {
        var screen = screen(skin(Map.of(PanelSkin.BANNER, GOLD)));

        screen.banner().show("won|Siz yutdingiz", 1600f, 900f);

        assertNotNull(frameOf(screen.gui()), "it should have fallen back to the plain one");
    }

    /** And a game that names none still shows the words. */
    @Test
    void withNoSkinAtAllTheWordsStillShow() {
        var screen = screen(PanelSkin.NONE);

        screen.banner().show("lost|Siz o'ldingiz", 1600f, 900f);

        assertEquals(null, frameOf(screen.gui()), "there is no picture to cut it from");
        assertEquals("Siz o'ldingiz", wordsOf(screen.gui()).getText(),
                "but the words are the point and the stone is the manners");
        assertTrue(screen.banner().showing());
    }

    /** It sits in the middle of the screen, plaque and all. */
    @Test
    void itIsCentredOnTheScreen() {
        var screen = dressed();

        screen.banner().show("depth|Chuqurlik 2", 1600f, 900f);

        var at = screen.gui().getChild(0).getLocalTranslation();
        float middleX = at.x + screen.banner().width() / 2f;
        float middleY = at.y - screen.banner().height() / 2f;
        assertEquals(800f, middleX, 1f, "across");
        assertEquals(450f, middleY, 1f, "and down");
    }

    /** An empty banner takes it off the screen. */
    @Test
    void nothingToSayMeansNothingOnScreen() {
        var screen = dressed();
        screen.banner().show("won|Siz yutdingiz", 1600f, 900f);
        assertTrue(screen.banner().showing());

        screen.banner().show(null, 1600f, 900f);
        assertEquals(Spatial.CullHint.Always, screen.gui().getChild(0).getLocalCullHint());

        screen.banner().show("depth|", 1600f, 900f);
        assertEquals(Spatial.CullHint.Always, screen.gui().getChild(0).getLocalCullHint(),
                "a kind with nothing after it is nothing to say");
    }

    /**
     * The plaque is cut once and left alone while it says the same thing.
     *
     * <p>A banner is up for seconds at a time, which is hundreds of frames. Re-cutting
     * it on every one of them is the fault the order markers had, and it is invisible
     * until something else on screen starts dropping frames.
     */
    @Test
    void itIsNotReCutOnAFrameThatChangedNothing() {
        var screen = dressed();
        screen.banner().show("won|Siz yutdingiz", 1600f, 900f);
        var first = frameOf(screen.gui());

        for (int frame = 0; frame < 100; frame++) {
            screen.banner().show("won|Siz yutdingiz", 1600f, 900f);
        }

        assertEquals(first, frameOf(screen.gui()), "the same plaque should still be up");
        assertEquals(2, ((Node) screen.gui().getChild(0)).getChildren().size(),
                "one plaque and one line of lettering, however many frames have passed");
    }
}
