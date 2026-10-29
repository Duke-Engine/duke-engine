package uz.dukeengine.client3d.desktop;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import uz.dukeengine.client3d.platform.TextPrompt;

/** A Swing input dialog, put up on the event thread while the caller waits. */
final class SwingTextPrompt implements TextPrompt {

    @Override
    public String ask(String prompt, String initial) {
        var result = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> result[0] = JOptionPane.showInputDialog(null, prompt, initial));
        } catch (Exception _) {
            return null;
        }
        return result[0];
    }
}
