package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.input.KeyInput;
import org.junit.jupiter.api.Test;

/**
 * A key belongs to one thing at a time.
 *
 * <p>This is a bug that shipped: the dungeon's W cast a skill <em>and</em> panned
 * the camera, because both were bound to it and an input manager fires every
 * binding a key has. Adding the game's mapping afterwards does not take the key
 * off the client — nothing is replaced, it is all added — so the client has to
 * leave a claimed key alone when it binds its own controls.
 */
class HotkeysTest {

    private static Hotkeys claiming(char... keys) {
        var hotkeys = Hotkeys.create();
        for (var key : keys) {
            hotkeys.on(key, game -> { });
        }
        return hotkeys;
    }

    /** A game that claims nothing leaves every control as it was. */
    @Test
    void anRtsWithNoKeysOfItsOwnKeepsTheStandardControls() {
        var none = Hotkeys.none();

        assertArrayEquals(new int[] {KeyInput.KEY_W, KeyInput.KEY_UP},
                none.unclaimed(KeyInput.KEY_W, KeyInput.KEY_UP));
        assertFalse(none.claims(KeyInput.KEY_W));
    }

    /** The claimed key goes, and only it. */
    @Test
    void aClaimedKeyIsTakenOffTheClientControl() {
        var dungeon = claiming('Q', 'W', 'E', 'R');

        assertTrue(dungeon.claims(KeyInput.KEY_W), "W casts a skill now");
        assertArrayEquals(new int[] {KeyInput.KEY_UP},
                dungeon.unclaimed(KeyInput.KEY_W, KeyInput.KEY_UP),
                "so panning up is left with the arrow");
    }

    /**
     * Which is why the camera survives a game taking WASD: every pan control has
     * an arrow behind it, and only the letter is given away.
     */
    @Test
    void theCameraStillPansOnTheArrows() {
        var takesWasd = claiming('W', 'A', 'S', 'D');

        assertArrayEquals(new int[] {KeyInput.KEY_UP},
                takesWasd.unclaimed(KeyInput.KEY_W, KeyInput.KEY_UP));
        assertArrayEquals(new int[] {KeyInput.KEY_LEFT},
                takesWasd.unclaimed(KeyInput.KEY_A, KeyInput.KEY_LEFT));
        assertArrayEquals(new int[] {KeyInput.KEY_DOWN},
                takesWasd.unclaimed(KeyInput.KEY_S, KeyInput.KEY_DOWN));
        assertArrayEquals(new int[] {KeyInput.KEY_RIGHT},
                takesWasd.unclaimed(KeyInput.KEY_D, KeyInput.KEY_RIGHT));
    }

    /** A control with one key and no fallback simply loses it, rather than sharing. */
    @Test
    void aControlWithNothingLeftBindsNothing() {
        var takesHalt = claiming('H');

        assertEquals(0, takesHalt.unclaimed(KeyInput.KEY_H).length,
                "sharing the key would fire both, which is the bug");
    }

    /** Keys are letters, however they were written. */
    @Test
    void aKeyIsTheSameKeyInEitherCase() {
        assertEquals(KeyInput.KEY_Q, Hotkeys.codeOf('q'));
        assertEquals(Hotkeys.codeOf('Q'), Hotkeys.codeOf('q'));
        assertTrue(claiming('q').claims(KeyInput.KEY_Q));
    }

    /** And anything that is not a letter is not a key this can bind. */
    @Test
    void whatIsNotALetterIsNotBound() {
        assertEquals(-1, Hotkeys.codeOf('1'));
        assertEquals(-1, Hotkeys.codeOf(' '));
    }

    /** The letters really are the letters, not an off-by-one through the table. */
    @Test
    void everyLetterMapsToItsOwnKey() {
        assertEquals(KeyInput.KEY_A, Hotkeys.codeOf('A'));
        assertEquals(KeyInput.KEY_M, Hotkeys.codeOf('M'));
        assertEquals(KeyInput.KEY_Z, Hotkeys.codeOf('Z'));
        assertEquals(KeyInput.KEY_W, Hotkeys.codeOf('W'));
        assertEquals(KeyInput.KEY_R, Hotkeys.codeOf('R'));
    }
}
