package uz.dukeengine.client3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import uz.dukeengine.core.content.EffectList;
import uz.dukeengine.core.content.Laser;
import uz.dukeengine.core.content.ParticleSystem;

/** What the game says its effects are made of, held for {@link Visuals}: recipes, systems, lists, lasers, budgets. */
final class EffectLooks {

    final Map<String, Visuals.EffectVisual> recipes = new LinkedHashMap<>();
    final Map<String, Float> seconds = new HashMap<>();
    final Map<String, Float> reach = new HashMap<>();
    int particleBudget;
    final Map<String, ParticleSystem> particleSystems = new LinkedHashMap<>();
    int particleSystemMost = Integer.MAX_VALUE;
    int particleSystemNeverRefusedFrom = Integer.MAX_VALUE;
    final Map<String, EffectList> lists = new LinkedHashMap<>();
    final Map<String, Laser> lasers = new LinkedHashMap<>();
    Visuals.EffectBudget budget = new Visuals.EffectBudget(4, 0f);
    private final Map<String, Visuals.HurtRule> hurtRules = new HashMap<>();

    /** A positive number kept for a recipe, by its name; anything else is left unsaid. */
    static void keep(Map<String, Float> into, String recipeName, float value) {
        if (recipeName != null && value > 0f) {
            into.put(recipeName, value);
        }
    }

    /** What was kept for a recipe, or 0. */
    static float kept(Map<String, Float> from, String recipeName) {
        var value = Visuals.named(from, recipeName);
        return value == null ? 0f : value;
    }

    void hurt(String template, String damageType, Visuals.HurtRule rule) {
        hurtRules.put(hurtKey(template, damageType), rule);
    }

    /** The rule for this blow: the most particular of template and type, then either alone, then neither. */
    Visuals.HurtRule hurtRule(String template, String damageType) {
        for (var key : new String[] {hurtKey(template, damageType), hurtKey(template, null),
            hurtKey(null, damageType), hurtKey(null, null)}) {
            var rule = hurtRules.get(key);
            if (rule != null) {
                return rule;
            }
        }
        return Visuals.HurtRule.NONE;
    }

    private static String hurtKey(String template, String damageType) {
        return (template == null ? "*" : template) + "|"
                + (damageType == null ? "*" : damageType.toUpperCase(java.util.Locale.ROOT));
    }

    List<String> laserTextures() {
        return lasers.values().stream().map(Laser::texture).filter(Objects::nonNull).distinct().toList();
    }

    List<String> debrisModels() {
        return lists.values().stream().flatMap(list -> list.entries().stream())
                .filter(EffectList.Debris.class::isInstance)
                .map(entry -> ((EffectList.Debris) entry).model())
                .filter(Objects::nonNull).distinct().toList();
    }

    List<String> scorchPictures() {
        return lists.values().stream().flatMap(list -> list.entries().stream())
                .filter(EffectList.Scorch.class::isInstance)
                .flatMap(entry -> ((EffectList.Scorch) entry).pictures().stream())
                .distinct().toList();
    }

    List<String> particleSystemTextures() {
        return particleSystems.values().stream().map(ParticleSystem::texture).filter(Objects::nonNull).distinct()
                .toList();
    }

    Collection<Visuals.EffectVisual> all() {
        return List.copyOf(recipes.values());
    }
}
