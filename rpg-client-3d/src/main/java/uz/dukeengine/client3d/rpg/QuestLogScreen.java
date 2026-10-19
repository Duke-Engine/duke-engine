package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.rpg.story.Lore;
import uz.dukeengine.rpg.story.Quest;
import uz.dukeengine.rpg.story.QuestLog;

/**
 * The quest log: the quests going, MAIN before SIDE, each with its steps — the ones done ticked, the one to do with
 * its count — and the finished ones folded under their heading; on the right the quest picked, its steps and its
 * description, and a button that opens the map on where it wants him, where it says. Only reading: the log is the
 * state handed in as the last frame left it, and every word is the data's.
 *
 * <p>Opening and closing it, the key and the map it opens are the game's: it paints in the middle of the window when
 * it is asked to, and says what a click landed on ({@link #click}).
 */
public final class QuestLogScreen implements Painter {

    /**
     * The words it says, in the game's language.
     *
     * @param title    at the top of the frame
     * @param closeHint at its foot: how it is put away
     * @param going    the heading over the quests going
     * @param none     under it when none is
     * @param finished the heading over the finished quests, counted after it
     * @param showOnMap the button that opens the map on where the quest picked wants him
     * @param marks    the face the ticks and diamonds are drawn in, where the text's own has none
     */
    public record Words(String title, String closeHint, String going, String none, String finished, String showOnMap,
            String marks) {

        /** The words of a game that has written none. */
        public static final Words DEFAULTS = new Words("Quests", "L: close", "Going", "No quests going",
                "Finished", "Show on map", "Segoe UI Symbol");
    }

    /** One step as the log says it: its words, the count in them, and whether it is done. */
    public record Line(String text, boolean done) {
    }

    /** One quest as the log lists it. */
    public record Entry(String quest, Quest.Kind kind, String title, List<Line> steps, String description,
            boolean finished) {
    }

    /** A place on the screen a click does something at: picks a quest, folds the finished, or opens the map. */
    public record Hit(float x, float y, float width, float height, String quest, boolean fold, boolean map) {

