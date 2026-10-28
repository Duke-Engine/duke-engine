package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The game is told the name of the file a screenshot was really written to. */
class ScreenshotsTest {

    @TempDir
    Path folder;

    @Test
    void theCallbackGivesTheNameOfTheFileThatWasWritten() throws IOException {
        var told = new ArrayList<String>();
        var shots = new Screenshots(folder + File.separator, "screenshot", told::add);
        shots.reshape(null, 4, 3); // the frame's size, as the window gives it

        var file = folder.resolve("screenshot7.png").toFile();
        shots.writeImageFile(file);

        assertEquals(List.of("screenshot7.png"), told);
        assertTrue(file.isFile() && file.length() > 0, "and that file is there");
    }
}
