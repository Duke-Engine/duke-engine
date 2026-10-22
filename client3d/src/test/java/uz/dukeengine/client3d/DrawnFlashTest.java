package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

/** A thing drawn at the size and flashed in the colour the simulation gives it (R23.5). */
class DrawnFlashTest {

    private static UnitView view(float scale, int flash) {
        return new UnitView(1, "Orb", 1, 0f, 0f, 0f, 100f, 100f, false, true, false, false, -1, 0f, 0f, 0f, false, 0,
                java.util.List.of(), java.util.List.of(), 1f, -1, true, null, false, null, 1, 1f, false, 0f, false,
                null, 0f, 0f, null, scale, flash);
    }

    @Test
    void itIsDrawnAtTheScaleItsViewSays() {
        assertEquals(0.5f, UnitPlacement.scale(view(0.5f, 0)).x, 0f);
        assertEquals(1f, UnitPlacement.scale(view(1f, 0)).y, 0f);
        assertEquals(1e-4f, UnitPlacement.scale(view(0f, 0)).z, 0f, "never quite nothing");
    }

    @Test
    void aRedFlashLightsItRedByItsStrengthAndIsOffWhenItClears() {
        var flashes = new DrawnFlash();
        var root = new Node();

        flashes.see(1, root, 0xFFFF0000);
        var full = flashes.colourOf(1);
        assertEquals(1f, full.r, 1e-6f);
        assertEquals(0f, full.g, 0f);
        assertEquals(1, root.getLocalLightList().size());

        flashes.see(1, root, 0x80FF0000);
        assertEquals(0x80 / 255f, flashes.colourOf(1).r, 1e-6f, "eased");

        flashes.see(1, root, 0);
        assertNull(flashes.colourOf(1));
        assertEquals(0, root.getLocalLightList().size());
    }
}
