package uz.dukeengine.core.content;

/**
 * How a beam the simulation owns is drawn ({@code World.beam}) — the reference's {@code W3DLaserDraw}: {@code
 * numBeams} lines laid over one another, the innermost {@code innerBeamWidth} wide, the outermost {@code
 * outerBeamWidth}, each between them its share of the way from one to the other, their colours worked out from
 * {@code innerColour} and {@code outerColour} as the reference works them, all added to what is behind them. A
 * picture along them — tiled so it is not stretched ({@code tile}, {@code tilingScalar} as written, the reference's
 * floor under it compiled out: {@code I_WANT_TO_BE_FIRED}), and scrolled {@code
 * scrollRate} of its length a second — and the line laid in {@code segments} along an arc {@code arcHeight} high in
 * its middle, each segment reaching {@code segmentOverlapRatio} of the line into the next. Every width is times the
 * share of its width the simulation gives the beam, 0 to 1. Drawing only.
 *
 * @param innerColour packed {@code 0xAARRGGBB}
 * @param outerColour packed {@code 0xAARRGGBB}
 * @param texture     a whole path from the resource root, or none for colour alone
 */
public record Laser(String name, int numBeams, float innerBeamWidth, float outerBeamWidth, int innerColour,
        int outerColour, String texture, float scrollRate, boolean tile, float tilingScalar, int segments,
        float arcHeight, float segmentOverlapRatio) {

    static final Laser DEFAULTS = new Laser(null, 1, 1f, 1f, 0xFFFFFFFF, 0xFFFFFFFF, null, 0f, false, 1f, 1, 0f, 0f);

    public Laser {
        numBeams = Math.max(1, numBeams);
        segments = Math.max(1, segments);
    }
}
