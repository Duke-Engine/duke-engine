package uz.duke.dungeon.run;

import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.level.HeroProgress;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.Skills;

/**
 * Everything the hero's panel shows, as one line of the status channel.
 *
 * <p>The channel is a string the engine carries and never reads, so this is the
 * game talking to its own client and the format is theirs to agree on. It is read
 * by {@code uz.duke.client3d.HeroPanel}, and the two have to be changed together.
 *
 * <pre>
 * name=Erika|rank=7-daraja|hp=128/200|xp=38/100|depth=III|depthWord=CHUQURLIK
 *   |skill=Q,ready|skill=W,cool,72,165|skill=E,ready|skill=R,lock,5-daraja
 * </pre>
 *
 * <p>The split between the two halves is: whatever is <em>words</em> is finished
 * here, and whatever is <em>drawn</em> is sent as numbers. So the client never
 * writes a word of its own — it has three other games to serve and no business
 * knowing which language this one speaks — and this class never decides how wide
 * a bar is or how much of a slot is still in shadow.
 *
 * <p>Cooldowns cross as frames, not seconds. Frames are what the simulation
 * counts; the conversion is a presentation decision and belongs on the far side
 * with the rest of them.
 */
final class HeroStatus {

    private HeroStatus() {
    }

    /** The line, or the empty string if there is no hero to describe. */
    static String of(GameObject hero, HeroProgress progress, int depth,
            DungeonSettings settings) {
        if (hero == null || hero.getBody() == null) {
            return "";
        }
        int level = progress.getLevel();
        var line = new StringBuilder()
                .append("name=").append(nameOf(hero))
                .append("|rank=").append(level).append(settings.hudRankSuffix())
                .append("|hp=").append(Math.round(hero.getBody().getHealth()))
                .append('/').append(Math.round(hero.getBody().getMaxHealth()))
                .append("|xp=").append(progress.getExperienceIntoLevel())
                .append('/').append(progress.getExperienceForNextLevel())
                .append("|depth=").append(roman(depth))
                .append("|depthWord=").append(settings.hudDepthWord());
        var book = hero.findModule(SkillBook.class);
        if (book != null) {
            line.append(Skills.slots(book, level, settings.hudRankSuffix()));
        }
        return line.toString();
    }

    /** What the player calls him, falling back to what the code calls him. */
    private static String nameOf(GameObject hero) {
        var display = hero.getTemplate().getDisplayName();
        return display == null || display.isBlank() ? hero.getTemplate().getName() : display;
    }

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] NUMERALS = {
        "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I",
    };

    /**
     * Depth in Roman numerals, because it is the one number in the game that only
     * ever goes up, and a numeral says that in a way a digit does not.
     *
     * <p>Anything a numeral cannot say — nothing at all, or more floors than Rome
     * could count — is given back as a digit rather than as a wrong numeral.
     */
    static String roman(int depth) {
        if (depth < 1 || depth > 3999) {
            return String.valueOf(depth);
        }
        var numeral = new StringBuilder();
        int left = depth;
        for (int i = 0; i < VALUES.length; i++) {
            while (left >= VALUES[i]) {
                numeral.append(NUMERALS[i]);
                left -= VALUES[i];
            }
        }
        return numeral.toString();
    }
}
