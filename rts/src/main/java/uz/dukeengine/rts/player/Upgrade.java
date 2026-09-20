package uz.dukeengine.rts.player;

import java.util.Map;
import java.util.TreeMap;

/**
 * A purchasable improvement: paid for once, and permanent for the side that
 * bought it.
 *
 * <p>Nearly every RTS has these — Warcraft's smithy, Age of Empires' blacksmith,
 * Generals' upgrades — so the mechanism belongs here. What an upgrade <em>does</em>
 * is the game's business, and that is the part this used to get wrong: it carried
 * a single weapon damage multiplier, which quietly ruled that the only thing worth
 * buying was more damage. An armoury that toughens infantry, a forge that lengthens
 * range, a doctrine that speeds production — none of them could be expressed.
 *
 * <p>Now it carries named {@link #effects}, each a multiplier applied to the
 * buyer's matching {@link RtsPlayer#getBonus bonus}. The engine never interprets
 * the names; it only has to carry them, and whatever reads a bonus decides what
 * it meant. {@link RtsPlayer#WEAPON_DAMAGE} is the one the engine's own weapon
 * reads, and a game is free to invent the rest.
 *
 * <p>Effects are held in name order so that applying an upgrade does the same
 * arithmetic in the same sequence on every machine.
 *
 * @param name    what identifies it; buying the same name twice does nothing
 * @param cost    charged against the buyer's treasury
 * @param effects bonus name to multiplier
 */
public record Upgrade(String name, int cost, Map<String, Float> effects) {

    public Upgrade {
        // A sorted map kept sorted: Map.copyOf would make it immutable but
        // unordered, which is precisely the property being guarded against here.
        effects = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(effects));
    }

    /** The common case: an upgrade that makes this side's weapons hit harder. */
    public static Upgrade weaponDamage(String name, int cost, float multiplier) {
        return new Upgrade(name, cost, Map.of(RtsPlayer.WEAPON_DAMAGE, multiplier));
    }

    /** What this upgrade multiplies {@code bonusName} by, or 1.0 if it does not. */
    public float effectOn(String bonusName) {
        return effects.getOrDefault(bonusName, 1.0f);
    }
}
