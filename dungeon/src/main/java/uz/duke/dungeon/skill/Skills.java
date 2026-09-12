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
    public static boolean cast(GameLogic logic, CastSkill order, int rank) {
        var hero = heroOf(logic, order.playerIndex());
        if (hero == null) {
            return false;
        }
        var book = hero.findModule(SkillBook.class);
        return book != null
                && book.cast(order.key(), rank, order.target(), order.point());
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

    /**
     * What the line under a slot's pips says.
     *
     * <p>Four states and four sentences. A rank he can raise says what it would
     * BECOME, because that is the decision in front of him; one he cannot afford
     * says only what it is; a full one is named; and one he has never bought says
     * so, because the badge beside it is the rest of the story.
     */
    private static String rankWord(Skill skill, int rank, boolean canRaise, Words words) {
        if (canRaise) {
            return rank + " → " + (rank + 1);
        }
        if (rank >= skill.maxRank()) {
            return words.master();
        }
        return rank > 0 ? rank + words.rankSuffix() : words.locked();
    }

    /**
     * The words a slot needs, gathered so the signature does not grow one string
     * at a time. All of them are the game's, out of its own file.
     */
    public record Words(String rankSuffix, String master, String locked, SkillTip.Words tip) {
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
     * <p>A slot is its key, the picture to draw in it, and one of three states:
     * {@code ready}, {@code cool} with the frames left and the frames it started
     * from, or {@code lock} with the words to write across it. The cooldown crosses
     * as a pair rather than as a count of seconds because the panel draws it as
     * well as writes it, and a fraction is what a shadow sweeping round a slot is
     * made of.
     *
     * <p>The picture crosses as a <em>path</em> rather than as a name. The client
     * serves other games and has no business knowing where this one keeps its art;
     * naming the file here means a fifth skill is a fifth block of INI. An empty
     * one is allowed and means the slot falls back to the letter of its key.
     *
     * <p>{@code rankSuffix} is the game's word for a level, so that a locked slot
     * can say what it is waiting for in the same language as the rest of the panel.
     */
    public static String slots(SkillBook book, SkillRanks ranks, int heroLevel,
            Words words, java.util.function.UnaryOperator<String> iconPath) {
        var fields = new StringBuilder();
        for (var skill : book.getSkills()) {
            int rank = ranks.rankOf(skill.key());
            fields.append("|skill=").append(skill.key()).append(',')
                    .append(iconPath.apply(skill.icon())).append(',');
            if (rank <= SkillRanks.UNLEARNT) {
                // Nothing spent on it. An ultimate says what it is waiting for;
                // an ordinary skill is waiting for nothing but a point, so it
                // says nothing and the button beside it is the whole story.
                fields.append("lock");
                if (skill.isUltimate()) {
                    fields.append(',').append(skill.levelForRank(1))
                            .append(words.rankSuffix());
                }
            } else {
                int left = book.cooldownOf(skill.key());
                if (left <= 0) {
                    fields.append("ready");
                } else {
                    fields.append("cool,").append(left).append(',')
                            .append(skill.cooldownAt(rank));
                }
            }
            // A field of its own rather than three more on the one above, which
            // already has three shapes. What the panel needs to draw a button:
            // what is in it, what fits in it, and whether the next point may go
            // there right now.
            boolean canRaise = ranks.canRaise(skill.key(), heroLevel);
            fields.append("|srank=").append(skill.key()).append(',').append(rank)
                    .append(',').append(skill.maxRank())
                    .append(',').append(canRaise ? "up" : "no")
                    // The line under the pips, finished here like every other word
                    // on this bar -- the client has three other games to serve and
                    // no business knowing which language this one speaks. The
                    // client picks the COLOUR, which is a fact about the state it
                    // can already see.
                    .append(',').append(rankWord(skill, rank, canRaise, words));
            // And everything the tooltip over it says. Written for all four every
            // frame rather than for whichever the cursor is on: the panel knows
            // what is hovered and the simulation does not, and asking it would be
            // a command, a frame of delay and a piece of state, to save a few
            // hundred characters on a line that is rebuilt anyway.
            fields.append(SkillTip.of(skill, rank, canRaise, words.tip()));
        }
        return fields.toString();
    }
}
