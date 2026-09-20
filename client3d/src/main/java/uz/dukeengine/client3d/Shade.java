package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;

/**
 * What "the same colour, lit" means on the interface layer.
 *
 * <p>Three small sums, in one place because two things now need them to agree:
 * the bar at the bottom of the screen and the bars over everybody's heads are
 * the same gauge drawn twice, and a player who saw one of them shaded one way
 * and the other another would be looking at two games.
 *
 * <p><b>A gauge is not a coloured rectangle.</b> Every filled bar in the design
 * runs bright along its top edge, sits at its own colour through the middle and
 * falls away dark at the foot — which is a cylinder catching a light from above,
 * and it is the whole of why a bar reads as a THING rather than as a region of
 * screen that happens to be red. Flat fill is the single difference between a
 * mockup that looks made and a game that looks unfinished.
 */
final class Shade {

    private Shade() {
    }

    /** How much of the way to white the top of a gauge is. */
    static final float LIT = 0.22f;
    /** And how far towards black its foot falls. */
    static final float FOOT = 0.30f;

    /**
     * The same colour, ready to go into a vertex buffer.
     *
     * <p>The client renders into an sRGB frame buffer, and a material's colour is
     * converted on its way to the shader — but a colour written straight into a
     * vertex buffer is not. Written raw it comes out pale, and by exactly the
     * amount that makes a carefully chosen palette look washed rather than wrong.
     */
    static ColorRGBA linear(ColorRGBA colour) {
        return new ColorRGBA(toLinear(colour.r), toLinear(colour.g), toLinear(colour.b),
                colour.a);
    }

    private static float toLinear(float channel) {
        return channel <= 0.04045f
                ? channel / 12.92f
                : (float) Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    static ColorRGBA lighter(ColorRGBA colour, float towardsWhite) {
        return new ColorRGBA(
                colour.r + (1f - colour.r) * towardsWhite,
                colour.g + (1f - colour.g) * towardsWhite,
                colour.b + (1f - colour.b) * towardsWhite, colour.a);
    }

    static ColorRGBA darker(ColorRGBA colour, float towardsBlack) {
        float keep = 1f - towardsBlack;
        return new ColorRGBA(colour.r * keep, colour.g * keep, colour.b * keep, colour.a);
    }

    /** The three stops a gauge of this colour is drawn with, top to bottom. */
    static ColorRGBA[] gauge(ColorRGBA colour) {
        return new ColorRGBA[] {lighter(colour, LIT), colour, darker(colour, FOOT)};
    }
}
