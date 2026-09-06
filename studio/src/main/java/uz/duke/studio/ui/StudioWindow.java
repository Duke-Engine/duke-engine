package uz.duke.studio.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import uz.duke.client3d.Duke3D;
import uz.duke.studio.export.GameExporter;
import uz.duke.studio.io.ProjectIO;
import uz.duke.studio.model.CapabilityType;
import uz.duke.studio.model.GameFactory;
import uz.duke.studio.model.StudioProject;

/**
 * Duke Studio's main window — the Unity-style editor shell built around the
 * faction-first authoring flow: create factions (Rohan, Mordor, USA…), add
 * each faction's units beneath it, place them on the map for players of that
 * faction, then Play or Export.
 */
public final class StudioWindow extends JFrame {

    private static final String SHARED = "(shared units)";
    private static final String CARD_UNIT = "unit";
    private static final String CARD_FACTION = "faction";

    private StudioProject project;
    private Path projectFile;
    private boolean dirty;
    private volatile boolean playing;

    // undo/redo: whole-project JSON snapshots (the document is small)
    private static final int HISTORY_LIMIT = 100;
    private final java.util.ArrayDeque<String> undoStack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> redoStack = new java.util.ArrayDeque<>();
    private String lastSnapshot;

    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("project");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    private final JTree tree = new JTree(treeModel);

    private final InspectorPanel inspector = new InspectorPanel(this::onProjectChanged, this::importAsset);
    private final FactionPanel factionPanel = new FactionPanel(this::onProjectChanged);
    private final CardLayout inspectorCards = new CardLayout();
    private final JPanel inspectorHolder = new JPanel(inspectorCards);

    private final MapPanel mapPanel;
    private final javax.swing.JComboBox<String> mapSelector = new javax.swing.JComboBox<>();
    private final ScriptsPanel scriptsPanel;
    private final JTextArea iniPreview = new JTextArea();
    private final JButton playButton = new JButton("▶ Play 3D");

