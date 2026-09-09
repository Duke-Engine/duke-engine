package uz.duke.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * The line the hero's panel is drawn from, written by a real run.
 *
 * <p>Its reader is {@code uz.duke.client3d.HeroPanel}, which this module cannot
 * see and which cannot see this. Nothing but a test connects them, so this holds
 * the writing end of the format and {@code HeroPanelTest} holds the reading end.
 *
 * <p>What is checked is the shape and where each part came from — that the name
 * is the one in the creature file, the words are the ones in {@code dungeon.ini},
 * and there is a slot for every skill the file gives him. Not the values: the
 * hero's health and the length of his cooldowns are balance, and balance is meant
 * to be tuned without a test objecting.
 */
class HeroStatusTest {

    /** One frame of a real dungeon, which is enough for the run to describe itself. */
    private static String lineFrom(long seed) {
        var session = Dungeon.newSession(seed);
        session.game().runHeadless(1);
        return session.game().getSnapshot().status();
    }

    @Test
    void aRunDescribesItsHero() {
        var line = lineFrom(4321L);

        assertTrue(line.startsWith("name="), "the panel reads nothing else: " + line);
        for (var field : new String[] {"rank=", "hp=", "xp=", "depth=", "depthWord="}) {
            assertTrue(line.contains("|" + field) || line.startsWith(field),
                    field + " missing from " + line);
        }
    }

    /**
     * The name is the one the file gives him, not the one the code calls him.
     *
     * <p>He is the {@code Hero} template everywhere in the game — the spawner, the
     * skill blocks, every test above this one — and {@code DisplayName} is the only
     * place that says what the player should call him.
     */
    @Test
    void heIsCalledWhateverTheCreatureFileCallsHim() {
        assertTrue(Content.read(Content.CREATURES).contains("DisplayName = Erika"),
                "the shipped hero should have a name of his own");
        assertTrue(lineFrom(11L).startsWith("name=Erika"), lineFrom(11L));
        assertFalse(lineFrom(11L).startsWith("name=Hero"),
                "that is his template, not his name");
    }

    /** One slot per skill the file gives him, in the file's order. */
    @Test
    void thereIsASlotForEverySkill() {
        var line = lineFrom(77L);
        var settings = DungeonSettings.load();

        assertEquals(settings.skills().size(), line.split("\\|skill=", -1).length - 1,
                "a slot each, no more and no fewer: " + line);
        for (var skill : settings.skills()) {
            assertTrue(line.contains("|skill=" + skill.key() + ","),
                    skill.key() + " has no slot in " + line);
        }
    }

    /**
     * A locked slot says what it is waiting for, in the game's words.
     *
     * <p>An ultimate is the one thing on the panel the player cannot use yet, so
     * it is the one slot that has to explain itself — and it explains itself in
     * whatever language {@code RankSuffix} is written in.
     */
    @Test
    void alockedSlotSaysWhatItWaitsFor() {
        var settings = DungeonSettings.load();
        var waiting = settings.skills().stream()
                .filter(skill -> !skill.unlockedAt(1))
                .findFirst().orElseThrow(() ->
                        new AssertionError("nothing is locked at level one any more"));

        assertTrue(lineFrom(5L).contains(
                        "|skill=" + waiting.key() + ",lock,"
                                + waiting.unlockLevel() + settings.hudRankSuffix()),
                "the locked slot should name its level the way the panel names them");
    }

    /** The panel's words are the file's — the client writes none of its own. */
    @Test
    void changingTheFileChangesTheWords() {
        var settings = DungeonSettings.parse("""
                DungeonHud Panel
                  DepthWord = FLOOR
                  RankSuffix = th level
                End
                """);

        assertEquals("FLOOR", settings.hudDepthWord());
        assertEquals("th level", settings.hudRankSuffix());
    }

    /**
     * Depth is a numeral, being the one number in the game that only goes up.
     *
     * <p>And anything a numeral cannot say comes back as a digit, because a wrong
     * numeral on the screen is worse than a plain number.
     */
    @Test
    void depthIsRoman() {
        assertEquals("I", HeroStatus.roman(1));
        assertEquals("IV", HeroStatus.roman(4));
        assertEquals("IX", HeroStatus.roman(9));
        assertEquals("XLII", HeroStatus.roman(42));
        assertEquals("MMMCMXCIX", HeroStatus.roman(3999));
        assertEquals("0", HeroStatus.roman(0));
        assertEquals("4000", HeroStatus.roman(4000));
    }
}
