package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The bar a game describes: the blocks it lists, in its order; its sockets the size it asks, as far as the bar
 * holds them; the colours it paints — and nothing of a block it left off, drawn or answering a click.
 */
class PanelLookTest {

    private record Built(Node gui, HeroPanel hero) {
    }

    private static Built panel(PanelLook look) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var hero = new HeroPanel(assets, font, null, gui, 1600f, 900f, PanelSkin.NONE, RangeLook.DEFAULT,
                IconLook.DEFAULT, StatLook.DEFAULT, look);
        assertTrue(hero.show(PanelLayoutTest.LINE, 0f), "the panel should have taken the line");
        gui.updateGeometricState();
        return new Built(gui, hero);
    }

    /** The design's look with one thing changed, as a game's file changes it. */
    private static PanelLook with(String component, Object value) throws ReflectiveOperationException {
        return with(java.util.Map.of(component, value));
    }

    /** The same, for a file that changes more than one. */
    private static PanelLook with(java.util.Map<String, Object> changes) throws ReflectiveOperationException {
        var parts = PanelLook.class.getRecordComponents();
        var types = new Class<?>[parts.length];
        var values = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            values[i] = changes.containsKey(parts[i].getName()) ? changes.get(parts[i].getName())
                    : parts[i].getAccessor().invoke(PanelLook.DEFAULTS);
        }
        try {
            return PanelLook.class.getDeclaredConstructor(types).newInstance(values);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // What the record itself refused, as the game's file would be told it.
            if (e.getCause() instanceof RuntimeException refused) {
                throw refused;
            }
            throw e;
        }
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

    /** Whether anything between the spatial and the top of the scene keeps it from being drawn. */
    private static boolean drawn(Spatial spatial) {
        for (var at = spatial; at != null; at = at.getParent()) {
            if (at.getCullHint() == Spatial.CullHint.Always) {
                return false;
            }
        }
        return true;
    }

    private static float left(Spatial spatial) {
        var bound = (BoundingBox) spatial.getWorldBound();
        return bound.getCenter().x - bound.getXExtent();
    }

    @Test
    void theBarIsTheBlocksTheGameListsInItsOrder() throws ReflectiveOperationException {
        var gui = panel(with("blocks", List.of(PanelBlock.SKILLS, PanelBlock.BAG, PanelBlock.HERO))).gui();

        var skills = find(gui, "skills");
        var items = find(gui, "items");
        var portrait = find(gui, "portrait");
        assertTrue(drawn(skills) && drawn(items) && drawn(portrait), "every block it lists is drawn");
        assertTrue(left(skills) < left(items) && left(items) < left(portrait), "left to right, as it lists them");
        for (var name : List.of("minimap-socket", "orders", "depth")) {
            assertFalse(drawn(find(gui, name)), name + " was left off the bar and is drawn on it");
        }
    }

    @Test
    void aBlockLeftOffAnswersNoClick() throws ReflectiveOperationException {
        var built = panel(with("blocks", List.of(PanelBlock.HERO, PanelBlock.DEPTH)));
        var hero = built.hero();

        assertNull(hero.minimapRect(), "a bar with no socket has no place for the minimap");
        float height = hero.heightPixels();
        for (float x = 0f; x <= 1600f; x += 4f) {
            for (float y = 0f; y <= height; y += 4f) {
                assertNull(hero.slotAt(x, y), "a skill or an order off the bar answered at " + x + ", " + y);
                assertNull(hero.badgeAt(x, y), "a badge off the bar answered at " + x + ", " + y);
            }
        }
    }

    /** More rows than the band holds: the sockets come out smaller and the bag stays inside the bar. */
    @Test
    void aBagTallerThanTheBarIsDrawnSmallerInsideIt() throws ReflectiveOperationException {
        var built = panel(with("itemRows", 5));
        var gui = built.gui();

        int sockets = 0;
        float top = built.hero().heightPixels();
        for (var child : ((Node) find(gui, "items")).getChildren()) {
            var bound = (BoundingBox) child.getWorldBound();
            assertTrue(bound.getCenter().y - bound.getYExtent() >= 0f
                    && bound.getCenter().y + bound.getYExtent() <= top, "a socket of the bag is outside the bar");
            sockets++;
        }
        assertEquals(15, sockets, "three across and five down");
    }

    @Test
    void theColoursAreTheGames() throws ReflectiveOperationException {
        var rim = (Geometry) find(panel(with("slabRimColour", 0x123456)).gui(), "rim");

        assertNotNull(rim);
        assertEquals(Shade.linear(HeroPanel.rgb(0x123456)), rim.getMaterial().getParam("Color").getValue());
    }

    @Test
    void aBlockIsOnTheBarOnce() {
        assertThrows(IllegalArgumentException.class, () -> with("blocks", List.of(PanelBlock.BAG, PanelBlock.BAG)));
    }

    @Test
    void aBlockStandsOnTheBarOrOnItsOwnAndNotBoth() {
        assertThrows(IllegalArgumentException.class, () -> with(java.util.Map.of(
                "blocks", List.of(PanelBlock.MINIMAP, PanelBlock.HERO),
                "places", List.of(PanelPlace.of("Minimap TopRight 12 12")))));
    }

    /** A line of a file is a block, where it hangs and how far in — and the last three may all be left out. */
    @Test
    void aPlaceIsReadAsTheFileWritesIt() {
        assertEquals(new PanelPlace(PanelBlock.MINIMAP, PanelAnchor.TOP_RIGHT, 12f, 8f), PanelPlace.of("Minimap TopRight 12 8"));
        assertEquals(PanelAnchor.BOTTOM_LEFT, PanelPlace.of("Bag bottom-left").anchor(),
                "a file is not Java: the same place however it is spelt");
        assertEquals(PanelAnchor.BOTTOM_LEFT, PanelPlace.of("Bag BOTTOM_LEFT").anchor());
        assertEquals(0f, PanelPlace.of("Depth Middle").x(), 0.001f);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PanelPlace.of("Nothing TopRight"))
                .getMessage().contains("MINIMAP"), "it should say what a block may be");
        assertThrows(IllegalArgumentException.class, () -> PanelPlace.of("Bag Nowhere"));
        assertThrows(IllegalArgumentException.class, () -> PanelPlace.of("Bag Top over-there"));
    }

    /**
     * A block the game hangs from a corner stands there rather than on the bar: on its own plate of the same
     * stone, in that corner at any window size, still handing out where the minimap goes and still taking the
     * clicks that land on it.
     */
    @Test
    void aBlockHungFromACornerStandsInIt() throws ReflectiveOperationException {
        var look = with(java.util.Map.of(
                "blocks", List.of(PanelBlock.HERO, PanelBlock.BAG, PanelBlock.SKILLS, PanelBlock.DEPTH),
                "places", List.of(PanelPlace.of("Minimap TopRight 12 12"))));
        var built = panel(look);
        var gui = built.gui();

        var socket = (BoundingBox) find(gui, "minimap-socket").getWorldBound();
        assertTrue(drawn(find(gui, "minimap-socket")), "a block it hung somewhere is still drawn");
        assertNotNull(find(gui, "plate"), "and stands on stone of its own");
        // The whole block -- its plate, the socket and the order buttons beside it -- hangs in the corner.
        var block = (BoundingBox) find(gui, "block-minimap").getWorldBound();
        assertTrue(1600f - (block.getCenter().x + block.getXExtent()) < 25f,
                "the block should hang by the right-hand edge, not at " + block.getCenter().x);
        assertTrue(900f - (block.getCenter().y + block.getYExtent()) < 25f,
                "and under the top edge, not at " + block.getCenter().y);
        assertTrue(socket.getCenter().y - socket.getYExtent() > built.hero().heightPixels(),
                "well clear of the bar the rest of the blocks are on");

        var rect = built.hero().minimapRect();
        assertNotNull(rect, "the map still goes in the socket, wherever the socket went");
        // Where the hole is, which is where the map is drawn -- the recess round it sits two pixels outside that.
        var hole = find(gui, "minimap-socket").getWorldTranslation();
        assertEquals(hole.x, rect[0], 0.5f);
        assertEquals(hole.y, rect[1], 0.5f);
        assertTrue(built.hero().contains(rect[0] + rect[2] / 2f, rect[1] + rect[2] / 2f),
                "a click on it is a click on the HUD, not on the world under it");
        assertFalse(built.hero().contains(800f, 500f), "and the middle of the screen is still the world");
    }

    /** Every block hung somewhere of its own is a game with no bar at all: no slab, and the world reaches the foot. */
    @Test
    void aGameThatHangsEveryBlockHasNoBar() throws ReflectiveOperationException {
        var built = panel(with(java.util.Map.of(
                "blocks", List.of(),
                "places", List.of(PanelPlace.of("Minimap TopRight 12 12"), PanelPlace.of("Skills Bottom 0 12"),
                        PanelPlace.of("Hero BottomLeft 12 12")))));

        assertEquals(0f, built.hero().heightPixels(), 0.001f, "no bar stands across the bottom of the window");
        assertFalse(drawn(find(built.gui(), "slab")), "and its stone is not drawn");
        assertTrue(drawn(find(built.gui(), "skills")), "the blocks themselves are");
        var skills = (BoundingBox) find(built.gui(), "skills").getWorldBound();
        assertEquals(800f, skills.getCenter().x, 60f, "the row hung from the middle of the bottom edge is in it");
    }
}
