package uz.dukeengine.core.module;

import java.util.List;
import java.util.Map;

/**
 * The armour a body wears while some words hold for its thing — an upgrade's, a crate's, a second life's. Chosen the
 * way a weapon set is ({@link uz.dukeengine.core.thing.Conditions#bestFit}): the set whose words all hold, the most
 * of them winning; a set that names none is worn when nothing more particular holds. The choice is made again each
 * blow, so a change of words changes the armour at once.
 *
 * @param conditions the words that must all hold for it
 * @param armor      its damage multipliers by type, as {@link Armor}'s
 */
public record ArmorSet(List<String> conditions, Map<DamageType, Float> armor) {

    public ArmorSet {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        armor = armor == null || armor.isEmpty() ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(armor));
    }
}
