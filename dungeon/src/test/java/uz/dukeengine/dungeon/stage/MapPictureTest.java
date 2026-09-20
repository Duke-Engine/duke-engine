package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The picture beside a map: the floor from above, and what stands on it where it stands. */
class MapPictureTest {

    private static final String DRAWN = """
            StaticMap
              Name = drawn
              Entrance = [2, 2]
              Cells = [
                "##########",
                "#00000000#",
                "#00000000#",
                "#00000000#",
                "#00000000#",
                "##########",
              ]
              Boss = Warden 7 4
            End
            """;

    @Test
    void theFloorIsDrawnFromAboveASquareACell() {
        var image = MapPicture.of(StageFile.read(DRAWN, "drawn"));

        // Ten cells across at 51 pixels each: a cell is whole pixels, so the picture comes out under 512.
        assertEquals(510, image.getWidth());
        assertEquals(306, image.getHeight());
        assertEquals(new Color(28, 28, 32).getRGB(), image.getRGB(5, 5), "the border is rock");
        assertNotEquals(image.getRGB(5, 5), image.getRGB(255, 153), "and the floor inside it is not");
        assertEquals(new Color(90, 170, 255).getRGB(), image.getRGB(2 * 51 + 25, 2 * 51 + 25),
                "the way in is marked where it stands");
    }

    @Test
    void thePictureIsWrittenIntoTheMapsOwnFolder(@TempDir Path folder) throws IOException {
        var path = MapPicture.write(StageFile.read(DRAWN, "drawn"), folder);

        assertEquals(folder.resolve("preview.png"), path);
        assertTrue(Files.size(path) > 0, "a picture with nothing in it is a picture nobody can show");
        assertEquals(510, ImageIO.read(path.toFile()).getWidth());
    }
}
