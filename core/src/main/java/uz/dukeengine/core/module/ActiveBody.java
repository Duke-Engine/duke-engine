package uz.dukeengine.core.module;

import java.util.List;
import java.util.Map;
import uz.dukeengine.core.thing.Conditions;
import uz.dukeengine.core.thing.GameObject;

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
     * {@code ARMOR_PIERCING = 2.0} is a weakness, {@code FLAME = 0.5} half the harm — and
     * {@code ArmorSets} worn instead of it while their words hold ({@link ArmorSet}).
     */
    public record Data(float maxHealth, Map<DamageType, Float> armor, List<ArmorSet> armorSets)
            implements ModuleData {
        public Data {
            // Kept in the order the block wrote them, not a hash's: see Armor, whose key is interned
            // and therefore hashes by identity.
            armor = armor == null || armor.isEmpty() ? Map.of()
                    : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(armor));
            armorSets = armorSets == null ? List.of() : List.copyOf(armorSets);
        }

        public Data(float maxHealth, Map<DamageType, Float> armor) {
            this(maxHealth, armor, List.of());
        }

        public Data(float maxHealth) {
            this(maxHealth, Map.of());
        }
    }

    private float maxHealth;
    private final Armor armor;
    private final List<List<String>> setWords;
    private final List<Armor> setArmor;
    private float health;

    public ActiveBody(GameObject owner, Data data) {
        super(owner);
        this.maxHealth = data.maxHealth();
        this.armor = new Armor(data.armor());
        this.setWords = data.armorSets().stream().map(ArmorSet::conditions).toList();
        this.setArmor = data.armorSets().stream().map(set -> new Armor(set.armor())).toList();
        this.health = data.maxHealth();
    }

    /** The armour worn now: the set that fits the thing's words best, or its plain armour where none does. */
    private Armor armorNow() {
        if (setWords.isEmpty()) {
            return armor;
        }
        int best = Conditions.bestFit(setWords, getOwner().getConditions());
        return best < 0 ? armor : setArmor.get(best);
    }

    @Override
    public void setMaxHealth(float most, MaxHealthChange rule) {
        float was = maxHealth;
        maxHealth = Math.max(0f, most);
        health = clamp(switch (rule) {
            case KEEP_SHARE -> was <= 0f ? maxHealth : health * maxHealth / was;
            case ADD_DIFFERENCE -> health + (maxHealth - was);
            case KEEP_HEALTH -> health;
        });
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
        health = clamp(health - amount * armorNow().getMultiplier(type));
    }

    @Override
    public float estimateDamage(float amount, DamageType type) {
        return amount <= 0f ? 0f : amount * armorNow().getMultiplier(type);
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
