package uz.duke.dungeon;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Opens the dungeon. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        var game = Dungeon.create();
        var simulation = game.startEngineOnly();

        SwingUtilities.invokeLater(() -> {
            var window = new JFrame(game.getTitle());
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.setContentPane(new DungeonView(game));
            window.pack();
            window.setLocationRelativeTo(null);
            // Closing the window ends the simulation, which ends main below.
            window.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent event) {
                    game.stop();
                }
            });
            window.setVisible(true);
        });

        simulation.join();
    }
}
