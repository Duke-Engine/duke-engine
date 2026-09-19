package uz.duke.dungeon.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * What the card over a slot says, and — the only claim it really makes — that
 * what it shows changing is what actually changes.
 *
 * <p>The way a tooltip goes wrong is not by crashing. It goes wrong by promising
 * a number that does not move, or by showing the same figure on both sides of an
 * arrow, and a player who spends a level on the strength of it has no way to find
 * out he was lied to except by spending another.
 */
class SkillTipTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The skills a player casts: a hero's. A monster's has no key, no card and no look. */
    private static java.util.List<uz.duke.dungeon.skill.Skill> playersSkills() {
        var heroes = SETTINGS.heroes().stream().map(uz.duke.dungeon.content.HeroLook::name)
                .collect(java.util.stream.Collectors.toSet());
        return SETTINGS.skills().stream().filter(skill -> heroes.contains(skill.heroTemplate()))
                .toList();
    }

    private static final SkillTip.Words WORDS = new SkillTip.Words(
            "Zarar", "Kuluar", "Radius", "Masofa", "Kuch",
            "1 nuqta", "Ctrl+", "eng yuqori", "nuqta yo'q", "-daraja", "s", "Mana");

    private static Skill fireball() {
        return SETTINGS.skillsFor("Mage").stream().filter(skill -> skill.key() == 'Q')
                .findFirst().orElseThrow();
    }

    // ---- what it says ----

    /** A skill he owns and can raise shows both figures, and an offer at the foot. */
    @Test
    void aRaisableSkillShowsNowAndNext() {
        var tip = SkillTip.of(fireball(), 2, true, WORDS);

        assertTrue(tip.contains("|tipName=Q,"), "it has no name: " + tip);
        assertTrue(tip.contains("|tipAt=Q,2-daraja · Q"),
                "it should say which rank it is at and which key it is on");
        assertTrue(tip.contains("|tipRow=Q,Zarar,"), "no damage row");
        assertTrue(tip.contains("|tipFoot=Q,Ctrl+Q · 1 nuqta"),
                "the foot should name the keys that buy it: " + tip);
        // 55 at the first rank and +9 a rank, so 64 at the second and 73 at the
        // third -- growth is counted from the FIRST rank, not from nothing.
        assertTrue(tip.contains("|tipRow=Q,Zarar,64,73"),
                "the damage row should be this rank and the next: " + tip);
    }

    /**
     * A figure that does not move is shown once, not as an arrow to itself.
     *
     * <p>An arrow between two equal numbers reads as a promise, and a tooltip
     * that makes one it does not keep is worse than one that says nothing:
     * the player learns to stop reading it.
     */
    @Test
    void aFigureThatDoesNotMoveIsShownOnce() {
        var tip = SkillTip.of(fireball(), 2, true, WORDS);

        // Radius is fixed for now -- the row is written with nothing after the
        // comma, which is the client's cue to draw one value rather than two.
        assertTrue(tip.contains("|tipRow=Q,Radius,26,"), "radius is promising growth: " + tip);
        assertFalse(tip.matches("(?s).*\\|tipRow=Q,Radius,26,26.*"),
                "radius points an arrow at its own value");
    }

    /** At nothing yet, the first column is blank: a point buys the whole figure. */
    @Test
    void anUnlearntSkillShowsOnlyWhatItWouldBe() {
        var tip = SkillTip.of(fireball(), 0, true, WORDS);

        assertTrue(tip.contains("|tipRow=Q,Zarar,,55"),
                "an unbought skill should show nothing now and its first rank next: " + tip);
        assertTrue(tip.contains("|tipAt=Q,Q"),
                "an unbought skill still has to say which key it is on: " + tip);
    }

    /** Full, and the foot says so instead of offering. */
    @Test
    void aFullSkillOffersNothing() {
        var fireball = fireball();
        var tip = SkillTip.of(fireball, fireball.maxRank(), false, WORDS);

        assertTrue(tip.contains("|tipFoot=Q,eng yuqori"), "the foot should say it is full");
        assertFalse(tip.matches("(?s).*\\|tipRow=Q,Zarar,\\d+,\\d+.*"),
                "a full skill is still showing something it would grow into: " + tip);
    }

    /** With no point to spend, the foot says that rather than offering. */
    @Test
    void withNoPointTheFootSaysSo() {
        var tip = SkillTip.of(fireball(), 2, false, WORDS);

        assertTrue(tip.contains("|tipFoot=Q,nuqta yo'q"));
    }

    // ---- against the file the game ships ----

    /** Every skill in the file is named and described. */
    @Test
    void everySkillIsNamedAndDescribed() {
        for (var skill : playersSkills()) {
            assertFalse(skill.name().isBlank(), skill.heroTemplate() + "'s " + skill.key()
                    + " has no name, so its card is headed by nothing");
            assertFalse(skill.blurb().isBlank(), skill.heroTemplate() + "'s " + skill.key()
                    + " has no description, so the player is shown figures for a skill"
                    + " nobody has told him about");
        }
    }

    /** And no description smuggles a number into itself. */
    @Test
    void noDescriptionCarriesItsOwnFigures() {
        for (var skill : playersSkills()) {
            assertFalse(skill.blurb().matches(".*\\d+.*"), skill.heroTemplate() + "'s "
                    + skill.key() + " writes a figure into its description: \""
                    + skill.blurb() + "\". Figures come out of the rank and go stale the"
                    + " moment anything is retuned; the sentence must say what it DOES");
        }
    }

    /** And every one of them has something to show that grows. */
    @Test
    void everySkillHasSomethingWorthBuying() {
        for (var skill : playersSkills()) {
            boolean grows = skill.damagePerLevel() > 0f || skill.boostPerLevel() != 0
                    || skill.cooldownPerLevel() != 0;
            assertTrue(grows, skill.heroTemplate() + "'s " + skill.key()
                    + " is the same skill at every rank, so a point spent on it buys"
                    + " the player nothing he can see");
        }
    }

    /** A row's label may not carry a comma, which is what separates the fields. */
    @Test
    void noWordUsedInARowCarriesASeparator() {
        for (var word : new String[] {SETTINGS.hud().damageWord(), SETTINGS.hud().cooldownWord(),
                SETTINGS.hud().radiusWord(), SETTINGS.hud().rangeWord(), SETTINGS.hud().boostWord()}) {
            assertFalse(word.contains(","), "\"" + word + "\" has a comma in it, and a row is"
                    + " split on commas -- the card would be drawn with its halves shuffled");
            assertFalse(word.contains("|"), "\"" + word + "\" has a pipe in it, which ends"
                    + " the field and would take the rest of the line with it");
        }
    }
}
