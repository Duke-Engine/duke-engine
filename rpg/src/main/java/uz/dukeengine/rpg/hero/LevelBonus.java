package uz.dukeengine.rpg.hero;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.combat.module.DamageModifier;
import uz.dukeengine.combat.CombatModuleGroups;

/**
 * A monster's level, and the two multipliers it gives: how much harder it hits, and what its health was grown by.
 *
 * <p>Attached where the monster is placed, or rises, rather than written into its template, because the same Runner
 * stands at every level and only where it was met is different: see the game's step that sets a creature's level, the
 * one step that makes a creature its level. A template is what a thing is; this is where it was found. A creature that
 * carries none -- a test's bare skeleton -- is level 1, its block exactly.
 *
 * <p>Rides the engine's {@link DamageModifier} seam for its weapon's blow, which is exactly the case that seam was
 * opened for: a bonus belonging to one unit rather than to its whole side, from a module the engine has never heard
 * of. Read by its skills too, which deal their own damage rather than going through the weapon (see
 * {@code SkillBook}); by the rifts it opens, so what climbs out stands at its level (see {@code SummoningUpdate}); by
 * its brain, for which of its skills its level has opened (see {@link #rankOf}); and by the card that shows what it
 * hits for.
 */
@ModuleGroup({ModuleGroups.COMBAT, CombatModuleGroups.PROGRESSION})
public final class LevelBonus extends Module implements DamageModifier, Saveable {

    private final int level;
    private final float multiplier;
    private final float healthMultiplier;

    public LevelBonus(GameObject owner, int level, float multiplier, float healthMultiplier) {
        super(owner);
        this.level = level;
        this.multiplier = multiplier;
        this.healthMultiplier = healthMultiplier;
    }

    /** The level of {@code creature}: the one its bonus says, or 1 for a creature that carries none. */
    public static int levelOf(GameObject creature) {
        var bonus = creature == null ? null : creature.findModule(LevelBonus.class);
        return bonus == null ? 1 : bonus.level;
    }

    /**
     * The rank {@code creature} holds of {@code skill}: 1 once its level has opened the skill's first rank -- the level
     * a hero's waits for, see {@link Skill#levelForRank} -- and 0 before. Never more: what makes a creature hit harder
     * is its level's bonus, not a second rank, as for its weapon.
     */
    public static int rankOf(GameObject creature, Skill skill) {
        return levelOf(creature) >= skill.levelForRank(1) ? 1 : 0;
    }

    public int level() {
        return level;
    }

    @Override
    public float damageMultiplier() {
        return multiplier;
    }

    /** What its health was grown by for its level. */
    public float healthMultiplier() {
        return healthMultiplier;
    }

    @Override
    public void save(SaveOut out) {
        out.writeInt(level).writeFloat(multiplier).writeFloat(healthMultiplier);
    }

    @Override
    public void load(SaveIn in) {
        throw new IllegalStateException("a level bonus is made again by LevelBonus.revive, never by its template");
    }

    /** One added where its creature was placed, made again as it was: what {@code ModuleFactory.revives} is given. */
    public static LevelBonus revive(GameObject owner, SaveIn in) {
        return new LevelBonus(owner, in.readInt(), in.readFloat(), in.readFloat());
    }
}
