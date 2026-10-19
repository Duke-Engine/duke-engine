package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.rpg.story.Conversation;
import uz.dukeengine.rpg.story.Lore;
import uz.dukeengine.rpg.story.Npc;
import uz.dukeengine.rpg.story.Quest;
import uz.dukeengine.rpg.story.QuestLog;

/** The quest log on the game's canvas: what is going with its step and its words, what is finished under a fold. */
class QuestLogScreenTest {

    private static Quest quest(String name, Quest.Kind kind, String description) {
        var kill = new Quest.Step(Quest.Goal.KILL, "rat", 3, "", "Rats killed: %d/%d", null, false, "");
        var report = new Quest.Step(Quest.Goal.TALK, "elder", 1, "", "Tell the elder", null, false, "");
        return new Quest(name, kind, name.toUpperCase(), List.of(kill, report), description);
    }

    private static final Map<String, Quest> QUESTS = Map.of(
            "rats", quest("rats", Quest.Kind.MAIN, "The cellar is full of them."),
            "herbs", quest("herbs", Quest.Kind.SIDE, "Pick some."),
            "old", quest("old", Quest.Kind.SIDE, "Long done."));

    private static final Lore LORE = new Lore() {
        @Override
        public Quest quest(String name) {
            return QUESTS.get(name);
        }

        @Override
        public Conversation conversation(String name) {
            return null;
        }

        @Override
        public Npc npc(String name) {
            return null;
        }
    };

    private static final QuestLog.State STATE = new QuestLog.State(
            List.of(new QuestLog.Active("herbs", 0, 0, 0), new QuestLog.Active("rats", 1, 0, 0)),
            List.of("old", "gone"), List.of());

    private final QuestLogScreen screen = new QuestLogScreen(MenuStyle.DEFAULTS, QuestLogScreen.Words.DEFAULTS, LORE,
            () -> STATE, quest -> quest.equals("rats"));

    @Test
    void theMainQuestComesFirstAndAQuestTheFilesLostIsLeftOut() {
        var entries = QuestLogScreen.entries(STATE, LORE);

        assertEquals(List.of("rats", "herbs", "old"), entries.stream().map(QuestLogScreen.Entry::quest).toList());
        var rats = entries.getFirst();
        assertEquals(List.of(new QuestLogScreen.Line("Rats killed: 3/3", true),
                new QuestLogScreen.Line("Tell the elder", false)), rats.steps());
        assertTrue(entries.getLast().finished());
    }

    @Test
    void aQuestGoingIsListedWithItsStepAndThePickedOneShowsItsDescriptionAndTheMapButton() {
        var canvas = new Drawn();
        screen.paint(canvas);

        assertTrue(canvas.text.contains("◆ RATS"), canvas.text.toString());
        assertTrue(canvas.text.contains("• Tell the elder"), "the step it is on, under its title");
        assertTrue(canvas.text.contains("The cellar is full of them."), "the first going is picked");
        assertTrue(canvas.text.contains("✓ Rats killed: 3/3"), "a step done is ticked");
        assertTrue(canvas.text.contains("Show on map"));
        assertTrue(screen.hits().stream().anyMatch(QuestLogScreen.Hit::map));
        assertFalse(canvas.text.contains("✓ OLD"), "the finished are folded");
        assertTrue(canvas.text.contains("▸ Finished (1)"));
    }

    @Test
    void theFinishedAreUnfoldedByAClickOnTheirHeadingAndPickedOneAtATime() {
        var canvas = new Drawn();
        screen.paint(canvas);
        var fold = screen.hits().stream().filter(QuestLogScreen.Hit::fold).findFirst().orElseThrow();
        assertNotNull(screen.click(Math.round(fold.x() + 1), Math.round(fold.y() + 1)));

        canvas = new Drawn();
        screen.paint(canvas);
        assertTrue(canvas.text.contains("▾ Finished (1)"), canvas.text.toString());
        assertTrue(canvas.text.contains("✓ OLD"));
        var old = screen.hits().stream().filter(hit -> "old".equals(hit.quest())).findFirst().orElseThrow();
        screen.click(Math.round(old.x() + 1), Math.round(old.y() + 1));
        assertEquals("old", screen.picked());

        canvas = new Drawn();
        screen.paint(canvas);
        assertTrue(canvas.text.contains("Long done."));
        assertFalse(canvas.text.contains("Show on map"), "a finished quest wants him nowhere");
    }
}
