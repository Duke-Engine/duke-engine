package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * Each player's cells kept by chunks, and the cells in sight by the frame each was last covered, answer every question
 * as the cells kept whole did — frame after frame, looked at, marked seen and revealed — and a view taken is kept as it
 * was taken, sharing the chunks that did not change since.
 */
class SightCellsKeptTest {

    private static final float CELL = 10f;

    @Test
    void everyAnswerIsTheOneTheCellsKeptWholeGave() {
        var random = new SplittableRandom(4242);
        var kept = new SightCells(CELL, 20, 1500f, 1310f);
        var whole = new SightCellsBefore(CELL, 20, 1500f, 1310f);
        for (int frame = 0; frame < 240; frame++) {
            kept.begin(frame);
            whole.begin(frame);
            int looks = random.nextInt(12);
            for (int i = 0; i < looks; i++) {
                int player = random.nextInt(4);
                var at = new Coord3D((float) random.nextDouble(-100, 1600), (float) random.nextDouble(-100, 1400), 0f);
                float reach = (float) random.nextDouble(0, 160);
                kept.look(player, at, reach);
                whole.look(player, at, reach);
            }
            if (random.nextInt(60) == 0) {
                int player = random.nextInt(4);
                kept.markSeen(player);
                whole.markSeen(player);
            }
            if (frame == 200) {
                kept.reveal(3);
                whole.reveal(3);
            }
            for (int player = 0; player < 5; player++) {
                var view = kept.view(player);
                for (int cy = -1; cy <= kept.height(); cy++) {
                    for (int cx = -1; cx <= kept.width(); cx++) {
                        var before = whole.sight(player, cx, cy).ordinal();
                        assertEquals(before, kept.sight(player, cx, cy).ordinal(),
                                "frame " + frame + ", player " + player + ", cell " + cx + "," + cy);
                        assertEquals(before, view.at(cx, cy).ordinal(), "the view, frame " + frame);
                    }
                }
            }
        }
    }

    @Test
    void aViewIsKeptAsItWasTakenAndSharesTheChunksThatDidNotChange() {
        var cells = new SightCells(CELL, 5, 2000f, 2000f);
        cells.begin(0);
        cells.look(1, new Coord3D(100f, 100f, 0f), 50f);
        var first = cells.view(1);
        assertEquals(SightCells.Sight.IN_SIGHT, first.at(100f, 100f));

        cells.begin(1);
        cells.look(1, new Coord3D(1500f, 1500f, 0f), 50f); // far away: another chunk
        var second = cells.view(1);
        assertSame(first.chunks()[0], second.chunks()[0], "the chunk nothing wrote since is the same array");
        int far = (1500 / 10 / SightCells.CHUNK) * second.chunksAcross() + 1500 / 10 / SightCells.CHUNK;
        assertTrue(first.chunks()[far] == null && second.chunks()[far] != null, "the chunk looked at is new");

        for (int frame = 2; frame < 8; frame++) {
            cells.begin(frame);
        }
        var third = cells.view(1);
        assertEquals(SightCells.Sight.SEEN, third.at(100f, 100f), "its while after is out");
        assertEquals(SightCells.Sight.IN_SIGHT, first.at(100f, 100f), "and the first view says what it said");
        assertNotSame(first.chunks()[0], third.chunks()[0], "copied before it was written");
    }

    @Test
    void whatAPlayerHasSeenIsRememberedAndRecalledAsItStood() {
        var random = new SplittableRandom(9);
        var cells = new SightCells(CELL, 30, 900f, 700f);
        for (int frame = 0; frame < 50; frame++) {
            cells.begin(frame);
            for (int i = 0; i < 3; i++) {
                cells.look(2, new Coord3D((float) random.nextDouble(0, 900), (float) random.nextDouble(0, 700), 0f),
                        70f);
            }
        }
        var memory = cells.remember(2);
        var again = new SightCells(CELL, 30, 900f, 700f);
        again.recall(2, memory);
        again.begin(49);
        for (int frame = 49; frame < 90; frame++) {
            if (frame > 49) {
                cells.begin(frame);
                again.begin(frame);
            }
            for (int cy = 0; cy < cells.height(); cy++) {
                for (int cx = 0; cx < cells.width(); cx++) {
                    assertEquals(cells.sight(2, cx, cy), again.sight(2, cx, cy), "frame " + frame + " " + cx + "," + cy);
                }
            }
        }
    }

    @Test
    void stoneHidesWhatIsBehindItAndAFloorAboveIsSeenNotInSight() {
        var cells = new SightCells(CELL, 5, 200f, 200f);
        // A wall across x = 10, and a raised floor at 5..7, 14..16.
        cells.setGround(new SightCells.Ground() {
            @Override
            public boolean stone(int cx, int cy) {
                return cx == 10;
            }

            @Override
            public int storey(int cx, int cy) {
                return cx >= 5 && cx <= 7 && cy >= 14 && cy <= 16 ? 1 : 0;
            }
        });
        cells.begin(0);
        cells.look(0, new Coord3D(55f, 105f, 0f), 80f); // standing in cell 5, 10
        assertEquals(SightCells.Sight.IN_SIGHT, cells.sight(0, 9, 10), "this side of the wall");
        assertEquals(SightCells.Sight.IN_SIGHT, cells.sight(0, 10, 10), "the wall itself");
        assertEquals(SightCells.Sight.NEVER_SEEN, cells.sight(0, 11, 10), "behind it");
        assertEquals(SightCells.Sight.SEEN, cells.sight(0, 6, 15), "the floor above: its side in view, nothing on it");
        assertEquals(SightCells.Sight.IN_SIGHT, cells.sight(0, 2, 10), "open ground round");
    }
}
