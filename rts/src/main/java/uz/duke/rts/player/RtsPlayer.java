package uz.duke.rts.player;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import uz.duke.core.player.Player;
import uz.duke.core.thing.World;

/**
 * An RTS side: the engine's {@link Player} plus the things an RTS runs on —
 * a treasury, the upgrades it has bought, and the bonuses they add up to.
 *
 * <p>{@code RtsSimulation} installs {@code RtsPlayer::new} as its roster's
 * factory, so every player in an RTS game is one of these. Modules reach it
 * through {@link #of(World, int)} rather than casting by hand.
 *
 * <p>Bonuses are <b>named</b> rather than enumerated. There used to be exactly
 * one — a weapon damage multiplier — which quietly decided that the only thing an
 * upgrade could improve was damage; an armoury that toughens infantry or a forge
 * that lengthens range had nowhere to go. The engine does not need to know what
 * {@code "WeaponDamage"} means to carry it, so a game names its own.
 *
 * <p>Both collections are sorted, and that is not tidiness. Anything iterated on
 * the simulation path has to come out in the same order on every machine, and a
 * hash-ordered set only stays safe while every caller remembers to sort it —
 * which is a promise made once and relied on forever. Sorting at the source
 * makes the guarantee the type's, not the caller's.
 */
public final class RtsPlayer extends Player {

    /** The bonus a weapon consults. Named so a game can add its own beside it. */
    public static final String WEAPON_DAMAGE = "WeaponDamage";

    private final Set<String> upgrades = new TreeSet<>();
    private final Map<String, Float> bonuses = new TreeMap<>();
    private int money;

    public RtsPlayer(int index, String name) {
        super(index, name);
    }

    /**
     * The RTS player at {@code index}, or {@code null} if the world has no such
     * player — or is not running an RTS roster.
     */
    public static RtsPlayer of(World world, int index) {
        return world != null && world.getPlayer(index) instanceof RtsPlayer player ? player : null;
    }

    public int getMoney() {
        return money;
    }

    public void deposit(int amount) {
        if (amount > 0) {
            money += amount;
        }
    }

    /** Withdraw up to {@code amount}; returns true if the player could afford it. */
    public boolean withdraw(int amount) {
        if (amount < 0 || money < amount) {
            return false;
        }
        money -= amount;
        return true;
    }

    public boolean hasUpgrade(String upgradeName) {
        return upgrades.contains(upgradeName);
    }

    /** Every upgrade bought, in name order. */
    public Set<String> getUpgrades() {
        return java.util.Collections.unmodifiableSortedSet(new TreeSet<>(upgrades));
    }

    /** Mark an upgrade as completed for this player. */
    public void addUpgrade(String upgradeName) {
        upgrades.add(upgradeName);
    }

    // ---- player-wide bonuses ----

    /**
     * A named multiplier this side has earned, or 1.0 if it has earned none.
     *
     * <p>Unknown names answer 1.0 rather than failing, so a unit can ask for a
     * bonus its game never defines and simply be unaffected.
     */
    public float getBonus(String name) {
        return bonuses.getOrDefault(name, 1.0f);
    }

    /** Set a bonus outright — what to use when the value is known. */
    public void setBonus(String name, float value) {
        bonuses.put(name, value);
    }

    /**
     * Compound a bonus, for an upgrade that stacks with what came before.
     *
     * <p>{@link #setBonus} is the one to reach for when the caller knows the
     * figure it wants. Multiplying was once the only way in, which forced anything
     * needing an exact value to divide out whatever was already there.
     */
    public void multiplyBonus(String name, float factor) {
        bonuses.put(name, getBonus(name) * factor);
    }

    /** Every bonus this side holds, in name order. */
    public Map<String, Float> getBonuses() {
        return java.util.Collections.unmodifiableSortedMap(new TreeMap<>(bonuses));
    }

    /** Player-wide multiplier applied to all owned units' weapon damage. */
    public float getWeaponDamageBonus() {
        return getBonus(WEAPON_DAMAGE);
    }

    public void setWeaponDamageBonus(float value) {
        setBonus(WEAPON_DAMAGE, value);
    }

    public void multiplyWeaponDamageBonus(float factor) {
        multiplyBonus(WEAPON_DAMAGE, factor);
    }
}
