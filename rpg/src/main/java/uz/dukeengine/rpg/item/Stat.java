package uz.dukeengine.rpg.item;

/**
 * The words for the figures a hero's own sheet reads off what he carries ({@code HeroProgress}). An item's
 * {@code Kind} and {@code Extra} are open: any other word is a figure of the game's own, which {@link LootBag#total}
 * and {@link LootBag#extra} add up for whatever asks. A spelling contract, as the module groups are.
 */
public final class Stat {

    /** Percent added to his weapon's blow. */
    public static final String ATTACK = "ATTACK";
    /** Flat maximum health: room for it, not the health itself. */
    public static final String HEALTH = "HEALTH";
    /** Percent taken off what reaches him. */
    public static final String ARMOUR = "ARMOUR";
    /** Flat maximum mana: room, as health is. */
    public static final String MANA = "MANA";
    /** Whole points of the attribute the item's {@code Attribute} line names. */
    public static final String ATTRIBUTE = "ATTRIBUTE";

    /** Whole points of health a second, beside what comes back on its own. */
    public static final String HEALTH_REGEN = "HEALTH_REGEN";
    /** Whole points of mana a second, beside what comes back on its own. */
    public static final String MANA_REGEN = "MANA_REGEN";
    /** Percent faster between blows. */
    public static final String ATTACK_SPEED = "ATTACK_SPEED";

    private Stat() {
    }
}
