package uz.duke.studio;

import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import uz.duke.studio.io.ProjectIO;
import uz.duke.studio.model.StudioProject;
import uz.duke.studio.ui.StudioWindow;

/** Entry point: opens Duke Studio — on a project file if one is given. */
public final class StudioMain {

    public static void main(String[] args) {
        try {
            for (var laf : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(laf.getName())) {
                    UIManager.setLookAndFeel(laf.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // default look and feel is fine
        }
        SwingUtilities.invokeLater(() -> {
            if (args.length > 0) {
                try {
                    var file = Path.of(args[0]).toAbsolutePath().normalize();
                    new StudioWindow(ProjectIO.load(file), file).setVisible(true);
                    return;
                } catch (Exception e) {
                    System.err.println("could not open " + args[0] + ": " + e);
                }
            }
            new StudioWindow(StudioProject.starter()).setVisible(true);
        });
    }
}
