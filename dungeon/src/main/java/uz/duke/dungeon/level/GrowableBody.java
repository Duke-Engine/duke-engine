package uz.duke.dungeon.level;

import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.module.Armor;
import uz.duke.core.module.BodyModule;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.ModuleData;
import uz.duke.core.thing.GameObject;

/**
 * A body whose maximum health and armour can change after it is built.
 *
 * <p>The engine's {@code ActiveBody} fixes maximum health and armour when the
 * unit is created, which is right for an RTS where a rifleman is a rifleman.
 * A roguelike hero is the other case: the whole game is him becoming harder to
 * kill. Rather than change the engine's body, the game supplies its own through
 * the module seam the engine already offers — which is what that seam is for.
 *
 * <p>Armour is the engine's {@link Armor}, rebuilt whenever it changes and set
 * across every damage type, so a hero who has earned protection is protected from
 * whatever the dungeon later learns to throw.
 *
 * <p>Growing raises current health by the same amount it raises the maximum. A
 * full heal on level-up would make levelling a free escape from a losing fight,
 * which turns "keep killing" into a way of avoiding the fight rather than a
 * reward for winning it.
 */
public final class GrowableBody extends BodyModule {

    /** INI configuration: the same {@code MaxHealth} field every body reads. */
    public record Data(float maxHealth) implements ModuleData {
    }

    private static final class DataBuilder {
        float maxHealth;
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("MaxHealth", Ini.real((b, v) -> b.maxHealth = v));

    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return new Data(builder.maxHealth);
    }

    private float maxHealth;
    private float health;
    private Armor armor = Armor.NONE;

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

    /** Take {@code multiplier} of the damage aimed at us — below 1 is armour. */
    public void setDamageTaken(float multiplier) {
        var builder = Armor.builder();
        for (var type : DamageType.values()) {
            builder.set(type, multiplier);
        }
        this.armor = builder.build();
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
