package uz.dukeengine.client3d.rpg;

import static uz.dukeengine.client3d.rpg.PanelBrush.attach;
import static uz.dukeengine.client3d.rpg.PanelColours.rgb;
import static uz.dukeengine.client3d.rpg.PanelMeshes.gradient;
import static uz.dukeengine.client3d.rpg.PanelMetrics.ABOVE_THE_BLOCK;
import static uz.dukeengine.client3d.rpg.PanelMetrics.BAND;
import static uz.dukeengine.client3d.rpg.PanelMetrics.VITALS_WIDTH;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.PanelSkin;

/**
 * The block under the experience bar: his figures down the left, his primary attribute in the middle and every
 * attribute down the right, with the boxes the cursor is answered by.
 */
final class StatBlock {

    /** The ring of bright gold round his primary's socket. */
    static final float PRIMARY_FRAME = 2f;

    /** Between a socket and the word beside it. */
    static final float ICON_GAP = 6f;

    /** Between one column of the block and the next. */
    static final float COLUMN_GAP = 8f;

    /** Between the longest word in a column and the values lined up after it. */
    static final float WORD_GAP = 6f;

    /**
     * How tall the block is kept, whatever arrives in it: room for the rows the game sized
     * it for, and never more than the band has left under the experience bar.
     *
     * <p>Fixed rather than fitted to each card, because the name, the title and the bar
     * are centred in the band together with it — a block that grew for a card with a
     * fourth attribute would move the hero's name.
     */
    static float blockHeight(StatLook look) {
        float figures = rowsTall(look.figureRows(), look.figureIcon(), look.rowGap());
        float primary = look.primaryIcon() + PRIMARY_FRAME * 2f + 2f + look.primaryText();
        float attributes = rowsTall(look.attributeRows(), look.attributeIcon(), look.rowGap());
        return Math.min(Math.max(figures, Math.max(primary, attributes)),
                BAND - ABOVE_THE_BLOCK - look.gapUnderBar());
    }

    private static float rowsTall(int rows, float icon, float gap) {
        return rows <= 0 ? 0f : rows * icon + (rows - 1) * gap;
    }

    /**
     * How {@code count} rows go into a column this tall: the size each socket is drawn at,
     * and the step from one row down to the next.
     *
     * <p>The size the game asked for while they fit. Past that they close up — the gaps
     * first, then the sockets themselves — rather than run off the bottom of the bar: a
     * game that adds a fourth attribute gets four smaller rows, not a fourth one under
     * the stone.
     */
    static float[] rowsIn(int count, float icon, float gap, float height) {
        if (count <= 1 || rowsTall(count, icon, gap) <= height) {
            float size = Math.min(icon, height);
            return new float[] {size, size + gap};
        }
        if (count * icon <= height) {
            return new float[] {icon, icon + (height - count * icon) / (count - 1)};
        }
        float size = height / count;
        return new float[] {size, size};
    }

    /** The size a row's lettering is set in: what the game asked for, never taller than the row. */
    static float letteringFor(float wanted, float row) {
        return Math.min(wanted, Math.max(6f, row - 4f));
    }

    /**
     * How much of a row is kept for what is lent: room for "+12" at that size.
     *
     * <p>Kept whether anything is lent or not, so the value beside it does not shuffle
     * sideways the moment he picks up a sword. A column of numbers that moves is a column
     * nobody can compare down.
     */
    static float lentRoom(float lettering) {
        return lettering * 2f;
    }

    /** Where the list of attributes starts across the column. */
    static float attributeColumnAt(StatLook look) {
        return Math.min(look.figureColumn() + look.primaryColumn() + COLUMN_GAP,
                VITALS_WIDTH - 80f);
    }

    /** How the block is drawn — see {@link StatLook}. */
    private final StatLook statLook;

    /** Where the block's top edge is, worked out with the rest of the column. */
    private final float statsTop;

    /** Everything in the block, taken down and built again when what it holds changes shape. */
    final Node node = new Node("stat-block");
    /** The lettering on each figure's row down the left, in the order the game sends them. */
    private final List<StatLine> figureLines = new ArrayList<>();
    /** And on each attribute's row down the right. */
    private final List<StatLine> attributeLines = new ArrayList<>();
    /** The value under his primary, or null while no primary is drawn. */
    private BitmapText primaryValue;
    /** The pictures and words the block was built for; a different set means rebuilding. */
    private String statsBuiltFor;
    /** Each attribute's row inside the column, as x, y, width, height — for the cursor. */
    private final List<float[]> attributeSpots = new ArrayList<>();
    /** His primary's framed socket, the same way; null while there is none. */
    private float[] primarySpot;
    /** Which attribute is drawn large in the middle, or -1. */
    private int primaryIndex = -1;
    /** Where the column starts across the bar and up it, in design pixels, as the bar's layout last put it. */
    private float originX;
    private float originY;

    /** The lettering on one row: its word, its value, and what was lent to it. */
    private record StatLine(BitmapText word, BitmapText value, BitmapText lent) {
    }

    private final PanelBrush brush;
    private final PanelColours colours;

