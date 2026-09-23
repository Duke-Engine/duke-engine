package uz.dukeengine.rts.module;

import java.util.List;

/**
 * The weapons a unit carries while some words hold for it — an upgrade bought, a car bomb fitted, a rank.
 * In the RTS this was measured in, 215 of 556 armed objects have more than one set, and 26 sets name two
 * conditions at once.
 *
 * <p>The set in use is the one whose conditions the unit has, the most of them winning, ties going to the
 * words sorted — the rule a template's conditional models are chosen by too
 * ({@link uz.dukeengine.core.thing.Conditions#bestFit}). A set that names no conditions is the one used
 * when nothing more particular holds.
 *
 * @param conditions the words that must all hold for it
 * @param slots      its weapons in order, the first the primary. The reference game has three; nothing here
 *                   depends on how many
 */
public record WeaponSet(List<String> conditions, List<WeaponSlot> slots) {

    public WeaponSet {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        slots = slots == null ? List.of() : List.copyOf(slots);
    }
}
