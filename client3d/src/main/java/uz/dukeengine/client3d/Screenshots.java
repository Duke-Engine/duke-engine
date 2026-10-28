package uz.dukeengine.client3d;

import com.jme3.app.state.ScreenshotAppState;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/** jME's screenshot state, telling the name of the file it saved once the picture is written. */
final class Screenshots extends ScreenshotAppState {

    private final Consumer<String> saved;

    Screenshots(String filePath, String fileName, Consumer<String> saved) {
        super(filePath, fileName);
        this.saved = saved;
    }

    @Override
    protected void writeImageFile(File file) throws IOException {
        super.writeImageFile(file);
        saved.accept(file.getName()); // not reached when the write failed, which jME logs
    }
}
