package uz.duke.rts.module;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.module.Module;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.ModuleGroups;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;

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
     * INI config: what this unit is worth when killed, the ladder it climbs, and
     * whether being promoted heals it.
     */
    public record Data(int experienceValue, List<Rank> ranks, boolean healOnPromotion)
            implements ModuleData {

        public Data {
            ranks = List.copyOf(ranks);
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

    private static final class DataBuilder {
        int experienceValue;
        List<Integer> thresholds = List.of();
        List<Float> bonuses = List.of();
        boolean healOnPromotion;

        Data build() {
            var ranks = new ArrayList<Rank>(thresholds.size());
            for (int i = 0; i < thresholds.size(); i++) {
                // A ladder may name its costs and say nothing about bonuses; the
                // rungs are then worth reaching only for whatever the game hangs
                // off the rank itself.
                float bonus = i < bonuses.size() ? bonuses.get(i) : 1f;
                ranks.add(new Rank(thresholds.get(i), bonus));
            }
            return new Data(experienceValue, ranks, healOnPromotion);
        }
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("ExperienceValue", Ini.integer((b, v) -> b.experienceValue = v))
            // ExperienceRequired = 60 180 360   (as many rungs as the game wants)
            .add("ExperienceRequired", (ini, b) -> {
                var thresholds = new ArrayList<Integer>();
                for (var token = ini.getNextTokenOrNull(); token != null;
                        token = ini.getNextTokenOrNull()) {
                    thresholds.add(Ini.scanInt(token));
                }
                b.thresholds = thresholds;
            })
            // LevelDamageBonus = 1.1 1.2 1.3    (one per rung; missing ones are 1.0)
            .add("LevelDamageBonus", (ini, b) -> {
                var bonuses = new ArrayList<Float>();
                for (var token = ini.getNextTokenOrNull(); token != null;
                        token = ini.getNextTokenOrNull()) {
                    bonuses.add(Ini.scanReal(token));
                }
                b.bonuses = bonuses;
            })
            .add("HealOnPromotion", Ini.bool((b, v) -> b.healOnPromotion = v));

    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return builder.build();
    }

    protected final Data data;
    private int experience;
    private int level; // 0 = unranked; 1..n index into the ladder

    public ExperienceModule(GameObject owner, Data data) {
        super(owner);
        this.data = data;
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
        var ranks = data.ranks();
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
        return data.ranks().size();
    }

    /** The combat damage multiplier the current rank carries. */
    @Override
    public float damageMultiplier() {
        return level == 0 ? 1f : data.ranks().get(level - 1).damageMultiplier();
    }
}
