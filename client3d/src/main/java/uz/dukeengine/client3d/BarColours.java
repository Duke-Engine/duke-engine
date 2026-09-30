package uz.dukeengine.client3d;

import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.view.UnitView;

/**
 * The colours of the plain bar over a thing — {@link UnitBarLook.Plain} — chosen per bar from the thing as the client
 * sees it: its health share, its words and its statuses. The game's to give with {@code Visuals.barColours}; the
 * reference's is {@link #REFERENCE}.
 */
@FunctionalInterface
public interface BarColours {

    /** A bar's fill and outline, each {@code 0xRRGGBB}. */
    record Colours(int fill, int outline) {
    }

    /** The colours of {@code thing}'s bar, or null for no bar over it: a fence's. */
    Colours of(UnitView thing);

    /**
     * The reference's ({@code Drawable::drawHealthBar}): green through yellow to red by the health share, the outline
     * half of it, and the fill pulled halfway to green while unhurt (above 70%) and halfway to red once badly hurt (35%
     * and below); blue to cyan while it is going up or disabled.
     */
    BarColours REFERENCE = thing -> {
        float share = Math.clamp(thing.healthFraction(), 0f, 1f);
        if (thing.has(ObjectStatus.UNDER_CONSTRUCTION) || thing.has(ObjectStatus.DISABLED)) {
            return new Colours(rgb(0f, share, 1f), rgb(0f, share * 128f / 255f, 128f / 255f));
        }
        float red = share >= 0.5f ? 2f - 2f * share : 1f;
        float green = share >= 0.5f ? 1f : 2f * share;
        int outline = rgb(red * 0.5f, green * 0.5f, 0f);
        if (share <= 0.35f) {
            red = (1f + red) / 2f;
            green /= 2f;
        } else if (share > 0.7f) {
            green = (1f + green) / 2f;
            red /= 2f;
        }
        return new Colours(rgb(red, green, 0f), outline);
    };

    /** Shares of 1 packed as the reference's {@code GameMakeColor} packs them: 255 times each, cut down to a whole. */
    private static int rgb(float red, float green, float blue) {
        return (int) (255f * red) << 16 | (int) (255f * green) << 8 | (int) (255f * blue);
    }
}
