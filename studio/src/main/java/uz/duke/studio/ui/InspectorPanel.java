package uz.duke.studio.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import uz.duke.studio.model.CapabilityType;
import uz.duke.studio.model.StudioProject;

/**
 * The unit inspector — Unity's Inspector for duke units. Shows the selected
 * unit's properties, its assets (model / animations / sounds), and a checkbox
 * per {@link CapabilityType}: tick "Movement" and a speed form appears; the
 * Studio turns it into engine modules. "Apply" writes the form back to the
 * project.
 */
public final class InspectorPanel extends JPanel {

    /** Imports a chosen file into the project's assets; null if cancelled. */
    public interface AssetHandler {
        String importAsset(String category, String description, String... extensions);
    }

    private final Runnable onChange;
    private final AssetHandler assets;

    private StudioProject project;
    private StudioProject.UnitDef unit;

    private final JTextField name = new JTextField();
    private final JTextField displayName = new JTextField();
    private final javax.swing.JComboBox<String> faction = new javax.swing.JComboBox<>();
    private final JCheckBox structure = new JCheckBox("Structure (building)");
    private final JTextField maxHealth = new JTextField();
    private final JTextField buildCost = new JTextField();
    private final JTextField buildTime = new JTextField();
    private final JTextField visionRange = new JTextField();

    private final JTextField modelPath = new JTextField();
    private final JTextField modelScale = new JTextField();
    private final JTextField modelYOffset = new JTextField();
    private final JTextField modelFacing = new JTextField();
    private final JTextField idleAnim = new JTextField();
    private final JTextField walkAnim = new JTextField();
    private final JTextField attackAnim = new JTextField();
    private final JTextField fireSound = new JTextField();
    private final JTextField dieSound = new JTextField();

    private final Map<CapabilityType, JCheckBox> capabilityChecks = new LinkedHashMap<>();
    private final Map<CapabilityType, Map<String, JTextField>> capabilityFields = new LinkedHashMap<>();
    private final Map<CapabilityType, JPanel> capabilityForms = new LinkedHashMap<>();

    private final JPanel scriptsBox = new JPanel();
    private final Map<String, JCheckBox> scriptChecks = new LinkedHashMap<>();

    public InspectorPanel(Runnable onChange, AssetHandler assets) {
        super(new BorderLayout());
        this.onChange = onChange;
        this.assets = assets;

        var column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        column.add(section("Unit", form(
                row("Name (id)", name),
                row("Display name", displayName),
                row("Faction", faction),
                full(structure),
                row("Max health", maxHealth),
                row("Build cost ($)", buildCost),
                row("Build time (sec)", buildTime),
                row("Vision range", visionRange))));

        var capabilitiesBox = new JPanel();
        capabilitiesBox.setLayout(new BoxLayout(capabilitiesBox, BoxLayout.Y_AXIS));
        for (var type : CapabilityType.values()) {
            var check = new JCheckBox(type.getDisplayName());
            capabilityChecks.put(type, check);
            capabilitiesBox.add(check);

            var fields = new LinkedHashMap<String, JTextField>();
            var rows = new Component[type.getParams().size()][];
            int i = 0;
            for (var param : type.getParams()) {
                var field = new JTextField(param.defaultValue());
                fields.put(param.key(), field);
                rows[i++] = row(param.label(), field);
            }
            capabilityFields.put(type, fields);
            var formPanel = form(rows);
            formPanel.setBorder(BorderFactory.createEmptyBorder(0, 24, 4, 0));
            formPanel.setVisible(false);
            capabilityForms.put(type, formPanel);
            capabilitiesBox.add(formPanel);

            check.addActionListener(e -> formPanel.setVisible(check.isSelected()));
        }
        column.add(section("Capabilities", capabilitiesBox));

        scriptsBox.setLayout(new BoxLayout(scriptsBox, BoxLayout.Y_AXIS));
        column.add(section("Custom scripts (code)", scriptsBox));

        column.add(section("Assets (3D)", form(
                row("Model", browseField(modelPath, "Models",
                        "3D models (.glb recommended)", "glb", "gltf", "j3o", "obj", "xml")),
                row("Scale", modelScale),
                row("Y offset", modelYOffset),
                row("Facing offset (deg)", modelFacing),
                row("Idle animation", idleAnim),
                row("Walk animation", walkAnim),
                row("Attack animation", attackAnim),
                row("Fire sound", browseField(fireSound, "Sounds", "Audio (.wav/.ogg)", "wav", "ogg")),
                row("Die sound", browseField(dieSound, "Sounds", "Audio (.wav/.ogg)", "wav", "ogg")))));

        var apply = new JButton("Apply changes");
        apply.addActionListener(e -> apply());
        column.add(apply);
        column.add(Box.createVerticalGlue());

        var scroll = new JScrollPane(column);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        setSelection(null, null);
    }

