package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.story.Conversation;
import uz.dukeengine.rpg.story.Speaker;

/**
 * The box at the bottom of the screen a conversation is said in: the speaker's picture in a frame — or the first
 * letter of their name, where they have none — their name, the line, and how to go on. A click, Space or Enter is the
 * next line; Esc skips the rest. An offer ends in a question, answered with Space, Enter or the left button, or turned
 * down with Esc or the right.
 *
 * <p>The window's alone, and only reading the simulation: the run says every conversation it has played, in order
 * ({@code QuestLog.said}), and this shows them one at a time, counting how many it has shown. The simulation never
 * waits for it. Played alone, the world is paused while a conversation is on the screen — the engine's own pause, the
 * flag Esc's menu sets, set from the window as that menu sets it; in a party it is not, since pausing one machine of a
 * lock-step game stalls them all: the box is only shown, and the fight goes on under it.
 *
 * <p>Drawn over another screen, whose painter and input it wraps: what it does not take, that is offered.
 */
public final class DialogueScreen implements Painter, CanvasInput {

    /**
     * The words it says, in the game's language, and the face it writes them in.
     *
     * @param continueHint  under a line: how to go on
     * @param offerQuestion the question an offer ends in, put by whoever said its last line
     * @param offerHint     under the question: how to answer it
     * @param face          the font the box is written in
     */
    public record Words(String continueHint, String offerQuestion, String offerHint, String face) {

        public static final Words DEFAULTS = new Words("Space: continue   Esc: skip", "Will you take it?",
                "Space: accept   Esc: decline", "Georgia");
    }

    /** What the box tells the game as it goes; the game posts its own orders in them. */
    public interface Answers {

        /** Every conversation up to the {@code shown}th has been shown to the end, or skipped. */
        void read(int player, int shown);

        /** The answer to the offer that was the {@code shown}th conversation; sent before the {@link #read} of it. */
        void answer(int player, int shown, boolean yes);

        /** The cutscene skipped, for everybody. */
        void skip(int player);
    }

    /** The one key it answers the engine names none for: Enter on the number pad, by its scan code. */
    private static final int NUMPAD_ENTER = 156;
    /** How long after its coming up the question of an offer takes no answer. */
    private static final long ASK_LOCKOUT_NANOS = 400_000_000L;

    private final Painter under;
    private final CanvasInput underInput;
    private final MenuStyle look;
    private final Words words;
    private final Function<String, Conversation> conversations;
    private final Function<String, Speaker> speakers;
    /** The match now being played, or null. */
    private final Supplier<DukeGame> match;
    /** Every conversation played, in order, by name: {@code QuestLog.said}. */
    private final Supplier<List<String>> said;
    /** Whether a cutscene — or an intro holding the screen between its cutscenes — is playing. */
    private final BooleanSupplier inCutscene;
    /** What the box calls the hero, whose name the game chose. */
    private final Supplier<String> hero;
    private final Answers orders;

    /** The match the counts below are of; a new match is shown from its own first conversation. */
    private DukeGame counted;
    private int shown;
    private int line;
    /** How many conversations this machine has told the run it has shown to the end. */
    private int told;
    /** The answers given to offers, by the conversation's place in the said list; sent with the next read. */
    private final Map<Integer, Boolean> given = new HashMap<>();
    /** When the question of an offer came on the screen, so a key pressed through its last line does not answer it. */
    private long askedSince;
    /** Whether the world was paused here, so it is this that lets it go again — and nothing else's pause. */
    private boolean pausedHere;