    StatBlock(PanelBrush brush, PanelColours colours, StatLook statLook, float statsTop) {
        this.brush = brush;
        this.colours = colours;
        this.statLook = statLook;
        this.statsTop = statsTop;
    }

    void place(float originX, float originY) {
        this.originX = originX;
        this.originY = originY;
    }

    float originX() {
        return originX;
    }

    float originY() {
        return originY;
    }

    /**
     * The attribute under a point, by its place in the game's list, or null.
     *
     * <p>The whole row rather than only its drawing — the word and the number are what
     * the eye is on, and the hand follows the eye — and his primary's framed socket as
     * well, which is the same attribute drawn large. A figure has no card, so it is never
     * the answer.
     */
    Integer attributeAt(float designX, float designY) {
        float x = designX - originX;
        float y = designY - originY;
        for (int i = 0; i < attributeSpots.size(); i++) {
            if (inside(x, y, attributeSpots.get(i))) {
                return i;
            }
        }
        if (primarySpot != null && inside(x, y, primarySpot)) {
            return primaryIndex;
        }
        return null;
    }

    /** Whether a point is in a box written as x, y, width, height. */
    private static boolean inside(float x, float y, float[] box) {
        return x >= box[0] && x <= box[0] + box[2] && y >= box[1] && y <= box[1] + box[3];
    }

    /**
     * The block under the experience bar, laid out the way Warcraft III lays out a hero.
     *
     * <p>Three columns. Down the left, the figures a fight is read by — what he hits for
     * and what he shrugs off — in the biggest sockets of the two lists. In the middle, his
     * primary attribute, large and framed, with its value under it: the one attribute
     * that is also his blow. Down the right, every attribute the game sends, in the game's
     * order, his primary in the brighter gold and the rest dimmer.
     *
     * <p><b>The green is the point of both lists.</b> A figure that only ever goes up says
     * nothing about whether the thing he just picked up was worth picking up; the
     * difference does, and it is the only number on the panel that answers "was that any
     * good".
     *
     * <p>Built to fit whatever arrives rather than a fixed three, for the same reason the
     * skill row is: what a game counts is the game's business. A fourth attribute is a
     * fourth row, and the rows close up to keep the block inside the band.
     */
    void show(List<PanelReading.Stat> figures, List<PanelReading.Stat> attributes) {
        var signature = new StringBuilder();
        for (var figure : figures) {
            signature.append(figure.icon()).append(',').append(figure.word()).append(';');
        }
        signature.append('|');
        for (var attribute : attributes) {
            signature.append(attribute.icon()).append(attribute.primary() ? '*' : ',')
                    .append(attribute.word()).append(';');
        }
        if (!signature.toString().equals(statsBuiltFor)) {
            statsBuiltFor = signature.toString();
            buildStatBlock(figures, attributes);
        }
        for (int i = 0; i < figures.size(); i++) {
            write(figureLines.get(i), figures.get(i));
        }
        for (int i = 0; i < attributes.size(); i++) {
            write(attributeLines.get(i), attributes.get(i));
        }
        if (primaryValue != null && primaryIndex >= 0 && primaryIndex < attributes.size()) {
            primaryValue.setText(attributes.get(primaryIndex).value());
        }
    }

    private static void write(StatLine line, PanelReading.Stat stat) {
        line.word().setText(stat.word());
        line.value().setText(stat.value());
        line.lent().setText(stat.bonus());
    }

