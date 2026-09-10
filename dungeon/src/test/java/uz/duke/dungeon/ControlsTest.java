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

        for (var skill : SHIPPED.skills()) {
            var aim = controls.aimOf(skill.key());
            assertNotNull(aim, "no binding for " + skill.key());
            assertEquals(switch (skill.effect().aim()) {
                case UNIT -> Hotkeys.Aim.UNIT;
                case OPEN_GROUND -> Hotkeys.Aim.OPEN_GROUND;
                case SELF -> Hotkeys.Aim.NOW;
            }, aim, skill.key() + " asks for the wrong thing");
        }
    }

    /** A hero the file invented gets his keys the same way, with no Java at all. */
    @Test
    void aSecondHerosKeysComeFromTheFileToo() {
        var settings = DungeonSettings.parse("""
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
}
