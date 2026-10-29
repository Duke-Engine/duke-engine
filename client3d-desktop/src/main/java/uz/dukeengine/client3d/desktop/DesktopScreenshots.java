package uz.dukeengine.client3d.desktop;

import com.jme3.app.state.ScreenshotAppState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import uz.dukeengine.client3d.platform.ClientPlatform;

/**
 * jME's screenshot state, telling the name of the file it saved once the picture is written — under jME's own name, or
 * where the game says ({@code Duke3D.screenshots}).
 */
final class DesktopScreenshots extends ScreenshotAppState {

    private final Consumer<String> saved;
    /** The folder the game's pictures go in and the name they are written under, or null for jME's own. */
    private final Path folder;
    private final String pattern;

    /** With a {@code pattern}, pictures go in {@code folder} at its first free number; without, jME's own way. */
    DesktopScreenshots(String folder, String pattern, Consumer<String> saved) {
        super(pattern == null && folder != null ? folder : "", "screenshot");
        this.pattern = pattern == null ? null : ClientPlatform.checkedScreenshotName(pattern);
        this.folder = pattern == null ? null : Path.of(folder);
        this.saved = saved;
    }

    @Override
    protected void writeImageFile(File file) throws IOException {
        var named = pattern;
        var target = named == null ? file : firstFree(folder, named).toFile();
        if (named != null) {
            Files.createDirectories(folder);
        }
        var format = format(target.getName());
        if (format.equals("png")) {
            super.writeImageFile(target);
        } else {
            // jME writes only its own png, with alpha, which a bmp cannot hold: read back, flattened, and written again.
            var png = Files.createTempFile("screenshot", ".png").toFile();
            try {
                super.writeImageFile(png);
                var read = javax.imageio.ImageIO.read(png);
                var flat = new java.awt.image.BufferedImage(read.getWidth(), read.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_RGB);
                flat.createGraphics().drawImage(read, 0, 0, null);
                if (!javax.imageio.ImageIO.write(flat, format, target)) {
                    throw new IOException("no way to write a " + format + " picture: " + target);
                }
            } finally {
                Files.deleteIfExists(png.toPath());
            }
        }
        saved.accept(target.getName()); // not reached when the write failed, which jME logs
    }

    /**
     * The first file of {@code pattern} in {@code folder} not there yet, counting from 1 — the reference's {@code
     * sshot%.3d.bmp} in its user data folder, as {@code W3DDisplay::takeScreenShot} seeks one.
     */
    static Path firstFree(Path folder, String pattern) {
        for (int number = 1; ; number++) {
            var file = folder.resolve(String.format(Locale.ROOT, pattern, number));
            if (!Files.exists(file)) {
                return file;
            }
        }
    }

    /** The picture format a file's name asks for: its extension, png where it has none. */
    static String format(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "png" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
