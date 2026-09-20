package uz.dukeengine.rts.module;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;

/**
 * Tracks a unit's combat experience and the rank it has earned.
 *
 * <p>A unit accrues experience by destroying enemies — each is "worth" some
 * {@link #getExperienceValue() experience value} to its killer — and climbs a
 * ladder of {@link Rank}s as it crosses their thresholds.
 *
 * <p>The ladder is <b>data</b>. It used to be four rungs named after Generals'
 * ranks with their multipliers written into an enum, which is one game's answer
 * and not a mechanism: a Warcraft hero climbs ten levels, a BFME battalion three,
 * and a game with no veterancy at all wants none. How many rungs there are, what
 * each costs and what each is worth all come from the unit's own definition, so
 * every one of those is the same module with a different table.
 *
 * <p>A rung whose threshold is zero or less is unreachable, which is how a game
 * says "count the experience but never promote" — useful when the counting is
 * wanted and the ranks are the game's own business.
 *
 * <p>Holds no per-frame behaviour, so it is a plain {@link Module}, not an
 * {@link UpdateModule}. It implements {@link DamageModifier}, which is how a rank
 * reaches the weapon: one modifier among whatever else the unit carries, rather
 * than a case inside the weapon itself.
 */
@ModuleGroup({RtsModuleGroups.PROGRESSION, ModuleGroups.COMBAT})
public class ExperienceModule extends Module implements DamageModifier {

    /** One rung: what it costs to reach, and what reaching it is worth. */
    public record Rank(int experience, float damageMultiplier) {
    }

    /**
     * What this unit is worth when killed, the ladder it climbs, and whether being promoted
     * heals it. The ladder is written as SAGE writes it, a list of costs and one of bonuses:
     * {@code ExperienceRequired = [60, 180, 360]}, {@code LevelDamageBonus = [1.1, 1.2, 1.3]}. A
     * ladder may name its costs and say nothing of bonuses; its rungs are then worth reaching only
     * for whatever the game hangs off the rank itself.
     */
    public record Data(int experienceValue, List<Integer> experienceRequired, List<Float> levelDamageBonus,
            boolean healOnPromotion) implements ModuleData {

        public Data {
            experienceRequired = experienceRequired == null ? List.of() : List.copyOf(experienceRequired);
            levelDamageBonus = levelDamageBonus == null ? List.of() : List.copyOf(levelDamageBonus);
            if (levelDamageBonus.size() > experienceRequired.size()) {
                throw new IllegalArgumentException("LevelDamageBonus names more rungs than ExperienceRequired");
            }
        }

        public Data(int experienceValue, List<Rank> ranks, boolean healOnPromotion) {
            this(experienceValue, ranks.stream().map(Rank::experience).toList(),
                    ranks.stream().map(Rank::damageMultiplier).toList(), healOnPromotion);
        }

        /** The ladder, one rung per cost. */
        public List<Rank> ranks() {
            var ranks = new ArrayList<Rank>(experienceRequired.size());
            for (int i = 0; i < experienceRequired.size(); i++) {
                ranks.add(new Rank(experienceRequired.get(i), i < levelDamageBonus.size() ? levelDamageBonus.get(i) : 1f));
            }
            return List.copyOf(ranks);
        }

        /** A ladder of thresholds that carry no combat bonus. */
        public static Data ofThresholds(int experienceValue, int... thresholds) {
            var ranks = new ArrayList<Rank>(thresholds.length);
            for (int threshold : thresholds) {
                ranks.add(new Rank(threshold, 1f));
            }
            return new Data(experienceValue, ranks, false);
        }
    }

    protected final Data data;
    private final List<Rank> ranks;
    private int experience;
    private int level; // 0 = unranked; 1..n index into the ladder

    public ExperienceModule(GameObject owner, Data data) {
        super(owner);
        this.data = data;
        this.ranks = data.ranks();
    }

    /** Grant experience and re-evaluate rank. Negative amounts are ignored. */
    public void addExperience(int amount) {
        if (amount <= 0) {
            return;
        }
        experience += amount;
        int earned = levelFor(experience);
        if (earned != level) {
            level = earned;
            onPromoted();
        }
    }

    /**
     * What promotion does beyond the rank itself.
     *
     * <p>Healing on promotion is Generals' rule, not every game's — a hero who
     * levels mid-fight and is restored to full is a very different game from one
     * who is not — so it is asked for in the data rather than assumed here.
     */
    protected void onPromoted() {
        if (!data.healOnPromotion()) {
            return;
        }
        var body = getOwner().getBody();
        if (body != null) {
            body.heal(body.getMaxHealth());
        }
    }

    /** The highest rung this much experience has reached; 0 if none. */
    protected int levelFor(int xp) {
        var ranks = this.ranks;
        for (int rung = ranks.size(); rung > 0; rung--) {
            int threshold = ranks.get(rung - 1).experience();
            if (threshold > 0 && xp >= threshold) {
                return rung;
            }
        }
        return 0;
    }

    /** Experience awarded to whoever destroys this unit. */
    public int getExperienceValue() {
        return data.experienceValue();
    }

    public int getExperience() {
        return experience;
    }

    /** The rung reached: 0 for unranked, 1 for the first rank, and so on. */
    public int getLevel() {
        return level;
    }

    /** How many rungs this unit's ladder has. */
    public int getRankCount() {
        return ranks.size();
    }

    /** The combat damage multiplier the current rank carries. */
    @Override
    public float damageMultiplier() {
        return level == 0 ? 1f : ranks.get(level - 1).damageMultiplier();
    }
}
