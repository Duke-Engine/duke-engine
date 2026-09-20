package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The two rules a bar over a creature's head is drawn by.
 *
 * <p>Both of them are arithmetic on a table, and both go wrong quietly: a bar
 * that is a few pixels short is nothing anybody notices, and a table that hands
 * out four marks instead of twelve looks like a deliberate choice rather than
 * like a rung being in the wrong place. Neither would fail anything.
 *
 * <p>What is checked here is the <em>rules</em>, against a table made up for the
 * purpose. Whether the table this game actually ships obeys them is a question
 * about the game's own file and is asked in {@code uz.dukeengine.dungeon} — see
 * {@code DungeonUnitBarTest}.
 */
class UnitBarLookTest {

    /** A table of the shape the game ships, small enough to reason about by hand. */
    private static final UnitBarLook LOOK = new UnitBarLook(
            List.of(new UnitBarLook.Step(100, 10),
                    new UnitBarLook.Step(500, 25),
                    new UnitBarLook.Step(0, 100)),
            30, 1400, 80f, 220f,
            13f, 5f, 2f, 1.4f,
            26f, 2f, 4f, 3f,
            0xA8322B, 0x8FC4AE, 0x3E6FA8, 0x16130F, 0x0A0806,
            0x16130F, 0x8FC4AE, 0xE8A33D, 0xD9CFBA,
            11f, 15f, 10f, 12f);

    // ---- what one mark is worth ----

    /**
     * The rung is chosen by the creature's size, and its ceiling is inclusive.
     *
     * <p>The boundary is the half of this worth testing. A rung that is exclusive
     * at the top leaves exactly one health value falling through to the next
     * rung, which is a creature whose marks are four times coarser than the one
     * beside it and no way to see why.
     */
    @Test
    void theRungIsPickedByHowBigTheCreatureIs() {
        assertEquals(10, LOOK.valueFor(60f));
        assertEquals(10, LOOK.valueFor(100f), "the ceiling belongs to its own rung");
        assertEquals(25, LOOK.valueFor(101f));
        assertEquals(25, LOOK.valueFor(500f));
        assertEquals(100, LOOK.valueFor(501f));
    }

    /**
     * Past the end of the table the last rung goes on applying.
     *
     * <p>The alternative is no marks at all on the one creature somebody built to
     * be bigger than anything planned for — which is a boss, and the bar it needs
     * most.
     */
    @Test
    void aCreatureBiggerThanTheTableStillGetsMarks() {
        assertEquals(100, LOOK.valueFor(9_000f));
        assertTrue(LOOK.segmentsFor(9_000f) > 0);
    }

    /**
     * The value is the table's, not the creature's.
     *
     * <p>This is the whole reason there is a table. Two monsters of different
     * sizes on the same rung are marked off in the same lots, so a player who has
     * counted one can read the other — and that stops being true the moment
     * anything scales its marks to its own maximum.
     */
    @Test
    void twoCreaturesOnOneRungAreMarkedTheSameWay() {
        assertEquals(LOOK.valueFor(150f), LOOK.valueFor(480f));
        assertEquals(6, LOOK.segmentsFor(150f), "150 in lots of 25");
        assertEquals(19, LOOK.segmentsFor(480f), "480 in the same lots");
    }

    /** A part-earned mark is not drawn: see the note on rounding down. */
    @Test
    void aMarkIsOnlyDrawnWhenItIsWhole() {
        assertEquals(6, LOOK.segmentsFor(174f), "six lots of twenty-five and a bit over");
        assertEquals(7, LOOK.segmentsFor(175f));
    }

    // ---- how long the bar is ----

    /**
     * A bigger creature never has a shorter bar.
     *
     * <p>The reason the length is not worked out from the marks. That version
     * read {@code marks × a fixed spacing}, which is shorter to write and makes
     * the two rules agree by construction — and it inverts at every rung
     * boundary, because a stepped table is not monotonic. Here the whole real
     * range is walked rather than a few chosen points, since an inversion is one
     * step wide and lands wherever the rungs happen to fall.
     */
    @Test
    void aBiggerCreatureNeverHasAShorterBar() {
        float was = 0f;
        for (int health = 1; health <= 3_000; health++) {
            float now = LOOK.widthFor(health);
            assertTrue(now >= was,
                    "a bar shrank between " + (health - 1) + " and " + health
                            + " health: " + was + " then " + now);
            was = now;
        }
    }

