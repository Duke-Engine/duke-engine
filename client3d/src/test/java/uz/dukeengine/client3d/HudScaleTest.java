package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector2f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.CommandButton;

/**
 * A screen twice as dense as the design (the desktop's emulation of a phone): the HUD is laid out as if the window were
 * half as big and drawn twice as large, and what hits it is told in the same halved pixels.
 */
class HudScaleTest {

    private static final HudScale TWICE = new HudScale(2f);

    @Test
    void theDesktopsScaleOfOneChangesNothing() {
        var point = new Vector2f(30f, 40f);
        assertSame(point, HudScale.NONE.of(point));
        assertEquals(1280, HudScale.NONE.of(1280));
        assertEquals(1f, new HudScale(0f).scale(), "a scale of nothing is no scale");
        assertEquals(1f, new HudScale(Float.NaN).scale());
    }

    @Test
    void aLengthAndAPointOfTheWindowAreHalvedAndALengthOfTheHudIsDoubled() {
        assertEquals(500, TWICE.of(1000));
        assertEquals(250f, TWICE.of(500f));
        assertEquals(new Vector2f(100f, 150f), TWICE.of(new Vector2f(200f, 300f)));
        assertEquals(60f, TWICE.inWindow(30f));
        assertNull(TWICE.of((Canvas.Point) null));
        assertEquals(new Canvas.Point(10f, 20f), TWICE.of(new Canvas.Point(20f, 40f)));
        assertEquals(List.of(1f, 2f, 3f, 4f), asList(TWICE.of(new float[] {2f, 4f, 6f, 8f})));
    }

    @Test
    void aRootOfTheHudIsDrawnTwiceAsLargeSoWhatIsLaidAtThirtyStandsAtSixty() {
        var root = TWICE.root("hud");
        var child = new com.jme3.scene.Node("piece");
        child.setLocalTranslation(30f, 40f, 0f);
        root.attachChild(child);
        root.updateGeometricState();

        assertEquals(60f, child.getWorldTranslation().x, 1e-4);
        assertEquals(80f, child.getWorldTranslation().y, 1e-4);
        assertEquals(2f, child.getWorldScale().x, 1e-4);
    }

    /** Ten buttons, two rows, so the bar has both its edges. */
    private static CommandBar barAt(HudScale hud, int windowWidth) {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var bar = new CommandBar(new StoneCraft(assets, font, MenuStyle.DEFAULTS), font);
        var buttons = new ArrayList<CommandButton>();
        for (int i = 0; i < 10; i++) {
            buttons.add(new CommandButton("b" + i, null, "b" + i, "", true, CommandButton.Aim.NOW, null, 0f,
                    CommandButton.NO_PROGRESS, null));
        }
        bar.show(buttons, hud.of(windowWidth));
        return bar;
    }

    /** The widest and highest hud-pixel a button answers at, scanning the HUD's own pixels. */
    private static float[] extent(CommandBar bar) {
        float right = -1f;
        float top = -1f;
        for (int x = 0; x < 600; x++) {
            for (int y = 0; y < 300; y++) {
                if (bar.at(x, y) != null) {
                    right = Math.max(right, x);
                    top = Math.max(top, y);
                }
            }
        }
        return new float[] {right, top};
    }

    @Test
    void theCommandBarIsLaidOutInHalvedPixelsAndAFingerOnItsWindowPlaceHitsTheSameButton() {
        var plain = barAt(HudScale.NONE, 500);
        var dense = barAt(TWICE, 1000); // the same screen, twice the pixels

        assertEquals(extent(plain)[0], extent(dense)[0], "the bar is laid out as if the window were 500 wide");
        assertEquals(extent(plain)[1], extent(dense)[1]);

        // A point on the dense window is the plain window's point doubled: the cursor is halved before it is asked.
        var hudPoint = new Vector2f(extent(dense)[0] - 5f, 20f);
        var buttonThere = dense.at(hudPoint.x, hudPoint.y);
        assertNotNull(buttonThere);
        var windowPoint = new Vector2f(hudPoint.x * 2f, hudPoint.y * 2f);
        var told = TWICE.of(windowPoint);
        assertEquals(buttonThere, dense.at(told.x, told.y), "the touch at the doubled place lands on the same button");
        assertNull(dense.at(windowPoint.x, windowPoint.y), "asked unhalved it would be somewhere else entirely");

        // Drawn: the last button ends a margin and a gap (16) short of the edge, which is 32 in the window.
        assertEquals(1000f - TWICE.inWindow(16f), TWICE.inWindow(extent(dense)[0] + 1f), 1e-3);
    }

    @Test
    void theCanvasHearsThePointerInTheHudsPixelsFromItsTop() {
        var heard = new ArrayList<CanvasInput.Event>();
        var inputs = new CanvasInputs(event -> heard.add(event), () -> 600, TWICE);

        // 200 across and 100 up from the bottom of a 600 tall window: 100 across and 250 down in the canvas's pixels.
        inputs.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, 200, 100));

        assertEquals(List.of(new CanvasInput.Button(100, 250, CanvasInput.Mouse.LEFT, true, false)), heard);
    }

    private static List<Float> asList(float[] values) {
        var list = new ArrayList<Float>();
        for (float value : values) {
            list.add(value);
        }
        return list;
    }
}
