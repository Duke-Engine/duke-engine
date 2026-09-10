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
 *
 * <p>Pass a {@link Shell} to say what the game puts in front of itself — its own
 * menu entries, or none at all. Without one it gets the client's standard menu,
 * which is what every game got before it could choose.
 */
public final class Duke3D {

    private Duke3D() {
    }

    public static void launch(DukeGame game, Visuals visuals) {
        launch(game, visuals, Shell.standard());
    }

    public static void launch(DukeGame game, Visuals visuals, Shell shell) {
        launch(game, visuals, shell, 1280, 720);
    }

    public static void launch(DukeGame game, Visuals visuals, int width, int height) {
        launch(game, visuals, Shell.standard(), width, height);
    }

    /**
     * With keys of the game's own — see {@link Hotkeys}. The client keeps its
     * standard controls; these are what a particular game adds on top.
     */
    public static void launch(DukeGame game, Visuals visuals, Shell shell, Hotkeys hotkeys) {
        launch(game, visuals, shell, hotkeys, 1280, 720);
    }

    public static void launch(DukeGame game, Visuals visuals, Shell shell, int width, int height) {
        launch(game, visuals, shell, Hotkeys.none(), width, height);
    }

    public static void launch(DukeGame game, Visuals visuals, Shell shell, Hotkeys hotkeys,
            int width, int height) {
        // the simulation starts when the player presses Play — or at once, if the
        // game asked for no menu at all
        var app = new DukeRtsApp(game, visuals, shell, hotkeys);
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
        // Frames per second is a developer's number. It sat over the line of
        // controls at the bottom of the screen, and neither could be read.
        app.setDisplayFps(false);
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
