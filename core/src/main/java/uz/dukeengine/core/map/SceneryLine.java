package uz.dukeengine.core.map;

import java.util.Locale;

/**
 * One piece of scenery as a map file writes it, on a line of its own:
 * {@code model[@picture] x y facing scale footprint [0xRRGGBB]}, in cells.
 *
 * <ul>
 *   <li>{@code model@picture} — the model drawn with another picture in place of its own (a dark castle of the same
 *       pieces); the model alone draws its own.</li>
 *   <li>{@code footprint} — a number, how far round its middle it stands in the way; or {@code WxD}, a box {@code W}
 *       cells along its facing from its origin and {@code D} across it, centred: a wall.</li>
 *   <li>{@code 0xRRGGBB} — a colour multiplied over it; white where the line leaves it out, so every line written
 *       before it was read reads the same.</li>
 * </ul>
 */
public record SceneryLine(String model, String picture, float x, float y, float facing, float scale, float footprint,
        float boxLength, float boxDepth, int tint) implements MapScenery {

    public SceneryLine {
        picture = picture == null ? "" : picture;
        tint &= 0xFFFFFF;
    }

    /** A line read back: six words, or seven with a tint. */
    public static SceneryLine of(String written) {
        var words = written.strip().split("\\s+");
        if (words.length != 6 && words.length != 7) {
            throw new IllegalArgumentException("'" + written
                    + "' is a model[@picture], then x y facing scale footprint|WxD [0xRRGGBB], in cells");
        }
        int at = words[0].indexOf('@');
        String model = at < 0 ? words[0] : words[0].substring(0, at);
        String picture = at < 0 ? "" : words[0].substring(at + 1);
        float footprint = 0f;
        float length = 0f;
        float depth = 0f;
        int box = words[5].indexOf('x');
        if (box > 0) {
            length = Float.parseFloat(words[5].substring(0, box));
            depth = Float.parseFloat(words[5].substring(box + 1));
        } else {
            footprint = Float.parseFloat(words[5]);
        }
        int tint = 0xFFFFFF;
        if (words.length == 7) {
            var colour = words[6];
            if (!colour.startsWith("0x") && !colour.startsWith("0X")) {
                throw new IllegalArgumentException("'" + colour + "' is a tint, 0xRRGGBB");
            }
            tint = Integer.parseUnsignedInt(colour.substring(2), 16);
        }
        return new SceneryLine(model, picture, Float.parseFloat(words[1]), Float.parseFloat(words[2]),
                Float.parseFloat(words[3]), Float.parseFloat(words[4]), footprint, length, depth, tint);
    }

    /** As a map file writes it: the picture, the box and the tint only where they say something. */
    public String written() {
        var line = new StringBuilder(model);
        if (!picture.isEmpty()) {
            line.append('@').append(picture);
        }
        line.append(' ').append(x).append(' ').append(y).append(' ').append(facing).append(' ').append(scale)
                .append(' ');
        if (boxLength > 0f) {
            line.append(boxLength).append('x').append(boxDepth);
        } else {
            line.append(footprint);
        }
        if (tint != 0xFFFFFF) {
            line.append(' ').append(String.format(Locale.ROOT, "0x%06X", tint));
        }
        return line.toString();
    }
}
