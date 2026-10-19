package uz.dukeengine.client3d.rpg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.story.Conversation;
import uz.dukeengine.rpg.story.Speaker;

/** The conversation box: lines in turn, an offer's question, and what the game is told as they go. */
class DialogueScreenTest implements DialogueScreen.Answers {

    private static final Conversation HELLO = new Conversation("hello",
            List.of(new Conversation.Line("Elder", "Welcome."), new Conversation.Line("Hero", "Thanks.")),
            List.of(), false);
    private static final Conversation QUEST = new Conversation("quest",
            List.of(new Conversation.Line("Elder", "Will you help?")), List.of("rats"), true);

    private final List<String> told = new ArrayList<>();
    private final List<String> said = new ArrayList<>();
    private final DukeGame game = DukeGame.create("dialogue-test")
            .loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
    private final DialogueScreen screen = new DialogueScreen(null, null, MenuStyle.DEFAULTS,
            DialogueScreen.Words.DEFAULTS, Map.of("hello", HELLO, "quest", QUEST)::get,
            Map.of("Elder", new Speaker("Elder", "Old Man", ""))::get, () -> game, () -> said, () -> false,
            () -> "Duke", this);

    DialogueScreenTest() {
        game.addPlayer("One", Color.BLUE);
        game.runHeadless(1);
    }

    @Override
    public void read(int player, int shown) {
        told.add("read " + shown);
    }

    @Override
    public void answer(int player, int shown, boolean yes) {
        told.add("answer " + shown + " " + yes);
    }

    @Override
    public void skip(int player) {
        told.add("skip");
    }

    private static CanvasInput.Key space() {
        return new CanvasInput.Key(CanvasInput.SPACE, true, false);
    }

    @Test
    void linesAreShownInTurnAndTheRunIsToldWhenTheLastIsRead() {
        said.add("hello");
        var canvas = new Drawn();
        screen.paint(canvas);
        assertTrue(canvas.text.contains("Old Man") && canvas.text.contains("Welcome."), canvas.text.toString());
        assertTrue(screen.busy());
        assertTrue(game.getLogic().isGamePaused(), "played alone, the world waits");

        screen.take(space());
        canvas.clear();
        screen.paint(canvas);
        assertTrue(canvas.text.contains("Duke") && canvas.text.contains("Thanks."), canvas.text.toString());
        assertTrue(told.isEmpty());

        screen.take(space());
        assertNull(screen.showing());
        assertEquals(List.of("read 1"), told);
        screen.paint(canvas);
        assertFalse(game.getLogic().isGamePaused(), "let go once none is shown");
    }

    @Test
    void anOfferEndsInAQuestionWhoseAnswerGoesBeforeTheRead() {
        said.add("quest");
        var canvas = new Drawn();
        screen.paint(canvas);
        screen.take(space());
        canvas.clear();
        screen.paint(canvas);
        assertTrue(canvas.text.contains("Will you take it?"), canvas.text.toString());

        screen.take(space());
        assertTrue(told.isEmpty(), "the key that read the last line does not answer");
        screen.answerNow(true);
        assertNull(screen.showing());
        assertEquals(List.of("answer 1 true", "read 1"), told);
    }

    @Test
    void escapeSkipsTheRestOfAConversation() {
        said.add("hello");
        screen.paint(new Drawn());
        assertTrue(screen.take(new CanvasInput.Key(CanvasInput.ESCAPE, true, false)));
        assertNull(screen.showing());
        assertEquals(List.of("read 1"), told);
    }
}
