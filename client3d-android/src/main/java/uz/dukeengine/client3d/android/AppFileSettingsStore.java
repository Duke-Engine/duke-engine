package uz.dukeengine.client3d.android;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import uz.dukeengine.client3d.platform.SettingsStore;

/** The settings as a file in the app's own files: the same bytes the desktop keeps beside the user. */
final class AppFileSettingsStore implements SettingsStore {

    private final File file;

    AppFileSettingsStore(File file) {
        this.file = file;
    }

    @Override
    public byte[] read() throws IOException {
        return file.exists() ? Files.readAllBytes(file.toPath()) : null;
    }

    @Override
    public void write(byte[] settings) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        Files.write(file.toPath(), settings);
    }
}
