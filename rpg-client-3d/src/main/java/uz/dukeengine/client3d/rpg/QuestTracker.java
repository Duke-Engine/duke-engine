package uz.dukeengine.client3d.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.rpg.story.Quest;
import uz.dukeengine.rpg.story.Tracked;

/**
 * The quests going, listed down the window's right edge on the game's canvas: each one's title, a mark for the kind
 * it is, and under it its steps — the one to do, brightening as it begins, and any just done, dimmed.
 *
 * <p>The list has no slab: small text with a thin shadow over a soft dark backing. Folded ({@link #fold}) it is only
 * a small tab. Drawn on the window's thread and only reading: the list is handed in whole, as
 * {@code QuestLog.entries()} gives it, and nothing is drawn while it is empty.
 */
public final class QuestTracker implements Painter {

    /**
     * The words and the look of the list.
     *
     * @param look        the colours: the torch's for a main quest, the bone's for the rest
     * @param missionsTab what the folded list says
     * @param mainMark    before a main quest's title
     * @param sideMark    before a side quest's title
     * @param stepMark    before the step to do
     * @param doneMark    before a step just done
     * @param face        the face the words are drawn in
     * @param marksFace   the face the marks are drawn in, from code point 256 up: the marks are rarely in {@code face}
     * @param glowFrames how long a step glows after it begins
     */
    public record Words(MenuStyle look, String missionsTab, String mainMark, String sideMark, String stepMark,
            String doneMark, String face, String marksFace, int glowFrames) {

        public static final Words DEFAULTS = new Words(MenuStyle.DEFAULTS, "Missions", "◆ ", "◇ ", "• ", "✓ ",
                "Georgia", "Segoe UI Symbol", 30);
    }

    /** One line of the list: its words, its colour, and whether it stands in under its title. */
    private record Row(String text, int argb, boolean indented) {
    }

    private final Supplier<List<Tracked>> entries;
    private final IntSupplier frame;
    private final Words words;
    /** Whether the list is folded to its tab: flipped by a key on the window's thread. */
    private volatile boolean folded;

    /** A list of {@code entries} as they stand at the logic frame {@code frame} gives. */
    public QuestTracker(Supplier<List<Tracked>> entries, IntSupplier frame, Words words) {
        this.entries = entries;
        this.frame = frame;
        this.words = words;
    }

    /** Fold the list, or unfold it. */
    public void fold() {
        folded = !folded;
    }

    public boolean isFolded() {
        return folded;
    }

    @Override
    public void paint(Canvas canvas) {
        var tracked = entries.get();
        if (tracked.isEmpty()) {
            return;
        }
        var look = words.look();
        var font = Canvas.Font.of(words.face(), Math.clamp(Math.round(canvas.height() * 0.018f), 12, 22))
                .wideFamily(words.marksFace());
        List<Row> rows = folded
                ? List.of(new Row(words.missionsTab(), 0x90000000 | look.boneColour(), false))
                : rows(tracked);
        float widest = 0f;
        float line = 0f;
        for (var row : rows) {
            var size = canvas.measure(font, row.text());
            widest = Math.max(widest, size.width() + (row.indented() ? font.pixelHeight() : 0f));
            line = Math.max(line, size.lineHeight());
        }
        float pad = font.pixelHeight() * 0.5f;
        var box = boxOf(canvas.width(), canvas.height(), widest + pad * 2f, line * rows.size() + pad * 2f);
        canvas.fillRect(box.x(), box.y(), box.width(), box.height(), 0x70000000);
        float y = box.y() + pad;
        for (var row : rows) {
            float x = box.x() + pad + (row.indented() ? font.pixelHeight() : 0f);
            canvas.drawText(font, row.text(), x + 1f, y + 1f, 0xC0000000);
            canvas.drawText(font, row.text(), x, y, row.argb());
            y += line;
        }
    }

    /** The lines of the list, top to bottom: each quest's title, then its steps. */
    private List<Row> rows(List<Tracked> tracked) {
        var look = words.look();
        int gold = 0xFF000000 | look.torchColour();
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0x90000000 | look.boneColour();
        int now = frame.getAsInt();
        var rows = new ArrayList<Row>();
        for (var quest : tracked) {
            boolean main = quest.kind() == Quest.Kind.MAIN;
            rows.add(new Row((main ? words.mainMark() : words.sideMark()) + quest.title(), main ? gold : bone, false));
            for (var step : quest.steps()) {
                if (step.done()) {
                    rows.add(new Row(words.doneMark() + step.text(), dim, true));
                } else {
                    rows.add(new Row(words.stepMark() + step.text(), lit(bone, glow(now, step.since())), true));
                }
            }
        }
        return rows;
    }

    /** How lit a step that began at frame {@code since} is at frame {@code now}: 1 just begun, falling to 0. */
    private float glow(int now, int since) {
        if (words.glowFrames() <= 0) {
            return 0f;
        }
        return Math.clamp(1f - (now - since) / (float) words.glowFrames(), 0f, 1f);
    }

    /** {@code argb} brought toward white by {@code glow}, 0 for itself and 1 for white. */
    private static int lit(int argb, float glow) {
        int out = argb & 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int part = (argb >> shift) & 0xFF;
            out |= Math.round(part + (255 - part) * glow) << shift;
        }
        return out;
    }

    /** Where a list {@code wide} by {@code high} stands: at the right edge, a little down, wholly on the window. */
    static Canvas.Box boxOf(int across, int down, float wide, float high) {
        float left = Math.max(0f, across - wide - 12f);
        float top = Math.clamp(down * 0.03f, 0f, Math.max(0f, down - high));
        return new Canvas.Box(left, top, wide, high);
    }
}
