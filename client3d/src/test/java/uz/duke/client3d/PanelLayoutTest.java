package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** Six sockets in the bag whatever he is carrying, and three of them full. */
    @Test
    void theBagAlwaysHasSixSockets() {
        var items = (Node) find(panel(1600f), "items");

        assertEquals(6, items.getChildren().size(),
                "an empty socket says there is room; a grid that grew would move the bar");
    }
}
