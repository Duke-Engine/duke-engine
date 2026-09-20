package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The painted edges are a coat of paint, and behave like one.
 *
 * <p>Three promises, and all three are about what happens when something is
 * wrong. A game that asks for nothing gets the panel it always had. A game that
 * asks for a file that is not there gets the panel it always had, and a line in
 * the log. And what a game asks for actually reaches the screen — which is the
 * one of the three nobody would notice failing, because a panel drawn without any
 * of it still looks like a panel.
 *
 * <p>Built with a headless asset manager, which is the whole panel bar the
 * pixels: the meshes, the materials and the colours are all made here and only
 * the drawing needs a window.
 */
class PanelSkinTest {

    /** A line with all four skills and the figures under the bars, so every part is built. */
    private static final String LINE =
            "name=Erika|rank=7-daraja|hp=128/200|xp=38/100|depth=III|depthWord=CHUQURLIK"
                    + "|stat=Zarba,34,+6|stat=Zirh,12,+2|stat=Tezlik,52"
                    + "|skill=Q,,ready|skill=W,,cool,72,165|skill=E,,ready|skill=R,,lock,5";

    /** Something really there, standing in for a frame the dungeon would name. */
    private static final String REAL = "Common/Textures/dot.png";

    private static PanelSkin skinOf(String texture, float inset, float scale, Color tint) {
        var piece = new PanelSkin.Piece(texture, inset, scale, tint);
        return new PanelSkin(Map.of(
                PanelSkin.MINIMAP, piece, PanelSkin.PORTRAIT, piece, PanelSkin.SLOT, piece,
                PanelSkin.GAUGE, piece, PanelSkin.CHIP, piece, PanelSkin.DIVIDER, piece));
    }