        boolean holds(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final MenuStyle look;
    private final Words words;
    private final Lore lore;
    private final Supplier<QuestLog.State> state;
    /** Whether the quest, by name, has a place on the map to show: the game's, which knows its maps. */
    private final Predicate<String> onTheMap;
    /** The quest picked, by name; blank for the first going. */
    private String picked = "";
    /** Whether the finished quests are unfolded under their heading. */
    private boolean unfolded;
    private final List<Hit> hits = new ArrayList<>();

    /**
     * @param state    the log as it stands now, read every frame it is painted
     * @param onTheMap whether a quest going has somewhere on the map to show; a button opens it where it has
     */
    public QuestLogScreen(MenuStyle look, Words words, Lore lore, Supplier<QuestLog.State> state,
            Predicate<String> onTheMap) {
        this.look = look;
        this.words = words;
        this.lore = lore;
        this.state = state;
        this.onTheMap = onTheMap;
    }

    /**
     * Every quest of {@code state} as the log lists them: the ones going, MAIN then SIDE in the order begun, then the
     * finished in the order finished. A quest the files no longer have is left out.
     */
    public static List<Entry> entries(QuestLog.State state, Lore lore) {
        var entries = new ArrayList<Entry>();
        for (var kind : Quest.Kind.values()) {
            for (var going : state.active()) {
                var quest = lore.quest(going.quest());
                if (quest == null || quest.kind() != kind) {
                    continue;
                }
                var steps = new ArrayList<Line>();
                for (int i = 0; i <= going.step() && i < quest.steps().size(); i++) {
                    var step = quest.steps().get(i);
                    steps.add(i < going.step() ? new Line(step.said(step.count()), true)
                            : new Line(step.said(going.count()), false));
                }
                entries.add(new Entry(quest.name(), kind, quest.title(), steps, quest.description(), false));
            }
        }
        for (var name : state.finished()) {
            var quest = lore.quest(name);
            if (quest != null) {
                entries.add(new Entry(name, quest.kind(), quest.title(),
                        quest.steps().stream().map(step -> new Line(step.said(step.count()), true)).toList(),
                        quest.description(), true));
            }
        }
        return entries;
    }

    /** The quest picked: the one asked for while it is listed, else the first going, else nothing. */
    public static Entry picked(List<Entry> entries, String asked) {
        return entries.stream().filter(entry -> entry.quest().equals(asked)).findFirst()
                .orElse(entries.stream().filter(entry -> !entry.finished()).findFirst().orElse(null));
    }

    public void pick(String quest) {
        picked = quest;
    }

    public String picked() {
        return picked;
    }

    public List<Hit> hits() {
        return List.copyOf(hits);
    }

    /** A click at {@code (x, y)}: the hit it landed on, having picked or folded, or null for none. */
    public Hit click(int x, int y) {
        for (var hit : hits) {
            if (!hit.holds(x, y)) {
                continue;
            }
            if (hit.fold()) {
                unfolded = !unfolded;
            } else if (hit.quest() != null) {
                picked = hit.quest();
            }
            return hit;
        }
        return null;
    }

    /** The log in the middle of the window. */
    @Override
    public void paint(Canvas canvas) {
        hits.clear();
        var entries = entries(state.get(), lore);
        int text = Math.clamp(Math.round(canvas.height() * 0.019f), 12, 22);
        var font = Canvas.Font.of("Georgia", text).wideFamily(words.marks());
        var bold = font.bold(true);
        var small = Canvas.Font.of("Georgia", Math.max(10, Math.round(text * 0.85f))).wideFamily(words.marks());
        float line = canvas.measure(font, "Ag").lineHeight() * 1.4f;
        float pad = text;
        float wide = Math.min(canvas.width() - 32f, text * 50f);
        float high = Math.min(canvas.height() - 32f, text * 34f);
        var box = frame(canvas, wide, high);
        int gold = 0xFF000000 | look.torchColour();
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0xA0000000 | look.boneColour();
        float top = box.y() + pad + line * 1.4f;
        float listWide = box.width() * 0.4f - pad;
        float lx = box.x() + pad;

        // The list on the left: what is going, then the finished folded under their heading.
        float y = top;
        var going = entries.stream().filter(entry -> !entry.finished()).toList();
        var done = entries.stream().filter(Entry::finished).toList();
        canvas.drawText(bold, words.going(), lx, y, gold);
        y += line;
        if (going.isEmpty()) {
            canvas.drawText(small, words.none(), lx, y, dim);
            y += line;
        }
        var chosen = picked(entries, picked);
        for (var entry : going) {
            y = listed(canvas, font, entry, entry == chosen, lx, y, listWide, line);
        }
        if (!done.isEmpty()) {
            y += line * 0.4f;
            var heading = (unfolded ? "▾ " : "▸ ") + words.finished() + " (" + done.size() + ")";
            canvas.drawText(bold, heading, lx, y, gold);
            hits.add(new Hit(lx, y, listWide, line, null, true, false));
            y += line;
            if (unfolded) {
                for (var entry : done) {
                    y = listed(canvas, font, entry, entry == chosen, lx, y, listWide, line);
                }
            }
        }

        // The quest picked on the right: its title, its steps, what it is about, and the way to the map.
        if (chosen == null) {
            return;
        }
        float rx = box.x() + box.width() * 0.4f + pad;
        float rightWide = box.x() + box.width() - pad - rx;
        canvas.line(rx - pad * 0.5f, top, rx - pad * 0.5f, box.y() + box.height() - pad * 2f, 1f,
                0x80000000 | look.stoneEdgeColour());
        y = top;
        canvas.drawText(bold, mark(chosen) + chosen.title(), rx, y, chosen.kind() == Quest.Kind.MAIN ? gold : bone);
        y += line * 1.2f;
        for (var step : chosen.steps()) {
            canvas.drawText(font, (step.done() ? "✓ " : "• ") + step.text(), rx, y, step.done() ? dim : bone);
            y += line;
        }
        y += line * 0.4f;
        for (var row : wrap(canvas, small, chosen.description(), rightWide)) {
            canvas.drawText(small, row, rx, y, dim);
            y += line * 0.85f;
        }
        if (!chosen.finished() && onTheMap.test(chosen.quest())) {
            var button = words.showOnMap();
            var size = canvas.measure(font, button);
            float bw = size.width() + pad * 1.5f;
            float bh = line * 1.1f;
            float by = box.y() + box.height() - pad * 2.2f - bh;
            canvas.fillRect(rx, by, bw, bh, 0xFF000000 | look.stoneColour());
            canvas.openRect(rx, by, bw, bh, 2f, gold);
            canvas.drawText(font, button, rx + pad * 0.75f, by + (bh - size.lineHeight()) / 2f, gold);
            hits.add(new Hit(rx, by, bw, bh, chosen.quest(), false, true));
        }
    }

    /** The stone slab in the middle of the window with its title at the top and its close hint at the foot. */
    private Canvas.Box frame(Canvas canvas, float wide, float high) {
        float left = (canvas.width() - wide) / 2f;
        float top = Math.max(16f, (canvas.height() - high) / 2.4f);
        canvas.fillRect(0f, 0f, canvas.width(), canvas.height(), 0x70000000);
        canvas.fillRect(left, top, wide, high, 0xF4000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());
        canvas.openRect(left + 4f, top + 4f, wide - 8f, high - 8f, 1f, 0x60000000 | look.stoneEdgeColour());
        int text = Math.clamp(Math.round(canvas.height() * 0.026f), 14, 30);
        var heading = Canvas.Font.of("Georgia", text).bold(true);
        var size = canvas.measure(heading, words.title());
        canvas.drawText(heading, words.title(), left + (wide - size.width()) / 2f, top + text * 0.6f,
                0xFF000000 | look.torchColour());
        var small = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.015f), 10, 18));
        var hintSize = canvas.measure(small, words.closeHint());
        canvas.drawText(small, words.closeHint(), left + wide - hintSize.width() - text * 0.6f,
                top + high - hintSize.lineHeight() - text * 0.5f, 0x90000000 | look.boneColour());
        return new Canvas.Box(left, top, wide, high);
    }

    /** One quest's title in the list, the one picked lit behind; where the next goes. */
    private float listed(Canvas canvas, Canvas.Font font, Entry entry, boolean chosen, float x, float y, float wide,
            float line) {
        // Under a quest going, the step it is on and its count, as the tracker says it.
        boolean stepShown = !entry.finished() && !entry.steps().isEmpty();
        float high = stepShown ? line * 1.75f : line;
        if (chosen) {
            canvas.fillRect(x - 4f, y - 2f, wide + 8f, high, 0x60000000 | look.torchColour());
        }
        int argb = entry.finished() ? 0x90000000 | look.boneColour()
                : entry.kind() == Quest.Kind.MAIN ? 0xFF000000 | look.torchColour() : 0xFF000000 | look.boneColour();
        canvas.drawText(font, mark(entry) + entry.title(), x, y, argb);
        if (stepShown) {
            var small = Canvas.Font.of(font.family(), Math.max(10, Math.round(font.pixelHeight() * 0.85f)))
                    .wideFamily(words.marks());
            canvas.drawText(small, "• " + entry.steps().getLast().text(), x + font.pixelHeight(), y + line * 0.85f,
                    0xA0000000 | look.boneColour());
        }
        hits.add(new Hit(x, y, wide, high, entry.quest(), false, false));
        return y + high;
    }

    /** The tracker's mark: ◆ for a MAIN, ◇ for a SIDE, ✓ for one finished. */
    private static String mark(Entry entry) {
        return entry.finished() ? "✓ " : entry.kind() == Quest.Kind.MAIN ? "◆ " : "◇ ";
    }

    /** {@code text} cut into rows no wider than {@code wide}, at the spaces. */
    private static List<String> wrap(Canvas canvas, Canvas.Font font, String text, float wide) {
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
