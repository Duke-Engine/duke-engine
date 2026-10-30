package uz.dukeengine.client3d.android;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;

public class AndroidScreenshotsTest {

    @Test
    public void takesTheFirstFreeNumberAsAPng() throws IOException {
        var folder = Files.createTempDirectory("shots").toFile();
        assertEquals("sshot001.png", AndroidScreenshots.firstFree(folder, "sshot%03d.bmp").getName());
        new File(folder, "sshot001.png").createNewFile();
        assertEquals("sshot002.png", AndroidScreenshots.firstFree(folder, "sshot%03d.bmp").getName());
        assertEquals("shot1.png", AndroidScreenshots.firstFree(folder, "shot%d").getName());
    }
}
