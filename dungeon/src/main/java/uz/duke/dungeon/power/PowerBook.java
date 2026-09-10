package uz.duke.dungeon.power;

import java.util.ArrayList;
import java.util.List;

/**
 * What the hero has picked up this run, and what it adds up to.
 *
 * <p>Rules and nothing else, like {@link uz.duke.dungeon.level.Levelling}: it
 * holds a list and answers questions about it. Nothing here touches the world, so
 * "what does two sharpened cooldowns come to?" can be asked without a dungeon.
 *
 * <p>It lives beside {@link uz.duke.dungeon.level.HeroProgress} rather than on
 * the hero, and for the same reason: descending replaces the hero object, and a
 * power the player chose has to survive that. A death, which replaces him too, is
 * meant to take everything — so {@link #clear()} is called by the run loop, told
 * rather than inferred.
 *
 * <p>Stacks add rather than compound. Two of "+25% damage" is +50%, not +56.25%:
 * a player reading two identical cards has every right to expect the second to be
 * worth what the first was, and compounding also drifts with the order they were
 * taken in.
 */
public final class PowerBook {

    /** In the order they were taken, which is the order the panel lists them. */
    private final List<Power> taken = new ArrayList<>();

    /** How far a cooldown may be sharpened, as a share of what it started at. */
    private final float minCooldownShare;

    public PowerBook(int minCooldownPercent) {
        this.minCooldownShare = minCooldownPercent / 100f;
    }

    /** Take one. A power already at its limit is refused rather than doubled up. */
    public boolean add(Power power) {
        if (power == null || isFull(power)) {
            return false;
        }
        taken.add(power);
        return true;
    }

    /** Everything picked up so far, oldest first. */
    public List<Power> getTaken() {
        return List.copyOf(taken);
    }

    public int stacksOf(String id) {
        int count = 0;
        for (var power : taken) {
            if (power.id().equals(id)) {
                count++;
            }
        }
        return count;
    }

    /** Whether it has been taken as often as it may be, and is done being offered. */
    public boolean isFull(Power power) {
        return stacksOf(power.id()) >= power.maxStacks();
    }

    /** Forget everything: a run has ended. */
    public void clear() {
        taken.clear();
    }

    // ---- what it all comes to ----

    /** What a skill's damage is multiplied by. */
    public float skillDamageMultiplier(char key) {
        return 1f + percentOf(PowerEffect.SKILL_DAMAGE, key) / 100f;
    }

    /**
     * What a skill's cooldown is multiplied by — floored, because a skill that
     * comes back instantly is not a skill, and a file is free to sharpen one too
     * far by accident.
     */
    public float cooldownMultiplier(char key) {
        return Math.max(minCooldownShare, 1f - percentOf(PowerEffect.COOLDOWN, key) / 100f);
    }

    /** What the hero's authored walking speed is multiplied by. */
    public float moveSpeedMultiplier() {
        return 1f + heroPercentOf(PowerEffect.MOVE_SPEED) / 100f;
    }

    /** The share of dealt damage that comes back as health; 0 when he has none. */
    public float lifestealFraction() {
        return heroPercentOf(PowerEffect.LIFESTEAL) / 100f;
    }

    /** How many extra casts a skill has before it starts recharging. */
    public int extraCharges(char key) {
        int extra = 0;
        for (var power : taken) {
            if (power.effect() == PowerEffect.EXTRA_CHARGE && power.appliesTo(key)) {
                extra += power.value();
            }
        }
        return extra;
    }

    private int percentOf(PowerEffect effect, char key) {
        int total = 0;
        for (var power : taken) {
            if (power.effect() == effect && power.appliesTo(key)) {
                total += power.value();
            }
        }
        return total;
    }

    private int heroPercentOf(PowerEffect effect) {
        int total = 0;
        for (var power : taken) {
            if (power.effect() == effect) {
                total += power.value();
            }
        }
        return total;
    }
}
