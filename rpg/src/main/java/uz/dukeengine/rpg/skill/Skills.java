package uz.dukeengine.rpg.skill;

import uz.dukeengine.combat.module.Errand;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.thing.GameObject;

/**
 * Where a {@link CastSkill} command turns into something happening: which hero the command meant, and his book
 * casting. What the HUD says of his skills is the hero card's ({@code HeroCard}, in rpg-client-3d).
 */
public final class Skills {

    private Skills() {
    }

    /**
     * Cast for whoever issued the command. Returns whether anything happened.
     *
     * <p>The hero is found rather than named in the command, so the input layer
     * never has to read the simulation to fill an id in. "The player's living unit
     * that has skills" is unambiguous in a dungeon; where it would not be, the
     * first in creation order wins, which is an order every peer agrees on.
     *
     * <p>Cast, it is the player's latest word, and gives up whatever errand he was
     * on as any order does — here, as the order comes in between frames, rather
     * than in the book: a monster casts from its own update, in which nothing may
     * change what modules it has.
     */
    public static boolean cast(GameLogic logic, CastSkill order, int rank) {
        var hero = heroOf(logic, order.playerIndex());
        if (hero == null) {
            return false;
        }
        var book = hero.findModule(SkillBook.class);
        if (book == null || !book.cast(order.key(), rank, order.target(), order.point())) {
            return false;
        }
        Errand.giveUpAll(hero);
        return true;
    }

    /**
     * Spend a level on a slot, if the rules allow it this instant.
     *
     * <p>Asked again here rather than trusted from the click: the panel drew its
     * button off a snapshot that was already a frame old, and a point spent
     * twice is a point that came from nowhere.
     */
    public static boolean raise(SkillRanks ranks, UpgradeSkill order, int heroLevel) {
        return ranks.raise(order.key(), heroLevel);
    }

    /** The player's living unit that has skills, in creation order. */
    public static GameObject heroOf(GameLogic logic, int playerIndex) {
        for (var object : logic.getObjects()) {
            if (object.getPlayerIndex() == playerIndex
                    && !object.isEffectivelyDead()
                    && object.findModule(SkillBook.class) != null) {
                return object;
            }
        }
        return null;
    }
}
