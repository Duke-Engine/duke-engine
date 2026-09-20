package uz.dukeengine.rts.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Bonuses a side earns, and the ordering that keeps them safe to iterate.
 *
 * <p>There used to be exactly one bonus — weapon damage — which decided for every
 * game that the only thing worth buying was more damage. Naming them lets a game
 * add whatever it counts without the engine learning what it means.
 */
class PlayerBonusTest {

    private static RtsPlayer player() {
        return new RtsPlayer(1, "Side");
    }

    @Test
    void anUnearnedBonusChangesNothing() {
        assertEquals(1.0f, player().getBonus("Whatever"), 1e-6f,
                "a unit may ask for a bonus its game never defines");
    }

    @Test
    void aBonusCanBeSetOutright() {
        var side = player();
        side.setBonus("Speed", 1.4f);
        assertEquals(1.4f, side.getBonus("Speed"), 1e-6f);

        side.setBonus("Speed", 2f);
        assertEquals(2f, side.getBonus("Speed"), 1e-6f, "set replaces rather than stacks");
    }

    /**
     * Assignment exists so a caller that knows its figure can say it. Before, the
     * only way in was multiplication, which forced anything needing an exact value
     * to divide out whatever happened to be there.
     */
    @Test
    void settingAvoidsHavingToDivideOutTheOldValue() {
        var side = player();
        side.multiplyWeaponDamageBonus(1.5f);
        side.multiplyWeaponDamageBonus(2f);
        assertEquals(3f, side.getWeaponDamageBonus(), 1e-6f);

        side.setWeaponDamageBonus(1.12f);

        assertEquals(1.12f, side.getWeaponDamageBonus(), 1e-6f, "exactly, in one step");
    }

    @Test
    void bonusesCompoundWhenMultiplied() {
        var side = player();
        side.multiplyBonus("Armour", 1.5f);
        side.multiplyBonus("Armour", 2f);
        assertEquals(3f, side.getBonus("Armour"), 1e-6f);
    }

    /**
     * The property that matters on the simulation path: whatever is iterated comes
     * out in the same order everywhere, so it cannot be a source of divergence.
     */
    @Test
    void bonusesAndUpgradesComeOutInNameOrder() {
        var side = player();
        side.setBonus("Speed", 1f);
        side.setBonus("Armour", 1f);
        side.setBonus("WeaponDamage", 1f);
        side.addUpgrade("Zeal");
        side.addUpgrade("Armoury");
        side.addUpgrade("Masonry");

        assertIterableEquals(List.of("Armour", "Speed", "WeaponDamage"),
                side.getBonuses().keySet());
        assertIterableEquals(List.of("Armoury", "Masonry", "Zeal"), side.getUpgrades());
    }

    /** An upgrade may improve several things at once. */
    @Test
    void anUpgradeCanCarryManyEffects() {
        var doctrine = new Upgrade("Doctrine", 300,
                Map.of("WeaponDamage", 1.2f, "Armour", 1.5f, "Speed", 1.1f));

        assertEquals(1.2f, doctrine.effectOn("WeaponDamage"), 1e-6f);
        assertEquals(1.5f, doctrine.effectOn("Armour"), 1e-6f);
        assertEquals(1.0f, doctrine.effectOn("Range"), 1e-6f, "and nothing it does not name");
    }

    /** Effects are applied in a fixed order, so every machine does the same sums. */
    @Test
    void anUpgradesEffectsAreOrdered() {
        var doctrine = new Upgrade("Doctrine", 0,
                Map.of("Speed", 1.1f, "Armour", 1.5f, "WeaponDamage", 1.2f));

        assertIterableEquals(List.of("Armour", "Speed", "WeaponDamage"),
                doctrine.effects().keySet());
    }

    @Test
    void theWeaponDamageHelpersAreJustANamedBonus() {
        var side = player();
        side.setBonus(RtsPlayer.WEAPON_DAMAGE, 1.7f);
        assertEquals(1.7f, side.getWeaponDamageBonus(), 1e-6f);
    }
}
