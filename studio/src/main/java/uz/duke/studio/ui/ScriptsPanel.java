package uz.duke.studio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import uz.duke.studio.model.ScriptCompiler;
import uz.duke.studio.model.StudioProject;

/**
 * The Studio's code editor: write {@code UnitScript} behaviours in Java, hit
 * Compile to check them with the real JDK compiler, then attach them to units
 * in the inspector. Play compiles automatically and refuses to start on
 * errors, so a broken script is caught here — never in the running game.
 */
public final class ScriptsPanel extends JPanel {

    private StudioProject project;
    private final Runnable onChange;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listModel);
    private final JTextArea editor = new JTextArea();
    private final JTextArea output = new JTextArea(5, 0);
    private String editingScript; // name of the script currently in the editor

    public ScriptsPanel(StudioProject project, Runnable onChange) {
        super(new BorderLayout());
        this.project = project;
        this.onChange = onChange;

        // left: script list + add/remove
        var left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(180, 0));
        left.setBorder(BorderFactory.createTitledBorder("Scripts"));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                openSelected();
            }
        });
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        var buttons = new JPanel();
        var add = new JButton("+");
        add.addActionListener(e -> addScript());
        var remove = new JButton("−");
        remove.addActionListener(e -> removeScript());
        buttons.add(add);
        buttons.add(remove);
        left.add(buttons, BorderLayout.SOUTH);

        // center: editor + compile output
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setTabSize(4);
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setForeground(new Color(180, 60, 60));

        var toolbar = new JPanel(new BorderLayout());
        var compile = new JButton("⚙ Compile all scripts");
        compile.addActionListener(e -> compileAll(true));
        toolbar.add(compile, BorderLayout.WEST);

        var center = new JPanel(new BorderLayout());
        center.add(toolbar, BorderLayout.NORTH);
        var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(editor), new JScrollPane(output));
        split.setResizeWeight(0.85);
        center.add(split, BorderLayout.CENTER);

        add(left, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        refresh();
    }

    public void setProject(StudioProject project) {
        this.project = project;
        editingScript = null;
        editor.setText("");
        refresh();
    }

    /** Push the editor's text back into the model (called before compile/save/play). */
    public void flushEditor() {
        if (editingScript != null) {
            var script = project.findScript(editingScript);
            if (script != null) {
                script.source = editor.getText();
            }
        }
    }

    /** Compile everything; optionally show a success dialog. Returns the result. */
    public ScriptCompiler.Result compileAll(boolean showSuccess) {
        flushEditor();
        var result = ScriptCompiler.compile(project.scripts);
        output.setText(result.ok() ? "" : result.errors());
        if (result.ok() && showSuccess) {
            JOptionPane.showMessageDialog(this,
                    project.scripts.isEmpty() ? "No scripts in the project."
                            : "All " + project.scripts.size() + " script(s) compile cleanly.",
                    "Compile", JOptionPane.INFORMATION_MESSAGE);
        }
        return result;
    }

    private void openSelected() {
        flushEditor();
        editingScript = list.getSelectedValue();
        var script = editingScript == null ? null : project.findScript(editingScript);
        editor.setText(script == null ? "" : script.source);
        editor.setCaretPosition(0);
        editor.setEnabled(script != null);
    }

    private void addScript() {
        var name = JOptionPane.showInputDialog(this,
                "Script class name (e.g. Berserker, GuardTower):", "New script",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        name = name.trim();
        if (!name.matches("[A-Z][A-Za-z0-9_]*")) {
            JOptionPane.showMessageDialog(this,
                    "The name must be a valid Java class name starting with a capital letter.");
            return;
        }
        if (project.findScript(name) != null) {
            JOptionPane.showMessageDialog(this, "A script named '" + name + "' already exists.");
            return;
        }
        project.scripts.add(new StudioProject.ScriptDef(name, StudioProject.scriptTemplate(name)));
        onChange.run();
        refresh();
        list.setSelectedValue(name, true);
    }

    private void removeScript() {
        var name = list.getSelectedValue();
        if (name == null) {
            return;
        }
        project.scripts.removeIf(s -> s.name.equals(name));
        for (var unit : project.units) {
            unit.scripts.remove(name);
        }
        if (name.equals(editingScript)) {
            editingScript = null;
            editor.setText("");
        }
        onChange.run();
        refresh();
    }

    public void refresh() {
        var selected = list.getSelectedValue();
        listModel.clear();
        for (var script : project.scripts) {
            listModel.addElement(script.name);
        }
        if (selected != null && project.findScript(selected) != null) {
            list.setSelectedValue(selected, false);
        }
    }
}
