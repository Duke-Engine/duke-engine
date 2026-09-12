package uz.duke.worldbuilder.ui;

import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.Layout;

/**
 * What to draw, asked before anything is drawn.
 *
 * <p>Two questions, and both of them have to come first. A bigger map is not the
 * same rooms further apart — it is a different floor — and the difficulty is the
 * depth the floor is <em>generated</em> at, which decides which kinds of monster
 * have appeared by then and which boss is standing at the end of it. Asking
 * afterwards would mean redrawing, on a floor the author had already spent an
 * hour arranging.
 *
 * <p>The descent's own floors are small on purpose: space beyond what the rooms
 * need becomes corridor, and corridor is walked through rather than played, which
 * is a bad trade for somewhere a player sees once. A stage is the opposite case —
 * drawn once, learnt, re-attempted — so it can afford to be large, and this is
 * where that is said.
 */
final class NewStage {

    /** What the author asked for: a seed, a size, and a depth to fight at. */
    record Wanted(long seed, Layout layout, int difficulty, int width, int height, int rooms) {

        static Wanted of(long seed, DungeonSettings settings, int width, int height, int rooms,
                int difficulty) {
            return new Wanted(seed, Layout.sized(settings, width, height, rooms), difficulty,
                    width, height, rooms);
        }

        /** The same question again, with a fresh seed — what "New seed" asks. */
        Wanted reseeded(long seed) {
            return new Wanted(seed, layout, difficulty, width, height, rooms);
        }
    }

    private NewStage() {
    }

    /** The size of floor the settings file describes, as somewhere to start. */
    static Wanted fromSettings(DungeonSettings settings, long seed) {
        return Wanted.of(seed, settings, settings.mapWidth(), settings.mapHeight(),
                Layout.roomsThatFit(settings.mapWidth(), settings.mapHeight()), 1);
    }

    /**
     * Put the question to the author, or {@code null} if he closed it.
     *
     * <p>Nothing is refused for being large or deep. The hints beside the boxes say
     * what a number means — how many rooms a map that size has space for, and what
     * a depth is as dangerous as — and then the author decides. A stage built
     * deeper than the descent ever reaches is not a mistake; it is most of the
     * reason to build one.
     */
    static Wanted ask(Component parent, DungeonSettings settings, Wanted current) {
        var width = spinner(current.width(), 24, 400);
        var height = spinner(current.height(), 24, 400);
        var rooms = spinner(current.rooms(), 2, 300);
        var difficulty = spinner(current.difficulty(), 1, 99);

        var roomHint = hint();
        var depthHint = hint();
        Runnable update = () -> {
            int fits = Layout.roomsThatFit(value(width), value(height));
            roomHint.setText("about " + fits + " fit a map "
                    + value(width) + " x " + value(height));
            depthHint.setText(depthMeans(settings, value(difficulty)));
        };
        width.addChangeListener(e -> update.run());
        height.addChangeListener(e -> update.run());
        difficulty.addChangeListener(e -> update.run());
        update.run();

        var form = new JPanel(new GridBagLayout());
        int row = 0;
        addRow(form, row++, "Width", width, new JLabel("cells across"));
        addRow(form, row++, "Height", height, new JLabel("cells down"));
        addRow(form, row++, "Rooms", rooms, null);
        addHint(form, row++, roomHint);
        addRow(form, row++, "Difficulty", difficulty, null);
        addHint(form, row, depthHint);

        int answer = JOptionPane.showConfirmDialog(parent, form, "New stage",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        return Wanted.of(current.seed(), settings, value(width), value(height), value(rooms),
                value(difficulty));
    }

    /**
     * What a depth is worth, in the game's own numbers.
     *
     * <p>Read off the settings rather than written here, so the sentence cannot
     * drift from the arithmetic it is describing. A depth past the last boss says
     * so outright: that is the interesting end of the scale and an author should
     * know when he has gone past where the game itself stops.
     */
    private static String depthMeans(DungeonSettings settings, int depth) {
        int health = Math.round(settings.monsterHealthAt(depth) * 100);
        int count = Math.round(settings.monsterCountAt(depth) * 100);
        var said = "monsters at " + health + "% health and " + count + "% of their number"
                + ", led by a " + settings.bossKindAt(depth);
        int bottom = settings.finalDepth();
        if (bottom > 0 && depth > bottom) {
            return said + " — deeper than the descent itself goes (" + bottom + ")";
        }
        return said;
    }

    private static JSpinner spinner(int value, int least, int most) {
        return new JSpinner(new SpinnerNumberModel(
                Math.clamp(value, least, most), least, most, 1));
    }

    private static int value(JSpinner spinner) {
        return (Integer) spinner.getValue();
    }

    private static JLabel hint() {
        var label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.ITALIC,
                label.getFont().getSize2D() - 1f));
        return label;
    }

    private static void addRow(JPanel form, int row, String label, JSpinner field, JLabel after) {
        var at = placedAt(row, 0);
        form.add(new JLabel(label), at);
        form.add(field, placedAt(row, 1));
        if (after != null) {
            form.add(after, placedAt(row, 2));
        }
    }

    /** A line of small print under the box it is about, out of the label column. */
    private static void addHint(JPanel form, int row, JLabel hint) {
        var at = placedAt(row, 1);
        at.gridwidth = 2;
        at.insets = new Insets(0, 4, 6, 4);
        form.add(hint, at);
    }

    private static GridBagConstraints placedAt(int row, int column) {
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 4, 3, 4);
        at.gridy = row;
        at.gridx = column;
        at.anchor = GridBagConstraints.WEST;
        return at;
    }
}
