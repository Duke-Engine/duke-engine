package uz.duke.studio.ui;

import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import uz.duke.studio.model.StudioProject;

/**
 * Edits the scenario's players: name, colour (hex), starting money and team —
 * same team means allies, different teams are enemies. Also picks which player
 * the window/local view belongs to.
 */
final class PlayersDialog extends JDialog {

    private final StudioProject project;
    private final DefaultTableModel model;
    private final JComboBox<String> localPlayer = new JComboBox<>();

    PlayersDialog(JFrame owner, StudioProject project) {
        super(owner, "Players", true);
        this.project = project;

        model = new DefaultTableModel(new Object[] {"Name", "Color (hex)", "Money", "Team", "Faction"}, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 2 || column == 3 ? Integer.class : String.class;
            }
        };
        for (var player : project.players) {
            model.addRow(new Object[] {player.name, player.colorHex, player.money, player.team, player.faction});
        }
        var table = new JTable(model);

        // faction column: pick from the project's factions (empty = any)
        var factionCombo = new JComboBox<String>();
        factionCombo.addItem("");
        for (var faction : project.factions) {
            factionCombo.addItem(faction.name);
        }
        table.getColumnModel().getColumn(4)
                .setCellEditor(new javax.swing.DefaultCellEditor(factionCombo));

        var buttons = new JPanel();
        var add = new JButton("Add player");
        add.addActionListener(e -> model.addRow(new Object[] {"Player", "#AAAAAA", 1000, model.getRowCount() + 1, ""}));
        var remove = new JButton("Remove selected");
        remove.addActionListener(e -> {
            if (table.getSelectedRow() >= 0 && model.getRowCount() > 1) {
                model.removeRow(table.getSelectedRow());
            }
        });
        var ok = new JButton("OK");
        ok.addActionListener(e -> {
            applyChanges();
            dispose();
        });
        buttons.add(add);
        buttons.add(remove);
        buttons.add(new JLabel("  Local player:"));
        for (int i = 0; i < project.players.size(); i++) {
            localPlayer.addItem((i + 1) + ". " + project.players.get(i).name);
        }
        localPlayer.setSelectedIndex(Math.min(project.localPlayer, project.players.size() - 1));
        buttons.add(localPlayer);
        buttons.add(ok);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(620, 320);
        setLocationRelativeTo(owner);
    }

    private void applyChanges() {
        project.players.clear();
        for (int row = 0; row < model.getRowCount(); row++) {
            project.players.add(new StudioProject.PlayerDef(
                    String.valueOf(model.getValueAt(row, 0)),
                    String.valueOf(model.getValueAt(row, 1)),
                    toInt(model.getValueAt(row, 2), 1000),
                    toInt(model.getValueAt(row, 3), 1),
                    String.valueOf(model.getValueAt(row, 4))));
        }
        project.localPlayer = Math.max(0,
                Math.min(localPlayer.getSelectedIndex(), project.players.size() - 1));
        // drop placements that referenced removed players
        project.placements.removeIf(p -> p.player < 0 || p.player >= project.players.size());
    }

    private static int toInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