    /**
     * A box over {@code under}, whose input is offered what it does not take.
     *
     * @param conversations a conversation by its name, or null
     * @param speakers      a speaker by the name the lines use, or null
     * @param match         the match being played, or null
     * @param said          every conversation the run has played, by name, in order
     * @param inCutscene    whether a cutscene holds the screen
     * @param hero          what the box calls the hero
     * @param orders        what it tells the game
     */
    public DialogueScreen(Painter under, CanvasInput underInput, MenuStyle look, Words words,
            Function<String, Conversation> conversations, Function<String, Speaker> speakers,
            Supplier<DukeGame> match, Supplier<List<String>> said, BooleanSupplier inCutscene,
            Supplier<String> hero, Answers orders) {
        this.under = under;
        this.underInput = underInput;
        this.look = look;
        this.words = words;
        this.conversations = conversations;
        this.speakers = speakers;
        this.match = match;
        this.said = said;
        this.inCutscene = inCutscene;
        this.hero = hero;
        this.orders = orders;
    }

    /** Whether a conversation or a cutscene has the screen: nothing else may open over it. */
    public boolean busy() {
        return inCutscene.getAsBoolean() || showing() != null;
    }

    /** The conversation on the screen now, and the line of it — or null for none. */
    record Showing(Conversation conversation, int line) {
    }

    Showing showing() {
        var game = match.get();
        if (game == null || game.getLogic() == null) {
            return null;
        }
        if (game != counted) {
            counted = game;
            shown = 0;
            line = 0;
            told = 0;
            given.clear();
        }
        var all = said.get();
        while (shown < all.size()) {
            var conversation = conversations.apply(all.get(shown));
            // An offer has one more place than it has lines: the question.
            if (conversation != null && line < conversation.lines().size() + (conversation.offer() ? 1 : 0)) {
                tell(game);
                return new Showing(conversation, line);
            }
            shown++;
            line = 0;
        }
        tell(game);
        return null;
    }

    /**
     * Tell the run, as this game's order, every time another conversation has been shown to the end (or skipped): a
     * cutscene waits on it, so it never starts while a line is on the screen.
     */
    private void tell(DukeGame game) {
        if (shown <= told) {
            return;
        }
        int player = game.getLocalPlayerIndex();
        for (int done = told + 1; done <= shown; done++) {
            var answer = given.remove(done);
            if (answer != null) { // his answer to an offer goes first, then the read that says he is done with it
                orders.answer(player, done, answer);
            }
        }
        told = shown;
        orders.read(player, shown);
    }

    /** The next line, or the end of the conversation: an offer's last line is followed by its question, not its end. */
    void next() {
        var now = showing();
        if (now != null && !asking(now)) {
            line++;
        }
    }

    /** The rest of this conversation, skipped: an offer's skipped to its question, which still has to be answered. */
    void skip() {
        var now = showing();
        if (now == null) {
            return;
        }
        if (now.conversation().offer() && !asking(now)) {
            line = now.conversation().lines().size();
            return;
        }
        shown++;
        line = 0;
    }

    /** Whether the screen shows an offer's question, not one of its lines. */
    private static boolean asking(Showing now) {
        return now.conversation().offer() && now.line() >= now.conversation().lines().size();
    }

    /**
     * The answer to the question on the screen: accepted or turned down, the conversation over either way. An answer
     * given within a moment of the question's coming up is not one: the key that read the last line is still down.
     */
    void answer(boolean accept) {
        var now = showing();
        if (now == null || !asking(now) || System.nanoTime() - askedSince < ASK_LOCKOUT_NANOS) {
            return;
        }
        shown++;
        given.put(shown, accept);
        line = 0;
        askedSince = 0;
    }

    /** A test's answer: no lockout, as no one is mashing a key. */
    void answerNow(boolean accept) {
        askedSince = 1;
        answer(accept);
    }

    @Override
    public void paint(Canvas canvas) {
        if (under != null) {
            under.paint(canvas);
        }
        var now = showing();
        pause(now != null);
        if (inCutscene.getAsBoolean()) {
            letterbox(canvas);
        }
        if (now != null) {
            box(canvas, now);
        }
    }

