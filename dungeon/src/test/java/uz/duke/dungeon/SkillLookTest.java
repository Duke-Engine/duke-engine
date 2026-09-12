package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.client3d.Visuals;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * What every skill looks like going off, checked against what the client can
 * actually draw.
 *
 * <p>Two files that have to agree and that nothing makes agree. A skill names a
 * {@code DungeonEffect} block by a string; a block that is not there is a skill
 * with no effect, and a block that is there but describes nothing is a skill with
 * an effect that draws nothing. Neither refuses at run time — both are a skill
 * that goes off in silence, which reads as a bug in the skill rather than as a
 * missing line in a data file, and so is looked for in the wrong place.
 *
 * <p>Nothing here says anything looks good. What it pins is the handful of ways
 * this arrangement fails quietly, plus two shapes worth holding still: an effect
 * is SHORT, and the knock is SMALL.
 */
class SkillLookTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /**
     * How long an effect may run.
     *
     * <p>A ceiling rather than a target, and a low one: the whole of a cast is
     * over before the next thing the player does. An effect he has to wait out is
     * one he learns to stop looking at, and the way this goes wrong is somebody
     * being pleased with a ring and slowing it down until it is furniture.
     *
     * <p>A standing GROUND_MARK is exempt and says so below — it is not an effect
     * so much as a promise, and its whole point is the wait.
     */
    private static final float LONGEST_SECONDS = 0.8f;

    /** And how hard the camera may be knocked. See {@link #theKnockIsSmall}. */
    private static final float HARDEST_KNOCK = 2f;

    // ---- the two files agree ----

    /** Every skill in the shipped file says what it looks like. */
    @Test
    void everySkillSaysWhatItLooksLike() {
        assertFalse(SETTINGS.skills().isEmpty(), "the shipped file describes no skills at all");
        for (var skill : SETTINGS.skills()) {
            assertTrue(skill.hasLook(), skill.heroTemplate() + "'s " + skill.key()
                    + " goes off in silence: no Look line, so nothing is drawn for it");
        }
    }

    /** And what it names is a block that is really there. */
    @Test
    void everyLookNamedIsABlockThatExists() {
        var described = SETTINGS.effects().stream().map(look -> look.name()).toList();
        for (var skill : SETTINGS.skills()) {
            assertTrue(described.contains(skill.look()), skill.heroTemplate() + "'s "
                    + skill.key() + " looks like " + skill.look() + ", which nothing describes");
        }
    }

    /** And that block draws something, rather than being a name with nothing behind it. */
    @Test
    void everyLookNamedDrawsSomething() {
        for (var skill : SETTINGS.skills()) {
            var look = lookOf(skill.look());
            boolean draws = look.kinds().contains(Visuals.EffectVisual.SHOCKWAVE)
                    || look.kinds().contains(Visuals.EffectVisual.GROUND_MARK)
                    || look.kinds().contains(Visuals.EffectVisual.IMPACT_BURST);
            assertTrue(draws, skill.heroTemplate() + "'s " + skill.key() + " names "
                    + look.name() + ", which draws none of the three things a cast can draw");
        }
    }

    /** A ring of no size is not a ring. */
    @Test
    void aRingHasSomewhereToOpenTo() {
        for (var look : SETTINGS.effects()) {
            if (!look.kinds().contains(Visuals.EffectVisual.SHOCKWAVE)) {
                continue;
            }
            assertTrue(look.waveTo() > 0f || namedByASkillWithARadius(look.name()),
                    look.name() + " is a ring that opens to nothing, and no skill that uses"
                            + " it hands it a width of its own either");
            assertTrue(look.waveSeconds() > 0f, look.name() + " opens in no time at all");
        }
    }

    /** And a mark that stays for no time does not stay. */
    @Test
    void aStandingMarkStaysForSomeTime() {
        for (var look : SETTINGS.effects()) {
            if (!look.kinds().contains(Visuals.EffectVisual.GROUND_MARK)) {
                continue;
            }
            assertTrue(look.markSeconds() > 0f,
                    look.name() + " is a standing mark that stands for no time");
        }
    }

    // ---- and the two shapes worth holding still ----

    /**
     * Nothing a skill draws outlives the moment it was drawn for.
     *
     * <p>The GROUND_MARK is the exception and is asked about separately: it is
     * the one thing here whose whole point is that it lasts, because a meteor
     * nobody can walk out of is a meteor with no skill in it.
     */
    @Test
    void everyEffectIsOverQuickly() {
        for (var look : SETTINGS.effects()) {
            if (look.kinds().contains(Visuals.EffectVisual.SHOCKWAVE)) {
                assertTrue(look.waveSeconds() <= LONGEST_SECONDS, look.name() + "'s ring runs "
                        + look.waveSeconds() + "s, and anything past " + LONGEST_SECONDS
                        + "s stops being an effect and becomes furniture");
            }
            assertTrue(look.burstSeconds() <= LONGEST_SECONDS,
                    look.name() + "'s burst runs " + look.burstSeconds() + "s");
            assertTrue(look.shakeSeconds() <= LONGEST_SECONDS,
                    look.name() + " shakes the camera for " + look.shakeSeconds() + "s");
        }
    }

    /**
     * The knock is felt rather than seen.
     *
     * <p>A ceiling because this is the one number on the page with a real cost if
     * it is got wrong: a shake that can be SEEN is a shake a player asks you to
     * turn off, and past a point it is one that cannot be played through at all.
     * Every block in the shipped file is well under this; what the test is for is
     * the day somebody decides a meteor should really land.
     */
    @Test
    void theKnockIsSmall() {
        for (var look : SETTINGS.effects()) {
            assertTrue(look.shakePower() <= HARDEST_KNOCK, look.name() + " knocks the camera "
                    + look.shakePower() + " units, and past " + HARDEST_KNOCK
                    + " it is an earthquake rather than an impact");
        }
    }

    /** Nothing that opens a ring forgets to say what colour it is. */
    @Test
    void everyRingIsSomeColour() {
        for (var skill : SETTINGS.skills()) {
            var look = lookOf(skill.look());
            assertFalse(look.colour() == 0x000000, look.name()
                    + " is drawn in black, which in a dark room is drawn not at all");
        }
    }

    // ---- and none of it reaches the fight ----

    /**
     * The look a skill names cannot change what the skill does.
     *
     * <p>The same claim {@code ProjectileEffectTest} makes about the burning, and
     * it has to be made again here because the {@code Look} line is on the SKILL
     * block rather than on an effect block — it travels in the same record as the
     * damage and the cooldown, and a record the simulation reads is a record that
     * can change what happens, with no way to tell by looking.
     */
    @Test
    void theLookCannotReachTheSimulation() {
        var with = DungeonSettings.load();
        var without = DungeonSettings.parse(withoutTheLooks(Content.read(Content.SETTINGS)));

        assertTrue(with.skills().stream().allMatch(skill -> skill.hasLook()));
        assertTrue(without.skills().stream().noneMatch(skill -> skill.hasLook()),
                "the stripped file still names looks");
        assertEquals(signature(with), signature(without),
                "a floor played differently once the skills were drawn with nothing");
    }

    /** The same file with every skill's Look line cut out of it. */
    private static String withoutTheLooks(String file) {
        var kept = new StringBuilder();
        for (var line : file.split("\n", -1)) {
            if (!line.trim().startsWith("Look = ")) {
                kept.append(line).append('\n');
            }
        }
        return kept.toString();
    }

    /** Two floors of one seed, as the simulation itself counts them. */
    private static String signature(DungeonSettings settings) {
        var session = Dungeon.newSession(31L, settings);
        var game = session.game();
        game.runHeadless(1);
        var line = new StringBuilder();
        for (int frame = 0; frame < 90; frame++) {
            game.runHeadless(1);
            line.append(game.getLogic().checksum()).append('|');
        }
        return line.toString();
    }

    private static DungeonSettings.EffectLook lookOf(String name) {
        return SETTINGS.effects().stream().filter(look -> look.name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("no block called " + name));
    }

    /** Whether some skill hands this ring a width of its own at the cast. */
    private static boolean namedByASkillWithARadius(String name) {
        return SETTINGS.skills().stream()
                .anyMatch(skill -> name.equals(skill.look()) && skill.radius() > 0f);
    }
}
