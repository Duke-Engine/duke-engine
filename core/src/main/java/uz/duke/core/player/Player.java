package uz.duke.core.player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A participant in the game — a human or AI faction, ported from SAGE's
 * {@code Player}.
 *
 * <p>Owns the diplomatic stance toward every other player and the player's
 * resources. Objects reference their owner by {@code playerIndex}, so a player's
 * index is its stable identity. Relationships are stored sparsely: any pair not
 * explicitly set is {@link Relationship#NEUTRAL}, and a player is always allied
 * with itself.
 */
public final class Player {

    private final int index;
    private final String name;
    private final Map<Integer, Relationship> relationships = new HashMap<>();
    private final Set<String> upgrades = new HashSet<>();
    private int money;
    private float weaponDamageBonus = 1.0f;

    public Player(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    /** This player's stance toward {@code other}. */
    public Relationship getRelationshipTo(Player other) {
        if (other.index == this.index) {
            return Relationship.ALLIES;
        }
        return relationships.getOrDefault(other.index, Relationship.NEUTRAL);
    }

    /** Set this player's one-way stance toward {@code other}. */
    public void setRelationshipTo(Player other, Relationship relationship) {
        if (other.index == this.index) {
            return; // a player's stance toward itself is fixed
        }
        relationships.put(other.index, relationship);
    }

    public boolean isEnemyOf(Player other) {
        return getRelationshipTo(other) == Relationship.ENEMIES;
    }

    public boolean isAllyOf(Player other) {
        return getRelationshipTo(other) == Relationship.ALLIES;
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
