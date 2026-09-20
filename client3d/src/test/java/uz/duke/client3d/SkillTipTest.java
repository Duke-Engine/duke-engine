package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;

/**
 * The card over a slot: that the line reaches it, and that resting on a socket
 * puts it on screen.
 *
 * <p>Written after it shipped doing nothing. Every part of it was right on its
 * own — the game wrote the fields, the parser read them, the card could draw
 * itself — and the whole did nothing, which is the shape of fault a test of any
 * one part will never find.
 *
 * <p>Runs headless. {@code DesktopAssetManager} loads a font and a material
 * without a window, so the panel here is the real one.
 */
class SkillTipTest {

    /** A line of the shape the dungeon really sends, tooltip fields and all. */
    private static final String LINE =
            "name=Erika|rank=1-daraja|hp=550/550|xp=0/30|depth=I|depthWord=CHUQURLIK"
                    + "|skWord=MAHORAT"
                    + "|skill=Q,,lock|srank=Q,0,4,up,0 → 1"
                    + "|tipName=Q,Og'ir o'q|tipAt=Q,Q"
                    + "|tipText=Q,Kamonni to'liq tortib otadi. O'q xonani kesib o'tadi,"
                    + " shuning uchun nishon qochib ulgurishi mumkin."
                    + "|tipRow=Q,Zarar,,45|tipRow=Q,Masofa,68,|tipRow=Q,Kuluar,,3.0s"
                    + "|tipFoot=Q,Ctrl+Q · 1 nuqta sarflanadi"
                    + "|skill=W,,lock|srank=W,1,4,no,1-daraja"
                    + "|tipName=W,O'q yomg'iri|tipAt=W,1-daraja · W"
                    + "|tipRow=W,Zarar,28,|tipFoot=W,Nuqta yo'q"
                    + "|pts=1,NUQTA";

    private static HeroPanel panel() {
        var assets = new DesktopAssetManager(true);
        assets.registerLocator("/", com.jme3.asset.plugins.ClasspathLocator.class);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var panel = new HeroPanel(assets, font, new Node("gui"), 1600f, 900f, PanelSkin.NONE,
                RangeLook.DEFAULT);
        assertTrue(panel.show(LINE, 0f), "the panel should have taken the line");
        return panel;
    }

    // ---- the line reaches the card ----

    /** Every part of a card survives the trip. */
    @Test
    void theLineCarriesTheWholeCard() {
        var reading = HeroPanel.Reading.parse(LINE);

        assertNotNull(reading, "the line was refused outright");
        var card = reading.tips().get('Q');
        assertNotNull(card, "no card for Q at all: " + reading.tips().keySet());
        assertEquals("Og'ir o'q", card.name());
        assertTrue(card.blurb().startsWith("Kamonni"), "the sentence: " + card.blurb());
        assertEquals(3, card.rows().size(), "three rows of figures");
        assertEquals("Zarar", card.rows().get(0).label());
        assertEquals("", card.rows().get(0).now(), "unbought, so nothing in the left column");
        assertEquals("45", card.rows().get(0).next());
        assertTrue(card.canRaise(), "its slot says a point may go there, so the foot offers");
    }

    /** A sentence with a comma in it is not cut at the comma. */
    @Test
    void aSentenceKeepsItsCommas() {
        var card = HeroPanel.Reading.parse(LINE).tips().get('Q');

        assertTrue(card.blurb().contains("o'tadi, shuning uchun"),
                "the description was cut at its first comma: " + card.blurb());
    }

    /** And the footer's offer follows the slot rather than being said twice. */
    @Test
    void aSlotWithNoPointOffersNothing() {
        var card = HeroPanel.Reading.parse(LINE).tips().get('W');

        assertNotNull(card);
        assertFalse(card.canRaise(), "W says 'no' on its rank field, so its card must agree");
    }

    // ---- and resting on a socket puts it on screen ----

    /**
     * ★ The one that would have caught it: hovering a slot shows the card.
     *
     * <p>It shipped doing nothing at all, and every piece of it was correct.
     */
    @Test
    void restingOnASlotShowsTheCard() {
        var panel = panel();

        panel.hover('Q');

        assertTrue(panel.tipShowing(), "the cursor is on a slot and no card appeared");
    }

    /** And leaving takes it away again. */
    @Test
    void leavingTakesItAway() {
        var panel = panel();
        panel.hover('Q');

        panel.hover(null);

        assertFalse(panel.tipShowing());
    }

    /** A key with no card of its own — an order button — shows none. */
    @Test
    void aButtonWithNoCardShowsNone() {
        var panel = panel();

        panel.hover('A');

        assertFalse(panel.tipShowing(), "an order button should not grow a tooltip");
    }

    /**
     * ★ The card is the size of what is written in it.
     *
     * <p>The fault this is here for, and it did not look like a sizing bug from a
     * chair: a {@code BitmapText} given a box reports the BOX'S height rather than
     * the height of what it wrote, so measuring with a box of
     * {@code Float.MAX_VALUE} -- the obvious way to ask "how tall unbounded?" --
     * came back astronomical. The card was built to that: a dark slab up the whole
     * side of the screen with its writing somewhere off the top. What a player
     * sees then is not a tooltip that looks wrong; it is a tooltip that looks
     * EMPTY.
     *
     * <p>A ceiling rather than a figure, because the exact height depends on how
     * many lines a sentence wraps to and that should be free to change. What must
     * not change is that it stays a card.
     */
    @Test
    void theCardIsTheSizeOfWhatIsInIt() {
        var panel = panel();

        panel.hover('Q');

        float tall = panel.tipHeight();
        assertTrue(tall > 40f, "a card with a name, a sentence and three rows is not " + tall);
        assertTrue(tall < 220f, "the card is " + tall + " tall, which is a slab up the side"
                + " of the screen rather than a tooltip");
    }

    /** A card with less in it is shorter, which is the point of measuring at all. */
    @Test
    void lessToSayIsASmallerCard() {
        var panel = panel();

        panel.hover('Q');
        float withEverything = panel.tipHeight();
        panel.hover('W');
        float withLess = panel.tipHeight();

        assertTrue(withLess < withEverything, "W has no sentence and one row against Q's"
                + " three, and came out " + withLess + " against " + withEverything);
    }

    /** And the key is on the card, whether or not he owns the skill yet. */
    @Test
    void theCardNamesItsKeys() {
        var reading = HeroPanel.Reading.parse(LINE);

        assertEquals("Q", reading.tips().get('Q').at(),
                "an unbought skill still has to say which key casts it");
        assertTrue(reading.tips().get('Q').foot().contains("Ctrl+Q"),
                "and which keys buy it: " + reading.tips().get('Q').foot());
    }
}
