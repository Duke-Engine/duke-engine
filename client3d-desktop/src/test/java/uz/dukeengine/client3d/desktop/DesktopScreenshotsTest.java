package uz.dukeengine.client3d.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.dukeengine.client3d.platform.ClientPlatform;

/** The game is told the name of the file a screenshot was really written to. */
class DesktopScreenshotsTest {

    @TempDir
    Path folder;

    @Test
    void theCallbackGivesTheNameOfTheFileThatWasWritten() throws IOException {
        var told = new ArrayList<String>();
        var shots = new DesktopScreenshots(folder + File.separator, null, told::add);
        shots.reshape(null, 4, 3); // the frame's size, as the window gives it

        var file = folder.resolve("screenshot7.png").toFile();
        shots.writeImageFile(file);

        assertEquals(List.of("screenshot7.png"), told);
        assertTrue(file.isFile() && file.length() > 0, "and that file is there");
    }

    @Test
    void theGamesFolderAndNameTakeTheFirstFreeNumberInTheFormatTheNameSays() throws IOException {
        var told = new ArrayList<String>();
        var userData = folder.resolve("Command and Conquer Generals Zero Hour Data");
        var shots = new DesktopScreenshots(userData.toString(), "sshot%03d.bmp", told::add);
        shots.reshape(null, 4, 3);
        java.nio.file.Files.createDirectories(userData);
        java.nio.file.Files.writeString(userData.resolve("sshot001.bmp"), "taken before");

        shots.writeImageFile(folder.resolve("ignored.png").toFile());
        shots.writeImageFile(folder.resolve("ignored.png").toFile());

        assertEquals(List.of("sshot002.bmp", "sshot003.bmp"), told, "the first free numbers, from 1");
        var written = javax.imageio.ImageIO.read(userData.resolve("sshot002.bmp").toFile());
        assertEquals(4, written.getWidth(), "a bmp the frame's size");
        assertTrue(!java.nio.file.Files.exists(folder.resolve("ignored.png")), "nothing under jME's own name");
    }

    @Test
    void aNameWithoutItsNumberIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ClientPlatform.checkedScreenshotName("sshot.bmp"));
        assertEquals("sshot%03d.bmp", ClientPlatform.checkedScreenshotName("sshot%03d.bmp"));
        assertEquals("png", DesktopScreenshots.format("screenshot"));
    }
}
