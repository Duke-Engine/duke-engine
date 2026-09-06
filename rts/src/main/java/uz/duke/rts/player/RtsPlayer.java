package uz.duke.rts.player;

import java.util.HashSet;
import java.util.Set;
import uz.duke.core.player.Player;
import uz.duke.core.thing.World;

/**
 * An RTS side: the engine's {@link Player} plus the things an RTS runs on —
 * a treasury, the upgrades it has bought, and the combat bonus they add up to.
 *
 * <p>{@code RtsSimulation} installs {@code RtsPlayer::new} as its roster's
 * factory, so every player in an RTS game is one of these. Modules reach it
 * through {@link #of(World, int)} rather than casting by hand.
 */
public final class RtsPlayer extends Player {

    private final Set<String> upgrades = new HashSet<>();
    private int money;
    private float weaponDamageBonus = 1.0f;

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

    /** All completed upgrade names (unmodifiable copy). */
    public Set<String> getUpgrades() {
        return Set.copyOf(upgrades);
    }

    /** Mark an upgrade as completed for this player. */
    public void addUpgrade(String upgradeName) {
        upgrades.add(upgradeName);
    }

    /** Player-wide multiplier applied to all owned units' weapon damage. */
    public float getWeaponDamageBonus() {
        return weaponDamageBonus;
    }

    public void multiplyWeaponDamageBonus(float factor) {
        weaponDamageBonus *= factor;
    }
}
