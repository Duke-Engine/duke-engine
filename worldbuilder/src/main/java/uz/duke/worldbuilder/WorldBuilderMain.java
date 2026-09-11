package uz.duke.worldbuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.stage.StageFile;
import uz.duke.worldbuilder.ui.BuilderWindow;

/**
 * Opens the world builder — on a stage file if one is named.
 *
 * <pre>{@code
 * ./gradlew :worldbuilder:run
 * ./gradlew :worldbuilder:run --args="stages/first.stage"
 * }</pre>
 *
 * <p>Without a file it starts on a dungeon drawn from the clock, which is a first
 * draft rather than a blank page: the generator has already put monsters in the
 * rooms and a boss at the end, so there is something to play with from the first
 * second and the work is moving things rather than placing everything.
 */
public final class WorldBuilderMain {

    private WorldBuilderMain() {
    }

    public static void main(String[] args) {
        // Nimbus, as the Studio does, so the two tools in this repository look like
        // the same pair of hands made them.
        try {
            for (var laf : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(laf.getName())) {
                    UIManager.setLookAndFeel(laf.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // the default look and feel is fine
        }

        var settings = DungeonSettings.load();
        SwingUtilities.invokeLater(() -> {
            var draft = args.length > 0 ? opened(args[0], settings) : null;
            new BuilderWindow(settings,
                    draft != null ? draft : StageDraft.generate(System.nanoTime(), settings))
                    .setVisible(true);
        });
    }

    /** The named stage, or nothing and a word about why — never a silent blank page. */
    private static StageDraft opened(String path, DungeonSettings settings) {
        try {
            var text = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            return StageDraft.of(StageFile.read(text, path), settings);
        } catch (Exception e) {
            System.err.println("could not open " + path + ": " + e.getMessage());
            return null;
        }
    }
}
