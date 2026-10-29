package uz.dukeengine.client3d.platform;

/** The window the client is drawn in: its size, its modes, and whether it fills the screen. */
public interface Window {

    /** Whether there is a window to ask. */
    boolean open();

    boolean fullscreen();

    /** Makes the window fill the screen or not; whether it could. */
    boolean fillTheScreen(boolean fill);

    /** Resizes the window, fullscreen or not; whether it could. */
    boolean resize(int w, int h, boolean fullscreen);

    /** The sizes the screen offers, as {width, height}. */
    java.util.List<int[]> modes();

    /** The size of the desktop, as {width, height}. */
    int[] desktopSize();
}
