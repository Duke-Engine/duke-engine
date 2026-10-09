package uz.dukeengine.rpg.hero;

import uz.dukeengine.core.module.BodyModule;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.combat.CombatModuleGroups;

/**
 * A body whose maximum health and armour can change after it is built.
 *
 * <p>The engine's {@code ActiveBody} fixes maximum health and armour when the
 * unit is created, which is right for an RTS where a rifleman is a rifleman.
 * A roguelike hero is the other case: the whole game is him becoming harder to
 * kill. Rather than change the engine's body, the game supplies its own through
 * the module seam the engine already offers — which is what that seam is for.
 *
 * <p>Armour is one multiplier over every damage type, named or not: the engine's
 * damage types are an open set, so a hero who has earned protection is protected
 * from whatever the dungeon later learns to throw.
 *
 * <p>Growing raises current health by the same amount it raises the maximum. A
 * full heal on level-up would make levelling a free escape from a losing fight,
 * which turns "keep killing" into a way of avoiding the fight rather than a
 * reward for winning it.
 */
@ModuleGroup({ModuleGroups.BODY, CombatModuleGroups.PROGRESSION})
public final class GrowableBody extends BodyModule {

    /** INI configuration: the same {@code MaxHealth} field every body reads. */
    public record Data(float maxHealth) implements ModuleData {
    }

    private float maxHealth;
    private float health;
    private float damageTaken = 1f;

    public GrowableBody(GameObject owner, Data data) {
        super(owner);
        this.maxHealth = data.maxHealth();
        this.health = data.maxHealth();
    }

    /** Raise the ceiling, and lift current health with it. */
    public void growMaxHealth(float extra) {
        if (extra <= 0f) {
            return;
        }
        maxHealth += extra;
        health = clamp(health + extra);
    }

    /**
     * A new ceiling, up or down, and current health left where it is but for what no longer fits. What he carries
     * moves the ceiling and nothing else: were it to lift him too, putting a heart down and picking it up again
     * would be a heal.
     */
    public void setMaxHealth(float max) {
        maxHealth = Math.max(1f, max);
        health = clamp(health);
    }

    /** Take {@code multiplier} of the damage aimed at us — below 1 is armour. */
    public void setDamageTaken(float multiplier) {
        this.damageTaken = multiplier;
    }

    @Override
    public float getHealth() {
        return health;
    }

    @Override
    public float getMaxHealth() {
        return maxHealth;
    }

    @Override
    public void setHealth(float health) {
        this.health = clamp(health);
    }

    @Override
    public void damage(float amount, DamageType type) {
        if (amount <= 0f) {
            return;
        }
        health = clamp(health - scaled(amount * damageTaken, type));
    }

    @Override
    public float estimateDamage(float amount, DamageType type) {
        return amount <= 0f ? 0f : amount * damageTaken;
    }

    @Override
    public void heal(float amount) {
        if (amount <= 0f) {
            return;
        }
        health = clamp(health + amount);
    }

    private float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        return Math.min(value, maxHealth);
    }
}
