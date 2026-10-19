package uz.dukeengine.rpg.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;

/**
 * The story half, lifted into the RPG library (E22): each quest goal moving on its own event and only on it, quests
 * chaining, a person's greeting and offer, the tracker's order, and a script's triggers by their words, flags and
 * delays — all of it saved and given back as it was.
 */
class StoryTest {

    private static final Loot LOCKET = new Loot("Locket", "Tumor", "", "QUEST", 0, 0, 1);
    private static final Loot GAUNTLET = new Loot("Gauntlet", "Qo'lqop", "", "DEFENCE", 3, 1, 1);

    private static final Map<String, Quest> QUESTS = Map.of(
            "Walls", new Quest("Walls", Quest.Kind.SIDE, "Devordagi skeletlar", List.of(
                    new Quest.Step(Quest.Goal.KILL, "Skeleton", 5, "", "Skeletlarni qiring %d/%d", null, false, ""),
                    new Quest.Step(Quest.Goal.TALK, "Commander", 1, "", "Qaytib boring",
                            new Quest.Done("Thanks", 150, "Gauntlet", ""), false, "")), ""),
            "Locket", new Quest("Locket", Quest.Kind.SIDE, "Tumor", List.of(
                    new Quest.Step(Quest.Goal.COLLECT, "Locket", 1, "", "Toping", null, false, ""),
                    new Quest.Step(Quest.Goal.GIVE, "Commander", 1, "Locket", "Bering", null, false, "")), ""),
            "Walk", new Quest("Walk", Quest.Kind.SIDE, "Yur", List.of(
                    new Quest.Step(Quest.Goal.REACH, "Well", 1, "", "Quduqqa bor",
                            new Quest.Done("", 0, "", "Second"), false, "")), ""),
            "Second", new Quest("Second", Quest.Kind.MAIN, "Ikkinchi", List.of(
                    new Quest.Step(Quest.Goal.TALK, "Commander", 1, "", "Gaplash", null, false, "")), ""));

    private static final Map<String, Conversation> TALK = Map.of(
            "Greeting", new Conversation("Greeting", List.of(new Conversation.Line("Commander", "Salom")),
                    List.of("Walls", "Locket"), false),
            "Thanks", new Conversation("Thanks", List.of(), List.of(), false),
            "Errand", new Conversation("Errand", List.of(), List.of("Walk"), true));

    private static final Map<String, Npc> PEOPLE = Map.of(
            "Commander", new Npc("Commander", "Greeting", 30f),
            "Trader", new Npc("Trader", "Errand", 30f));

    private static final Lore LORE = new Lore() {
        @Override
        public Quest quest(String name) {
            return QUESTS.get(name);
        }

        @Override
        public Conversation conversation(String name) {
            return TALK.get(name);
        }

        @Override
        public Npc npc(String name) {
            return PEOPLE.get(name);
        }
    };

    private static QuestLog log() {
        return new QuestLog(LORE, List.of(LOCKET, GAUNTLET), LootBag.JOIN);
    }

    @Test
    void aKillCountsOnlyItsOwnDeathsAndHandingInPays() {
        var log = log();
        var bag = new LootBag();
        assertTrue(log.talk("Commander", bag, false), "her greeting");
        assertEquals(List.of("Walls", "Locket"), log.active().stream().map(QuestLog.Active::quest).toList());
        assertFalse(log.talk("Commander", bag, false), "walking up again says nothing new");
        log.reached("Well", bag);
        log.died("Runner", List.of(bag));
        for (int i = 0; i < 4; i++) {
            log.died("Skeleton", List.of(bag));
        }
        assertEquals("Skeletlarni qiring 4/5", log.entries().getFirst().steps().getFirst().text());
        log.died("Skeleton", List.of(bag));
        assertEquals(QuestLog.Mark.HAND_IN, log.markOf("Commander"));
        assertTrue(log.talk("Commander", bag, false));
        assertEquals(List.of("Walls"), log.finished());
        assertEquals(150, log.takeExperience());
        assertEquals(0, log.takeExperience(), "paid once");
        assertTrue(bag.slots().stream().anyMatch(each -> each != null && each.id().equals("Gauntlet")), "rewarded");
        assertEquals("Thanks", log.said().getLast());
    }

    @Test
    void collectCountsTheBagsAndGiveTakesIt() {
        var log = log();
        var bag = new LootBag();
        log.start("Locket");
        bag.take(LOCKET, 0, 0);
        log.talk("Commander", bag, false);
        assertEquals(0, log.active().getFirst().step(), "held is not counted until the bags are looked at");
        log.holding(List.of(bag));
        assertEquals(1, log.active().getFirst().step(), "found");
        assertTrue(log.talk("Commander", bag, false), "handed in");
        assertTrue(log.finished().contains("Locket"));
        assertTrue(bag.slots().stream().noneMatch(each -> each != null && each.id().equals("Locket")), "she has it");
    }

