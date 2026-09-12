package uz.duke.dungeon.skill;

import uz.duke.core.GameConstants;

/**
 * What a slot's tooltip says: what the skill is called, what it does, and — the
 * whole reason it exists — what one more point would change.
 *
 * <p><b>Without this the upgrade system is a coin toss.</b> A player asked to
 * spend a level on one of four skills, shown only their names, is not making a
 * decision; he is guessing and then finding out. So every figure that moves with
 * a rank is shown twice: what it is now, and what it becomes.
 *
 * <p>Built here rather than on the far side because every part of it is the
 * game's. The words are the game's — the client serves three other games and
 * writes none of its own — and so are the numbers, which come out of
 * {@link Skill} and nowhere else. What crosses is finished text; what the client
 * decides is where to put it and what colour the second number is.
 *
 * <p><b>Only what actually changes is shown as changing.</b> A row whose figure
 * is the same at the next rank is drawn with one value, not with an arrow to an
 * identical number — that reads as a promise and is worth nothing. Today that
 * means damage, what a buff is worth, and cooldowns; reach and radius are fixed
 * and say so by having no second half.
 */
public final class SkillTip {

    private SkillTip() {
    }

    /**
     * The words the tooltip needs, all of them out of the file.
     *
     * @param damage    what a damage row is called
     * @param cooldown  what a cooldown row is called
     * @param radius    how wide it reaches, where that means anything
     * @param range     how far it can be put
     * @param boost     what a buff is worth, in percent
     * @param raise     the footer when he may spend a point on it
     * @param raiseKey  what goes in front of the letter to say "and hold this" —
     *     the modifier that turns casting into buying, as a finished word
     * @param maxed     the footer when there is nothing left to spend
     * @param noPoints  the footer when he has nothing to spend
     * @param rankWord  "N-daraja", as the suffix the rest of the panel uses
     * @param seconds   the letter after a number of seconds
     */
    public record Words(String damage, String cooldown, String radius, String range,
            String boost, String raise, String raiseKey, String maxed, String noPoints,
            String rankWord, String seconds, String mana) {
    }

    /**
     * One slot's tooltip, as fields of the status channel.
     *
     * <p>Four kinds of field rather than one long one, because they are four
     * different shapes and packing them into a single string would need an escape
     * scheme for a saving nobody asked for. A row's label may not contain a comma;
     * a name and a blurb may contain anything, since they are read as "the rest of
     * the field".
     */
    public static String of(Skill skill, int rank, boolean canRaise, Words words) {
        var out = new StringBuilder();
        char key = skill.key();
        out.append("|tipName=").append(key).append(',').append(skill.name());
        // ★ The letter is ALWAYS on the line, whether or not he owns it yet: the
        // card is where a player finds out which key a skill is on, and a skill he
        // has not bought is exactly the one he does not know.
        out.append("|tipAt=").append(key).append(',')
                .append(rank > 0 ? rank + words.rankWord() + " · " + key : String.valueOf(key));
        if (!skill.blurb().isEmpty()) {
            out.append("|tipText=").append(key).append(',').append(skill.blurb());
        }
        rows(out, skill, rank, canRaise, words);
        // And the foot names the keys that BUY it, which is the one thing the
        // panel cannot show by drawing: a badge says a point may go here and says
        // nothing about the hand already resting on the letter.
        out.append("|tipFoot=").append(key).append(',').append(
                rank >= skill.maxRank() ? words.maxed()
                        : canRaise ? words.raiseKey() + key + " · " + words.raise()
                                : words.noPoints());
        return out.toString();
    }

    /**
     * The figures, and which of them move.
     *
     * <p>A row is written at all only when the skill has that figure: a dash has
     * no damage and a blast has no reach, and an empty row saying "0" is a number
     * the player has to read before he can ignore it.
     */
    private static void rows(StringBuilder out, Skill skill, int rank, boolean canRaise,
            Words words) {
        int next = rank + 1;
        // At nothing yet, the "now" column is blank and the arrow points at what
        // the first point would buy. Showing the same figure on both sides would
        // say a point changes nothing, which is the opposite of true.
        boolean unlearnt = rank < 1;
        boolean shows = canRaise && next <= skill.maxRank();
        if (skill.damage() > 0f) {
            row(out, skill.key(), words.damage(),
                    unlearnt ? "" : round(skill.damageAt(rank)),
                    shows ? round(skill.damageAt(next)) : "");
        }
        if (skill.boostPercent() > 0) {
            row(out, skill.key(), words.boost(),
                    unlearnt ? "" : skill.boostAt(rank) + "%",
                    shows ? skill.boostAt(next) + "%" : "");
        }
        // Fixed for now, and drawn with one value so it reads as a fact rather
        // than as something he is buying.
        if (skill.radius() > 0f) {
            row(out, skill.key(), words.radius(), round(skill.radius()), "");
        }
        float reach = skill.range() > 0f ? skill.range() : skill.distance();
        if (reach > 0f) {
            row(out, skill.key(), words.range(), round(reach), "");
        }
        // What it costs, and what the next point would do to that -- which may be
        // either way round. A cost that climbs makes an upgraded ultimate
        // something to save for; one that falls makes it something to lean on,
        // and the arrow is how a player finds out which this is BEFORE spending
        // the point rather than after.
        if (skill.manaAt(Math.max(1, rank)) > 0) {
            row(out, skill.key(), words.mana(),
                    unlearnt ? "" : String.valueOf(skill.manaAt(rank)),
                    shows && skill.manaCostPerLevel() != 0
                            ? String.valueOf(skill.manaAt(next)) : "");
        }
        // Seconds, not frames. Frames are what the simulation counts; turning
        // them into something a player can feel is a presentation decision and
        // belongs on this side of the line with the rest of them.
        String now = unlearnt ? "" : seconds(skill.cooldownAt(rank), words);
        String then = shows && skill.cooldownPerLevel() != 0
                ? seconds(skill.cooldownAt(next), words) : "";
        row(out, skill.key(), words.cooldown(), now, then);
    }

    private static void row(StringBuilder out, char key, String label, String now, String next) {
        if (now.isEmpty() && next.isEmpty()) {
            return;
        }
        out.append("|tipRow=").append(key).append(',').append(label)
                .append(',').append(now).append(',').append(next);
    }

    private static String seconds(int frames, Words words) {
        float value = frames / (float) GameConstants.LOGICFRAMES_PER_SECOND;
        return String.format(java.util.Locale.ROOT, "%.1f", value) + words.seconds();
    }

    /** Whole numbers: a player reads 48, not 48.0, and never 47.99999. */
    private static String round(float value) {
        return String.valueOf(Math.round(value));
    }
}
