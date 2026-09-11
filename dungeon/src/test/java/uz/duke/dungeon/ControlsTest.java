package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.client3d.Hotkeys;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * The keys the game claims, and what each of them asks the player to point at.
 *
 * <p>Why this is worth a test: there are two ways to cast a skill — press its
 * letter, or click its slot on the hero's bar — and they have to be the same
 * thing. They are, structurally: the click ends up at the client's own
 * {@code pressHotkey}, the same method a keypress reaches, so both walk into the
 * <em>one</em> binding registered here, both arm a skill that needs aiming, and
 * both end in {@code postCommand}. Nothing applies an effect on the render
 * thread.
 *
 * <p>What a test can hold still is the other half of that: that there is exactly
 * one binding per skill, that it came out of the data file, and that what it asks
 * to be pointed at follows from what the skill does. If a second, parallel way of
 * casting were ever added, it would have to come through here too.
 */
class ControlsTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    @Test
    void everySkillInTheFileGetsAKey() {
        var keys = Main.controls(SHIPPED).claimedKeys();

        for (var skill : SHIPPED.skills()) {
            assertTrue(keys.contains(skill.key()),
                    skill.key() + " is in the file but bound to nothing");
        }
        assertFalse(keys.isEmpty());
    }

    /**
     * What a key asks for follows from the effect, not from the key.
     *
     * <p>A strike is aimed at a creature whatever its numbers say, and there is no
     * useful sense in which one hero's dash is aimed and another's is not — so the
     * effect is what knows, and this checks that the binding took its word for it.
     */
    @Test
    void whatAKeyAsksForFollowsFromWhatTheSkillDoes() {
        var controls = Main.controls(SHIPPED);

        // The played hero's four. Both heroes cast on the same four keys, so the
        // file has two skills on Q and only one of them is bound.
        for (var skill : SHIPPED.skillsFor(SHIPPED.playedHero())) {
            var aim = controls.aimOf(skill.key());
            assertNotNull(aim, "no binding for " + skill.key());
            assertEquals(switch (skill.effect().aim()) {
                case UNIT -> Hotkeys.Aim.UNIT;
                case OPEN_GROUND -> Hotkeys.Aim.OPEN_GROUND;
                case SELF -> Hotkeys.Aim.NOW;
            }, aim, skill.key() + " asks for the wrong thing");
        }
    }

    /**
     * A hero the file invented gets his keys the same way, with no Java at all.
     *
     * <p>Two lines of file: who is being played, and what he can do. The first was
     * not needed while there was one hero and is the whole point now — keys belong
     * to whoever walks into the dungeon, because two heroes cast on the same four
     * letters and only one of them can have Q.
     */
    @Test
    void aSecondHerosKeysComeFromTheFileToo() {
        var settings = DungeonSettings.parse("""
                DungeonRun Loop
                  DefaultHero = Rogue
                End
                DungeonSkill Rogue Z
                  Effect = DASH
                  Distance = 40
                  CooldownFrames = 60
                End
                """);

        assertTrue(Main.controls(settings).claimedKeys().contains('Z'),
                "a skill nobody wrote Java for should still have a key");
        assertEquals(Hotkeys.Aim.OPEN_GROUND, Main.controls(settings).aimOf('Z'));
    }

    /**
     * And the hero who is <em>not</em> being played gets none of them.
     *
     * <p>The fault this is really about. Both of the shipped heroes cast on Q, W,
     * E and R, and what a key asks the player to point at follows from the skill
     * behind it — so binding every skill in the file would leave Q asking for
     * whatever the last hero read wanted. The archer's Q wants a creature and the
     * knight's wants a patch of floor, and the wrong one of those is not a small
     * wrongness: the click is refused and the skill never goes off.
     */
    @Test
    void theHeroWhoIsNotPlayedGetsNoKeys() {
        var settings = DungeonSettings.parse("""
                DungeonRun Loop
                  DefaultHero = Rogue
                End
                DungeonSkill Rogue Z
                  Effect = DASH
                  Distance = 40
                  CooldownFrames = 60
                End
                DungeonSkill Bard Y
                  Effect = AREA_DAMAGE
                  Radius = 20
                  CooldownFrames = 60
                End
                """);

        var controls = Main.controls(settings);

        assertTrue(controls.claimedKeys().contains('Z'));
        assertFalse(controls.claimedKeys().contains('Y'),
                "a hero nobody is playing took a key off the one who is");
    }

    /** The shipped file's two heroes really do share their four letters. */
    @Test
    void bothShippedHeroesCastOnTheSameFourKeys() {
        var archer = SHIPPED.skillsFor("Hero").stream().map(s -> s.key()).sorted().toList();
        var knight = SHIPPED.skillsFor("Knight").stream().map(s -> s.key()).sorted().toList();

        assertEquals(archer, knight,
                "if they stopped sharing keys, binding only the played hero's would be"
                        + " a precaution against nothing -- worth knowing either way");
        assertFalse(archer.isEmpty(), "the archer lost his skills");
    }
}
