package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.TextFloated;
import uz.dukeengine.core.math.Coord3D;

/** A text floated up from a point of the world, rising a pixel a frame and fading as the reference's does. */
class FloatingTextsTest {

    private static final FloatingTexts.Look LOOK = FloatingTexts.Look.REFERENCE;
    /** "$200" over a derrick, 230 opaque, floated in frame 100. */
    private static final TextFloated MONEY = new TextFloated(100, new Coord3D(0f, 0f, 0f), "$200", 0xE600FF00);

    private static FloatingTexts texts() {
        var font = new DesktopAssetManager(true).loadFont("Interface/Fonts/Default.fnt");
        return new FloatingTexts(font, new Node("gui"), LOOK);
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
    void itRisesAPixelAFrameKeepsItsColourTenFramesAndIsGone82FramesAfterItAppeared() {
        assertEquals(10f, LOOK.riseAt(10), 1e-6f);
        assertEquals(230, LOOK.alphaAt(10, 230), "full for ten frames");
        assertTrue(LOOK.alphaAt(81, 230) > 0);
        assertEquals(0, LOOK.alphaAt(82, 230), "gone 82 frames after it appeared");
    }

    @Test
    void drawnFromTheNextFrameTenPixelsHigherTenFramesLaterAndGoneByFrame183() {
        var texts = texts();
        var camera = camera();
        texts.add(MONEY, 100);
        texts.update(100, camera, (x, y) -> true);
        assertEquals(Spatial.CullHint.Always, texts.face(0).getLocalCullHint(), "not the frame it was floated");

        texts.update(101, camera, (x, y) -> true);
        assertEquals(Spatial.CullHint.Inherit, texts.face(0).getLocalCullHint(), "from the next");
        float first = texts.face(0).getLocalTranslation().y;
        texts.update(111, camera, (x, y) -> true);
        assertEquals(first + 10f, texts.face(0).getLocalTranslation().y, 1e-3f, "10 frames later, 10 pixels higher");
        assertEquals(230 / 255f, texts.face(0).getColor().a, 1e-3f, "at full alpha");

        texts.update(182, camera, (x, y) -> true);
        assertEquals(1, texts.count(), "still just there");
        texts.update(183, camera, (x, y) -> true);
        assertEquals(0, texts.count(), "gone by frame 183");
    }

    @Test
    void itIsNotDrawnWhereThePointIsFoggedAndAPausedGameHoldsIt() {
        var texts = texts();
        var camera = camera();
        texts.add(MONEY, 100);
        texts.update(105, camera, (x, y) -> false);
        assertEquals(Spatial.CullHint.Always, texts.face(0).getLocalCullHint(), "fogged: not drawn");

        texts.update(105, camera, (x, y) -> true);
        var held = texts.face(0).getLocalTranslation().clone();
        texts.update(105, camera, (x, y) -> true); // paused: no new frame of the game
        assertEquals(held, texts.face(0).getLocalTranslation(), "held where it is");
    }
}
