package uz.duke.rts.player;

/**
 * A purchasable technology, ported in spirit from SAGE's {@code Upgrade}/
 * {@code UpgradeTemplate}.
 *
 * <p>Once bought (charged against the player's money), an upgrade applies a
 * permanent, player-wide effect — here a multiplier to all the player's units'
 * weapon damage. SAGE upgrades can trigger many kinds of effect; this is the
 * common combat-bonus case, kept simple and extensible.
 */
public record Upgrade(String name, int cost, float weaponDamageMultiplier) {
}
