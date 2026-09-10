package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // ---- keys that wait to be pointed at something ----

    /**
     * A plain key acts on the press; the other two go quiet and wait for a click.
     *
     * <p>Which is the whole difference the client needs to know about. What is done
     * with the creature or the spot once it is chosen stays the game's, and this
     * class never looks inside it.
     */
    @Test
    void aKeyKnowsWhetherItNeedsPointingAtSomething() {
        var keys = Hotkeys.create();
        keys.on('W', game -> { });
        keys.onUnit('Q', (game, id) -> { });
        keys.onGround('E', (game, spot) -> { });

        assertEquals(Hotkeys.Aim.NOW, keys.all().get('W').aim());
        assertEquals(Hotkeys.Aim.UNIT, keys.all().get('Q').aim());
        assertEquals(Hotkeys.Aim.GROUND, keys.all().get('E').aim());
    }

    /** An aimed key still takes its letter off the client's own controls. */
    @Test
    void anAimedKeyIsClaimedLikeAnyOther() {
        var keys = Hotkeys.create();
        keys.onGround('S', (game, spot) -> { });

        assertTrue(keys.claims(KeyInput.KEY_S));
        assertArrayEquals(new int[] {KeyInput.KEY_DOWN},
                keys.unclaimed(KeyInput.KEY_S, KeyInput.KEY_DOWN));
    }

    /** What the player pointed at reaches the game's own code, unchanged. */
    @Test
    void whatWasPointedAtIsHandedOn() {
        var chosen = new int[] {-1};
        var landed = new uz.duke.core.math.Coord3D[1];
        var keys = Hotkeys.create();
        keys.onUnit('Q', (game, id) -> chosen[0] = id);
        keys.onGround('E', (game, spot) -> landed[0] = spot);

        keys.all().get('Q').run().accept(null, new Hotkeys.Aimed(42, null));
        keys.all().get('E').run().accept(null,
                new Hotkeys.Aimed(0, new uz.duke.core.math.Coord3D(7f, 9f, 0f)));

        assertEquals(42, chosen[0]);
        assertEquals(7f, landed[0].x(), 0.001f);
        assertEquals(9f, landed[0].y(), 0.001f);
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

    // ---- choices the game puts on screen ----

    /**
     * A choice the client drew reaches the game, by position and nothing else.
     *
     * <p>The client knows there were three cards and which one was clicked; what
     * that means is the game's, so what crosses is an index. Keeping it to that is
     * what lets the seam serve a level-up screen without knowing what a level is.
     */
    @Test
    void aChoiceIsReportedByItsPosition() {
        var taken = new int[] {-1};
        var keys = Hotkeys.create().onChoose((game, index) -> taken[0] = index);

        keys.choose(null, 2);

        assertEquals(2, taken[0]);
    }

    /** A game that offers nothing binds nothing, and a stray click does nothing. */
    @Test
    void aGameThatOffersNothingIgnoresAChoice() {
        Hotkeys.none().choose(null, 1); // must not throw
    }

    /**
     * The letters a game claimed, readable by the game itself.
     *
     * <p>What a key does stays the client's — the same work whether the letter was
     * pressed or the slot on the bar was clicked — but which letters were claimed,
     * and what each asks to be pointed at, is something a game may check against
     * its own data file.
     */
    @Test
    void aGameCanReadBackTheKeysItClaimed() {
        var keys = Hotkeys.create()
                .on('q', game -> { })
                .onUnit('w', (game, id) -> { })
                .onGround('e', (game, spot) -> { });

        assertEquals(java.util.List.of('Q', 'W', 'E'),
                java.util.List.copyOf(keys.claimedKeys()), "in the order they were claimed");
        assertEquals(Hotkeys.Aim.NOW, keys.aimOf('Q'));
        assertEquals(Hotkeys.Aim.UNIT, keys.aimOf('w'), "asked for in either case");
        assertEquals(Hotkeys.Aim.GROUND, keys.aimOf('E'));
        assertNull(keys.aimOf('Z'), "a letter nobody claimed");
    }
}
