package uz.dukeengine.game.swing;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import uz.dukeengine.game.DukeGame;

/**
 * The game window: a frame hosting the {@link GamePanel}. Closing it quits the
 * engine loop cleanly.
 */
public final class GameWindow extends JFrame {

    public GameWindow(DukeGame game, String title, int width, int height) {
        super(title);
        var panel = new GamePanel(game);
        panel.setPreferredSize(new Dimension(width, height));
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                game.stop();
            }
        });
    }
}
