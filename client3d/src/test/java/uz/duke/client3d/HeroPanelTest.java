package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What the hero's panel will and will not draw.
 *
 * <p>The drawing itself needs a window and cannot be tested here. What can be —
 * and what actually breaks — is the line the game sends: the panel is the only
 * thing that reads it, the game is the only thing that writes it, and neither
 * compiler sees the other. So this holds the format still from the reading end,
 * and {@code uz.duke.dungeon.run.HeroStatusTest} holds it from the writing end;
 * change one side and one of the two goes red.
 *
 * <p>The other half is the promise to every game that is not a dungeon. Three
 * other apps launch this client, and the status channel is free-form — a line
 * this panel does not understand has to leave it hidden rather than half-drawn.
 */
class HeroPanelTest {

    /** A line of the kind the dungeon sends, with all three skill states in it. */
    private static final String LINE =
            "name=Erika|rank=7-daraja|hp=128/200|xp=38/100|depth=III|depthWord=CHUQURLIK"
                    + "|skill=Q,ready|skill=W,cool,72,165|skill=E,ready|skill=R,lock,5-daraja";

    @Test
    void aDungeonLineIsRead() {
        var reading = HeroPanel.Reading.parse(LINE);

        assertNotNull(reading, "the panel should have understood its own game's line");
        assertEquals("Erika", reading.name());
        assertEquals("7-daraja", reading.rank());
        assertEquals(128f, reading.health(), 0.001f);
        assertEquals(200f, reading.maxHealth(), 0.001f);
        assertEquals(38f, reading.experience(), 0.001f);
        assertEquals("III", reading.depth());
        assertEquals("CHUQURLIK", reading.depthWord());
        assertEquals(4, reading.skills().size());
    }

    /** Each of the three states a slot can be in, and what it needs to draw it. */
    @Test
    void eachSkillStateIsRead() {
        var skills = HeroPanel.Reading.parse(LINE).skills();

        assertEquals(HeroPanel.Reading.State.READY, skills.get(0).state());
        assertEquals('Q', skills.get(0).key());

        var cooling = skills.get(1);
        assertEquals(HeroPanel.Reading.State.COOLING, cooling.state());
        // 72 frames of 30 is 2.4 seconds, and 72 of 165 is how much shadow is left.
        assertEquals("2.4", cooling.label());
        assertEquals(72f / 165f, cooling.left(), 0.001f);

        var locked = skills.get(3);
        assertEquals(HeroPanel.Reading.State.LOCKED, locked.state());
        assertEquals("5-daraja", locked.label(),
                "the level it waits for is the game's words, not the client's");
    }

    /**
     * Long cooldowns lose the decimal. "27.3" seconds is a number nobody reads at
     * a glance, and the slot it has to fit inside is sixty pixels wide.
     */
    @Test
    void aLongCooldownIsRoundedToWholeSeconds() {
        var skills = HeroPanel.Reading.parse(
                "name=E|rank=1|hp=1/1|xp=0/1|depth=I|depthWord=D|skill=R,cool,820,900").skills();

        assertEquals("27", skills.get(0).label());
    }

    /** Anything that is not this format leaves the panel out of it. */
    @Test
    void anotherGamesStatusIsNotTakenOver() {
        assertNull(HeroPanel.Reading.parse(null));
        assertNull(HeroPanel.Reading.parse(""));
        assertNull(HeroPanel.Reading.parse("Wave 4    2 bases left"));
        assertNull(HeroPanel.Reading.parse("name=Erika|morale=high"),
                "a field this panel cannot draw means the line was never meant for it");
    }

    /** A malformed line of the right shape is refused rather than half-drawn. */
    @Test
    void aBrokenLineIsRefused() {
        assertNull(HeroPanel.Reading.parse("name=Erika|hp=lots/200"));
        assertNull(HeroPanel.Reading.parse("name=Erika|hp=200"));
        assertNull(HeroPanel.Reading.parse("name=Erika|skill=Q,melted"));
        assertNull(HeroPanel.Reading.parse("name=Erika|skill=Q,cool,72"),
                "a cooldown without its total has no fraction to sweep");
    }

    /**
     * Skills are drawn in the order they arrive, since that is the order the file
     * lists them in and the order the keys sit on the keyboard.
     */
    @Test
    void slotsKeepTheOrderTheyWereSentIn() {
        var skills = HeroPanel.Reading.parse(LINE).skills();

        var keys = new StringBuilder();
        skills.forEach(skill -> keys.append(skill.key()));
        assertEquals("QWER", keys.toString());
    }

    /** A hero with no skills at all is still a hero with health. */
    @Test
    void aPanelWithoutSkillsIsStillAPanel() {
        var reading = HeroPanel.Reading.parse(
                "name=Erika|rank=1-daraja|hp=550/550|xp=0/30|depth=I|depthWord=CHUQURLIK");

        assertNotNull(reading);
        assertTrue(reading.skills().isEmpty());
    }
}
