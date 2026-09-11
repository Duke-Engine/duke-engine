package uz.duke.worldbuilder.ui;

import java.awt.Color;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.worldbuilder.StageDraft;

/**
 * What the next click puts down.
 *
 * <p>Two questions rather than one long list: what sort of thing, and then which
 * one. An author spends his afternoon on the second box and touches the first
 * four times, and a single dropdown holding every monster twice — once as itself
 * and once as a boss — would make the common case pay for the rare one.
 *
 * <p>Every name in both boxes comes out of the settings file. Not a convenience:
 * the generator draws from those same lists, so a builder that kept its own could
 * offer a monster the game has no template for, and the mistake would not show up
 * until something failed to spawn in a stage somebody had already shipped.
 */
final class Palette extends JPanel {

    /** The four things a click can mean. */
    enum What {
        MONSTER("Monster"),
        PROP("Prop"),
        BOSS("Boss"),
        ENTRANCE("Entrance");

        private final String label;

        What(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** One click's worth of intent: what sort of thing, and which kind of it. */
    record Tool(What what, String kind) {

        void placeOn(StageDraft draft, int cx, int cy) {
            switch (what) {
                case MONSTER -> draft.putMonster(kind, cx, cy);
                case PROP -> draft.putProp(kind, cx, cy);
                case BOSS -> draft.putBoss(kind, cx, cy);
                case ENTRANCE -> draft.putEntrance(cx, cy);
            }
        }
    }

    private static final Color UNKNOWN = new Color(200, 60, 200);

    private final Map<String, Color> monsterColours = new LinkedHashMap<>();
    private final String[] monsters;
    private final String[] props;

    private final JComboBox<What> what = new JComboBox<>(What.values());
    private final JComboBox<String> kind = new JComboBox<>();

    Palette(DungeonSettings settings) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (var monster : settings.monsters()) {
            monsterColours.put(monster.name(), monster.awtColour());
        }
        monsters = monsterColours.keySet().toArray(new String[0]);
        props = settings.propKinds().stream().map(kind -> kind.template()).toArray(String[]::new);

        add(new JLabel("Place:"));
        add(what);
        add(kind);
        what.addActionListener(e -> refreshKinds());
        refreshKinds();
    }

    /**
     * The kinds that make sense for what is being placed.
     *
     * <p>The boss shares the monster list because a boss is not a kind of creature
     * — it is a creature the stage is won by killing. Which one that is, is the
     * author's decision and the generator's own {@code Bosses} list is only its
     * default.
     */
    private void refreshKinds() {
        var choosing = (What) what.getSelectedItem();
        var names = switch (choosing) {
            case MONSTER, BOSS -> monsters;
            case PROP -> props;
            case ENTRANCE -> new String[0];
        };
        var wasShowing = (String) kind.getSelectedItem();
        kind.setModel(new DefaultComboBoxModel<>(names));
        kind.setEnabled(names.length > 0);
        // Only if this list actually has it. A combo box will hold a selection its
        // own model does not contain, which would leave the prop tool offering to
        // place a Skeleton — and that is a stage the game refuses to load.
        if (wasShowing != null && java.util.List.of(names).contains(wasShowing)) {
            kind.setSelectedItem(wasShowing);
        }
    }

    /** What a click means right now, or null if nothing can be placed. */
    Tool selected() {
        var choosing = (What) what.getSelectedItem();
        if (choosing == What.ENTRANCE) {
            return new Tool(What.ENTRANCE, null);
        }
        var name = (String) kind.getSelectedItem();
        return name == null ? null : new Tool(choosing, name);
    }

    /**
     * What a creature is drawn as, from the file that says what it is drawn as.
     *
     * <p>The same colour the game gives it on the minimap, so a stage read here
     * and a stage played look like each other. A kind the file has never heard of
     * is drawn in a colour nothing else uses, which is the point — the checker
     * will say so in words, and until it is read the map itself is shouting.
     */
    Color colourOf(String monsterKind) {
        return monsterColours.getOrDefault(monsterKind, UNKNOWN);
    }
}
