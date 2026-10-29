package uz.dukeengine.game;

/**
 * A display colour as one ARGB int ({@code 0xAARRGGBB}), the same packing as AWT's {@code Color.getRGB()}, so the game
 * API names no desktop toolkit. The named ones match AWT's of the same name.
 */
public record Colour(int argb) {

    public static final Colour WHITE = rgb(255, 255, 255);
    public static final Colour LIGHT_GRAY = rgb(192, 192, 192);
    public static final Colour GRAY = rgb(128, 128, 128);
    public static final Colour DARK_GRAY = rgb(64, 64, 64);
    public static final Colour BLACK = rgb(0, 0, 0);
    public static final Colour RED = rgb(255, 0, 0);
    public static final Colour PINK = rgb(255, 175, 175);
    public static final Colour ORANGE = rgb(255, 200, 0);
    public static final Colour YELLOW = rgb(255, 255, 0);
    public static final Colour GREEN = rgb(0, 255, 0);
    public static final Colour MAGENTA = rgb(255, 0, 255);
    public static final Colour CYAN = rgb(0, 255, 255);
    public static final Colour BLUE = rgb(0, 0, 255);

    /** An opaque colour; each channel 0 to 255. */
    public static Colour rgb(int red, int green, int blue) {
        return rgba(red, green, blue, 255);
    }

    /** Each channel 0 to 255. */
    public static Colour rgba(int red, int green, int blue, int alpha) {
        return new Colour((channel(alpha) << 24) | (channel(red) << 16) | (channel(green) << 8) | channel(blue));
    }

    public int red() {
        return (argb >>> 16) & 0xFF;
    }

    public int green() {
        return (argb >>> 8) & 0xFF;
    }

    public int blue() {
        return argb & 0xFF;
    }

    public int alpha() {
        return argb >>> 24;
    }

    private static int channel(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("a colour channel is 0 to 255, not " + value);
        }
        return value;
    }
}
