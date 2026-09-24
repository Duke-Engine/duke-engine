package uz.dukeengine.client3d;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import uz.dukeengine.core.event.ObjectHurt;

/**
 * What a blow shows, by name: {@code hurt.<template>.<damage type>.<major|minor>} — the damage type lower-cased,
 * major where the blow was worth at least the game's threshold for that template and damage type — or nothing,
 * where the same thing showed a blow of the same type too lately. The reference's {@code DamageFX}: a major and a
 * minor look per damage type, and a throttle per damage type, counted per victim.
 *
 * <p>The name falls back by the dotted rule, as {@code died.*} does: a game that names only {@code hurt.Tank}
 * shows it for every blow a tank takes, and one that names {@code hurt.Tank.small_arms.major} shows that for the
 * heavy ones of that kind.
 */
final class HurtMoments {

    private final BiFunction<String, String, Visuals.HurtRule> rules;
    /** The game frame each victim last showed a blow of each damage type, by victim and type. */
    private final Map<String, Integer> lastShown = new HashMap<>();

    /** @param rules the game's rule for a template and a damage type's name */
    HurtMoments(BiFunction<String, String, Visuals.HurtRule> rules) {
        this.rules = rules;
    }

    /**
     * The moment this blow shows, or {@code null} for one throttled — the same victim showed one of the same type
     * fewer than the rule's frames ago.
     */
    String nameFor(ObjectHurt hurt) {
        var type = hurt.damageType().name();
        var rule = rules.apply(hurt.templateName(), type);
        var key = hurt.object().value() + "|" + type;
        var last = lastShown.get(key);
        // ActiveBody::doDamageFX: shown when the next allowed frame has come, and the next is now plus the throttle.
        if (last != null && hurt.frame() - last < rule.throttleFrames()) {
            return null;
        }
        lastShown.put(key, hurt.frame());
        return "hurt." + hurt.templateName() + "." + type.toLowerCase(Locale.ROOT) + "."
                + (hurt.amount() >= rule.majorAt() ? "major" : "minor");
    }

    /** A victim gone: what it last showed is forgotten. */
    void forget(int victim) {
        lastShown.keySet().removeIf(key -> key.startsWith(victim + "|"));
    }
}
