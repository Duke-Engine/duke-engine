package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.system.AppSettings;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.DukeGame;

/** A game can ask whether the window fills the screen. */
class FullscreenTest {

    private static DukeRtsApp app(boolean fullscreen) {
        var app = new DukeRtsApp(DukeGame.create("fullscreen-test"), null, Shell.standard(), null, null, null, null,
                null, null, null);
        var settings = new AppSettings(true);
        settings.setFullscreen(fullscreen);
        app.setSettings(settings);
        return app;
    }

    @Test
    void theAnswerIsWhatTheWindowIsAsOfItsLastFrame() {
        var filling = app(true);
        assertFalse(filling.isFullscreen(), "no frame yet");
        filling.refreshFullscreen();
        assertTrue(filling.isFullscreen());

        var windowed = app(false);
        windowed.refreshFullscreen();
        assertFalse(windowed.isFullscreen());
    }

    @Test
    void beforeTheWindowIsOpenItIsNotFullscreen() {
        assertFalse(Duke3D.of(DukeGame.create("not-launched"), null).isFullscreen());
    }
}
