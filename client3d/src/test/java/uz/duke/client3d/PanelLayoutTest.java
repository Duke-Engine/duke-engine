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
                    + "|cmd=A,march,Yur,off|cmd=S,blade,Hujum,off"
                    + "|cmd=D,halt,To'xta,off|cmd=F,shield,Himoya,on"
                    + "|itWord=NARSALAR|it=blade,3|it=flask,1|it=shield,2"
                    + "|skWord=MAHORAT"
                    + "|skill=Q,,ready|skill=W,,cool,72,165|skill=E,,ready|skill=R,,lock,5-daraja"
                    + "|pwWord=Kuchlar|pw=shot,2|pw=boot,1";

    private static Node panel(float width) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, gui, width, PanelSkin.NONE);
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

    @Test
    void everyBlockOfTheDesignIsDrawn() {
        var gui = panel(1600f);

        for (var name : List.of("minimap-socket", "orders", "portrait", "vitals",
                "items", "skills", "powers", "depth", "badge")) {
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
                "items", "skills", "powers", "badge")) {
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

    /**
     * A creature's card is drawn short rather than drawn empty.
     *
     * <p>The bar was written for one card and every block on it always had
     * something in it. A skeleton has no experience being earned, no skills, no
     * bag and no level — and blocks drawn with nothing in them do not read as
     * "this creature has none of that", they read as a broken panel.
     */
    @Test
    void aCreaturesCardLeavesOutWhatIsNotItsOwn() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, gui, 1600f, PanelSkin.NONE);
        assertTrue(hero.show(CREATURE, 0f), "the panel should have taken a creature's card");
        gui.updateGeometricState();

        for (var gone : List.of("items", "item-heading", "skill-heading", "experience", "badge")) {
            assertEquals(Spatial.CullHint.Always, find(gui, gone).getLocalCullHint(),
                    gone + " is his, not the creature's, and should not be drawn");
        }
        // And what the card does say is still there.
        for (var kept : List.of("minimap-socket", "portrait", "vitals", "depth")) {
            assertNotEquals(Spatial.CullHint.Always, find(gui, kept).getLocalCullHint(),
                    kept + " belongs to the floor or the creature and should stay");
        }
        assertNotNull(find(gui, "face-glyph"),
                "an archer's silhouette on a skeleton's card would be the panel lying");
    }

    /** And the blocks that remain close up rather than leaving a hole. */
    @Test
    void theShortCardClosesItsOwnGaps() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, gui, 1600f, PanelSkin.NONE);
        hero.show(CREATURE, 0f);
        gui.updateGeometricState();

        float vitalsEnd = spanOf(gui, "vitals").to();
        float depthStart = spanOf(gui, "depth").from();
        // Two blocks' worth of empty stone between them would be the hole.
        assertTrue(depthStart - vitalsEnd < 120f,
                "the bar should have closed up, but left " + (depthStart - vitalsEnd) + " units");
    }

    /** The card the game sends for something that is not his. */
    private static final String CREATURE =
            "name=Skeleton|hp=34/40|depth=III / IV|depthWord=CHUQURLIK|face=skull"
                    + "|stat=Zarba,7|stat=Tezlik,16";

    /** And the card it sends when nothing at all is selected. */
    private static final String NOBODY = "name=|depth=III / IV|depthWord=CHUQURLIK";

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
                name + " is about a creature and there is no creature, but it is still drawn");
    }

    private static Node showing(String card) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, gui, 1600f, PanelSkin.NONE);
        assertTrue(hero.show(card, 0f), "the panel should have taken the card");
        gui.updateGeometricState();
        return gui;
    }

    /**
     * With nothing selected the bar keeps the screen and loses the creature.
     *
     * <p>The map is still the map and the floor is still the floor; everything
     * else on the bar was about somebody, and there is nobody. A portrait frame
     * with no face in it beside a health bar at zero does not read as "nothing is
     * selected" — it reads as a panel that has lost its hero.
     */
    @Test
    void withNothingSelectedOnlyTheScreensOwnThingsAreLeft() {
        var gui = showing(NOBODY);

        for (var gone : List.of("portrait", "vitals", "items", "skills", "orders")) {
            assertNothingDrawn(gui, gone);
        }
        for (var kept : List.of("minimap-socket", "depth")) {
            assertNotEquals(Spatial.CullHint.Always, find(gui, kept).getLocalCullHint(),
                    kept + " belongs to the screen and should stay");
        }
    }

    /**
     * And what is left closes up rather than sitting where it always sat.
     *
     * <p>The map at one end and the floor at the other with a screen of empty
     * stone between them would be the shape of the bar that is missing, which is
     * the thing the player should not be shown.
     */
    @Test
    void theEmptyBarClosesUp() {
        float full = spanOf(panel(1600f), "depth").from()
                - spanOf(panel(1600f), "minimap-socket").to();
        var gui = showing(NOBODY);

        float empty = spanOf(gui, "depth").from() - spanOf(gui, "minimap-socket").to();

        assertTrue(empty < full / 3f,
                "the bar should have closed up, but left " + empty + " of the full " + full);
    }

    /** Six sockets in the bag whatever he is carrying, and three of them full. */
    @Test
    void theBagAlwaysHasSixSockets() {
        var items = (Node) find(panel(1600f), "items");

        assertEquals(6, items.getChildren().size(),
                "an empty socket says there is room; a grid that grew would move the bar");
    }
}
