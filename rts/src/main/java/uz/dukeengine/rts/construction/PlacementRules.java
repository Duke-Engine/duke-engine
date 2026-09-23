package uz.dukeengine.rts.construction;

/**
 * What a game says about where its buildings may stand and what building them costs — as numbers, because
 * each is a decision one game made and another will make differently.
 *
 * @param maxRise     how far the ground under a footprint may rise and fall, in world units, before the
 *                    place is too steep to build on. One RTS measured says 10
 * @param edgeMargin  how near the edge of the map a footprint may come, in world units. The same RTS says 30
 * @param refundShare what share of the cost comes back when a site is cancelled before it is finished. Half,
 *                    there
 * @param startShare  what share of its health a site has the frame it rises, growing to whole as it is built
 */
public record PlacementRules(float maxRise, float edgeMargin, float refundShare, float startShare) {

    /**
     * For a game that says nothing: any slope, right up to the edge, half back on a cancel, and a site that
     * rises at a tenth of its health — the one choice here with nothing to recommend it but that it is the
     * thing a site looks like, standing but frail.
     */
    public static final PlacementRules DEFAULTS = new PlacementRules(Float.MAX_VALUE, 0f, 0.5f, 0.1f);

    public PlacementRules {
        if (maxRise < 0f || edgeMargin < 0f || refundShare < 0f || refundShare > 1f
                || startShare <= 0f || startShare > 1f) {
            throw new IllegalArgumentException("placement rules out of range: " + maxRise + ", " + edgeMargin
                    + ", " + refundShare + ", " + startShare);
        }
    }
}