    /** The black bars across the top and the bottom of a cutscene. */
    static void letterbox(Canvas canvas) {
        float bar = canvas.height() * 0.11f;
        canvas.fillRect(0f, 0f, canvas.width(), bar, 0xFF000000);
        canvas.fillRect(0f, canvas.height() - bar, canvas.width(), bar, 0xFF000000);
    }

    private void skipCutscene() {
        var game = match.get();
        if (game != null && game.getLogic() != null) {
            orders.skip(game.getLocalPlayerIndex());
        }
    }

    /**
     * A cutscene takes the player's hand: no orders and no keys but Esc, which skips it — its lines with it — and
     * Space, Enter or a click, which is the next line while one is shown and skips the cutscene when none is.
     */
    private boolean cutsceneTakes(Event event) {
        boolean line = showing() != null;
        switch (event) {
            case Key key -> {
                if (key.down() && !key.repeat() && key.code() == ESCAPE) {
                    if (line) {
                        skip();
                    }
                    skipCutscene();
                } else if (key.down() && !key.repeat() && goesOn(key)) {
                    if (line) {
                        next();
                    } else {
                        skipCutscene();
                    }
                }
                return true;
            }
            case Button button -> {
                if (button.down() && button.button() == Mouse.LEFT) {
                    if (line) {
                        next();
                    } else {
                        skipCutscene();
                    }
                }
                return true;
            }
            case Pointer pointer -> {
                return false; // where the pointer is, the cutscene does not mind
            }
            default -> {
                return true;
            }
        }
    }

    private static boolean goesOn(Key key) {
        return key.code() == SPACE || key.code() == ENTER || key.code() == NUMPAD_ENTER;
    }

    @Override
    public void resized(int width, int height) {
        if (under != null) {
            under.resized(width, height);
        }
    }

    @Override
    public boolean take(Event event) {
        if (inCutscene.getAsBoolean()) {
            return cutsceneTakes(event);
        }
        var asked = showing();
        if (asked == null) {
            return underInput != null && underInput.take(event);
        }
        if (asking(asked)) {
            // An offer's question: Enter, Space or the left button accept it; Escape or the right button turn it down.
            if (event instanceof Key key) {
                if (key.down() && !key.repeat()) {
                    if (goesOn(key)) {
                        answer(true);
                    } else if (key.code() == ESCAPE) {
                        answer(false);
                    }
                }
            } else if (event instanceof Button button && button.down()) {
                if (button.button() == Mouse.LEFT) {
                    answer(true);
                } else if (button.button() == Mouse.RIGHT) {
                    answer(false);
                }
            }
            return !(event instanceof Pointer);
        }
        switch (event) {
            case Key key -> {
                if (key.down() && !key.repeat() && goesOn(key)) {
                    next();
                } else if (key.down() && key.code() == ESCAPE) {
                    skip();
                }
                return goesOn(key) || key.code() == ESCAPE;
            }
            case Button button -> {
                if (button.down() && button.button() == Mouse.LEFT) {
                    next();
                }
                return true; // a click on the box, or past it, is not an order while she is talking
            }
            default -> {
                return underInput != null && underInput.take(event);
            }
        }
    }

    /** The world paused while a conversation is shown, played alone; let go once none is. */
    private void pause(boolean talking) {
        var game = match.get();
        var logic = game == null ? null : game.getLogic();
        if (logic == null || game.isMultiplayer()) {
            return;
        }
        if (talking && !logic.isGamePaused()) {
            logic.setGamePaused(true);
            pausedHere = true;
        } else if (!talking && pausedHere) {
            logic.setGamePaused(false);
            pausedHere = false;
        }
    }

    /** Who says it, as the box writes them: a Speaker's DisplayName, or the hero's. */
    String nameOf(String speaker) {
        if (Speaker.HERO.equals(speaker)) {
            return hero.get();
        }
        var known = speakers.apply(speaker);
        return known == null || known.displayName().isBlank() ? speaker : known.displayName();
    }