    @Test
    void anOfferIsMadeOnlyWhenAskedAndTakenOnlyWhenAccepted() {
        var log = log();
        assertFalse(log.talk("Trader", null, false), "unasked, an offer says nothing");
        assertTrue(log.talk("Trader", null, true));
        assertTrue(log.active().isEmpty(), "said, not yet taken");
        log.accept("Errand", true);
        assertEquals("Walk", log.active().getFirst().quest());
        log.reached("Well", null);
        assertEquals(List.of(Quest.Kind.MAIN), log.entries().stream().map(Tracked::kind).toList(),
                "its OnDone started the next");
        assertEquals(List.of(new QuestLog.Event(Trigger.When.QUEST_STARTED, "Walk", 0),
                new QuestLog.Event(Trigger.When.STEP_DONE, "Walk", 1), new QuestLog.Event(Trigger.When.QUEST_DONE,
                        "Walk", 0), new QuestLog.Event(Trigger.When.QUEST_STARTED, "Second", 0)),
                log.takeEvents().stream().filter(e -> !e.when().equals(Trigger.When.HEARD)).toList());
    }

    @Test
    void theLogIsSavedAndGivenBack() {
        var log = log();
        log.start("Walls");
        log.died("Skeleton", List.of());
        log.died("Skeleton", List.of());
        var saved = log.state();
        var again = log();
        again.restore(saved);
        assertEquals(2, again.active().getFirst().count());
        assertEquals(saved, again.state());
    }

    /** One thing a trigger does, as a game writes it. */
    private record Say(String target) implements Trigger.Action {
    }

    /** What a game's files write a trigger's actions as: a block of its own record, by the word Action. */
    public record Action(String act, String target, int count) implements Trigger.Action {
    }

    @Test
    void aTriggerIsReadFromAGamesFilesItsActionsTheGamesOwnRecord() {
        var text = """
                Trigger
                  Name = QueenAwaits
                  Map = world
                  When = QUEST_DONE
                  Target = Follow
                  If = [
                    Condition
                      Is = FLAG_UNSET
                      Target = Welcomed
                    End
                  ]
                  Actions = [
                    Action
                      Act = SAY
                      Target = Queen
                      Count = 1
                    End
                  ]
                End
                """;
        var trigger = new uz.dukeengine.core.data.Binder()
                .vocabulary(Trigger.Action.class, Map.of("Action", Action.class))
                .bind(uz.dukeengine.core.data.DukeText.parse(text, "act1.duke").getFirst(), Trigger.class);
        assertEquals(Trigger.When.QUEST_DONE, trigger.when());
        assertEquals(new Trigger.Condition(Trigger.Is.FLAG_UNSET, "Welcomed"), trigger.If().getFirst());
        assertEquals(new Action("SAY", "Queen", 1), trigger.actions().getFirst());
    }

    @Test
    void aTriggerFiresOnItsWordWhenItsConditionsHoldAfterItsDelay() {
        var script = new Script(List.of(
                new Trigger("Arrive", "world", Trigger.When.ZONE, "Gate", 0, 2, false,
                        List.of(new Trigger.Condition(Trigger.Is.FLAG_SET, "Open")), List.of(new Say("Welcome"))),
                new Trigger("Then", "", Trigger.When.AFTER, "Arrive", 0, 0, false, List.of(), List.of()),
                new Trigger("Beaten", "", "BEATEN", "Boss", 0, 0, true, List.of(), List.of())));
        script.mapStarted("world");
        script.standing(List.of("Gate"));
        assertTrue(script.due().isEmpty(), "its flag not set: unfired");
        script.standing(List.of());
        script.flag("Open");
        script.standing(List.of("Gate"));
        assertTrue(script.due().isEmpty());
        assertTrue(script.due().isEmpty());
        var due = script.due();
        assertEquals(List.of("Arrive", "Then"), due.stream().map(Trigger::name).toList(), "two frames on, and after it");
        assertEquals(new Say("Welcome"), due.getFirst().actions().getFirst());

        script.heard("BEATEN", "Boss", 0);
        script.heard("BEATEN", "Boss", 0);
        assertEquals(2, script.due().size(), "a game's own word, every time it is heard");

        var saved = script.state();
        var again = new Script(List.of(new Trigger("Arrive", "world", Trigger.When.ZONE, "Gate", 0, 2, false,
                List.of(), List.of())));
        again.restore(saved);
        assertEquals(List.of("Arrive"), again.state().fired(), "a trigger the files no longer have is left out");
        assertTrue(again.flagged("Open"));
    }
}
