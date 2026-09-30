package uz.dukeengine.combat.module;

import java.util.List;
import uz.dukeengine.core.data.Link;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.combat.message.OrderSource;

/**
 * One of a {@link WeaponSet}'s weapons, and how the unit is to choose it.
 *
 * @param weapon            the {@link Weapon} it carries, by name
 * @param preferredAgainst  kinds it wins outright against, whatever another slot would deal — the reference
 *                          game's Comanche cannon, always used on infantry rather than its anti-tank missiles.
 *                          None, the default, is no preference
 * @param autoChoosable     whether the unit may pick it by itself, for what it acquires on its own. No keeps it
 *                          for targets it is ordered to attack. Yes by default
 * @param autoChooseSources the sources whose orders may pick it — the reference's {@code AutoChooseSources}: a
 *                          player's, the game's (a unit's own look among them), or {@code NONE}, a slot fired only
 *                          while the weapon is locked to it ({@link WeaponUpdate#lock}). None named, the default,
 *                          is every source
 */
public record WeaponSlot(@Link(Weapon.class) String weapon, List<Kind> preferredAgainst, boolean autoChoosable,
        List<OrderSource> autoChooseSources) {

    /** What a block leaves out: no preference, and chosen by the unit itself as freely as by an order. */
    static final WeaponSlot DEFAULTS = new WeaponSlot(null, List.of(), true, List.of());

    public WeaponSlot {
        preferredAgainst = preferredAgainst == null ? List.of() : List.copyOf(preferredAgainst);
        autoChooseSources = autoChooseSources == null ? List.of() : List.copyOf(autoChooseSources);
    }

    /** A slot any source may pick: every slot from before one could name its sources. */
    public WeaponSlot(String weapon, List<Kind> preferredAgainst, boolean autoChoosable) {
        this(weapon, preferredAgainst, autoChoosable, List.of());
    }

    /** A slot carrying {@code weapon}, with no preference, that the unit may pick by itself. */
    public WeaponSlot(String weapon) {
        this(weapon, List.of(), true);
    }

    /** Whether an order of {@code source}'s may pick it: every source where it names none. */
    boolean pickedBy(OrderSource source) {
        return source != OrderSource.NONE && (autoChooseSources.isEmpty() || autoChooseSources.contains(source));
    }
}