    private void box(Canvas canvas, Showing now) {
        boolean question = asking(now);
        if (question && askedSince == 0) {
            askedSince = System.nanoTime();
        } else if (!question) {
            askedSince = 0;
        }
        // An offer's question is put by the one who said its last line.
        var spoken = question
                ? new Conversation.Line(now.conversation().lines().getLast().speaker(), words.offerQuestion())
                : now.conversation().lines().get(now.line());
        var speaker = speakers.apply(spoken.speaker());
        var font = Canvas.Font.of(words.face(), Math.clamp(Math.round(canvas.height() * 0.022f), 13, 26));
        var small = Canvas.Font.of(words.face(), Math.clamp(Math.round(canvas.height() * 0.016f), 11, 18));
        float pad = font.pixelHeight();
        float wide = Math.min(canvas.width() - 32f, Math.max(480f, canvas.width() * 0.55f));
        float face = font.pixelHeight() * 5f;
        float textWide = wide - face - pad * 3f;
        var rows = wrap(canvas, font, spoken.text(), textWide);
        float lineHigh = canvas.measure(font, "Ag").lineHeight();
        float high = Math.max(face + pad * 2f, pad * 2f + lineHigh * (rows.size() + 2) + pad * 0.5f);
        var panel = canvas.bottomPanel();
        float floor = panel == null ? canvas.height() - 16f : panel.y() - 8f;
        float left = (canvas.width() - wide) / 2f;
        float top = Math.max(8f, floor - high);
        canvas.fillRect(left, top, wide, high, 0xE8000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());

        // The face: the speaker's picture, framed, or the first letter of their name.
        float fx = left + pad;
        float fy = top + pad;
        int gold = 0xFF000000 | look.torchColour();
        var name = nameOf(spoken.speaker());
        if (speaker != null && !speaker.picture().isBlank()) {
            canvas.drawImage(Canvas.Image.of(speaker.picture()), fx, fy, fx + face, fy + face, 0xFFFFFFFF,
                    Canvas.Blend.ALPHA);
        } else {
            canvas.fillRect(fx, fy, face, face, 0xFF000000 | look.stoneEdgeColour());
            var big = Canvas.Font.of(words.face(), Math.round(face * 0.6f));
            var letter = name.isEmpty() ? "?" : name.substring(0, 1);
            var size = canvas.measure(big, letter);
            canvas.drawText(big, letter, fx + (face - size.width()) / 2f, fy + (face - size.lineHeight()) / 2f, gold);
        }
        canvas.openRect(fx, fy, face, face, 2f, gold);

        float tx = fx + face + pad;
        float ty = top + pad;
        canvas.drawText(font, name, tx, ty, gold);
        ty += lineHigh * 1.2f;
        int bone = 0xFF000000 | look.boneColour();
        for (var row : rows) {
            canvas.drawText(font, row, tx, ty, bone);
            ty += lineHigh;
        }
        var hint = question ? words.offerHint() : words.continueHint();
        var hintSize = canvas.measure(small, hint);
        canvas.drawText(small, hint, left + wide - pad - hintSize.width(), top + high - pad * 0.5f
                - hintSize.lineHeight(), 0x90000000 | look.boneColour());
    }

    /** {@code text} cut into rows no wider than {@code wide}, at the spaces. */
    static List<String> wrap(Canvas canvas, Canvas.Font font, String text, float wide) {
        var rows = new ArrayList<String>();
        var row = new StringBuilder();
        for (var word : text.split(" ")) {
            var longer = row.isEmpty() ? word : row + " " + word;
            if (!row.isEmpty() && canvas.measure(font, longer).width() > wide) {
                rows.add(row.toString());
                row.setLength(0);
                row.append(word);
            } else {
                row.setLength(0);
                row.append(longer);
            }
        }
        if (!row.isEmpty()) {
            rows.add(row.toString());
        }
        return rows;
    }
}
