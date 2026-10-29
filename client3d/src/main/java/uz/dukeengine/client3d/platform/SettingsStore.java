package uz.dukeengine.client3d.platform;

/** The player's settings as one block of bytes the client reads and writes whole. */
public interface SettingsStore {

    /** What was last written, or {@code null} when nothing was. */
    byte[] read() throws java.io.IOException;

    void write(byte[] settings) throws java.io.IOException;
}