    public StudioWindow(StudioProject project) {
        super("Duke Studio");
        this.project = project;
        this.mapPanel = new MapPanel(project, this::onProjectChanged);
        this.scriptsPanel = new ScriptsPanel(project, this::onProjectChanged);

        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        add(buildProjectTreePanel(), BorderLayout.WEST);
        add(buildCenterTabs(), BorderLayout.CENTER);

        inspectorHolder.setPreferredSize(new Dimension(340, 0));
        inspectorHolder.add(inspector, CARD_UNIT);
        inspectorHolder.add(factionPanel, CARD_FACTION);
        add(inspectorHolder, BorderLayout.EAST);

        refreshAll();
        lastSnapshot = ProjectIO.toJson(project); // undo baseline
        setSize(1360, 860);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    /** Open the window on an already-loaded project file. */
    public StudioWindow(StudioProject project, Path projectFile) {
        this(project);
        this.projectFile = projectFile;
        refreshTitle();
    }

    // ---- layout pieces ----

    private JMenuBar buildMenuBar() {
        var bar = new JMenuBar();

        var file = new JMenu("File");
        file.add(item("New project", e -> newProject()));
        file.add(item("Open…", e -> openProject()));
        file.add(item("Save", e -> saveProject(false)));
        file.add(item("Save As…", e -> saveProject(true)));
        file.addSeparator();
        file.add(item("Export game…", e -> exportGame()));
        file.addSeparator();
        file.add(item("Exit", e -> dispose()));
        bar.add(file);

        var edit = new JMenu("Edit");
        var undoItem = item("Undo", e -> undo());
        undoItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke("control Z"));
        edit.add(undoItem);
        var redoItem = item("Redo", e -> redo());
        redoItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke("control Y"));
        edit.add(redoItem);
        edit.addSeparator();
        var duplicateItem = item("Duplicate unit", e -> duplicateUnit());
        duplicateItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke("control D"));
        edit.add(duplicateItem);
        bar.add(edit);

        var game = new JMenu("Game");
        game.add(item("Play 3D", e -> play(true)));
        game.add(item("Play 2D", e -> play(false)));
        bar.add(game);

        var projectMenu = new JMenu("Project");
        projectMenu.add(item("Add faction", e -> addFaction()));
        projectMenu.add(item("Players…", e -> editPlayers()));
        projectMenu.add(item("Add map", e -> addMap()));
        projectMenu.add(item("Map size…", e -> editMapSize()));
        projectMenu.add(item("Import map (text/image)…", e -> importMap()));
        projectMenu.add(item("Game menu…", e -> editGameMenu()));
        bar.add(projectMenu);

        var help = new JMenu("Help");
        help.add(item("About", e -> JOptionPane.showMessageDialog(this,
                """
                Duke Studio — the RTS game editor of duke-engine.

                The authoring flow:
                  1. Create factions (Rohan, Mordor, USA, China…)
                  2. Add each faction's units and their capabilities
                  3. Assign players a faction and place starting units
                  4. Press Play — export a standalone game when ready.
                """,
                "About", JOptionPane.INFORMATION_MESSAGE)));
        bar.add(help);
        return bar;
    }

    private JToolBar buildToolBar() {
        var bar = new JToolBar();
        bar.setFloatable(false);
        playButton.addActionListener(e -> play(true));
        bar.add(playButton);
        var play2d = new JButton("▶ Play 2D");
        play2d.addActionListener(e -> play(false));
        bar.add(play2d);
        bar.addSeparator();
        var export = new JButton("⇪ Export game");
        export.addActionListener(e -> exportGame());
        bar.add(export);
        return bar;
    }

    private JPanel buildProjectTreePanel() {
        var panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(230, 0));
        panel.setBorder(BorderFactory.createTitledBorder("Factions & Units"));

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> onTreeSelection());
        panel.add(new JScrollPane(tree), BorderLayout.CENTER);

        var buttons = new JPanel();
        var addFaction = new JButton("+ Faction");
        addFaction.addActionListener(e -> addFaction());
        var addUnit = new JButton("+ Unit");
        addUnit.addActionListener(e -> addUnit());
        var remove = new JButton("−");
        remove.addActionListener(e -> removeSelected());
        buttons.add(addFaction);
        buttons.add(addUnit);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JTabbedPane buildCenterTabs() {
        var tabs = new JTabbedPane();

        var mapTab = new JPanel(new BorderLayout());
        var mapBar = new JToolBar();
        mapBar.setFloatable(false);
        mapBar.add(new javax.swing.JLabel(" Map: "));
        mapSelector.addActionListener(e -> {
            var selected = (String) mapSelector.getSelectedItem();
            if (selected != null) {
                var chosen = project.findMap(selected);
                if (chosen != null) {
                    mapPanel.editMap(chosen);
                }
            }
        });
        mapBar.add(mapSelector);
        var addMapButton = new JButton("+ Map");
        addMapButton.addActionListener(e -> addMap());
        mapBar.add(addMapButton);
        mapTab.add(mapBar, BorderLayout.NORTH);
        mapTab.add(mapPanel, BorderLayout.CENTER);
        tabs.addTab("Map", mapTab);
        tabs.addTab("Scripts", scriptsPanel);
        iniPreview.setEditable(false);
        iniPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("Generated INI", new JScrollPane(iniPreview));
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 2) {
                iniPreview.setText(GameFactory.toIni(project));
            }
        });
        return tabs;
    }

    private JMenuItem item(String label, java.awt.event.ActionListener action) {
        var item = new JMenuItem(label);
        item.addActionListener(action);
        return item;
    }

    // ---- tree & selection ----

    /** Rebuild the faction→unit tree from the project, preserving selection. */
    private void rebuildTree() {
        var selected = selectedName();
        treeRoot.removeAllChildren();
        for (var faction : project.factions) {
            var factionNode = new DefaultMutableTreeNode(faction.name);
            for (var unit : project.units) {
                if (unit.faction.equals(faction.name)) {
                    factionNode.add(new DefaultMutableTreeNode(unit.name));
                }
            }
            treeRoot.add(factionNode);
        }
        var shared = new DefaultMutableTreeNode(SHARED);
        for (var unit : project.units) {
            if (unit.faction.isEmpty()) {
                shared.add(new DefaultMutableTreeNode(unit.name));
            }
        }
        if (shared.getChildCount() > 0) {
            treeRoot.add(shared);
        }
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        if (selected != null) {
            selectByName(selected);
        }
    }

    private String selectedName() {
        var node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        return node == null ? null : String.valueOf(node.getUserObject());
    }

    private void selectByName(String name) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            var node = (DefaultMutableTreeNode) tree.getPathForRow(i).getLastPathComponent();
            if (name.equals(String.valueOf(node.getUserObject()))) {
                tree.setSelectionRow(i);
                return;
            }
        }
    }

    private StudioProject.UnitDef selectedUnit() {
        var name = selectedName();
        return name == null ? null : project.findUnit(name);
    }

    private StudioProject.FactionDef selectedFaction() {
        var name = selectedName();
        return name == null ? null : project.findFaction(name);
    }

    /** The faction context for "+ Unit": the selected faction, or the selected unit's. */
    private String selectionFactionName() {
        var faction = selectedFaction();
        if (faction != null) {
            return faction.name;
        }
        var unit = selectedUnit();
        return unit != null ? unit.faction : "";
    }

    private void onTreeSelection() {
        var unit = selectedUnit();
        if (unit != null) {
            inspector.setSelection(project, unit);
            inspectorCards.show(inspectorHolder, CARD_UNIT);
            return;
        }
        var faction = selectedFaction();
        if (faction != null) {
            factionPanel.setSelection(project, faction);
            inspectorCards.show(inspectorHolder, CARD_FACTION);
        }
    }

    // ---- actions ----

    private void addFaction() {
        var name = JOptionPane.showInputDialog(this,
                "Faction name (e.g. Rohan, Mordor, USA):", "New faction",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        name = name.trim();
        if (project.findFaction(name) != null) {
            JOptionPane.showMessageDialog(this, "A faction named '" + name + "' already exists.");
            return;
        }
        project.factions.add(new StudioProject.FactionDef(name, name, "#888888"));
        onProjectChanged();
        selectByName(name);
    }

    private void addUnit() {
        if (project.factions.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Create a faction first — the flow is: faction, then its units.");
            return;
        }
        var unit = new StudioProject.UnitDef();
        int n = project.units.size() + 1;
        while (project.findUnit("Unit" + n) != null) {
            n++;
        }
        unit.name = "Unit" + n;
        unit.faction = selectionFactionName().isEmpty()
                ? project.factions.get(0).name
                : selectionFactionName();
        unit.capabilities.put(CapabilityType.MOVE.name(), StudioProject.defaults(CapabilityType.MOVE));
        project.units.add(unit);
        onProjectChanged();
        selectByName(unit.name);
    }

    private void removeSelected() {
        var unit = selectedUnit();
        if (unit != null) {
            project.units.remove(unit);
            for (var map : project.maps) {
                map.neutrals.removeIf(p -> p.unitName.equals(unit.name));
            }
            for (var faction : project.factions) {
                faction.startingUnits.removeIf(s -> unit.name.equals(s.unit));
            }
            onProjectChanged();
            return;
        }
        var faction = selectedFaction();
        if (faction != null) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Remove faction '" + faction.name + "'?\nIts units become shared (not deleted).",
                    "Remove faction", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            project.factions.remove(faction);
            project.ensureIntegrity(); // units/players referencing it become shared
            onProjectChanged();
        }
    }

    private void newProject() {
        project = StudioProject.starter();
        projectFile = null;
        undoStack.clear();
        redoStack.clear();
        lastSnapshot = ProjectIO.toJson(project);
        mapPanel.setProject(project);
        scriptsPanel.setProject(project);
        dirty = true;
        refreshAll();
    }

    private void openProject() {
        var chooser = projectChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            project = ProjectIO.load(chooser.getSelectedFile().toPath());
            projectFile = chooser.getSelectedFile().toPath();
            undoStack.clear();
            redoStack.clear();
            lastSnapshot = ProjectIO.toJson(project);
            mapPanel.setProject(project);
            scriptsPanel.setProject(project);
            dirty = false;
            refreshAll();
        } catch (Exception e) {
            error("Could not open project", e);
        }
    }

    private void saveProject(boolean saveAs) {
        scriptsPanel.flushEditor(); // the code in the editor belongs to the save
        if (projectFile == null || saveAs) {
            var chooser = projectChooser();
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            var path = chooser.getSelectedFile().toPath();
            if (!path.toString().endsWith("." + ProjectIO.EXTENSION)) {
                path = path.resolveSibling(path.getFileName() + "." + ProjectIO.EXTENSION);
            }
            projectFile = path;
        }
        try {
            ProjectIO.save(project, projectFile);
            dirty = false;
            refreshTitle();
        } catch (Exception e) {
            error("Could not save project", e);
        }
    }

    private JFileChooser projectChooser() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Duke Studio project (*.duke)", ProjectIO.EXTENSION));
        return chooser;
    }

    private void play(boolean threeD) {
        if (playing) {
            JOptionPane.showMessageDialog(this, "A game window is already running — close it first.");
            return;
        }
        // scripts must compile before the game may start
        var compiled = scriptsPanel.compileAll(false);
        if (!compiled.ok()) {
            JOptionPane.showMessageDialog(this,
                    "Fix the script errors first (see the Scripts tab):\n\n" + compiled.errors(),
                    "Scripts do not compile", JOptionPane.ERROR_MESSAGE);
            return;
        }
        playing = true;
        playButton.setEnabled(false);
        new Thread(() -> {
            try {
                var game = GameFactory.toGame(project, compiled.scripts());
                if (threeD) {
                    var visuals = GameFactory.toVisuals(project);
                    var assets = assetsRoot();
                    if (assets != null && Files.isDirectory(assets)) {
                        visuals.assetRoot(assets.toString());
                    }
                    Duke3D.launch(game, visuals);
                } else {
                    game.start();
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> error("Could not start the game", e));
            } finally {
                playing = false;
                SwingUtilities.invokeLater(() -> playButton.setEnabled(true));
            }
        }, "duke-play").start();
    }

    private void exportGame() {
        var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose an empty folder for the exported game");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            var summary = GameExporter.export(project,
                    chooser.getSelectedFile().toPath(), findEngineRoot(), assetsRoot());
            JOptionPane.showMessageDialog(this, summary, "Export finished", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            error("Export failed", e);
        }
    }

    /** The project's assets folder ({@code <name>_assets} next to the .duke file). */
    private Path assetsRoot() {
        if (projectFile == null) {
            return null;
        }
        var name = projectFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return projectFile.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + "_assets");
    }

    /** Import a file into the assets folder; returns the engine-relative path. */
    private String importAsset(String category, String description, String... extensions) {
        if (projectFile == null) {
            JOptionPane.showMessageDialog(this,
                    "Save the project first — assets live in a folder next to the project file.");
            saveProject(false);
            if (projectFile == null) {
                return null;
            }
        }
        var chooser = new JFileChooser();
        chooser.setDialogTitle("Import into " + category);
        chooser.setFileFilter(new FileNameExtensionFilter(description, extensions));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        try {
            var relative = uz.duke.studio.io.AssetImporter.importAsset(
                    assetsRoot(), chooser.getSelectedFile().toPath(), category);
            onProjectChanged();
            return relative;
        } catch (Exception e) {
            error("Could not import the asset", e);
            return null;
        }
    }

    /** Walk up from the working directory to the duke-engine root. */
    private static Path findEngineRoot() {
        var dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++, dir = dir.getParent()) {
            if (Files.exists(dir.resolve("settings.gradle.kts")) && Files.isDirectory(dir.resolve("core"))) {
                return dir;
            }
        }
        return Path.of("").toAbsolutePath();
    }

    private void editPlayers() {
        var dialog = new PlayersDialog(this, project);
        dialog.setVisible(true);
        onProjectChanged();
    }

    private StudioProject.MapDef currentMap() {
        var selected = (String) mapSelector.getSelectedItem();
        var map = selected == null ? null : project.findMap(selected);
        return map != null ? map : (project.maps.isEmpty() ? null : project.maps.get(0));
    }

    private void addMap() {
        var name = JOptionPane.showInputDialog(this, "Map name:", "New map", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        name = name.trim();
        if (project.findMap(name) != null) {
            JOptionPane.showMessageDialog(this, "A map named '" + name + "' already exists.");
            return;
        }
        project.maps.add(new StudioProject.MapDef(name, 70, 45));
        onProjectChanged();
        mapSelector.setSelectedItem(name);
    }

    private void editMapSize() {
        var map = currentMap();
        if (map == null) {
            return;
        }
        var wide = new JSpinner(new SpinnerNumberModel(map.cellsWide, 10, 400, 5));
        var high = new JSpinner(new SpinnerNumberModel(map.cellsHigh, 10, 400, 5));
        var panel = new JPanel();
        panel.add(new javax.swing.JLabel("Cells wide:"));
        panel.add(wide);
        panel.add(new javax.swing.JLabel("Cells high:"));
        panel.add(high);
        if (JOptionPane.showConfirmDialog(this, panel, "Map size — " + map.name,
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            map.cellsWide = (int) wide.getValue();
            map.cellsHigh = (int) high.getValue();
            onProjectChanged();
        }
    }

    private void importMap() {
        var chooser = new JFileChooser();
        chooser.setDialogTitle("Import map — ASCII text (# = blocked) or image (dark = blocked)");
        chooser.setFileFilter(new FileNameExtensionFilter("Maps (text or image)",
                "txt", "map", "png", "jpg", "jpeg", "bmp", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        var map = currentMap();
        if (map == null) {
            return;
        }
        try {
            var summary = uz.duke.studio.io.MapImporter.importInto(
                    map, chooser.getSelectedFile().toPath());
            onProjectChanged();
            JOptionPane.showMessageDialog(this, summary, "Map imported", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            error("Could not import the map", e);
        }
    }

    private void editGameMenu() {
        var titleField = new javax.swing.JTextField(project.title, 24);
        var subtitleField = new javax.swing.JTextField(project.menuSubtitle, 24);
        var panel = new JPanel(new java.awt.GridLayout(2, 2, 4, 4));
        panel.add(new javax.swing.JLabel("Game title:"));
        panel.add(titleField);
        panel.add(new javax.swing.JLabel("Menu subtitle:"));
        panel.add(subtitleField);
        if (JOptionPane.showConfirmDialog(this, panel, "Game menu",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            project.title = titleField.getText().trim();
            project.menuSubtitle = subtitleField.getText().trim();
            onProjectChanged();
        }
    }

    private void error(String message, Exception e) {
        JOptionPane.showMessageDialog(this, message + ":\n" + e, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ---- undo / redo ----

    /** Snapshot-based history: every change pushes the previous whole document. */
    private void recordHistory() {
        if (lastSnapshot != null) {
            undoStack.push(lastSnapshot);
            while (undoStack.size() > HISTORY_LIMIT) {
                undoStack.removeLast();
            }
            redoStack.clear();
        }
        lastSnapshot = ProjectIO.toJson(project);
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(ProjectIO.toJson(project));
        restoreSnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(ProjectIO.toJson(project));
        restoreSnapshot(redoStack.pop());
    }

    private void restoreSnapshot(String json) {
        project = ProjectIO.fromJson(json);
        lastSnapshot = json;
        dirty = true;
        mapPanel.setProject(project);
        scriptsPanel.setProject(project);
        refreshAll();
    }

    private void duplicateUnit() {
        var unit = selectedUnit();
        if (unit == null) {
            return;
        }
        // deep copy through the same JSON the document uses
        var copy = ProjectIO.fromJson(ProjectIO.toJson(project)).findUnit(unit.name);
        int n = 2;
        while (project.findUnit(copy.name + n) != null) {
            n++;
        }
        copy.name = copy.name + n;
        project.units.add(copy);
        onProjectChanged();
        selectByName(copy.name);
    }

    // ---- refresh ----

    private void onProjectChanged() {
        dirty = true;
        project.ensureIntegrity();
        recordHistory();
        refreshAll();
    }

    private void refreshAll() {
        rebuildTree();
        rebuildMapSelector();
        mapPanel.refreshTools();
        scriptsPanel.refresh();
        refreshTitle();
        onTreeSelection();
    }

    private void rebuildMapSelector() {
        var selected = (String) mapSelector.getSelectedItem();
        var model = new javax.swing.DefaultComboBoxModel<String>();
        for (var map : project.maps) {
            model.addElement(map.name);
        }
        mapSelector.setModel(model);
        if (selected != null && project.findMap(selected) != null) {
            mapSelector.setSelectedItem(selected);
        } else if (model.getSize() > 0) {
            mapSelector.setSelectedIndex(0);
        }
    }

    private void refreshTitle() {
        setTitle("Duke Studio — " + project.title
                + (projectFile != null ? " [" + projectFile.getFileName() + "]" : "")
                + (dirty ? " *" : ""));
    }
}
