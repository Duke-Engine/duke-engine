package uz.duke.studio.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import uz.duke.studio.model.StudioProject;

/**
 * The faction inspector — edits a faction's identity (Rohan, Mordor, USA…).
 * Renaming a faction keeps every unit and player that references it in sync.
 */
public final class FactionPanel extends JPanel {

    private final Runnable onChange;

    private StudioProject project;
    private StudioProject.FactionDef faction;

    private final JTextField name = new JTextField();
    private final JTextField displayName = new JTextField();
    private final JTextField colorHex = new JTextField();
    private final JTextArea description = new JTextArea(4, 20);

    public FactionPanel(Runnable onChange) {
        super(new BorderLayout());
        this.onChange = onChange;

        var column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        var form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Faction"));
        var constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 4, 2, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, constraints, 0, "Name (id)", name);
        addRow(form, constraints, 1, "Display name", displayName);
        addRow(form, constraints, 2, "Color (hex)", colorHex);
        constraints.gridy = 3;
        constraints.gridx = 0;
        constraints.weightx = 0;
        form.add(new JLabel("Description:"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        description.setLineWrap(true);
        form.add(new JScrollPane(description), constraints);
        column.add(form);

        var apply = new JButton("Apply changes");
        apply.addActionListener(e -> apply());
        column.add(apply);

        column.add(buildStartingUnits());
        column.add(Box.createVerticalGlue());
        add(new javax.swing.JScrollPane(column), BorderLayout.CENTER);
    }

    private final javax.swing.table.DefaultTableModel startingModel =
            new javax.swing.table.DefaultTableModel(new Object[] {"Unit", "dx", "dy"}, 0) {
                @Override
                public Class<?> getColumnClass(int column) {
                    return column == 0 ? String.class : Float.class;
                }
            };

    private javax.swing.JPanel buildStartingUnits() {
        var panel = new javax.swing.JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                "Starting base (units this faction spawns with, offset from its start position)"));
        var table = new javax.swing.JTable(startingModel);
        table.setPreferredScrollableViewportSize(new java.awt.Dimension(300, 120));
        panel.add(new javax.swing.JScrollPane(table), BorderLayout.CENTER);

        var buttons = new javax.swing.JPanel();
        var unitCombo = new javax.swing.JComboBox<String>();
        buttons.add(unitCombo);
        var add = new JButton("Add unit");
        add.addActionListener(e -> {
            if (unitCombo.getSelectedItem() != null) {
                startingModel.addRow(new Object[] {unitCombo.getSelectedItem(), 0f, 0f});
                applyStartingUnits();
            }
        });
        var remove = new JButton("Remove");
        remove.addActionListener(e -> {
            if (table.getSelectedRow() >= 0) {
                startingModel.removeRow(table.getSelectedRow());
                applyStartingUnits();
            }
        });
        buttons.add(add);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);

        // keep the model→project sync on every edit
        startingModel.addTableModelListener(e -> applyStartingUnits());
        this.unitCombo = unitCombo;
        return panel;
    }

    private javax.swing.JComboBox<String> unitCombo;

    private void refreshUnitCombo() {
        if (unitCombo == null) {
            return;
        }
        unitCombo.removeAllItems();
        for (var unit : project.units) {
            unitCombo.addItem(unit.name);
        }
    }

    private boolean loadingStarting;

    private void applyStartingUnits() {
        if (faction == null || loadingStarting) {
            return;
        }
        faction.startingUnits.clear();
        for (int row = 0; row < startingModel.getRowCount(); row++) {
            var unit = String.valueOf(startingModel.getValueAt(row, 0));
            faction.startingUnits.add(new StudioProject.StartingUnit(unit,
                    toFloat(startingModel.getValueAt(row, 1)), toFloat(startingModel.getValueAt(row, 2))));
        }
        onChange.run();
    }

    private static float toFloat(Object value) {
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private static void addRow(JPanel form, GridBagConstraints constraints, int y,
            String label, JTextField field) {
        constraints.gridy = y;
        constraints.gridx = 0;
        constraints.weightx = 0;
        form.add(new JLabel(label + ":"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(field, constraints);
    }

    public void setSelection(StudioProject project, StudioProject.FactionDef faction) {
        this.project = project;
        this.faction = faction;
        if (faction == null) {
            return;
        }
        name.setText(faction.name);
        displayName.setText(faction.displayName);
        colorHex.setText(faction.colorHex);
        description.setText(faction.description);

        refreshUnitCombo();
        loadingStarting = true;
        startingModel.setRowCount(0);
        for (var startingUnit : faction.startingUnits) {
            startingModel.addRow(new Object[] {startingUnit.unit, startingUnit.dx, startingUnit.dy});
        }
        loadingStarting = false;
    }

    private void apply() {
        if (faction == null) {
            return;
        }
        var newName = name.getText().trim();
        if (!newName.isEmpty() && !newName.equals(faction.name)) {
            // keep unit and player references consistent on rename
            for (var unit : project.units) {
                if (unit.faction.equals(faction.name)) {
                    unit.faction = newName;
                }
            }
            for (var player : project.players) {
                if (player.faction.equals(faction.name)) {
                    player.faction = newName;
                }
            }
            faction.name = newName;
        }
        faction.displayName = displayName.getText().trim();
        faction.colorHex = colorHex.getText().trim();
        faction.description = description.getText();
        onChange.run();
    }
}
