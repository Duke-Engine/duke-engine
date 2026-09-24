package uz.dukeengine.client3d;

/**
 * The game's own drawing, done every frame the window draws: over the world and the client's HUD, with no world at
 * all while its front end is up, and over whatever runs behind it. On the window's thread, so it may read whatever
 * the game keeps for its screens without a lock.
 */
@FunctionalInterface
public interface Painter {

    void paint(Canvas canvas);

    /** The screen is now this size — once before the first frame is painted, and whenever the window changes. */
    default void resized(int width, int height) {
    }
}
