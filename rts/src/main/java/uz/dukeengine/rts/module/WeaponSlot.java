package uz.dukeengine.rts.module;

import java.util.List;
import uz.dukeengine.core.data.Link;
import uz.dukeengine.core.thing.Kind;

/**
 * One of a {@link WeaponSet}'s weapons, and how the unit is to choose it.
 *
 * @param weapon           the {@link Weapon} it carries, by name
 * @param preferredAgainst kinds it wins outright against, whatever another slot would deal — the reference
 *                         game's Comanche cannon, always used on infantry rather than its anti-tank missiles.
 *                         None, the default, is no preference
 * @param autoChoosable    whether the unit may pick it by itself, for what it acquires on its own. No keeps it
 *                         for targets it is ordered to attack. Yes by default
 */
public record WeaponSlot(@Link(Weapon.class) String weapon, List<Kind> preferredAgainst, boolean autoChoosable) {

    /** What a block leaves out: no preference, and chosen by the unit itself as freely as by an order. */
    static final WeaponSlot DEFAULTS = new WeaponSlot(null, List.of(), true);

    public WeaponSlot {
        preferredAgainst = preferredAgainst == null ? List.of() : List.copyOf(preferredAgainst);
    }

    /** A slot carrying {@code weapon}, with no preference, that the unit may pick by itself. */
    public WeaponSlot(String weapon) {
        this(weapon, List.of(), true);
    }
}
