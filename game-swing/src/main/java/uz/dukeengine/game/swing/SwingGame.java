package uz.dukeengine.game.swing;

import javax.swing.SwingUtilities;
import uz.dukeengine.game.DukeGame;

/** Runs a {@link DukeGame} in a 2D Swing window. */
public final class SwingGame {

    private SwingGame() {
    }

    /**
     * Boot the engine, open the window, and run the game. Blocks until the
     * window is closed (or {@link DukeGame#stop()} is called).
     */
    public static void start(DukeGame game) {
        var engineThread = game.startEngineOnly();

        var windowRef = new GameWindow[1];
        SwingUtilities.invokeLater(() -> {
            windowRef[0] = new GameWindow(game, game.getTitle(), game.getWindowWidth(), game.getWindowHeight());
            windowRef[0].setVisible(true);
        });

        try {
            engineThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        SwingUtilities.invokeLater(() -> {
            if (windowRef[0] != null) {
                windowRef[0].dispose();
            }
        });
    }
}