    /** Build a panel showing a full line, and hand back the node it drew into. */
    private static Node panelWith(PanelSkin skin) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var panel = new HeroPanel(assets, font, gui, 1280f, 720f, skin, RangeLook.DEFAULT);
        assertTrue(panel.show(LINE, 0f), "the panel should have taken its own game's line");
        return gui;
    }

    /** Every painted frame in the panel, by the name the geometry carries. */
    private static List<Geometry> frames(Spatial root) {
        var found = new ArrayList<Geometry>();
        collect(root, found);
        return found;
    }

    private static void collect(Spatial spatial, List<Geometry> found) {
        if (spatial instanceof Geometry geometry && geometry.getName().startsWith("frame-")) {
            found.add(geometry);
        }
        if (spatial instanceof Node node) {
            for (var child : node.getChildren()) {
                collect(child, found);
            }
        }
    }

    private static Geometry frame(Spatial root, String piece) {
        return frames(root).stream()
                .filter(geometry -> geometry.getName().equals("frame-" + piece))
                .findFirst()
                .orElse(null);
    }

    @Test
    void aGameThatAsksForNothingIsPaintedWithNothing() {
        assertTrue(frames(panelWith(PanelSkin.NONE)).isEmpty(),
                "every game but this one names no skin, and must get the panel it had");
    }

    /**
     * A picture the client cannot find costs that edge and nothing else.
     *
     * <p>The same bargain a missing skill icon strikes — see
     * {@code HeroPanelTest} — and the reason it has to be struck twice is that
     * these are named in a different file by a different hand, so a typo here is
     * just as likely and has to be just as cheap.
     */
    @Test
    void aPictureThatWillNotLoadLeavesThePanelAsItWas() {
        var gui = panelWith(skinOf("ui/borders/no-such-frame.png", 16f, 1f, Color.WHITE));

        assertTrue(frames(gui).isEmpty(), "nothing should have been painted");
        // And the panel is still a panel: the check above would also pass on a
        // panel that had thrown its way out of being built.
        assertNotNull(gui.getChild("hero-panel"), "the bar itself should still be there");
    }

    @Test
    void whatTheGameNamesReachesEveryPartOfThePanel() {
        var gui = panelWith(skinOf(REAL, 8f, 1f, Color.WHITE));

        for (var piece : List.of(PanelSkin.MINIMAP, PanelSkin.PORTRAIT, PanelSkin.SLOT,
                PanelSkin.GAUGE, PanelSkin.CHIP, PanelSkin.DIVIDER)) {
            assertNotNull(frame(gui, piece), piece + " was named and never drawn");
        }
    }

    /**
     * A skin with only one piece in it paints only that piece.
     *
     * <p>Which is what makes the block in the file optional one at a time rather
     * than all together, and is the shape a half-finished skin takes while someone
     * is still choosing frames.
     */
    @Test
    void aPieceLeftOutIsSimplyNotPainted() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var only = Map.of(PanelSkin.GAUGE,
                new PanelSkin.Piece(REAL, 2f, 1f, Color.WHITE));
        new HeroPanel(assets, font, gui, 1280f, 720f, new PanelSkin(only), RangeLook.DEFAULT).show(LINE, 0f);

        assertNotNull(frame(gui, PanelSkin.GAUGE));
        assertEquals(null, frame(gui, PanelSkin.SLOT), "nobody asked for a socket rim");
        assertEquals(null, frame(gui, PanelSkin.MINIMAP));
    }

    /**
     * Changing the numbers in the file changes what is drawn.
     *
     * <p>Without this the whole chain could be wired to a constant and every test
     * above would still pass. The inset is read at the far end of it — file, to
     * settings, to the client's skin, to a vertex — so a vertex that moves when
     * the file's number moves is the chain end to end.
     */
    @Test
    void theInsetAndTheScaleAreReallyReadRatherThanAssumed() {
        var light = frame(panelWith(skinOf(REAL, 8f, 1f, Color.WHITE)), PanelSkin.MINIMAP);
        var heavy = frame(panelWith(skinOf(REAL, 8f, 2f, Color.WHITE)), PanelSkin.MINIMAP);

        assertEquals(2f * cornerOf(light), cornerOf(heavy), 0.01f,
                "twice the scale should be twice the border");
    }

    @Test
    void theTintIsReallyReadToo() {
        var gold = frame(panelWith(skinOf(REAL, 8f, 1f, new Color(0xC9A24B))), PanelSkin.SLOT);
        var bone = frame(panelWith(skinOf(REAL, 8f, 1f, new Color(0xDCD2BC))), PanelSkin.SLOT);

        var goldColour = (ColorRGBA) gold.getMaterial().getParam("Color").getValue();
        var boneColour = (ColorRGBA) bone.getMaterial().getParam("Color").getValue();
        assertNotEquals(goldColour, boneColour, "two colours in the file, one on the screen");
        assertFalse(goldColour.equals(ColorRGBA.White),
                "and neither of them is the white the picture is painted in");
    }

    /**
     * A painted socket still goes dead when the skill behind it is.
     *
     * <p>The one thing a coat of paint could quietly take away. A locked slot is
     * drawn dark on purpose — that darkness is what says "not yet" — and a gold
     * rim laid over it at full strength made the one slot he cannot cast the
     * brightest thing on the bar.
     */
    @Test
    void aLockedSocketsRimGoesDeadWithIt() {
        var gui = panelWith(skinOf(REAL, 8f, 1f, new Color(0xC9A24B)));
        // Skill sockets only: the bag and the order buttons wear the same picture,
        // and it is the skill row that has states.
        var rims = frames(gui).stream()
                .filter(geometry -> geometry.getName().equals("frame-" + PanelSkin.SLOT)
                        && "slot".equals(geometry.getParent().getName()))
                .map(geometry -> (ColorRGBA) geometry.getMaterial().getParam("Color").getValue())
                .toList();

        assertEquals(4, rims.size(), "the line names four skills");
        // Q and E are ready, W is reloading and R is locked: three strengths of
        // the one colour, and the locked one the faintest of them.
        float ready = rims.getFirst().r;
        float locked = rims.getLast().r;
        assertTrue(locked < ready * 0.6f,
                "the locked rim should be plainly darker, but was " + locked + " against " + ready);
        assertTrue(rims.get(1).r < ready, "and the reloading one dimmer than a ready one");
    }

    /** Where the frame's first cut falls, which is the border it was given. */
    private static float cornerOf(Geometry frame) {
        var buffer = frame.getMesh().getFloatBuffer(VertexBuffer.Type.Position);
        buffer.rewind();
        return buffer.get(3); // the x of the second vertex in the bottom row
    }
}
