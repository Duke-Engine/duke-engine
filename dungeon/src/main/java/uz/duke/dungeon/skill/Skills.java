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
        return book != null && book.cast(order.key(), level);
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
     * The skill bar, as one line of the status channel.
     *
     * <p>Each skill is its key and its state: ready, the seconds left, or the level
     * it is waiting for. Seconds rather than frames here and only here — frames are
     * what the simulation counts, and a player reading a number off the screen
     * thinks in seconds.
     */
    public static String bar(SkillBook book, int level) {
        var line = new StringBuilder();
        for (var skill : book.getSkills()) {
            if (!line.isEmpty()) {
                line.append("  ");
            }
            line.append(skill.key()).append(' ').append(state(book, skill, level));
        }
        return line.toString();
    }

    private static String state(SkillBook book, Skill skill, int level) {
        if (!skill.unlockedAt(level)) {
            return "lv" + skill.unlockLevel();
        }
        int left = book.cooldownOf(skill.key());
        if (left <= 0) {
            return "ready";
        }
        // Rounded up, so a bar never reads 0 while the skill is still refusing.
        return (left + FRAMES_PER_SECOND - 1) / FRAMES_PER_SECOND + "s";
    }

    private static final int FRAMES_PER_SECOND =
            uz.duke.core.GameConstants.LOGICFRAMES_PER_SECOND;
}
