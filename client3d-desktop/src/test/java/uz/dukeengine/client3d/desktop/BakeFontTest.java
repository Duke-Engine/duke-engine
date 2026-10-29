package uz.dukeengine.client3d.desktop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.dukeengine.client3d.BitmapFontBaker;

/** The tool writes the .fnt and a .png of exactly the atlas the baker made. */
class BakeFontTest {

    @TempDir
    Path out;

    @Test
    void writesTheFntAndThePngOfTheBakedAtlas() throws Exception {
        var face = new AwtGlyphs().system("SansSerif").at(24f, false, 1f, true);

        BakeFont.write(face, out, "sans");

        var baked = BitmapFontBaker.bake(face, "sans", BitmapFontBaker.DEFAULT_CHARACTERS);
        assertEquals(baked.fnt(), Files.readString(out.resolve("sans.fnt")));
        var png = ImageIO.read(out.resolve("sans.png").toFile());
        assertArrayEquals(baked.argb(), png.getRGB(0, 0, baked.width(), baked.height(), null, 0, baked.width()));
    }
}