    /**
     * It stops growing at both ends, and the anchors are where it stops.
     *
     * <p>Both ends matter and for different reasons. The floor is so that the
     * weakest thing in the game still has something to look at; the ceiling is so
     * that a creature nobody planned for cannot draw a bar across the screen.
     */
    @Test
    void theBarStopsGrowingAtBothEnds() {
        assertEquals(80f, LOOK.widthFor(1f), 0.01f);
        assertEquals(80f, LOOK.widthFor(30f), 0.01f, "the anchor itself is the floor");
        assertEquals(220f, LOOK.widthFor(1_400f), 0.01f);
        assertEquals(220f, LOOK.widthFor(50_000f), 0.01f);
    }

    /**
     * The curve is a ratio, not a proportion.
     *
     * <p>What that means on screen: the halfway point of the bar is the
     * <em>geometric</em> middle of the two anchors, so doubling a creature's
     * health moves its bar by the same amount wherever it started. Against a
     * forty-six-fold range that is the difference between a readable scale and
     * one where everything below a boss is the same stub.
     */
    @Test
    void theLengthIsLogarithmicRatherThanProportional() {
        float middle = (float) Math.sqrt(30.0 * 1400.0); // ~205 health
        assertEquals(150f, LOOK.widthFor(middle), 0.5f,
                "the geometric middle sits at the middle of the bar");
        assertEquals(LOOK.widthFor(120f) - LOOK.widthFor(60f),
                LOOK.widthFor(480f) - LOOK.widthFor(240f), 0.5f,
                "a doubling is worth the same width wherever it starts");
    }

    /** Spacing falls out of the other two, which is what a repeating texture needs. */
    @Test
    void theSpacingIsTheLengthDividedByTheMarks() {
        assertEquals(LOOK.widthFor(300f) / LOOK.segmentsFor(300f),
                LOOK.tickSpacingFor(300f), 0.001f);
    }

    // ---- a game that has none of this ----

    /**
     * A client with no table draws nothing rather than something invented.
     *
     * <p>Three other games launch this client and none of them has a dungeon's
     * idea of what a creature is worth. A default table would be the client
     * claiming to know, and it would be marks on a bar that mean nothing.
     */
    @Test
    void aGameThatNamesNoTableGetsNoBars() {
        assertFalse(UnitBarLook.NONE.draws());
        assertEquals(0, UnitBarLook.NONE.valueFor(500f));
        assertEquals(0, UnitBarLook.NONE.segmentsFor(500f));
        assertFalse(UnitBarLook.NONE.hasMana());
    }

    /** And one that says everything but the mana draws the health bar alone. */
    @Test
    void aGameWithNoManaDrawsTheHealthBarOnly() {
        var dry = new UnitBarLook(LOOK.steps(), LOOK.shortestAt(), LOOK.longestAt(),
                LOOK.shortest(), LOOK.longest(), LOOK.height(), 0f, LOOK.gap(),
                LOOK.lift(), LOOK.ring(), LOOK.ringEdge(), LOOK.ringGap(), LOOK.arc(),
                LOOK.enemy(), LOOK.friend(), LOOK.mana(), LOOK.trough(), LOOK.tick(),
                LOOK.ringFace(), LOOK.ringRim(), LOOK.bossRim(),
                LOOK.lettering(), LOOK.nameSize(), LOOK.bossNameSize(), LOOK.countSize(),
                LOOK.levelSize());

        assertTrue(dry.draws());
        assertFalse(dry.hasMana());
    }

    /** Whose it is decides the fill, and whether it is the boss decides the rim. */
    @Test
    void sideAndRankAreReadOffTheColours() {
        assertEquals(UnitBarLook.colour(0x8FC4AE), LOOK.fill(true));
        assertEquals(UnitBarLook.colour(0xA8322B), LOOK.fill(false));
        assertEquals(UnitBarLook.colour(0xE8A33D), LOOK.rim(true), "a boss is torch-lit");
        assertEquals(UnitBarLook.colour(0x8FC4AE), LOOK.rim(false));
        assertTrue(LOOK.nameSize(true) > LOOK.nameSize(false));
    }
}
