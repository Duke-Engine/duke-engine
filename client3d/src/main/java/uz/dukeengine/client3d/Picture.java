package uz.dukeengine.client3d;

/**
 * A picture the game makes itself and remakes as the match goes — a radar's map, its blips, its shroud — drawn on the
 * {@link Canvas} with {@link Canvas#drawPicture} as a file's picture is drawn with {@code drawImage}. The game writes
 * its pixels and says when it has ({@link #changed}); the card is handed them again only then, so a picture redrawn
 * every few frames costs one hand-over each time it changes and none when it has not.
 *
 * <p>Kept by the game and drawn from the window's thread, which is where its pixels are written too.
 */
public final class Picture {

    private final int width;
    private final int height;
    private final int[] argb;
    private int version;

    public Picture(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a picture of " + width + " by " + height);
        }
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Its pixels, {@code 0xAARRGGBB}, row after row from the top: write them, then say {@link #changed}. */
    public int[] argb() {
        return argb;
    }

    /** One pixel, from the top left. */
    public void set(int x, int y, int argb) {
        this.argb[y * width + x] = argb;
    }

    /** Its pixels were written: the next time it is drawn, the card is handed them. */
    public void changed() {
        version++;
    }

    /** A number that moves each time it is changed. */
    int version() {
        return version;
    }
}
