package uz.duke.core.module;

import java.util.Map;
import uz.duke.core.thing.GameObject;

/**
 * The standard damageable body, ported from SAGE's {@code ActiveBody}.
 *
 * <p>Starts at full health and tracks current/max health, clamping both damage
 * and healing to the valid range.
 */
@ModuleGroup(ModuleGroups.BODY)
public final class ActiveBody extends BodyModule {

    /**
     * {@code MaxHealth}, and an {@code Armor} block of damage multipliers by type:
     * {@code ARMOR_PIERCING = 2.0} is a weakness, {@code FLAME = 0.5} half the harm.
     */
    public record Data(float maxHealth, Map<DamageType, Float> armor) implements ModuleData {
        public Data {
            armor = armor == null ? Map.of() : Map.copyOf(armor);
        }

        public Data(float maxHealth) {
            this(maxHealth, Map.of());
        }
    }

    private final float maxHealth;
    private final Armor armor;
    private float health;

    public ActiveBody(GameObject owner, Data data) {
        super(owner);
        this.maxHealth = data.maxHealth();
        this.armor = new Armor(data.armor());
        this.health = data.maxHealth();
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
        health = clamp(health - amount * armor.getMultiplier(type));
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
