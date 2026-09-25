package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.StripPlayed;
import uz.dukeengine.core.math.Coord3D;

/** A picture strip at a point of the world, rising and fading as the reference's world animations do. */
class PictureStripsTest {

    /** A unit's new rank over it: 10 up, for 4 seconds, rising 15 over them, played in frame 100. */
    private static final StripPlayed RANK = new StripPlayed(100, "LevelGained", new Coord3D(0f, 0f, 10f), 4f, 15f);

    private static final Visuals.Strip STRIP = new Visuals.Strip(List.of("icons/rank_1.png", "icons/rank_2.png"),
            100, true);

    private static PictureStrips strips() {
        var picture = new Texture2D(new Image(Image.Format.RGBA8, 32, 16, BufferUtils.createByteBuffer(32 * 16 * 4),
                com.jme3.texture.image.ColorSpace.Linear));
        var assets = new DesktopAssetManager(true);
        return new PictureStrips(new Node("gui"), path -> picture, name -> name.equals("LevelGained") ? STRIP : null,
                texture -> {
                    var look = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
                    look.setTexture("ColorMap", texture);
                    return look;
                });
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
    void drawnAt10ThenAt17AndAHalfAfter2SecondsAndGoneAfter4() {
        assertEquals(10f, PictureStrips.heightAt(RANK, 0), 1e-4f, "at its point");
        assertEquals(17.5f, PictureStrips.heightAt(RANK, 60), 1e-4f, "half its rise half its time on");
        assertEquals(1f, PictureStrips.alphaAt(60, 120), "whole until its last second");
        assertEquals(0.5f, PictureStrips.alphaAt(105, 120), 1e-4f, "then fading");

        var strips = strips();
        var camera = camera();
        strips.add(RANK, 100);
        strips.update(101, camera, (x, y) -> true);
        assertEquals(Spatial.CullHint.Inherit, strips.quad(0).getLocalCullHint(), "drawn from the next frame");
        assertEquals(32f, strips.quad(0).getLocalScale().x, "at its picture's size");
        float first = strips.quad(0).getLocalTranslation().y;
        strips.update(161, camera, (x, y) -> true);
        assertTrue(strips.quad(0).getLocalTranslation().y > first, "higher on the screen as it rises");

        strips.update(220, camera, (x, y) -> true);
        assertEquals(1, strips.count(), "up to its last frame");
        strips.update(221, camera, (x, y) -> true);
        assertEquals(0, strips.count(), "gone after 4 seconds");
    }

    @Test
    void oneWhosePointIsNotInClearViewIsNotDrawn() {
        var strips = strips();
        strips.add(RANK, 100);
        strips.update(101, camera(), (x, y) -> false);

        assertEquals(Spatial.CullHint.Always, strips.quad(0).getLocalCullHint());
    }

    @Test
    void aStripTheGameHasNoneOfDrawsNothing() {
        var strips = strips();
        strips.add(new StripPlayed(100, "Nothing", new Coord3D(0f, 0f, 0f), 4f, 15f), 100);

        assertEquals(0, strips.count());
    }
}
