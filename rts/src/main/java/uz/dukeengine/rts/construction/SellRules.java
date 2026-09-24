package uz.dukeengine.rts.construction;

/**
 * How a sold building comes down: the game's numbers, the reference's by default.
 *
 * @param scaffoldFrames  how long it stands, scaffolded, before it starts coming down — the reference's 45 frames
 * @param percentPerFrame how much of it comes down a frame, its construction share counting down from just under 100
 *     past 0 to -50 — the reference's 100/90
 * @param sellShare       what share of its cost comes back, where its template names no refund of its own — the
 *     reference's {@code SellPercentage}, 50%
 */
public record SellRules(int scaffoldFrames, float percentPerFrame, float sellShare) {

    /** The reference's, from {@code BuildAssistant} and {@code GameData.ini}. */
    public static final SellRules DEFAULT = new SellRules(45, 100f / 90f, 0.5f);

    public SellRules {
        scaffoldFrames = Math.max(0, scaffoldFrames);
        percentPerFrame = percentPerFrame > 0f ? percentPerFrame : 100f / 90f;
        sellShare = Math.max(0f, sellShare);
    }
}
