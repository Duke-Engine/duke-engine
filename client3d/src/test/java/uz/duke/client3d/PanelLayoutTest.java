package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The blocks of the bar are side by side and inside it.
 *
 * <p>A panel is laid out by adding widths together, and the way that goes wrong
 * is silent: a block whose width was worked out from the wrong constant lands on
 * top of its neighbour, and what the player sees is a name written through a
 * portrait. No exception, no failing assertion anywhere, and nothing catches it
 * but a pair of eyes.
 *
 * <p>So the eyes are replaced by arithmetic on the scene graph itself — where
 * each block actually ended up, not where the code meant to put it, and at four
 * window widths rather than the one somebody happened to look at.
 *
 * <p>What it does not see is lettering: jME gives a {@code BitmapText} no bounds
 * until it is drawn, so a block measured here is its stone and its sockets and
 * not its words. That is most of the bar and all of the part that can overlap
 * invisibly -- two blocks of stone in the same place is the fault being hunted --
 * but a word running past the end of its own block is still something only eyes
 * will catch.
 */
class PanelLayoutTest {

    /** A full line of the kind the dungeon really sends, taken from its own output. */
    private static final String LINE =
            "name=Erika|title=O'q ustasi|rank=7-daraja|hp=128/200|xp=38/100"
                    + "|depth=III / IV|depthWord=CHUQURLIK"
                    + "|stat=Zarba,34,+6|stat=Zirh,12,+2|stat=Tezlik,52"
                    + "|cmds=mine|cmd=A,march,Yur,off|cmd=S,blade,Hujum,off"
                    + "|cmd=D,halt,To'xta,off|cmd=F,shield,Himoya,on"
                    + "|itWord=NARSALAR|it=blade,3|it=flask,1|it=shield,2"
                    + "|skWord=MAHORAT"
                    + "|skill=Q,,ready|skill=W,,cool,72,165|skill=E,,ready|skill=R,,lock,5-daraja"
                    + "|pwWord=Kuchlar|pw=shot,2|pw=boot,1";

