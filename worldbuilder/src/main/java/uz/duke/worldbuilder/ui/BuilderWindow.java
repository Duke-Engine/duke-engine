package uz.duke.worldbuilder.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.stage.StageFile;
import uz.duke.worldbuilder.StageDraft;

/**
 * The world builder's one window.
 *
 * <p>Deliberately one. This is not a level editor in the sense of a program that
 * can draw a dungeon — it cannot paint a wall, add a room or move a corridor, and
 * it is not going to learn. The generator draws the place and carries a guarantee
 * that every room can be walked to; a mouse cannot carry that guarantee, so what
 * it is given to do is the part a generator is bad at, which is deciding what
 * should be standing in the fourth room and where the fight at the end is.
 *
 * <p>So: a seed at the top, a map in the middle, a list of what is wrong with it
 * at the bottom. The list is the whole quality story — it is the same check the
 * game runs when it loads the file, so an author never gets told one thing here
 * and another when he presses play.
 */
public final class BuilderWindow extends JFrame {

    private static final String EXTENSION = "stage";
    private static final int HISTORY = 60;

    private final DungeonSettings settings;
    private final Palette palette;
    private final StageCanvas canvas;

    private StageDraft draft;
    private Path file;

    private final JTextField seedField = new JTextField(12);
    private final JTextField nameField = new JTextField(16);
    private final JTextField aboutField = new JTextField(28);
    private final JSpinner difficulty = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final JSpinner players = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
    private final DefaultListModel<String> faults = new DefaultListModel<>();
    private final JLabel state = new JLabel(" ");

    // Undo is a stack of the stage's own text. The document is a few kilobytes and
    // the format is already the thing that has to be read back correctly, so there
    // is no second representation of a stage to get out of step with the first.
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final ArrayDeque<String> redo = new ArrayDeque<>();
    private String lastSaved;
    /** The stage as it stood at the last check — which is one edit ago. */
    private String lastChecked;

    public BuilderWindow(DungeonSettings settings, StageDraft draft) {
        super("Duke World Builder");
        this.settings = settings;
        this.draft = draft;
        this.palette = new Palette(settings);
        this.canvas = new StageCanvas(draft, palette, this::edited);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(bar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(faults(), BorderLayout.SOUTH);
        keys();

        showDraft(draft);
        pack();
        setLocationRelativeTo(null);
    }

    // ---- the furniture ----

    private JPanel bar() {
        var top = new JToolBar();
        top.setFloatable(false);
        top.add(new JLabel("Seed: "));
        top.add(seedField);
        top.add(button("Generate", this::generateTyped));
        top.add(button("New seed", this::generateFresh));
        top.addSeparator();
        top.add(button("Open…", this::open));
        top.add(button("Save", this::save));
        top.add(button("Save as…", this::saveAs));
        top.addSeparator();
        top.add(button("Undo", this::undo));
        top.add(button("Redo", this::redo));

        var about = new JToolBar();
        about.setFloatable(false);
        about.add(new JLabel("Name: "));
        about.add(nameField);
        about.add(new JLabel("  About: "));
        about.add(aboutField);
        about.add(new JLabel("  Difficulty: "));
        about.add(difficulty);
        about.add(new JLabel("  Players: "));
        about.add(players);
        about.addSeparator();
        about.add(palette);

        nameField.addActionListener(e -> readMetadata());
        aboutField.addActionListener(e -> readMetadata());
        difficulty.addChangeListener(e -> readMetadata());
        players.addChangeListener(e -> readMetadata());

        var both = new JPanel(new GridLayout(2, 1));
        both.add(top);
        both.add(about);
        return both;
    }

    private JPanel faults() {
        var list = new JList<>(faults);
        list.setForeground(new Color(170, 40, 40));
        var scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(900, 110));
        scroll.setBorder(BorderFactory.createTitledBorder("What is wrong with it"));

        var below = new JPanel(new BorderLayout());
        below.add(scroll, BorderLayout.CENTER);
        state.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        below.add(state, BorderLayout.SOUTH);
        return below;
    }