    private void buildStatBlock(List<PanelReading.Stat> figures, List<PanelReading.Stat> attributes) {
        node.detachAllChildren();
        figureLines.clear();
        attributeLines.clear();
        attributeSpots.clear();
        primarySpot = null;
        primaryIndex = -1;
        primaryValue = null;
        var look = statLook;
        float height = blockHeight(look);

        // ★ LEFT: the figures, in the sockets a fight is read by.
        column(figures, 0f, look.figureColumn() - COLUMN_GAP, look.figureIcon(),
                look.figureText(), height, figureLines, null,
                stat -> rgb(look.labelColour()), stat -> rgb(look.valueColour()),
                stat -> rgb(look.figureTint()));

        // ★ MIDDLE: his primary, large, in a ring of bright gold, with its value under it.
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.get(i).primary()) {
                primaryIndex = i;
                break;
            }
        }
        if (primaryIndex >= 0) {
            var primary = attributes.get(primaryIndex);
            float size = look.primaryIcon();
            float framedSize = size + PRIMARY_FRAME * 2f;
            float drawn = framedSize + 2f + look.primaryText();
            float x = look.figureColumn() + (look.primaryColumn() - size) / 2f;
            float y = statsTop - (height - drawn) / 2f - PRIMARY_FRAME - size;
            attach(node, brush.flat("primary-frame", framedSize, framedSize,
                    rgb(look.frameColour())), x - PRIMARY_FRAME, y - PRIMARY_FRAME, -0.5f);
            socket(x, y, size, primary.icon(), primary.word(), rgb(look.primaryTint()));
            primaryValue = brush.text(look.primaryText(), rgb(look.primaryColour()),
                    look.figureColumn(), y - PRIMARY_FRAME - 2f - look.primaryText(),
                    look.primaryColumn(), BitmapFont.Align.Center);
            node.attachChild(primaryValue);
            primarySpot = new float[] {x - PRIMARY_FRAME, y - PRIMARY_FRAME, framedSize,
                framedSize};
        }

        // ★ RIGHT: every attribute, his primary in the brighter gold and the rest dimmer.
        float from = attributeColumnAt(look);
        column(attributes, from, VITALS_WIDTH - from, look.attributeIcon(),
                look.attributeText(), height, attributeLines, attributeSpots,
                stat -> rgb(stat.primary() ? look.primaryColour() : look.attributeColour()),
                stat -> rgb(stat.primary() ? look.primaryColour() : look.attributeColour()),
                stat -> rgb(stat.primary() ? look.primaryTint() : look.attributeTint()));
    }

    /**
     * One column of rows — a socket, a word, a value and what was lent — centred down the
     * block, and closed up when there are more rows than the block was sized for.
     *
     * <p>The values stand in a line of their own just after the longest word, rather than
     * against the column's far edge: a value a hundred pixels from its own word is a value
     * nobody reads as belonging to it. And the word and the value are given separate room
     * rather than one box each end of — they used to share one, and "Tezlik" and its
     * figure came out as "Tezli29", both exactly where they had been put.
     */
    private void column(List<PanelReading.Stat> stats, float x, float width, float icon,
            float lettering, float height, List<StatLine> lines, List<float[]> spots,
            java.util.function.Function<PanelReading.Stat, ColorRGBA> wordColour,
            java.util.function.Function<PanelReading.Stat, ColorRGBA> valueColour,
            java.util.function.Function<PanelReading.Stat, ColorRGBA> tint) {
        if (stats.isEmpty()) {
            return;
        }
        var rows = rowsIn(stats.size(), icon, statLook.rowGap(), height);
        float size = rows[0];
        float step = rows[1];
        float tall = (stats.size() - 1) * step + size;
        float top = statsTop - (height - tall) / 2f;
        float letters = letteringFor(lettering, size);
        float widest = 0f;
        for (var stat : stats) {
            widest = Math.max(widest, brush.widthOf(stat.word(), letters));
        }
        float room = width - size - ICON_GAP;
        float valueEnd = Math.min(room - lentRoom(letters),
                widest + WORD_GAP + brush.widthOf("000", letters));
        for (int i = 0; i < stats.size(); i++) {
            var stat = stats.get(i);
            float y = top - size - i * step;
            socket(x, y, size, stat.icon(), stat.word(), tint.apply(stat));
            float from = x + size + ICON_GAP;
            float baseline = y + (size - letters) / 2f;
            var word = brush.text(letters, wordColour.apply(stat), from, baseline, room,
                    BitmapFont.Align.Left);
            var value = brush.text(letters, valueColour.apply(stat), from, baseline, valueEnd,
                    BitmapFont.Align.Right);
            var lent = brush.text(letters, rgb(statLook.gainColour()), from + valueEnd + 3f, baseline,
                    Math.max(1f, room - valueEnd - 3f), BitmapFont.Align.Left);
            node.attachChild(word);
            node.attachChild(value);
            node.attachChild(lent);
            lines.add(new StatLine(word, value, lent));
            if (spots != null) {
                spots.add(new float[] {x, y, width, size});
            }
        }
    }

    /**
     * A picture in a socket of its own: the stone, the rim, and the drawing.
     *
     * <p>The picture is the game's, named beside the row it belongs to. When it names one
     * the client cannot find — or none — the socket carries the first letter of the row's
     * word instead, so it still says which one it is: a missing file costs a line in the
     * log, never the row.
     */
    private void socket(float x, float y, float size, String icon, String word,
            ColorRGBA tint) {
        var box = new Node("stat-box");
        attach(box, brush.flat("stat-edge", size, size, colours.edge), 0f, 0f, 0f);
        var stone = brush.shaded("stat-stone",
                gradient(size - 2f, size - 2f, colours.stoneLit, colours.stone));
        attach(box, stone, 1f, 1f, 1f);
        float share = statLook.iconShare();
        var drawn = brush.picture(icon, size, share, tint);
        if (drawn != null) {
            float inset = size * (1f - share) / 2f;
            attach(box, drawn, inset, inset, 2f);
        } else {
            float lettering = size * 0.6f;
            var letter = brush.text(lettering, colours.gold, 0f, (size - lettering) / 2f, size,
                    BitmapFont.Align.Center);
            letter.setName("stat-letter");
            letter.setText(initial(word));
            letter.setLocalTranslation(0f, 0f, 2f);
            box.attachChild(letter);
        }
        brush.framed(box, PanelSkin.CHIP, 0f, 0f, size, size, 2.5f);
        attach(node, box, x, y, 0f);
    }

    /** The first letter of a word, for a socket whose picture is not there. */
    static String initial(String word) {
        if (word == null || word.isBlank()) {
            return "?";
        }
        int first = word.strip().codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(first)));
    }
}
