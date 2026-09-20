package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which floor looks like what.
 *
 * <p>Two promises are worth holding still here and they pull in opposite
 * directions. The look has to be <em>settled</em> — the same seed shows the same
 * floor to every player, on every machine, and again tomorrow — and it has to be
 * <em>nothing but a look</em>, so that deciding it cannot move the world by a
 * step. The first is this file; the second is in {@code DungeonThemeTest}, which
 * plays the same seed twice under different themes and compares the checksums.
 *
 * <p>Nothing here asserts which theme is beautiful or which depth ought to wear
 * it. Those are in the file, and a re-ordering should move these tests with it
 * rather than break them — so what is checked is the mechanism: that the order is
 * followed, that running out of order does what the file says, and that a
 * variation is drawn rather than chosen.
 */
class ThemesTest {

    /** Which depth wears which: the map's. */
    private static final String ORDER = """
            ProceduralMap
              Themes = [Stone, Stone, Ice, Lava]
              WhenExhausted = Repeat
            End
            """;

    private static final String THREE = """
            Theme
              Name = Stone
              TileSize = 2
              Tones = [
                Tone
                  Name = Damp
                  Floor = Models/stone/damp.obj
                End
                Tone
                  Name = Dry
                  Floor = Models/stone/dry.obj
                End
              ]
            End
            Theme
              Name = Ice
              FogTint = 0x0A1830
              Tones = [
                Tone
                  Name = Blue
                  Floor = Models/ice/blue.obj
                End
              ]
            End
            Theme
              Name = Lava
              Tones = [
                Tone
                  Name = Hot
                  Floor = Models/lava/hot.obj
                End
              ]
            End
            """;

    private static Themes themes(String order, String themes) {
        return DungeonSettings.parse(order + themes).themes();
    }

    /** The order in the file is the order the depths wear. */
    @Test
    void depthFollowsTheOrderTheFileGives() {
        var themes = themes(ORDER, THREE);

        assertEquals("Stone", themes.nameFor(1));
        assertEquals("Stone", themes.nameFor(2));
        assertEquals("Ice", themes.nameFor(3));
        assertEquals("Lava", themes.nameFor(4));
    }

    /** Past the end of the list it begins again, when the file says so. */
    @Test
    void repeatComesRoundAgain() {
        var themes = themes(ORDER, THREE);

        assertEquals("Stone", themes.nameFor(5), "the fifth floor is the first again");
        assertEquals("Ice", themes.nameFor(7));
        assertEquals("Lava", themes.nameFor(12));
    }

    /** Or stays in the deepest look for ever, when it says that instead. */
    @Test
    void lastStaysInTheDeepestLook() {
        var themes = themes(ORDER.replace("WhenExhausted = Repeat", "WhenExhausted = Last"), THREE);

        assertEquals("Lava", themes.nameFor(4));
        assertEquals("Lava", themes.nameFor(5));
        assertEquals("Lava", themes.nameFor(400), "there is no bottom, and no fifth theme");
    }

    /** Re-order the file and the depths wear something else. The order is data. */
    @Test
    void changingTheFileChangesWhatADepthWears() {
        var themes = themes(ORDER.replace("[Stone, Stone, Ice, Lava]", "[Lava, Ice, Stone]"), THREE);

        assertEquals("Lava", themes.nameFor(1), "the first floor is now lava");
        assertEquals("Ice", themes.nameFor(2));
        assertEquals("Stone", themes.nameFor(3));
    }

    /**
     * One seed and one depth always give the same floor.
     *
     * <p>Which is the whole of the promise: two players down the same seed see the
     * same room built of the same stone, and a player who dies and plays it again
     * sees what he saw. Asked of a freshly parsed file each time, so it is a fact
     * about the numbers rather than about one object's memory.
     */
    @Test
    void oneSeedAndOneDepthAlwaysGiveTheSameFloor() {
        for (int depth = 1; depth <= 8; depth++) {
            var first = themes(ORDER, THREE).pick(4321L, depth);
            var again = themes(ORDER, THREE).pick(4321L, depth);

            assertNotNull(first, "depth " + depth + " should have a look");
            assertEquals(first.asStatus(), again.asStatus(),
                    "depth " + depth + " came out differently the second time");
        }
    }

    /**
     * And a theme worn twice in one run is not the same floor twice.
     *
     * <p>The variation is drawn from the depth as well as the seed. Without that,
     * every stone floor of a run would be the identical stone floor, and the
     * variations would be decoration nobody ever saw more than one of.
     */
    @Test
    void theSameThemeAtTwoDepthsCanVary() {
        var themes = themes(ORDER, THREE);
        var shallow = themes.pick(99L, 1);
        var deeper = themes.pick(99L, 5);

        assertEquals("Stone", shallow.theme().name());
        assertEquals("Stone", deeper.theme().name(), "both are stone");
        // Two tones and two floors: they need not differ, but over a spread of
        // seeds they must not always agree, or the depth is being ignored.
        boolean everDiffers = false;
        for (long seed = 0; seed < 40 && !everDiffers; seed++) {
            everDiffers = !themes.pick(seed, 1).tone().name()
                    .equals(themes.pick(seed, 5).tone().name());
        }
        assertTrue(everDiffers, "the same theme always drew the same tone, whatever the depth");
    }

    /** A different seed is a different dungeon, tones and all. */
    @Test
    void differentSeedsDrawDifferentVariations() {
        var themes = themes(ORDER, THREE);

        boolean everDiffers = false;
        for (long seed = 0; seed < 40 && !everDiffers; seed++) {
            everDiffers = !themes.pick(seed, 1).tone().name()
                    .equals(themes.pick(seed + 1, 1).tone().name());
        }
        assertTrue(everDiffers, "every seed drew the same tone, so it is not drawn at all");
    }

    /** A theme's own numbers and its variations are one block, the tones inside it. */
    @Test
    void aThemeIsAssembledFromItsThreeKindsOfBlock() {
        var stone = themes(ORDER, THREE).themeNamed("Stone");

        assertNotNull(stone);
        assertEquals(2f, stone.tileSize(), 0.001f);
        assertEquals(2, stone.tones().size(), "both of its tones, and neither of anyone else's");
        assertEquals(1, themes(ORDER, THREE).themeNamed("Ice").tones().size());
        assertEquals(0x0A1830, themes(ORDER, THREE).themeNamed("Ice").fogTint());
    }

    /** A tone's pieces are the whole paths its block writes, with no folder put in front. */
    @Test
    void aTonesPiecesAreThePathsItsBlockWrites() {
        var stone = themes(ORDER, THREE).themeNamed("Stone");

        var tone = stone.tones().get(0);
        assertEquals("Models/stone/damp.obj", tone.floor());
        assertNull(tone.wall(), "a tone that names no wall still names no wall");
    }

    /** A game that describes no themes gets none, and is drawn as it always was. */
    @Test
    void aFileWithNoThemesAsksForNothing() {
        var themes = themes("", "Run\n  RespawnDelayFrames = 60\nEnd\n");

        assertTrue(themes.isEmpty());
        assertNull(themes.pick(1L, 1), "nothing to pick from");
        assertNull(themes.nameFor(1));
    }

    /** An order naming a theme the file never described picks nothing, rather than guessing. */
    @Test
    void anOrderNamingSomethingUnknownDrawsNothing() {
        var themes = themes(ORDER.replace("[Stone, Stone, Ice, Lava]", "[Fog]"), THREE);

        assertEquals("Fog", themes.nameFor(1), "the order is still what the file said");
        assertNull(themes.pick(1L, 1), "but there is no such theme to draw");
        assertFalse(themes.isEmpty());
    }
}
