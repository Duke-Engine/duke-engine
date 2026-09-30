package uz.dukeengine.combat.module;

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
 * @param conditions                 the words that must all hold for it
 * @param slots                      its weapons in order, the first the primary. The reference game has three;
 *                                   nothing here depends on how many
 * @param weaponLockSharedAcrossSets whether a unit whose set in use changes to this one keeps the lock its weapon had
 *                                   ({@link WeaponUpdate#lock}), and the slot locked, as they were — the reference's
 *                                   {@code WeaponLockSharedAcrossSets}, read on the set arrived at, not the one left
 *                                   ({@code WeaponSet::updateWeaponSet}); in the RTS this was measured in, 28 of 899
 *                                   sets say so, each "so similar to the default set that it can hold the weapon
 *                                   lock". No, the default, lets the lock go at the change
 */
public record WeaponSet(List<String> conditions, List<WeaponSlot> slots, boolean weaponLockSharedAcrossSets) {

    public WeaponSet {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /** A set a change to which lets the lock go, as every set did before one could keep it. */
    public WeaponSet(List<String> conditions, List<WeaponSlot> slots) {
        this(conditions, slots, false);
    }
}
