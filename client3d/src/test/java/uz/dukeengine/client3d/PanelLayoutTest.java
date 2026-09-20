package uz.dukeengine.client3d;

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
    static final String LINE =
            "name=Erika|title=O'q ustasi|rank=7-daraja|hp=128/200|xp=38/100"
                    + "|depth=III / IV|depthWord=CHUQURLIK"
                    + "|stat=Zarba,34,+6|stat=Zirh,12,+2|stat=Tezlik,52"
                    + "|cmds=mine|cmd=A,march,Yur,off|cmd=S,blade,Hujum,off"
                    + "|cmd=D,halt,To'xta,off|cmd=F,shield,Himoya,on"
                    + "|itWord=NARSALAR|it=blade,3|it=flask,1|it=shield,2"
                    + "|skWord=MAHORAT"
                    + "|skill=Q,,ready|skill=W,,cool,72,165|skill=E,,ready|skill=R,,lock,5-daraja";

    private static Node panel(float width) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, gui, width, 900f, PanelSkin.NONE, RangeLook.DEFAULT);
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
                "items", "skills", "depth", "portrait-bars")) {
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
                "items", "skills", "portrait-bars")) {
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
        var hero = new HeroPanel(assets, font, gui, 1600f, 900f, PanelSkin.NONE, RangeLook.DEFAULT);
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
        var hero = new HeroPanel(assets, font, gui, 1600f, 900f, PanelSkin.NONE, RangeLook.DEFAULT);
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
        var look = StatLook.DEFAULT;

        // Each list in its own room and its own lettering: the figures down the
        // left, the attributes down the right.
        float figureRoom = look.figureColumn() - HeroPanel.COLUMN_GAP - look.figureIcon()
                - HeroPanel.ICON_GAP;
        float attributeRoom = HeroPanel.VITALS_WIDTH - HeroPanel.attributeColumnAt(look)
                - look.attributeIcon() - HeroPanel.ICON_GAP;
        fits(font, List.of("Zarba", "Zirh", "Tezlik", "Qon"), look.figureText(), figureRoom);
        fits(font, List.of("Kuch", "Epchillik", "Aql"), look.attributeText(), attributeRoom);
    }

    /**
     * The widest word in a list, the widest value lined up after it, and what is lent
     * after that, all in one row's room — with air between.
     */
    private static void fits(com.jme3.font.BitmapFont font, List<String> words, float size,
            float room) {
        float lent = HeroPanel.lentRoom(size);
        float widest = 0f;
        for (var word : words) {
            widest = Math.max(widest, width(font, word, size));
        }
        for (var figure : List.of("0", "34", "129", "10%")) {
            // A clear gap, not merely a fit: two strings that end and begin in the
            // same pixel are two strings nobody can tell apart.
            float wide = widest + HeroPanel.WORD_GAP + width(font, figure, size);
            assertTrue(wide + GAP <= room - lent,
                    "\"" + figure + "\" after the widest of " + words + " wants "
                            + Math.round(wide + GAP) + " of the " + Math.round(room - lent)
                            + " a row has before what is lent begins");
        }
        // And the green figure has room of its own at the end of the same line.
        assertTrue(width(font, "+12", size) <= lent,
                "what is lent does not fit the room kept for it");
    }

    /** How much clear air a word and the figure beside it want between them. */
    private static final float GAP = 6f;

    /** A hero's line the way the dungeon sends it: three attributes with cards, two figures. */
    private static final String ATTRIBUTES = LINE.replace(
            "|stat=Zarba,34,+6|stat=Zirh,12,+2|stat=Tezlik,52",
            "|attr=Kuch,22,,,primary|attr=Epchillik,10,+4,|attr=Aql,8,,"
                    + "|atTipName=0,Kuch|atTipRow=0,Jon,+12,"
                    + "|atTipName=1,Epchillik|atTipRow=1,Tezlik,+0.15,"
                    + "|atTipFoot=1,Hozirgi tezlik: 29"
                    + "|atTipName=2,Aql|atTipRow=2,Mana,+5,"
                    + "|stat=Zarba,34,+6|stat=Zirh,12,+2");

    /**
     * His attributes stand in a list down the right in the game's order, his primary is
     * drawn large in the middle, and resting the cursor on either opens its card — where
     * a figure down the left has none.
     */
    @Test
    void anAttributesCardOpensOverItAndAFigureHasNone() {
        var bar = bar(ATTRIBUTES);
        var lowest = new float[3][];
        var highest = new float[3][];
        var leftmost = new float[3];
        java.util.Arrays.fill(leftmost, Float.MAX_VALUE);
        var found = new java.util.TreeSet<Integer>();
        for (float y = 0f; y < 400f; y += 1f) {
            for (float x = 0f; x < 1600f; x += 1f) {
                var at = bar.attributeAt(x, y);
                if (at == null) {
                    continue;
                }
                found.add(at);
                if (at < 3) {
                    if (lowest[at] == null) {
                        lowest[at] = new float[] {x, y};
                    }
                    highest[at] = new float[] {x, y};
                    leftmost[at] = Math.min(leftmost[at], x);
                }
            }
        }
        assertEquals(java.util.Set.of(0, 1, 2), found,
                "the cursor should find the three attributes and nothing else -- no figure");
        assertTrue(highest[0][1] > highest[1][1] && highest[1][1] > highest[2][1],
                "strength over agility over intelligence, in the game's order");
        assertEquals(leftmost[1], leftmost[2], 1f, "the list stands in one column");
        assertTrue(leftmost[0] < leftmost[1] - 20f,
                "and his primary is found in the middle as well, left of the list");

        bar.hoverAttribute(1);
        assertTrue(bar.tipShowing(), "resting on agility opens its card");
        bar.hoverAttribute(7);
        assertFalse(bar.tipShowing(), "a place with no card opens none");
        bar.hoverAttribute(null);
        assertFalse(bar.tipShowing());
    }

    /**
     * However wide the window and however many attributes the game sends, the block stays
     * inside its band, its three columns stand in order, and no socket is drawn over
     * another.
     *
     * <p>Measured off the sockets' stone plates, which are geometry and have bounds
     * headless — the lettering has none until something draws it. A fourth attribute is
     * the case that matters: the block was sized for three, and a fourth row that ran on
     * would stand under the stone of the bar.
     */
    @Test
    void theBlockStaysInItsBandWithItsColumnsApartAtEverySize() {
        var four = ATTRIBUTES.replace("|attr=Aql,8,,", "|attr=Aql,8,,|attr=Quvvat,7,,");
        for (float width : new float[] {1024f, 1280f, 1600f, 2560f}) {
            var assets = new DesktopAssetManager(true);
            var font = assets.loadFont("Interface/Fonts/Default.fnt");
            var gui = new Node("gui");
            var hero = new HeroPanel(assets, font, gui, width, 900f, PanelSkin.NONE, RangeLook.DEFAULT);
            assertTrue(hero.show(four, 0f), "the panel should have taken the line at " + width);
            gui.updateGeometricState();

            var sockets = new java.util.ArrayList<BoundingBox>();
            for (var child : ((Node) find(gui, "stat-block")).getChildren()) {
                if ("stat-box".equals(child.getName())) {
                    sockets.add((BoundingBox) ((Node) child).getChild("stat-edge").getWorldBound());
                }
            }
            assertEquals(2 + 1 + 4, sockets.size(),
                    "two figures, his primary and four attributes at " + width);

            // Off the bar's trough, not the bar's node: its lettering has no bounds yet
            // and sits at the node's origin, which is the bottom of the band. And the
            // scale is the bar's own, which is fitted to the blocks rather than to the
            // window -- so it is read back from how tall the trough came out.
            var trough = (BoundingBox) ((Node) find(gui, "experience")).getChild("trough")
                    .getWorldBound();
            float scale = (top(trough) - bottom(trough)) / HeroPanel.XP_HEIGHT;
            float underTheBar = bottom(trough);
            var column = spanOf(gui, "vitals");
            for (var socket : sockets) {
                assertTrue(bottom(socket) >= 10f * scale - 1f,
                        "a socket hangs below the band at " + width + ": " + bottom(socket)
                                + " under " + 10f * scale);
                assertTrue(top(socket) <= underTheBar + 1f,
                        "a socket climbs into the experience bar at " + width + ": its top "
                                + top(socket) + ", the bar's bottom " + underTheBar);
                assertTrue(left(socket) >= column.from() - 1f && right(socket) <= column.to() + 1f,
                        "a socket leaves the column at " + width);
            }
            for (int i = 0; i < sockets.size(); i++) {
                for (int j = i + 1; j < sockets.size(); j++) {
                    assertFalse(overlap(sockets.get(i), sockets.get(j)),
                            "two sockets are drawn over each other at " + width);
                }
            }
            // Built left to right: the two figures, his primary, then the list.
            float figuresEnd = Math.max(right(sockets.get(0)), right(sockets.get(1)));
            float listStart = Float.MAX_VALUE;
            for (int i = 3; i < sockets.size(); i++) {
                listStart = Math.min(listStart, left(sockets.get(i)));
            }
            assertTrue(figuresEnd < left(sockets.get(2)) && right(sockets.get(2)) < listStart,
                    "figures, then his primary, then the list, at " + width);
        }
    }

    private static float bottom(BoundingBox box) {
        return box.getCenter().y - box.getYExtent();
    }

    private static float top(BoundingBox box) {
        return box.getCenter().y + box.getYExtent();
    }

    private static float left(BoundingBox box) {
        return box.getCenter().x - box.getXExtent();
    }

    private static float right(BoundingBox box) {
        return box.getCenter().x + box.getXExtent();
    }

    /** Whether two boxes share more than an edge. */
    private static boolean overlap(BoundingBox a, BoundingBox b) {
        return Math.abs(a.getCenter().x - b.getCenter().x) < a.getXExtent() + b.getXExtent() - 0.5f
                && Math.abs(a.getCenter().y - b.getCenter().y)
                        < a.getYExtent() + b.getYExtent() - 0.5f;
    }

    /**
     * A picture the game names and the client cannot find is the first letter of its word,
     * in the socket where the picture would have been — never an empty socket, and never a
     * panel that fails to draw.
     */
    @Test
    void aPictureThatIsNotThereIsTheFirstLetterOfItsWord() {
        var gui = showing(ATTRIBUTES.replace("|attr=Kuch,22,,,primary",
                "|attr=Kuch,22,,icons/stats/no_such_picture.png,primary"));

        var letters = new java.util.ArrayList<String>();
        lettersIn(find(gui, "stat-block"), letters);
        assertEquals(2, java.util.Collections.frequency(letters, "K"),
                "strength's two sockets -- in the middle and in the list -- should say K: "
                        + letters);
        assertTrue(letters.contains("Z"), "and a figure with no picture says its letter: " + letters);
        assertEquals("E", HeroPanel.initial("epchillik"));
        assertEquals("?", HeroPanel.initial(" "));
    }

    private static void lettersIn(Spatial spatial, List<String> letters) {
        if (spatial instanceof com.jme3.font.BitmapText text && "stat-letter".equals(text.getName())) {
            letters.add(text.getText());
        } else if (spatial instanceof Node node) {
            for (var child : node.getChildren()) {
                lettersIn(child, letters);
            }
        }
    }

    /** How wide a string is in the lettering a figure is set in. */
    private static float width(com.jme3.font.BitmapFont font, String words) {
        return width(font, words, 11f);
    }

    private static float width(com.jme3.font.BitmapFont font, String words, float size) {
        var line = new com.jme3.font.BitmapText(font);
        line.setSize(size);
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