    private static Node panel(float width) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, gui, width, PanelSkin.NONE, RangeLook.DEFAULT);
        assertTrue(hero.show(LINE, 0f), "the panel should have taken the line");
        gui.updateGeometricState();
        return gui;
    }

    private static Spatial find(Spatial root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Node node) {
            for (var child : node.getChildren()) {
                var found = find(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Where a block sits across the screen, in window pixels. */
    private record Span(float from, float to) {

        boolean overlaps(Span other) {
            return from < other.to - 1f && other.from < to - 1f;
        }
    }

    private static Span spanOf(Spatial root, String name) {
        var block = find(root, name);
        assertNotNull(block, "the bar should have a block called " + name);
        var bound = (BoundingBox) block.getWorldBound();
        return new Span(bound.getCenter().x - bound.getXExtent(),
                bound.getCenter().x + bound.getXExtent());
    }

    /** The blocks of the bar, left to right, as the design orders them. */
    private static Map<String, Span> blocks(Node gui) {
        var spans = new LinkedHashMap<String, Span>();
        for (var name : List.of("minimap-socket", "orders", "portrait", "vitals",
                "items", "skills", "depth")) {
            spans.put(name, spanOf(gui, name));
        }
        return spans;
    }

    /**
     * What the experience bar says his level is, or "" for a card with none.
     *
     * <p>Found by its size, which is the one thing about it that is not shared
     * with the reading at the other end of the same bar. A miss fails rather than
     * returning nothing, since nothing is also the right answer for a skeleton
     * and the two must not be confused.
     */
    private static String rankShown(Node gui) {
        var bar = (Node) find(gui, "experience");
        for (var child : bar.getChildren()) {
            // By SIZE, which is the one thing about the level that neither the
            // count at the other end of the bar nor either of their shadows
            // shares. It was looking for 13 -- the count's size, not the
            // level's -- and passed anyway, because the card it was asked about
            // has neither and "" equals "".
            if (child instanceof com.jme3.font.BitmapText line
                    && line.getSize() == 15f) {
                return line.getText();
            }
        }
        return org.junit.jupiter.api.Assertions.fail(
                "no 15-point lettering in the experience bar: has the level moved again?");
    }

    /**
     * What is left of him hangs under his own face, and inside the bar.
     *
     * <p>The health and mana gauges moved out of the middle column and under the
     * portrait, which is a block that now has to STACK rather than sit: the frame
     * is a fixed height, the two bars go beneath it, and between them they have a
     * band of a hundred and seventy-two pixels and no more. Getting that wrong
     * puts a mana bar through the floor of the panel, and every left-to-right
     * check in this class would still pass.
     */
    @Test
    void whatIsLeftOfHimHangsUnderHisOwnFace() {
        var gui = panel(1600f);
        var frame = (BoundingBox) find(gui, "portrait").getWorldBound();
        var bars = (BoundingBox) find(gui, "portrait-bars").getWorldBound();

        assertTrue(bars.getCenter().y + bars.getYExtent()
                        <= frame.getCenter().y - frame.getYExtent() + 1f,
                "the gauges should be under the frame, not across it");
        // Wider than the frame, and the frame centred on them: the gauges fill
        // the column the portrait stands in rather than the portrait itself,
        // which is what makes them read as the block's own rather than as part
        // of the picture.
        assertTrue(bars.getXExtent() > frame.getXExtent(),
                "the gauges should fill the column, not the frame");
        assertEquals(frame.getCenter().x, bars.getCenter().x, 1.5f,
                "and the frame should stand in the middle of them");
        assertFalse(new Span(bars.getCenter().x - bars.getXExtent(),
                bars.getCenter().x + bars.getXExtent())
                .overlaps(spanOf(gui, "vitals")), "nor into the column beside it");
    }

    @Test
    void everyBlockOfTheDesignIsDrawn() {
        var gui = panel(1600f);

        for (var name : List.of("minimap-socket", "orders", "portrait", "vitals",
                "items", "skills", "powers", "depth", "portrait-bars")) {
            assertNotNull(find(gui, name), name + " is in the design and not on the bar");
        }
    }

    /**
     * No two blocks stand on each other.
     *
     * <p>The failure this is really about. Everything else on the bar can be
     * slightly wrong and still legible; two blocks in the same place cannot.
     */
    @Test
    void theBlocksStandSideBySide() {
        var spans = blocks(panel(1600f));
        var names = List.copyOf(spans.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                var left = spans.get(names.get(i));
                var right = spans.get(names.get(j));
                assertFalse(left.overlaps(right), names.get(i) + " (" + left.from() + ".."
                        + left.to() + ") runs into " + names.get(j) + " (" + right.from()
                        + ".." + right.to() + ")");
            }
        }
    }

    /** And they are in the order the design reads in, left to right. */
    @Test
    void theyAreInTheOrderTheDesignDrawsThem() {
        var spans = blocks(panel(1600f));
        var names = List.copyOf(spans.keySet());
        for (int i = 1; i < names.size(); i++) {
            assertTrue(spans.get(names.get(i - 1)).from() < spans.get(names.get(i)).from(),
                    names.get(i - 1) + " should come before " + names.get(i));
        }
    }

    /**
     * The whole bar fits on the screen at every size it is drawn at.
     *
     * <p>Both ends of the range and the middle. The scale is clamped, so a narrow
     * enough window stops shrinking the bar and starts cutting it off — which is
     * the one thing the clamp can get wrong, and it gets it wrong silently.
     */
    @Test
    void theBarFitsTheWindowAtEverySize() {
        for (float width : new float[] {640f, 1180f, 1600f, 2560f}) {
            var spans = blocks(panel(width));
            float from = Float.MAX_VALUE;
            float to = -Float.MAX_VALUE;
            for (var span : spans.values()) {
                from = Math.min(from, span.from());
                to = Math.max(to, span.to());
            }
            assertTrue(from >= -1f, "the bar starts off the left edge at " + width + ": " + from);
            assertTrue(to <= width + 1f,
                    "the bar runs off the right edge at " + width + ": " + to + " > " + width);
        }
    }

    /**
     * And nothing hangs out of the bar's top or bottom.
     *
     * <p>The other half of the same fault, and the easier one to introduce: a
     * block laid out from the wrong height slides up over the world or down off
     * the screen, and every left-to-right check here would still pass.
     */
    @Test
    void nothingHangsOutOfTheBar() {
        var gui = panel(1600f);
        float slabTop = 0f;
        for (var name : List.of("minimap-socket", "orders", "portrait", "vitals",
                "items", "skills", "powers", "portrait-bars")) {
            var bound = (BoundingBox) find(gui, name).getWorldBound();
            float bottom = bound.getCenter().y - bound.getYExtent();
            float top = bound.getCenter().y + bound.getYExtent();
            assertTrue(bottom >= -1f, name + " hangs below the bar at " + bottom);
            slabTop = Math.max(slabTop, top);
        }
        // The bar is the band plus a hand's padding either side, scaled.
        float scale = Math.clamp(1600f / 1300f, 0.55f, 1.30f);
        assertTrue(slabTop <= (172f + 20f) * scale + 1f,
                "something stands proud of the bar at " + slabTop);
    }
    /** The card the game sends for something that is not his. */
    private static final String CREATURE =
            "name=Skeleton|hp=34/40|depth=III / IV|depthWord=CHUQURLIK|face=skull"
                    + "|itWord=NARSALAR|skWord=MAHORAT"
                    + "|cmds=theirs|cmd=A,march,Yur,off|cmd=S,blade,Hujum,off"
                    + "|cmd=D,halt,To'xta,off|cmd=F,shield,Himoya,on"
                    + "|stat=Zarba,7|stat=Tezlik,16";

    /** And the card it sends when nothing at all is selected. */
    private static final String NOBODY =
            "name=|depth=III / IV|depthWord=CHUQURLIK|itWord=NARSALAR|skWord=MAHORAT"
                    + "|cmds=theirs|cmd=A,march,Yur,off|cmd=S,blade,Hujum,off"
                    + "|cmd=D,halt,To'xta,off|cmd=F,shield,Himoya,off";

    private static Node showing(String card) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, gui, 1600f, PanelSkin.NONE, RangeLook.DEFAULT);
        assertTrue(hero.show(card, 0f), "the panel should have taken the card");
        gui.updateGeometricState();
        return gui;
    }

    /**
     * The bar is the same shape whatever is selected — or nothing is.
     *
     * <p>The rule the whole panel is built on, and the one a player notices being
     * broken without being able to say what broke: <b>the sockets are furniture
     * and what goes in them is data.</b> Six sockets in his bag whether he is
     * carrying six things or none; four skill sockets whether they hold skills or
     * nothing; a portrait frame whether there is a face for it. A bar that changed
     * shape every time the player clicked would make him hunt for the thing he was
     * about to press.
     *
     * <p>So every block is measured against every card, and they have to agree.
     */
    @Test
    void theBarIsTheSameShapeWhateverIsSelected() {
        var his = blocks(panel(1600f));
        for (var card : List.of(CREATURE, NOBODY)) {
            var other = blocks(showing(card));
            for (var name : his.keySet()) {
                assertEquals(his.get(name).from(), other.get(name).from(), 1f,
                        name + " moved when the selection changed");
                assertEquals(his.get(name).to(), other.get(name).to(), 1f,
                        name + " changed width when the selection changed");
            }
        }
    }

    /**
     * And what the card does not say is simply not drawn in it.
     *
     * <p>The other half of the same rule. The furniture stays; the figure in the
     * portrait, the level inside the experience bar and the experience being
     * earned are the card's, and a card that carries none of them shows none of
     * them.
     */
    @Test
    void whatTheCardDoesNotSayIsNotDrawnInIt() {
        var nobody = showing(NOBODY);

        assertNothingDrawn(nobody, "figure");
        assertEquals(null, find(nobody, "face-glyph"),
                "an empty frame has no face in it, not even a borrowed one");
        assertEquals("7-daraja", rankShown(showing(LINE)),
                "the finder has to work in the other direction too, or \"\" proves nothing");
        assertEquals("", rankShown(nobody), "nobody is any level");
        assertEquals(Spatial.CullHint.Always, find(nobody, "title-line").getLocalCullHint(),
                "and nobody is anything");
        assertEquals(Spatial.CullHint.Always, find(nobody, "xp-fill").getLocalCullHint(),
                "nor earning anything");

        // A creature has a face of its own and still none of the rest.
        var skeleton = showing(CREATURE);
        assertNotNull(find(skeleton, "face-glyph"), "a skeleton is not an archer");
        assertEquals("", rankShown(skeleton), "a skeleton holds no level");
    }

    /**
     * Nothing of that block reaches the screen — by any of the three ways a block
     * can be absent.
     *
     * <p>They really are three different things and all three are correct: a block
     * the panel hides is culled, a block built from a list the card did not send
     * is empty, and a block only built when something needs it was never attached
     * at all. Insisting on one of them would be a test about how the panel is put
     * together rather than about what the player sees.
     */
    private static void assertNothingDrawn(Node gui, String name) {
        var block = find(gui, name);
        if (block == null) {
            return; // never built
        }
        if (block.getLocalCullHint() == Spatial.CullHint.Always) {
            return; // built and hidden
        }
        assertTrue(block instanceof Node empty && empty.getChildren().isEmpty(),
                name + " is the card's and the card does not carry it, but it is drawn");
    }

    /**
     * The order buttons go dim for a creature that is not his, and still say what
     * it is doing.
     *
     * <p>Two halves of one idea. A button he cannot press must not look pressable
     * -- a lit control that does nothing is worse than no control -- and the row is
     * still worth reading, because what the skeleton across the room is doing is
     * exactly what the player wants to know before he clicks anything.
     */
    @Test
    void aCreatureThatIsNotHisHasDimButtonsThatStillShowWhatItIsDoing() {
        var his = (Node) find(panel(1600f), "orders");
        var theirs = (Node) find(showing(CREATURE), "orders");

        assertEquals(his.getChildren().size(), theirs.getChildren().size(),
                "the same four buttons either way: they are furniture");
        var mine = glyphColours(his);
        var other = glyphColours(theirs);
        assertNotEquals(mine, other, "a creature he cannot command should not look the same");
    }

    private static java.util.List<com.jme3.math.ColorRGBA> glyphColours(Node orders) {
        var colours = new java.util.ArrayList<com.jme3.math.ColorRGBA>();
        for (var button : orders.getChildren()) {
            var glyph = (com.jme3.scene.Geometry) find(button, "order-glyph");
            colours.add((com.jme3.math.ColorRGBA) glyph.getMaterial().getParam("Color").getValue());
        }
        return colours;
    }

    /** Six sockets in the bag whatever he is carrying, and three of them full. */
    @Test
    void theBagAlwaysHasSixSockets() {
        var items = (Node) find(panel(1600f), "items");

        assertEquals(6, items.getChildren().size(),
                "an empty socket says there is room; a grid that grew would move the bar");
    }

    // ---- which of the four he is in ----

    /** The panel itself rather than the node it drew into. */
    private static HeroPanel bar(String card) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, gui, 1600f, PanelSkin.NONE, RangeLook.DEFAULT);
        assertTrue(hero.show(card, 0f), "the panel should have taken the card");
        gui.updateGeometricState();
        return hero;
    }

    /**
     * ★ The order he is in wears the mark an armed skill wears.
     *
     * <p>It had only a warm light — and a warm light is also what a button under
     * the cursor gets, so the one fact these four buttons exist to tell him was
     * being said in the same voice as "your hand is here". Four buttons that can
     * only be pressed and never read are half a control.
     */
    @Test
    void theOrderHeIsInIsMarked() {
        var bar = bar(LINE);

        assertTrue(bar.orderIsMarked('F'),
                "his card says Himoya is the one he is in and nothing on the button says so");
        for (char other : new char[] {'A', 'S', 'D'}) {
            assertFalse(bar.orderIsMarked(other),
                    other + " is marked as well, so the mark says nothing");
        }
    }

    /**
     * And another creature's state is read rather than marked.
     *
     * <p>A skeleton walking still shows its walk — that is worth as much as
     * reading his own — but the mark is the PLAYER'S, and putting it on something
     * he cannot command would offer him an order he is not being given.
     */
    @Test
    void anotherCreaturesStateIsNotMarked() {
        var bar = bar(CREATURE);

        assertFalse(bar.orderIsMarked('F'),
                "the skeleton's own state is wearing the player's mark");
    }

    /**
     * A figure's word and its number do not grow into each other.
     *
     * <p>They did: on screen "Tezlik 29" came out as "Tezli29". The word is
     * drawn from the left of its box and the number to the right of a shorter
     * one, so the two approach from opposite ends of the same cell and meet in
     * the middle with nothing to say about it. Both were exactly where they had
     * been put, so nothing failed.
     *
     * <p>★ IT IS CHECKED AS ARITHMETIC RATHER THAN AS POSITIONS, and not for
     * want of trying: jME gives a {@code BitmapText} its place through a box it
     * does not hand back, and no bounds at all until something has drawn it. A
     * test that read {@code getWorldTranslation} got the origin of every line on
     * the panel and cheerfully reported the hero's name running into his first
     * figure. What CAN be measured headlessly is how wide a string is, so the
     * check is that the widest word the game ships, plus the widest number
     * beside it, fits the room the panel divides a cell into — which is the
     * constraint the overlap was a symptom of.
     */
    @Test
    void aFiguresWordAndItsNumberDoNotMeet() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        float room = HeroPanel.roomInAFigure();

        for (var word : List.of("Zarba", "Zirh", "Tezlik", "Qon")) {
            for (var figure : List.of("0", "34", "129", "10%")) {
                float wide = width(font, word) + width(font, figure);
                // A clear gap, not merely a fit: two strings that end and begin
                // in the same pixel are two strings nobody can tell apart.
                assertTrue(wide + GAP <= room - HeroPanel.STAT_LENT,
                        "\"" + word + " " + figure + "\" wants " + Math.round(wide + GAP)
                                + " of the " + Math.round(room - HeroPanel.STAT_LENT)
                                + " a figure has before what is lent begins");
            }
        }
        // And the green figure has room of its own at the end of the same line.
        assertTrue(width(font, "+12") <= HeroPanel.STAT_LENT,
                "what is lent does not fit the room kept for it");
    }

    /** How much clear air a word and the figure beside it want between them. */
    private static final float GAP = 6f;

    /** How wide a string is in the lettering a figure is set in. */
    private static float width(com.jme3.font.BitmapFont font, String words) {
        var line = new com.jme3.font.BitmapText(font);
        line.setSize(11f);
        line.setText(words);
        return line.getLineWidth();
    }

    /**
     * A hero with nothing earned yet has an empty experience bar.
     *
     * <p>★ HE HAD A FULL ONE. Two lines owned the fill's cull hint: one hid a bar
     * with nothing in it, the other showed it again whenever the card had a level
     * at all, and what came back was the last width it had — which for a fresh
     * hero is the whole bar. The panel was telling a player at nought experience
     * that he was one kill from levelling.
     */
    @Test
    void aHeroWhoHasEarnedNothingHasAnEmptyBar() {
        var fresh = showing("name=Erika|rank=1-daraja|hp=550/550|xp=0/30"
                + "|depth=I / IV|depthWord=CHUQURLIK");

        // ★ BY ITS OWN NAME. All three bars called their fill "fill", so the
        // first version of this found the HEALTH bar -- which is full, and was
        // meant to be -- and passed while the fault it was written for was on
        // screen.
        assertEquals(Spatial.CullHint.Always, find(fresh, "xp-fill").getLocalCullHint(),
                "nothing earned, nothing drawn");
        assertEquals(Spatial.CullHint.Inherit, find(fresh, "hp-fill").getLocalCullHint(),
                "and he is perfectly healthy");
    }
}
