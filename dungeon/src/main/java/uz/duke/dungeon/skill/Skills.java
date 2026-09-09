package uz.duke.dungeon.skill;

import uz.duke.core.GameLogic;
import uz.duke.core.thing.GameObject;

/**
 * Where a {@link CastSkill} command turns into something happening, and how the
 * result is described to the player.
 *
 * <p>Both halves are here because both are about the whole set rather than one
 * skill: which hero the command meant, and what the HUD says about all four.
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
     */
    public static boolean cast(GameLogic logic, CastSkill order, int level) {
        var hero = heroOf(logic, order.playerIndex());
        if (hero == null) {
            return false;
        }
        var book = hero.findModule(SkillBook.class);
        return book != null
                && book.cast(order.key(), level, order.target(), order.point());
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

    /**
     * The four slots, as fields of the status channel — one {@code |skill=…} each,
     * in the order the file lists them.
     *
     * <p>A slot is its key and one of three states: {@code ready}, {@code cool}
     * with the frames left and the frames it started from, or {@code lock} with the
     * words to write across it. The cooldown crosses as a pair rather than as a
     * count of seconds because the panel draws it as well as writes it, and a
     * fraction is what a shadow sweeping round a slot is made of.
     *
     * <p>{@code rankSuffix} is the game's word for a level, so that a locked slot
     * can say what it is waiting for in the same language as the rest of the panel.
     */
    public static String slots(SkillBook book, int level, String rankSuffix) {
        var fields = new StringBuilder();
        for (var skill : book.getSkills()) {
            fields.append("|skill=").append(skill.key()).append(',');
            if (!skill.unlockedAt(level)) {
                fields.append("lock,").append(skill.unlockLevel()).append(rankSuffix);
                continue;
            }
            int left = book.cooldownOf(skill.key());
            if (left <= 0) {
                fields.append("ready");
            } else {
                fields.append("cool,").append(left).append(',').append(skill.cooldownAt(level));
            }
        }
        return fields.toString();
    }
}
