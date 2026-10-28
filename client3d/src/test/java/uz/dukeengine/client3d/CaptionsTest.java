package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The words the game puts over a thing: drawn over it while given, gone when taken away. */
class CaptionsTest {

    private static Captions captions() {
        return new Captions(new DesktopAssetManager(true).loadFont("Interface/Fonts/Default.fnt"), new Node("gui"));
    }

    private static Camera camera() {
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(45f, 1600f / 900f, 1f, 2000f);
        camera.setLocation(new Vector3f(0f, 200f, 200f));
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        camera.update();
        return camera;
    }

    @Test
    void theGivenTextIsDrawnCentredOverItsPointAndGoneOnceNoLongerGiven() {
        var captions = captions();
        var camera = camera();
        var at = new Vector3f(0f, 10f, 0f);

        captions.update(camera, List.of(new Captions.Line(7, "Rally here", at)));
        var text = captions.of(7);
        assertNotNull(text, "drawn");
        assertEquals("Rally here", text.getText());
        var screen = camera.getScreenCoordinates(at);
        assertEquals(screen.x, text.getLocalTranslation().x + text.getLineWidth() / 2f, 1e-3f, "centred on it");
        assertTrue(text.getLocalTranslation().y > screen.y, "over it");

        captions.update(camera, List.of(new Captions.Line(7, "Hold", at)));
        assertEquals("Hold", captions.of(7).getText(), "reworded in place");

        captions.update(camera, List.of());
        assertNull(captions.of(7), "taken away with its words");
        assertNull(text.getParent(), "and off the interface");
    }

    @Test
    void aPointBehindTheCameraGetsNoText() {
        var captions = captions();
        captions.update(camera(), List.of(new Captions.Line(1, "Behind", new Vector3f(0f, 0f, 1000f))));
        assertNull(captions.of(1));
    }
}