    /** Show a unit in the inspector (or clear with nulls). */
    public void setSelection(StudioProject project, StudioProject.UnitDef unit) {
        this.project = project;
        this.unit = unit;
        boolean enabled = unit != null;
        setPanelEnabled(this, enabled);
        if (unit == null) {
            return;
        }
        name.setText(unit.name);
        displayName.setText(unit.displayName);
        faction.removeAllItems();
        faction.addItem(""); // shared by all factions
        for (var factionDef : project.factions) {
            faction.addItem(factionDef.name);
        }
        faction.setSelectedItem(unit.faction);
        structure.setSelected(unit.structure);
        maxHealth.setText(String.valueOf(unit.maxHealth));
        buildCost.setText(String.valueOf(unit.buildCost));
        buildTime.setText(String.valueOf(unit.buildTimeSeconds));
        visionRange.setText(String.valueOf(unit.visionRange));
        modelPath.setText(unit.modelPath);
        modelScale.setText(String.valueOf(unit.modelScale));
        modelYOffset.setText(String.valueOf(unit.modelYOffset));
        modelFacing.setText(String.valueOf(unit.modelFacing));
        idleAnim.setText(unit.idleAnim);
        walkAnim.setText(unit.walkAnim);
        attackAnim.setText(unit.attackAnim);
        fireSound.setText(unit.fireSound);
        dieSound.setText(unit.dieSound);

        for (var type : CapabilityType.values()) {
            var params = unit.capabilities.get(type.name());
            capabilityChecks.get(type).setSelected(params != null);
            capabilityForms.get(type).setVisible(params != null);
            for (var entry : capabilityFields.get(type).entrySet()) {
                var value = params != null ? params.get(entry.getKey()) : null;
                entry.getValue().setText(value != null ? value
                        : defaultFor(type, entry.getKey()));
            }
        }

        // scripts are project data, so this section is rebuilt per selection
        scriptsBox.removeAll();
        scriptChecks.clear();
        if (project.scripts.isEmpty()) {
            scriptsBox.add(new JLabel("  (write scripts in the Scripts tab)"));
        }
        for (var script : project.scripts) {
            var check = new JCheckBox(script.name);
            check.setSelected(unit.scripts.contains(script.name));
            scriptChecks.put(script.name, check);
            scriptsBox.add(check);
        }
        scriptsBox.revalidate();
        scriptsBox.repaint();
    }

    private static String defaultFor(CapabilityType type, String key) {
        for (var param : type.getParams()) {
            if (param.key().equals(key)) {
                return param.defaultValue();
            }
        }
        return "";
    }

    /** Write the form back into the project model. */
    private void apply() {
        if (unit == null) {
            return;
        }
        var newName = name.getText().trim();
        if (!newName.isEmpty() && !newName.equals(unit.name)) {
            // keep the scene consistent when a unit is renamed
            for (var placement : project.placements) {
                if (placement.unitName.equals(unit.name)) {
                    placement.unitName = newName;
                }
            }
            unit.name = newName;
        }
        unit.displayName = displayName.getText().trim();
        unit.faction = faction.getSelectedItem() == null ? "" : (String) faction.getSelectedItem();
        unit.structure = structure.isSelected();
        unit.maxHealth = parseFloat(maxHealth.getText(), unit.maxHealth);
        unit.buildCost = (int) parseFloat(buildCost.getText(), unit.buildCost);
        unit.buildTimeSeconds = parseFloat(buildTime.getText(), unit.buildTimeSeconds);
        unit.visionRange = parseFloat(visionRange.getText(), unit.visionRange);
        unit.modelPath = modelPath.getText().trim();
        unit.modelScale = parseFloat(modelScale.getText(), unit.modelScale);
        unit.modelYOffset = parseFloat(modelYOffset.getText(), unit.modelYOffset);
        unit.modelFacing = parseFloat(modelFacing.getText(), unit.modelFacing);
        unit.idleAnim = idleAnim.getText().trim();
        unit.walkAnim = walkAnim.getText().trim();
        unit.attackAnim = attackAnim.getText().trim();
        unit.fireSound = fireSound.getText().trim();
        unit.dieSound = dieSound.getText().trim();

        unit.capabilities.clear();
        for (var type : CapabilityType.values()) {
            if (!capabilityChecks.get(type).isSelected()) {
                continue;
            }
            var params = new LinkedHashMap<String, String>();
            for (var entry : capabilityFields.get(type).entrySet()) {
                params.put(entry.getKey(), entry.getValue().getText().trim());
            }
            unit.capabilities.put(type.name(), params);
        }

        unit.scripts.clear();
        for (var entry : scriptChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                unit.scripts.add(entry.getKey());
            }
        }
        onChange.run();
    }

    private static float parseFloat(String text, float fallback) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void setPanelEnabled(java.awt.Container container, boolean enabled) {
        for (var child : container.getComponents()) {
            child.setEnabled(enabled);
            if (child instanceof java.awt.Container inner) {
                setPanelEnabled(inner, enabled);
            }
        }
    }

    // ---- tiny form helpers ----

    /** A text field with a "…" button that imports a file into the project assets. */
    private Component browseField(JTextField field, String category, String description,
            String... extensions) {
        var panel = new JPanel(new BorderLayout(2, 0));
        panel.add(field, BorderLayout.CENTER);
        var browse = new JButton("…");
        browse.setMargin(new Insets(0, 6, 0, 6));
        browse.addActionListener(e -> {
            var imported = assets.importAsset(category, description, extensions);
            if (imported != null) {
                field.setText(imported);
            }
        });
        panel.add(browse, BorderLayout.EAST);
        return panel;
    }

    private static JPanel section(String title, Component body) {
        var panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(body, BorderLayout.CENTER);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static Component[] row(String label, Component field) {
        return new Component[] {new JLabel(label + ":"), field};
    }

    private static Component[] full(Component component) {
        return new Component[] {component};
    }

    private static JPanel form(Component[]... rows) {
        var panel = new JPanel(new GridBagLayout());
        var constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 4, 2, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        for (int y = 0; y < rows.length; y++) {
            var row = rows[y];
            constraints.gridy = y;
            if (row.length == 1) {
                constraints.gridx = 0;
                constraints.gridwidth = 2;
                constraints.weightx = 1;
                panel.add(row[0], constraints);
                constraints.gridwidth = 1;
            } else {
                constraints.gridx = 0;
                constraints.weightx = 0;
                panel.add(row[0], constraints);
                constraints.gridx = 1;
                constraints.weightx = 1;
                panel.add(row[1], constraints);
            }
        }
        return panel;
    }
}
