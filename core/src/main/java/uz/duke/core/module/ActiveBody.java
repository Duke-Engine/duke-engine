package uz.duke.core.module;

import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.thing.GameObject;

/**
 * The standard damageable body, ported from SAGE's {@code ActiveBody}.
 *
 * <p>Starts at full health and tracks current/max health, clamping both damage
 * and healing to the valid range.
 */
public final class ActiveBody extends BodyModule {

    /**
     * INI configuration for an {@link ActiveBody}: {@code MaxHealth} and optional
     * {@link Armor}. The single-arg form (no armor) keeps existing call sites
     * working.
     */
    public record Data(float maxHealth, Armor armor) implements ModuleData {
        public Data(float maxHealth) {
            this(maxHealth, Armor.NONE);
        }
    }

    /** Mutable accumulator used while parsing a {@link Data} from INI. */
    private static final class DataBuilder {
        float maxHealth;
        final Armor.Builder armor = Armor.builder();

        Data build() {
            return new Data(maxHealth, armor.build());
        }
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("MaxHealth", Ini.real((b, v) -> b.maxHealth = v))
            // Armor = ARMOR_PIERCING:1.5  (one weakness/resistance per line, repeatable)
            .add("Armor", (ini, b) -> {
                var type = Ini.scanEnum(DamageType.class, ini.getNextToken(Ini.SEPS_COLON));
                b.armor.set(type, Ini.scanReal(ini.getNextToken(Ini.SEPS_COLON)));
            });

    /** Read an {@link ActiveBody} sub-block's fields up to its {@code End}. */
    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return builder.build();
    }

    private final float maxHealth;
    private final Armor armor;
    private float health;

    public ActiveBody(GameObject owner, Data data) {
        super(owner);
        this.maxHealth = data.maxHealth();
        this.armor = data.armor();
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
