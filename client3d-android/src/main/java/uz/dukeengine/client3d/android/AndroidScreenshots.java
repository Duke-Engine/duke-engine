package uz.dukeengine.client3d.android;

import com.jme3.app.state.ScreenshotAppState;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;
import uz.dukeengine.client3d.platform.ClientPlatform;

/**
 * jME's screenshot state, writing into the app's own files and telling the name of each file once it is written —
 * under jME's own name, or the game's pattern at its first free number.
 */
final class AndroidScreenshots extends ScreenshotAppState {

    private final File folder;
    private final String pattern;
    private final Consumer<String> saved;

    AndroidScreenshots(File folder, String pattern, Consumer<String> saved) {
        super(folder.getPath() + File.separator, "screenshot");
        this.folder = folder;
        this.pattern = pattern == null ? null : ClientPlatform.checkedScreenshotName(pattern);
        this.saved = saved;
    }

    @Override
    protected void writeImageFile(File file) throws IOException {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("could not make the screenshots' folder " + folder);
        }
        var target = pattern == null ? file : firstFree(folder, pattern);
        super.writeImageFile(target);
        saved.accept(target.getName()); // not reached when the write failed, which jME logs
    }

    /**
     * The first file of {@code pattern} in {@code folder} not there yet, counting from 1, as the desktop seeks one.
     * shortcut: always a png, the one format jME writes here (a pattern naming a bmp gets a png); re-encode when a game
     * needs another.
     */
    static File firstFree(File folder, String pattern) {
        for (int number = 1; ; number++) {
            var name = String.format(Locale.ROOT, pattern, number);
            int dot = name.lastIndexOf('.');
            var file = new File(folder, (dot < 0 ? name : name.substring(0, dot)) + ".png");
            if (!file.exists()) {
                return file;
            }
        }
    }
}
