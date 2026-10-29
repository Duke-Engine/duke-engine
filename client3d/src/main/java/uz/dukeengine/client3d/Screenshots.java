package uz.dukeengine.client3d;

import com.jme3.app.state.ScreenshotAppState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * jME's screenshot state, telling the name of the file it saved once the picture is written — under jME's own name, or
 * where the game says ({@link Duke3D#screenshots}).
 */
final class Screenshots extends ScreenshotAppState {

    private final Consumer<String> saved;
    /** The folder the game's pictures go in and the name they are written under, or null for jME's own. */
    private volatile Path folder;
    private volatile String pattern;

    Screenshots(String filePath, String fileName, Consumer<String> saved) {
        super(filePath, fileName);
        this.saved = saved;
    }

    /** Each picture from now on written in {@code folder}, at the first free number of {@code pattern}. */
    void saveAs(Path folder, String pattern) {
        this.pattern = checked(pattern);
        this.folder = folder;
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

    /** {@code pattern}, where it names each number apart — {@code sshot%03d.bmp} — or an error for one that does not. */
    static String checked(String pattern) {
        if (pattern == null || String.format(Locale.ROOT, pattern, 1).equals(String.format(Locale.ROOT, pattern, 2))) {
            throw new IllegalArgumentException("a screenshot's name needs its number, as sshot%03d.bmp: " + pattern);
        }
        return pattern;
    }

    /** The picture format a file's name asks for: its extension, png where it has none. */
    static String format(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "png" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
