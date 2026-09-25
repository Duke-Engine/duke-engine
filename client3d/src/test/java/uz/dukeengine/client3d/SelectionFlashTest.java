package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;

/**
 * A thing flashing as it is picked, and as an order is given on it — the reference's selection flash: a colour added at
 * once, held the frame its envelope turns, and eased out over four frames.
 */
class SelectionFlashTest {

    @Test
    void selectingAThingAddsAQuarterGreyThatFrameEasedOutOverFourAfterTheTurn() {
        var flashes = new SelectionFlash();
        var root = new Node("tank");

        flashes.flash(3, root, Visuals.SelectionFlashLook.REFERENCE, ColorRGBA.Red);
        assertEquals(0.25f, flashes.colourOf(3).r, 1e-6f, "white saturated by a half: +0.25 on that frame");
        assertEquals(0.25f, flashes.colourOf(3).b, 1e-6f, "grey");
        assertEquals(1, root.getLocalLightList().size(), "laid over the thing alone");

        float[] after = {0.25f, 0.1875f, 0.125f, 0.0625f};
        for (float expected : after) {
            flashes.update(1f);
            assertEquals(expected, flashes.colourOf(3).g, 1e-6f);
        }
        flashes.update(1f);
        assertNull(flashes.colourOf(3), "gone the fifth frame on: the reference holds its peak the frame it turns");
        assertEquals(0, root.getLocalLightList().size());
    }

    @Test
    void inItsOwnersColourWhereTheGameSaysSo() {
        var flashes = new SelectionFlash();
        flashes.flash(4, new Node("tank"), new Visuals.SelectionFlashLook(0.5f, true, 4), ColorRGBA.Red);

        var added = flashes.colourOf(4);
        assertEquals(0.25f, added.r, 1e-6f);
        assertEquals(-0.25f, added.g, 1e-6f, "a red house colour saturated takes green and blue away");
        assertEquals(-0.25f, added.b, 1e-6f);
    }

    @Test
    void anOrderNamedToFlashItsTargetFlashesItAndOneNamedToDrawNothingDrawsNothing() {
        var visuals = Visuals.create().wordMark("enter", Visuals.WordMark.FLASH)
                .wordMark("dock", Visuals.WordMark.NONE);

        assertEquals(Visuals.WordMark.FLASH, visuals.wordMarkFor("enter"), "entering a thing flashes it");
        assertEquals(Visuals.WordMark.NONE, visuals.wordMarkFor("dock"), "a dock draws nothing");
        assertEquals(Visuals.WordMark.MARK, visuals.wordMarkFor("capture"), "one named nothing: the order mark");
        assertNull(visuals.getSelectionFlash(), "and no flash on selection unless the game names one");
    }
}
