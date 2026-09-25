package uz.dukeengine.game.view;

import java.util.List;

/**
 * Words for a thing's moments, which the snapshot adds to the words it holds while they last, so its looks choose
 * their model, clip and pieces by them as by any other — the reference's model condition flags ({@code
 * ModelConditionFlags}: MOVING, ATTACKING, FIRING_A, BETWEEN_FIRING_SHOTS_A, RELOADING_A, TURRET_ROTATE). A word left
 * null, or a slot left out, is not said; nothing the simulation decides reads them.
 *
 * @param moving        while it moves
 * @param attacking     while its weapon has a target
 * @param firing        for each weapon slot of the set in use, by its index, on the frame it fires
 * @param betweenShots  for each slot, while it waits between shots
 * @param reloading     for each slot, while it refills its clip
 * @param turretTurning while its turret turns, as its game's {@code Turret} says: the frames its turn changed
 */
public record MomentWords(String moving, String attacking, List<String> firing, List<String> betweenShots,
        List<String> reloading, String turretTurning) {
    public MomentWords {
        firing = firing == null ? List.of() : List.copyOf(firing);
        betweenShots = betweenShots == null ? List.of() : List.copyOf(betweenShots);
        reloading = reloading == null ? List.of() : List.copyOf(reloading);
    }

    /** The word of slot {@code slot} of {@code words}, or null for none. */
    public static String of(List<String> words, int slot) {
        return slot >= 0 && slot < words.size() ? words.get(slot) : null;
    }
}
