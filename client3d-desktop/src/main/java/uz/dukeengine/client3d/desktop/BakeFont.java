package uz.dukeengine.client3d.desktop;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import uz.dukeengine.client3d.BitmapFontBaker;
import uz.dukeengine.client3d.platform.Face;

/**
 * Bakes a TrueType font into the {@code .fnt} and {@code .png} jME draws with: {@link BitmapFontBaker} does the baking,
 * and this writes its two files.
 *
 * <pre>{@code
 * java -cp … uz.dukeengine.client3d.desktop.BakeFont Cinzel-Bold.ttf 32 out/ cinzel-32
 * }</pre>
 */
public final class BakeFont {

    private BakeFont() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: BakeFont <font.ttf> <size> <outDir> <name>");
            System.exit(2);
        }
        var typeface = new AwtGlyphs().load(Files.readAllBytes(Path.of(args[0])));
        if (typeface == null) {
            System.err.println("not a font: " + args[0]);
            System.exit(1);
        }
        var out = Path.of(args[2]);
        write(typeface.at(Float.parseFloat(args[1]), false, 1f, true), out, args[3]);
        System.out.println("wrote " + args[3] + ".fnt and " + args[3] + ".png to " + out);
    }

    /** The face baked, its {@code name.fnt} and {@code name.png} in {@code out}. */
    static void write(Face face, Path out, String name) throws IOException {
        Files.createDirectories(out);
        var baked = BitmapFontBaker.bake(face, name, BitmapFontBaker.DEFAULT_CHARACTERS);
        var atlas = new BufferedImage(baked.width(), baked.height(), BufferedImage.TYPE_INT_ARGB);
        atlas.setRGB(0, 0, baked.width(), baked.height(), baked.argb(), 0, baked.width());
        Files.writeString(out.resolve(name + ".fnt"), baked.fnt(), StandardCharsets.UTF_8);
        ImageIO.write(atlas, "png", out.resolve(name + ".png").toFile());
    }
}