    private static JButton button(String label, Runnable action) {
        var button = new JButton(label);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void keys() {
        var root = getRootPane();
        int menu = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(root, KeyStroke.getKeyStroke('Z', menu), "undo", this::undo);
        bind(root, KeyStroke.getKeyStroke('Y', menu), "redo", this::redo);
        bind(root, KeyStroke.getKeyStroke('S', menu), "save", this::save);
        bind(root, KeyStroke.getKeyStroke('O', menu), "open", this::open);
    }

    private static void bind(javax.swing.JRootPane root, KeyStroke stroke, String name,
            Runnable action) {
        root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        root.getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // ---- the draft ----

    private void showDraft(StageDraft shown) {
        this.draft = shown;
        canvas.show(shown);
        seedField.setText(String.valueOf(shown.seed()));
        nameField.setText(shown.name());
        aboutField.setText(shown.description());
        difficulty.setValue(shown.difficulty());
        players.setValue(shown.players());
        recheck();
    }

    /**
     * Called after every edit: remember where we were, then say what is wrong now.
     *
     * <p>The snapshot is taken <em>before</em> the edit rather than after, which is
     * what makes undo land where the author expects — one press puts back the
     * state he was looking at when he clicked.
     */
    private void edited() {
        remember();
        recheck();
    }

    private void remember() {
        // The text of the draft before this edit is whatever the last check saw.
        if (lastChecked != null) {
            undo.push(lastChecked);
            redo.clear();
            while (undo.size() > HISTORY) {
                undo.removeLast();
            }
        }
    }

    private void recheck() {
        lastChecked = StageFile.write(draft.toStage());
        faults.clear();
        for (var problem : draft.problems()) {
            faults.addElement(problem);
        }
        var where = file == null ? "(not saved yet)" : file.toString();
        var dirty = lastChecked.equals(lastSaved) ? "" : " — unsaved";
        state.setText(faults.isEmpty()
                ? "Ready to play · " + where + dirty
                : faults.size() + " to fix · " + where + dirty);
    }

    private void readMetadata() {
        draft.setName(nameField.getText());
        draft.setDescription(aboutField.getText());
        draft.setDifficulty((Integer) difficulty.getValue());
        draft.setPlayers((Integer) players.getValue());
        // Put back what was actually kept, so a typed ';' visibly becomes a comma
        // rather than silently surviving until the file refuses it.
        nameField.setText(draft.name());
        aboutField.setText(draft.description());
        edited();
    }

    // ---- the buttons ----

    private void generateTyped() {
        try {
            regenerate(Long.parseLong(seedField.getText().strip()));
        } catch (NumberFormatException e) {
            say("That is not a seed: " + seedField.getText());
        }
    }

    private void generateFresh() {
        regenerate(System.nanoTime());
    }

    /**
     * Throw the floor away and draw another.
     *
     * <p>Everything placed goes with it, which is why it asks. A seed is a first
     * draft and looking at three of them before settling is the way this is meant
     * to be used — but so is spending an hour on the fourth.
     */
    private void regenerate(long seed) {
        if (!undo.isEmpty() && !confirm("Draw a new dungeon? Everything placed on this one"
                + " will be lost.")) {
            return;
        }
        remember();
        showDraft(StageDraft.generate(seed, settings));
    }

    private void undo() {
        step(undo, redo);
    }

    private void redo() {
        step(redo, undo);
    }

    private void step(ArrayDeque<String> from, ArrayDeque<String> to) {
        if (from.isEmpty()) {
            return;
        }
        to.push(lastChecked);
        showDraft(StageDraft.of(StageFile.read(from.pop(), "undo"), settings));
    }

    private void open() {
        var chooser = chooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        var chosen = chooser.getSelectedFile().toPath();
        try {
            var text = Files.readString(chosen, StandardCharsets.UTF_8);
            var opened = StageDraft.of(StageFile.read(text, chosen.toString()), settings);
            file = chosen;
            lastSaved = text;
            undo.clear();
            redo.clear();
            lastChecked = null;
            showDraft(opened);
        } catch (IOException | RuntimeException e) {
            say("Could not open " + chosen + ":\n" + e.getMessage());
        }
    }

    /**
     * Freeze it: write the stage to its file.
     *
     * <p>It will save a stage that has something wrong with it, on purpose. An
     * author stops in the middle, and a tool that refused to keep unfinished work
     * would teach him to finish everything in one sitting or lose it. The game is
     * the one that refuses to <em>play</em> a broken stage, which is the right
     * place for the refusal: that is where being wrong actually costs something.
     */
    private void save() {
        if (file == null) {
            saveAs();
            return;
        }
        try {
            var text = StageFile.write(draft.toStage());
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            lastSaved = text;
            recheck();
        } catch (IOException | RuntimeException e) {
            say("Could not save " + file + ":\n" + e.getMessage());
        }
    }

    private void saveAs() {
        var chooser = chooser();
        chooser.setSelectedFile(new java.io.File(draft.id() + "." + EXTENSION));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        var chosen = chooser.getSelectedFile().toPath();
        file = chosen.toString().endsWith("." + EXTENSION)
                ? chosen
                : chosen.resolveSibling(chosen.getFileName() + "." + EXTENSION);
        save();
    }

    private JFileChooser chooser() {
        var chooser = new JFileChooser(file == null
                ? Path.of("stages").toFile() : file.getParent().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Duke stage", EXTENSION));
        return chooser;
    }

    private boolean confirm(String question) {
        return JOptionPane.showConfirmDialog(this, question, "Duke World Builder",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }

    private void say(String message) {
        JOptionPane.showMessageDialog(this, message, "Duke World Builder",
                JOptionPane.WARNING_MESSAGE);
    }
}
