package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rpg.story.Quest;
import uz.dukeengine.rpg.story.Tracked;

/** The quest list on the canvas: the main quest first with its mark, the steps under it, folded to a tab. */
class QuestTrackerTest {

    private static final int FRAME = 100;
    private static final List<Tracked> QUESTS = List.of(
            new Tracked(Quest.Kind.MAIN, "Find the key", List.of(new Tracked.Line("Open the gate", false, FRAME),
                    new Tracked.Line("Kill the guard", true, 0))),
            new Tracked(Quest.Kind.SIDE, "Feed the cat", List.of(new Tracked.Line("Fish 1/3", false, 0))));

    private final QuestTracker tracker = new QuestTracker(() -> QUESTS, () -> FRAME, QuestTracker.Words.DEFAULTS);

    private List<String> lines() {
        var canvas = new Drawn();
        tracker.paint(canvas);
        return canvas.text.stream().distinct().toList();
    }

    @Test
    void questsAreListedWithTheirMarksAndStepsUnderEach() {
        assertEquals(List.of("◆ Find the key", "• Open the gate", "✓ Kill the guard", "◇ Feed the cat", "• Fish 1/3"),
                lines());
    }

    @Test
    void aStepThatJustBeganIsLitAndAnOldOneIsNot() {
        var canvas = new Drawn();
        tracker.paint(canvas);
        int bone = 0xFF000000 | QuestTracker.Words.DEFAULTS.look().boneColour();
        assertEquals(0xFFFFFFFF, canvas.colours.get("• Open the gate"));
        assertEquals(bone, canvas.colours.get("• Fish 1/3"));
    }

    @Test
    void foldedItIsOnlyATabAndUnfoldedAgainTheListIsBack() {
        tracker.fold();
        assertTrue(tracker.isFolded());
        assertEquals(List.of("Missions"), lines());
        tracker.fold();
        assertNotEquals(List.of("Missions"), lines());
    }

    @Test
    void nothingIsDrawnWhileNothingIsListed() {
        var empty = new QuestTracker(List::of, () -> FRAME, QuestTracker.Words.DEFAULTS);
        var canvas = new Drawn();
        empty.paint(canvas);
        assertTrue(canvas.text.isEmpty());
    }
}
