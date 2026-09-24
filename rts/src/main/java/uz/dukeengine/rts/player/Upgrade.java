package uz.dukeengine.rts.player;

import java.util.Map;
import java.util.TreeMap;

/**
 * A purchasable improvement: paid for once, and permanent for the side that
 * bought it — or, researched at one building, for that building alone.
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
 * reads, and a game is free to invent the rest. And a finished upgrade is a word
 * on the things it reaches ({@code GameObject.setCondition}), so a weapon set or a
 * model chosen by that word changes with it.
 *
 * <p>Effects are held in name order so that applying an upgrade does the same
 * arithmetic in the same sequence on every machine.
 *
 * <p><b>Research.</b> Queued at a building like a unit ({@code
 * ProductionUpdate.queueResearch}): charged when queued, refunded when called off,
 * finished {@link #frames} later. In the RTS this was measured in, 81 upgrades take 0
 * to 60 seconds; 58 of its buttons research one for the whole side and 35 one for the
 * building or unit that researches it.
 *
 * @param name    what identifies it; buying the same name twice does nothing
 * @param cost    charged against the buyer's treasury
 * @param effects bonus name to multiplier
 * @param frames  how long researching it takes; 0 is at once
 * @param scope   whether it is the whole side's or the one researcher's
 * @param icon    the picture a queue draws it with, a whole path; none for no picture
 */
public record Upgrade(String name, int cost, Map<String, Float> effects, int frames, Scope scope, String icon) {

    /** What a block leaves out: free, no effects, at once, the side's. */
    static final Upgrade DEFAULTS = new Upgrade(null, 0, Map.of(), 0, Scope.PLAYER, null);

    /** Whose an upgrade is once it is finished. */
    public enum Scope {
        /** The whole side's: every thing of it has it, and every thing it makes afterwards. */
        PLAYER,
        /** The one building or unit that researched it. */
        OBJECT
    }

    public Upgrade {
        // A sorted map kept sorted: Map.copyOf would make it immutable but
        // unordered, which is precisely the property being guarded against here.
        effects = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(effects == null ? Map.of() : effects));
        frames = Math.max(0, frames);
        scope = scope == null ? Scope.PLAYER : scope;
    }

    /** An upgrade of the side's, bought at once: every upgrade from before research. */
    public Upgrade(String name, int cost, Map<String, Float> effects) {
        this(name, cost, effects, 0, Scope.PLAYER, null);
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
