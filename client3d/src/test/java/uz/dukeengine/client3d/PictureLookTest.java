package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;
import uz.dukeengine.render.PictureSettings;
import uz.dukeengine.render.Quality;

/** What a {@code Picture} block leaves out is the full picture; the air reddens a low sun and leaves a high one be. */
class PictureLookTest {

    @Test
    void aBlockThatSaysNothingIsTheFullPicture() {
        assertEquals(PictureSettings.FULL, PictureLook.DEFAULTS.settings());
    }

    @Test
    void whatTheBlockSaysIsLaidOverItsStep() {
        var look = new PictureLook(Quality.LOW, 1.2f, true, false, 0.02f, 0.1f, true).settings();
        assertEquals(Quality.LOW.settings().ambientOcclusion(), look.ambientOcclusion());
        assertEquals(1.2f, look.exposure());
        assertTrue(look.atmosphere() && look.water());
        assertEquals(0.02f, look.fogDensity());
    }

    @Test
    void theAirReddensALowSunAndLeavesAHighOne() {
        var white = 0xFFFFFF;
        var noon = new SceneLight(new Sunlight(89f, 0f, 1f, 0.5f, white, white), new Node(), true).sunColour();
        var evening = new SceneLight(new Sunlight(4f, 0f, 1f, 0.5f, white, white), new Node(), true).sunColour();
        var without = new SceneLight(new Sunlight(4f, 0f, 1f, 0.5f, white, white), new Node(), false).sunColour();
        assertEquals(1f, noon.r, 0.02f);
        assertEquals(1f, noon.b, 0.02f);
        assertTrue(evening.r > 2f * evening.b, "a setting sun is red: " + evening);
        assertTrue(evening.r < 1f);
        assertEquals(1f, without.b, 1e-6f);
    }
}
