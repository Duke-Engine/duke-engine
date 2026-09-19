package uz.duke.dungeon.world;

/**
 * How the creatures of every kind fight and move, where no one kind's block has a say.
 *
 * @param closeDistance      how close a fighter walks before stopping to let its weapon work —
 *     shorter than any weapon's reach on purpose
 * @param wayAheadProbe      how far ahead a walking thing looks for a body; see {@code WayAhead}
 * @param retreatTurnDegrees how much further aside each try turns when a monster that keeps its
 *     distance backs away and straight back is stone
 * @param retreatTurns       and how many tries it makes each side of straight back before it is
 *     cornered
 * @param summonTurnDegrees  how much further aside each try at a spot for what a monster calls up;
 *     see {@code Summoning}
 * @param summonTurns        and how many tries each way before it has run out of spots
 * @param arrowTemplate      the creature an archer's shot becomes once it is in the air
 * @param arrowSpeed         how fast it travels, in world units per second
 * @param arrowMuzzleOffset  how far in front of an archer his arrow appears — the bow, not his chest
 */
public record Combat(float skeletonSenseRadius, float skeletonChaseRadius, int skeletonRepathFrames,
        float closeDistance, int heroRepathFrames, float wayAheadProbe, float retreatTurnDegrees,
        int retreatTurns, float summonTurnDegrees, int summonTurns, String arrowTemplate, float arrowSpeed,
        float arrowMuzzleOffset) {

    /** What a block leaves out. */
    public static final Combat DEFAULTS = new Combat(90f, 150f, 10, 4f, 10, 5f, 30f, 3, 45f, 4, "Arrow",
            260f, 5f);
}
