package uz.dukeengine.client3d;

/**
 * A movie a game plays: its pictures one after another at a steady rate, and its sound, if it has one, started with
 * the first picture — the sound keeping the time, so picture and sound stay together. Stretched over the whole window,
 * or into a rectangle of it, over the world and the HUD and under the game's canvas, so the game can draw over it.
 *
 * <p>Plain on purpose. A game converts its own movies, whatever they were, into what this reads: a zip of pictures
 * (JPEG, or anything the machine's image reader reads) in the order they are stored, and a sound file.
 *
 * @param frames          the zip of pictures, by its whole path from the resource root
 * @param framesPerSecond how many pictures a second
 * @param sound           the sound to play with it, a whole path, or null for a silent movie
 * @param holdLastFrame   whether it stays on its last picture once it has shown them all, until it is stopped, rather
 *     than going
 * @param x               the rectangle it is drawn in, in screen pixels from the top left — width or height 0 for the
 *     whole window
 */
public record Movie(String frames, float framesPerSecond, String sound, boolean holdLastFrame,
        int x, int y, int width, int height) {

    public Movie {
        framesPerSecond = framesPerSecond > 0f ? framesPerSecond : 30f;
    }

    /** A silent movie over the whole window, that goes when it has shown its last picture. */
    public static Movie of(String frames, float framesPerSecond) {
        return new Movie(frames, framesPerSecond, null, false, 0, 0, 0, 0);
    }

    /** With a sound, which keeps its time. */
    public Movie withSound(String path) {
        return new Movie(frames, framesPerSecond, path, holdLastFrame, x, y, width, height);
    }

    /** Staying on its last picture until it is stopped. */
    public Movie holdingItsLastFrame() {
        return new Movie(frames, framesPerSecond, sound, true, x, y, width, height);
    }

    /** Drawn into this rectangle rather than over the whole window. */
    public Movie into(int x, int y, int width, int height) {
        return new Movie(frames, framesPerSecond, sound, holdLastFrame, x, y, width, height);
    }

    /** Whether it is drawn over the whole window. */
    public boolean wholeWindow() {
        return width <= 0 || height <= 0;
    }
}
