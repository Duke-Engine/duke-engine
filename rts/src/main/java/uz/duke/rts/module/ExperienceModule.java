package uz.duke.rts.module;

import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.module.Module;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;

/**
 * Tracks a unit's combat experience and rank, ported from SAGE's
 * {@code ExperienceTracker}.
 *
 * <p>A unit accrues experience by destroying enemies (each unit is "worth" some
 * {@link #getExperienceValue() experience value} to its killer) and ranks up
 * through {@link VeterancyLevel}s as it crosses the configured thresholds, which
 * in turn boosts its combat performance. Holds no per-frame behaviour, so it is
 * a plain {@link Module}, not an {@link UpdateModule}.
 */
public class ExperienceModule extends Module implements DamageModifier {

    /**
     * INI config: how much this unit is worth when killed, and the experience
     * required to reach VETERAN / ELITE / HEROIC.
     */
    public record Data(int experienceValue, int veteranXp, int eliteXp, int heroicXp) implements ModuleData {
    }

    private static final class DataBuilder {
        int experienceValue;
        int veteranXp;
        int eliteXp;
        int heroicXp;

        Data build() {
            return new Data(experienceValue, veteranXp, eliteXp, heroicXp);
        }
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("ExperienceValue", Ini.integer((b, v) -> b.experienceValue = v))
            .add("ExperienceRequired", (ini, b) -> {
                b.veteranXp = Ini.scanInt(ini.getNextToken());
                b.eliteXp = Ini.scanInt(ini.getNextToken());
                b.heroicXp = Ini.scanInt(ini.getNextToken());
            });

    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return builder.build();
    }

    protected final Data data;
    private int experience;
    private VeterancyLevel level = VeterancyLevel.REGULAR;

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
        var newLevel = levelFor(experience);
        if (newLevel != level) {
            level = newLevel;
            onPromoted();
        }
    }

    /** Promotion fully heals the unit, as in Generals. */
    protected void onPromoted() {
        var body = getOwner().getBody();
        if (body != null) {
            body.heal(body.getMaxHealth());
        }
    }

    protected VeterancyLevel levelFor(int xp) {
        if (data.heroicXp() > 0 && xp >= data.heroicXp()) {
            return VeterancyLevel.HEROIC;
        }
        if (data.eliteXp() > 0 && xp >= data.eliteXp()) {
            return VeterancyLevel.ELITE;
        }
        if (data.veteranXp() > 0 && xp >= data.veteranXp()) {
            return VeterancyLevel.VETERAN;
        }
        return VeterancyLevel.REGULAR;
    }

    /** Experience awarded to whoever destroys this unit. */
    public int getExperienceValue() {
        return data.experienceValue();
    }

    public int getExperience() {
        return experience;
    }

    public VeterancyLevel getLevel() {
        return level;
    }

    /**
     * The combat damage multiplier from the current rank.
     *
     * <p>Implements {@link DamageModifier}, so a rank raising damage is now one
     * example of a general seam rather than a rule wired into the weapon: a game
     * that wants different levels, or none, attaches something else.
     */
    @Override
    public float damageMultiplier() {
        return level.getDamageMultiplier();
    }

    /** @deprecated use {@link #damageMultiplier()} — the seam every modifier shares. */
    @Deprecated
    public float getDamageMultiplier() {
        return damageMultiplier();
    }
}
