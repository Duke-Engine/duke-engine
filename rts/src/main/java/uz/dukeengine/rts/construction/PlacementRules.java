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
 * @param words       the words a site holds while it goes up, and a sold building while it comes down
 * @param siteAtOrder whether a site is put down the moment its order is taken — the reference's {@code
 *                    DozerAIUpdate::construct}: standing, seen, able to be shot and in the way from then, awaiting its
 *                    builder, who then goes to it; a builder that gives up leaves it standing. Otherwise it is put
 *                    down when its builder arrives, and a builder that gives up on the way has the money back
 */
public record PlacementRules(float maxRise, float edgeMargin, float refundShare, float startShare, SiteWords words,
        boolean siteAtOrder, java.util.Set<uz.dukeengine.core.thing.Kind> standsOver) {

    /**
     * The words a site holds while it goes up — the reference's {@code AWAITING_CONSTRUCTION}, {@code
     * PARTIALLY_CONSTRUCTED} and {@code ACTIVELY_BEING_CONSTRUCTED}, which its looks are chosen by: {@code awaiting}
     * until a builder first works on it, {@code partlyBuilt} from then until it is finished, {@code beingBuilt} on the
     * frames a builder works on it; none once it is finished. A building being sold holds the last two while it comes
     * down, as the reference's does. A word left out is not held.
     */
    public record SiteWords(String awaiting, String partlyBuilt, String beingBuilt) {

        /** For a game that names none. */
        public static final SiteWords NONE = new SiteWords(null, null, null);
    }

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
        words = words == null ? SiteWords.NONE : words;
        standsOver = standsOver == null ? java.util.Set.of() : java.util.Set.copyOf(standsOver);
    }

    /** Rules whose sites stand over nothing that does not move: every rule from before a site could. */
    public PlacementRules(float maxRise, float edgeMargin, float refundShare, float startShare, SiteWords words,
            boolean siteAtOrder) {
        this(maxRise, edgeMargin, refundShare, startShare, words, siteAtOrder, java.util.Set.of());
    }

    /** Rules whose sites hold no words, as every site did before they could. */
    public PlacementRules(float maxRise, float edgeMargin, float refundShare, float startShare) {
        this(maxRise, edgeMargin, refundShare, startShare, null);
    }

    /** Rules whose sites are put down when their builder arrives, as every site was before they could be earlier. */
    public PlacementRules(float maxRise, float edgeMargin, float refundShare, float startShare, SiteWords words) {
        this(maxRise, edgeMargin, refundShare, startShare, words, false);
    }

    /** The same rules, with sites put down the moment their order is taken, or not. */
    public PlacementRules siteAtOrder(boolean atOrder) {
        return new PlacementRules(maxRise, edgeMargin, refundShare, startShare, words, atOrder, standsOver);
    }

    /**
     * The same rules, a site standing over the still things of {@code kinds} — the reference's shrubbery, things
     * cleared by a build, mines and inert things ({@code BuildAssistant::isRemovableForConstruction}): not in its way.
     * What a site stands over the game clears as it is put down ({@code RtsSimulation.onPlaced}).
     */
    public PlacementRules standsOver(java.util.Set<uz.dukeengine.core.thing.Kind> kinds) {
        return new PlacementRules(maxRise, edgeMargin, refundShare, startShare, words, siteAtOrder, kinds);
    }
}
