package uz.duke.client3d;

import com.jme3.system.AppSettings;
import uz.duke.game.DukeGame;

/**
 * Launches a {@link DukeGame} with the 3D client — the Unity-style "press
 * play" for a full 3D RTS:
 *
 * <pre>{@code
 * var game = DukeGame.create("My RTS").loadUnits(DukeGame.STARTER_UNITS)...;
 * var visuals = Visuals.create().unit("Tank", u -> u.model("Models/tank.gltf"));
 * Duke3D.launch(game, visuals);   // opens the 3D window, blocks until closed
 * }</pre>
 *
 * <p>The simulation runs deterministically on its own thread; the jME window is
 * pure presentation. Closing the window stops both.
 */
public final class Duke3D {

    private Duke3D() {
    }

    public static void launch(DukeGame game, Visuals visuals) {
        launch(game, visuals, 1280, 720);
    }

    public static void launch(DukeGame game, Visuals visuals, int width, int height) {
        // the simulation starts when the player presses Play on the main menu
        var app = new DukeRtsApp(game, visuals);
        var settings = new AppSettings(true);
        settings.setTitle(game.getTitle());
        // saved display settings win over the caller's defaults
        int resIndex = DukeRtsApp.PREFS.getInt("resIndex", -1);
        if (resIndex >= 0 && resIndex < 3) {
            int[][] resolutions = {{1280, 720}, {1600, 900}, {1920, 1080}};
            settings.setResolution(resolutions[resIndex][0], resolutions[resIndex][1]);
        } else {
            settings.setResolution(width, height);
        }
        settings.setFullscreen(DukeRtsApp.PREFS.getBoolean("fullscreen", false));
        settings.setVSync(true);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.setDisplayStatView(false);
        app.setPauseOnLostFocus(false);
        app.start();

        try {
            app.awaitStop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        game.stop();
        var simThread = app.getSimThread();
        if (simThread != null) {
            try {
                simThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
