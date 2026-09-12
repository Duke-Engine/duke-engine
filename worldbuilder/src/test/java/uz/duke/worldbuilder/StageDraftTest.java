package uz.duke.worldbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.GeneratedDungeon.Placement;
import uz.duke.dungeon.gen.Layout;
import uz.duke.dungeon.stage.StageFile;

/**
 * The editing rules, without a window.
 *
 * <p>Everything the builder actually decides happens in {@link StageDraft} —
 * where a click puts a thing, what it takes away, and what the stage is called —
 * and none of it needs a screen. Which is what keeps this module honest in a
 * build that has no display anywhere in it: the window draws what this says, and
 * this is what is tested.
 */
class StageDraftTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static StageDraft draft() {
        return StageDraft.generate(4242L, SETTINGS);
    }

    /** An empty floor cell, well inside the room the hero starts in. */
    private static int[] freeCellIn(StageDraft draft) {
        var start = draft.rooms().get(0);
        for (int cy = start.y() + 1; cy < start.y() + start.h() - 1; cy++) {
            for (int cx = start.x() + 1; cx < start.x() + start.w() - 1; cx++) {
                if (draft.whatIsAt(cx, cy) == null) {
                    return new int[] {cx, cy};
                }
            }
        }
        throw new AssertionError("the starting room has no room in it");
    }

    /** What the generator hands over is already a stage that would play. */
    @Test
    void aFreshDraftHasNothingWrongWithIt() {
        assertEquals(List.of(), draft().problems());
    }

    @Test
    void aMonsterGoesWhereItIsPut() {
        var draft = draft();
        var cell = freeCellIn(draft);
        int before = draft.monsters().size();

        draft.putMonster("Skeleton", cell[0], cell[1]);

        assertEquals(before + 1, draft.monsters().size());
        assertEquals("Skeleton", draft.whatIsAt(cell[0], cell[1]));
        assertEquals(List.of(), draft.problems(), "and it is a stage that still plays");
    }

    /**
     * Putting something where something already stands replaces it.
     *
     * <p>One cell holds one thing: a solid prop dropped on a skeleton leaves that
     * skeleton unable to take a single step for the rest of the run. The editor
     * obeys rather than refuses, which teaches the rule faster than a click that
     * silently does nothing.
     */
    @Test
    void oneCellHoldsOneThing() {
        var draft = draft();
        var cell = freeCellIn(draft);
        draft.putMonster("Skeleton", cell[0], cell[1]);
        int after = draft.monsters().size();

        draft.putProp("Pillar", cell[0], cell[1]);

        assertEquals(after - 1, draft.monsters().size(), "the skeleton made way");
        assertEquals("Pillar", draft.whatIsAt(cell[0], cell[1]));
        assertEquals(List.of(), draft.problems());
    }

    @Test
    void rightClickingTakesThingsAway() {
        var draft = draft();
        var cell = freeCellIn(draft);
        draft.putMonster("Runner", cell[0], cell[1]);

        assertTrue(draft.removeAt(cell[0], cell[1]));
        assertEquals(null, draft.whatIsAt(cell[0], cell[1]));
        assertFalse(draft.removeAt(cell[0], cell[1]), "and there is nothing left to take");
    }

    /** A mistake shows up at once, and stops showing up when it is undone. */
    @Test
    void aMonsterInTheRockIsReportedWhileYouWork() {
        var draft = draft();
        var stone = stoneCell(draft);
        draft.putMonster("Skeleton", stone[0], stone[1]);

        assertTrue(draft.problems().stream().anyMatch(p -> p.contains("inside stone")),
                draft.problems().toString());

        draft.removeAt(stone[0], stone[1]);
        assertEquals(List.of(), draft.problems());
    }

    /** Moving the boss moves the room the stage is pointed at, in one gesture. */
    @Test
    void theBossTakesHisRoomWithHim() {
        var draft = draft();
        int wasRoom = draft.bossRoom();
        var cell = freeCellIn(draft); // in room 0, which is not where a boss starts
        assertNotEquals(0, wasRoom, "seed 4242 should not put the boss in the start room");

        draft.putBoss("Champion", cell[0], cell[1]);

        assertEquals(0, draft.bossRoom(), "the boss's room follows the boss");
        assertEquals("Champion", draft.bossKind());
        assertEquals(Placement.atCell(cell[0], cell[1]), draft.bossAt());
    }

    @Test
    void theWayInCanBeMoved() {
        var draft = draft();
        var cell = freeCellIn(draft);
        draft.putEntrance(cell[0], cell[1]);
        assertEquals(Placement.atCell(cell[0], cell[1]), draft.entrance());
        assertEquals(List.of(), draft.problems());
    }

    /**
     * A semicolon typed into a name is turned into a comma where it is typed.
     *
     * <p>It begins a comment in this format, so a name with one in it is a name
     * that would come back cut in half. Taken out while a person is watching
     * rather than thrown at him an hour later when he tries to save.
     */
    @Test
    void aSemicolonCannotGetIntoAName() {
        var draft = draft();
        draft.setName("Two rooms; one boss");
        assertEquals("Two rooms, one boss", draft.name());
        StageFile.write(draft.toStage()); // would throw if one had got through
    }

    /** What the builder holds and what the file holds are the same stage. */
    @Test
    void aDraftSurvivesBeingWrittenAndOpened() {
        var draft = draft();
        var cell = freeCellIn(draft);
        draft.setName("Test");
        draft.putMonster("Brute", cell[0], cell[1]);

        var stage = draft.toStage();
        var reopened = StageDraft.of(StageFile.read(StageFile.write(stage), "test"), SETTINGS);

        assertEquals(stage, reopened.toStage());
    }

    /**
     * A draft drawn at the size the author asked for — and playable at it.
     *
     * <p>The size cannot be applied to a floor afterwards: a bigger map is a
     * different set of rooms, not the same rooms further apart. So it is asked
     * before anything is drawn, and what comes back has to be both the size that
     * was asked for and a stage the game would accept.
     */
    @Test
    void aDraftIsDrawnAtTheSizeAskedFor() {
        var draft = StageDraft.generate(8L, SETTINGS, Layout.sized(SETTINGS, 140, 100, 30), 1);

        assertEquals(140, draft.cellsAcross());
        assertEquals(100, draft.cellsDown());
        assertTrue(draft.rooms().size() > SETTINGS.maxRooms(),
                "a map this size should hold more rooms than the shipped floor does");
        assertEquals(List.of(), draft.problems());
    }

    /** And at the difficulty asked for, which the stage then carries as its depth. */
    @Test
    void aDraftRemembersTheDifficultyItWasDrawnAt() {
        var draft = StageDraft.generate(8L, SETTINGS, Layout.of(SETTINGS), 9);

        assertEquals(9, draft.difficulty());
        assertEquals(9, draft.toStage().difficulty());
        assertEquals(List.of(), draft.problems());
    }

    /**
     * The size is recoverable from a floor already drawn, so another seed can be
     * rolled at the same size by somebody who was not there when it was asked for.
     */
    @Test
    void theSizeCanBeReadBackOffTheFloor() {
        var draft = StageDraft.generate(8L, SETTINGS, Layout.sized(SETTINGS, 120, 90, 20), 1);
        var again = draft.layout(SETTINGS);

        assertEquals(120, again.width());
        assertEquals(90, again.height());
        assertEquals(draft.rooms().size(), again.minRooms());
    }

    private static int[] stoneCell(StageDraft draft) {
        var rows = draft.terrain().strip().split("\n");
        for (int cy = 0; cy < rows.length; cy++) {
            for (int cx = 0; cx < rows[cy].length(); cx++) {
                if (rows[cy].charAt(cx) == '#') {
                    return new int[] {cx, cy};
                }
            }
        }
        throw new AssertionError("a dungeon with no stone in it");
    }
}
