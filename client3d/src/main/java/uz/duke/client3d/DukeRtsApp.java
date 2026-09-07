package uz.duke.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.app.SimpleApplication;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import uz.duke.core.event.ObjectDied;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.ObjectId;
import uz.duke.game.DukeGame;
import uz.duke.game.view.UnitView;
import uz.duke.game.view.WorldSnapshot;
import uz.duke.rts.event.WeaponFired;
import uz.duke.rts.message.GameMessage;

/**
 * The 3D presentation of a {@link DukeGame}: renders the simulation with
 * jMonkeyEngine, playing each unit's model, animations and sounds as configured
 * in {@link Visuals} — with clean primitive stand-ins for units that have no
 * art yet.
 *
 * <p>Reads only the thread-safe {@link WorldSnapshot}; every command crosses
 * back through {@link DukeGame#postCommand}, so the deterministic simulation
 * stays untouched by the render thread.
 *
 * <p>Controls: LMB select (Shift adds), RMB move / attack, WASD-arrows pan,
 * wheel zoom, H halt, P pause.
 */
final class DukeRtsApp extends SimpleApplication {

    private static final Logger LOG = Logger.getLogger(DukeRtsApp.class.getName());

    /** How long a muzzle flash stays lit after a shot. Display time, not game time. */
    private static final float MUZZLE_FLASH_SECONDS = 0.08f;

    private enum Screen { MENU, PLAYING, PAUSED, SETTINGS }

    /** Persisted display/audio settings, shared by every duke-engine game. */
    static final java.util.prefs.Preferences PREFS =
            java.util.prefs.Preferences.userRoot().node("duke-engine/game");
    private static final int[][] RESOLUTIONS = {{1280, 720}, {1600, 900}, {1920, 1080}};
    private static final int[] VOLUMES = {100, 75, 50, 25, 0};

    private final DukeGame game;
    private final Visuals visuals;
    private final Shell shell;
    private final CountDownLatch stopped = new CountDownLatch(1);

    private Screen screen = Screen.MENU;
    private Screen settingsReturn = Screen.MENU;
    private MenuOverlay menu;
    private Thread simThread; // started when the player presses Play

    private final Node unitsNode = new Node("units");
    private final Map<Integer, UnitNode> unitNodes = new HashMap<>();
    private final Set<Integer> selected = new HashSet<>();
    private final Map<String, AudioNode> audioCache = new HashMap<>();
    private final Set<String> missingAssets = new HashSet<>();

    private WorldSnapshot snapshot = WorldSnapshot.EMPTY;
    private WorldSnapshot lastEventedSnapshot = WorldSnapshot.EMPTY;
    private final Vector3f camTarget = new Vector3f();
    private float camDistance = 140f;
    private final boolean[] pan = new boolean[4]; // W A S D
    private BitmapText hud;
    private BitmapText buildMenu;
    private BitmapText banner;

    // minimap: fixed-size overlay in the bottom-right corner
    private static final float MINIMAP_SIZE = 190f;
    private final Node minimapNode = new Node("minimap");
    private final Map<Integer, Geometry> minimapDots = new HashMap<>();
    private float minimapScale;   // screen px per world unit
    private float minimapX;       // screen position of the minimap's origin
    private float minimapY;

    /** Everything the scene keeps per live unit. */
    private static final class UnitNode {
        Node root;
        Geometry ring;
        Node healthBar;
        Geometry healthFill;
        Geometry flash;
        AnimComposer composer;
        AnimChannel legacyChannel;
        String currentAnim = "";
        float flashUntil;
        UnitView view;
    }

    DukeRtsApp(DukeGame game, Visuals visuals, Shell shell) {
        this.game = game;
        this.visuals = visuals;
        this.shell = shell;
    }

    /** The simulation thread, if the player ever pressed Play. */
    Thread getSimThread() {
        return simThread;
    }

    void awaitStop() throws InterruptedException {
        stopped.await();
    }

    @Override
    public void destroy() {
        super.destroy();
        stopped.countDown();
    }

    // ---- scene setup ----

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.07f, 0.10f, 1f));

        // project assets folder (Studio Play); exported games use the classpath
        if (visuals.getAssetRoot() != null) {
            assetManager.registerLocator(visuals.getAssetRoot(),
                    com.jme3.asset.plugins.FileLocator.class);
        }

        var sun = new DirectionalLight(new Vector3f(-0.4f, -1f, -0.5f).normalizeLocal(),
                new ColorRGBA(1f, 0.97f, 0.9f, 1f));
        rootNode.addLight(sun);
        rootNode.addLight(new AmbientLight(new ColorRGBA(0.45f, 0.45f, 0.5f, 1f)));

        buildTerrain();
        rootNode.attachChild(unitsNode);

        centerCameraOnOwnBase();
        installInput();

        hud = new BitmapText(guiFont);
        hud.setLocalTranslation(10, cam.getHeight() - 10f, 0);
        guiNode.attachChild(hud);

        buildMenu = new BitmapText(guiFont);
        buildMenu.setLocalTranslation(10, cam.getHeight() - 40f, 0);
        buildMenu.setColor(new ColorRGBA(0.8f, 1f, 0.8f, 1f));
        guiNode.attachChild(buildMenu);

        banner = new BitmapText(guiFont);
        banner.setSize(guiFont.getCharSet().getRenderedSize() * 4f);
        banner.setColor(new ColorRGBA(1f, 0.9f, 0.3f, 1f));
        guiNode.attachChild(banner);

        var hint = new BitmapText(guiFont);
        hint.setText("LMB select   Shift+LMB add   RMB move/attack   WASD pan   wheel zoom   H halt   P pause   Esc menu");
        hint.setLocalTranslation(10, hint.getLineHeight() + 6f, 0);
        hint.setAlpha(0.6f);
        guiNode.attachChild(hint);

        buildMinimap();
        menu = new MenuOverlay(guiFont, assetManager, guiNode, cam.getWidth(), cam.getHeight());
        showMainMenu();
        applyVolume();
    }

    /** Static minimap backdrop: map bounds + blocked terrain; unit dots update live. */
    private void buildMinimap() {
        var grid = game.getTerrain();
        float worldW = grid == null ? 700f : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? 450f : grid.getHeight() * grid.getCellSize();
        minimapScale = MINIMAP_SIZE / Math.max(worldW, worldH);
        minimapX = cam.getWidth() - worldW * minimapScale - 12f;
        minimapY = 34f; // above the hint line

        var backdrop = new Geometry("mm-bg", new Quad(worldW * minimapScale, worldH * minimapScale));
        backdrop.setMaterial(unshaded(new ColorRGBA(0.10f, 0.14f, 0.08f, 1f)));
        minimapNode.attachChild(backdrop);

        if (grid != null) {
            var rock = unshaded(new ColorRGBA(0.35f, 0.32f, 0.26f, 1f));
            float cellPx = grid.getCellSize() * minimapScale;
            for (int cy = 0; cy < grid.getHeight(); cy++) {
                for (int cx = 0; cx < grid.getWidth(); cx++) {
                    if (!grid.isBlocked(cx, cy)) {
                        continue;
                    }
                    var cell = new Geometry("mm-rock", new Quad(cellPx, cellPx));
                    cell.setMaterial(rock);
                    // minimap y grows up the screen, world y grows down the map
                    cell.setLocalTranslation(cx * cellPx, (grid.getHeight() - 1 - cy) * cellPx, 0);
                    minimapNode.attachChild(cell);
                }
            }
        }
        minimapNode.setLocalTranslation(minimapX, minimapY, 0);
        guiNode.attachChild(minimapNode);
    }

    /** World → minimap screen position (y flipped: screen up vs map down). */
    private com.jme3.math.Vector2f minimapPoint(float worldX, float worldY) {
        var grid = game.getTerrain();
        float worldH = grid == null ? 450f : grid.getHeight() * grid.getCellSize();
        return new com.jme3.math.Vector2f(worldX * minimapScale, (worldH - worldY) * minimapScale);
    }

    /** If the cursor is over the minimap, move the camera there. Returns true if handled. */
    private boolean minimapClick() {
        var cursor = inputManager.getCursorPosition();
        var grid = game.getTerrain();
        float worldW = grid == null ? 700f : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? 450f : grid.getHeight() * grid.getCellSize();
        float localX = cursor.x - minimapX;
        float localY = cursor.y - minimapY;
        if (localX < 0 || localY < 0
                || localX > worldW * minimapScale || localY > worldH * minimapScale) {
            return false;
        }
        camTarget.set(localX / minimapScale, 0, worldH - localY / minimapScale);
        return true;
    }

    /** Live unit dots, coloured by player, sized up for structures. */
    private void syncMinimap() {
        var seen = new HashSet<Integer>();
        for (var view : snapshot.units()) {
            seen.add(view.id());
            var dot = minimapDots.computeIfAbsent(view.id(), id -> {
                float size = view.structure() ? 6f : 3.5f;
                var geometry = new Geometry("mm-dot", new Quad(size, size));
                geometry.setMaterial(unshaded(toColor(game.getColor(view.playerIndex()))));
                minimapNode.attachChild(geometry);
                return geometry;
            });
            var point = minimapPoint(view.x(), view.y());
            dot.setLocalTranslation(point.x - 2f, point.y - 2f, 1);
        }
        var gone = minimapDots.entrySet().iterator();
        while (gone.hasNext()) {
            var entry = gone.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().removeFromParent();
                gone.remove();
            }
        }
    }

    // ---- menus ----

    // skirmish choice (null = the game's defaults)
    private String chosenMap;
    private final java.util.List<String> chosenFactions = new java.util.ArrayList<>();

    /**
     * Show the front menu the game asked for.
     *
     * <p>Which entries exist is the game's call ({@link Shell}); whether one of
     * them would mean anything is still the client's. Offering a LAN game to a
     * game that seats one player, or a map choice to one with a single map, would
     * be a dead button.
     */
    private void showMainMenu() {
        if (shell.startsImmediately()) {
            startGame(); // this game has no front menu; nothing to come back to
            return;
        }
        screen = Screen.MENU;
        var items = new java.util.ArrayList<MenuOverlay.Item>();
        for (var chosen : shell.entries()) {
            var action = actionFor(chosen.getKey());
            if (action != null) {
                items.add(new MenuOverlay.Item(chosen.getValue(), action));
            }
        }
        menu.show(game.getTitle(), game.getSubtitle(), items);
    }

    /** What an entry does, or {@code null} when it would do nothing worth offering. */
    private Runnable actionFor(Shell.Entry entry) {
        return switch (entry) {
            case PLAY -> this::startGame;
            case SKIRMISH -> !game.getMapChoices().isEmpty() && !game.isMultiplayer()
                    ? this::showSkirmishMenu : null;
            case HOST_LAN -> game.supportsMultiplayer() && !game.isMultiplayer()
                    ? this::hostFlow : null;
            case JOIN_LAN -> game.supportsMultiplayer() && !game.isMultiplayer()
                    ? this::joinFlow : null;
            case SETTINGS -> () -> showSettingsMenu(Screen.MENU);
            case QUIT -> this::stop;
        };
    }

    /** Choose the map and cycle each player's faction before playing. */
    private void showSkirmishMenu() {
        var maps = game.getMapChoices();
        var factions = game.getFactionChoices();
        if (chosenMap == null && !maps.isEmpty()) {
            chosenMap = maps.get(0);
        }
        while (chosenFactions.size() < 2 && !factions.isEmpty()) {
            chosenFactions.add(factions.get(chosenFactions.size() % factions.size()));
        }

        var items = new java.util.ArrayList<MenuOverlay.Item>();
        items.add(new MenuOverlay.Item("Map: " + chosenMap, () -> {
            chosenMap = maps.get((maps.indexOf(chosenMap) + 1) % maps.size());
            showSkirmishMenu();
        }));
        for (int p = 0; p < chosenFactions.size(); p++) {
            final int player = p;
            items.add(new MenuOverlay.Item("Player " + (p + 1) + ": " + chosenFactions.get(p), () -> {
                int next = (factions.indexOf(chosenFactions.get(player)) + 1) % factions.size();
                chosenFactions.set(player, factions.get(next));
                showSkirmishMenu();
            }));
        }
        items.add(new MenuOverlay.Item("Start match", () -> {
            game.selectSkirmish(chosenMap, chosenFactions);
            startGame();
        }));
        items.add(new MenuOverlay.Item("Back", this::showMainMenu));
        menu.show(game.getTitle(), "skirmish setup", items);
    }

    // ---- multiplayer flows (menu thread-hops: network blocks, jME must not) ----

    private void hostFlow() {
        int port = uz.duke.game.MultiplayerSession.DEFAULT_PORT;
        int wanted = game.getMaxNetworkPlayers();
        showLobby(port, 0, wanted);
        new Thread(() -> {
            try {
                // The count comes back as each guest arrives, so the host can see
                // who is still missing instead of staring at "waiting…".
                game.hostMultiplayer(port, wanted, joined -> enqueue(() -> showLobby(port, joined, wanted)));
                enqueue(this::startGame);
            } catch (Exception e) {
                enqueue(this::showMainMenu); // cancelled or failed — back to the menu
            }
        }, "duke-host").start();
    }

    private void showLobby(int port, int joined, int wanted) {
        menu.show(game.getTitle(),
                "hosting on port " + port + " — " + (joined + 1) + " of " + wanted + " players in",
                java.util.List.of(new MenuOverlay.Item("Cancel", () -> {
                    game.cancelHosting();
                    showMainMenu();
                })));
    }

    private void joinFlow() {
        menu.show(game.getTitle(), "joining…", java.util.List.of());
        new Thread(() -> {
            var ip = askText("Host IP address:", "127.0.0.1");
            if (ip == null || ip.isBlank()) {
                enqueue(this::showMainMenu);
                return;
            }
            try {
                game.joinMultiplayer(ip.trim(), uz.duke.game.MultiplayerSession.DEFAULT_PORT);
                enqueue(this::startGame);
            } catch (Exception e) {
                enqueue(this::showMainMenu);
            }
        }, "duke-join").start();
    }

    private static String askText(String prompt, String initial) {
        var result = new String[1];
        try {
            javax.swing.SwingUtilities.invokeAndWait(() ->
                    result[0] = javax.swing.JOptionPane.showInputDialog(null, prompt, initial));
        } catch (Exception e) {
            return null;
        }
        return result[0];
    }

    private void startGame() {
        if (simThread == null) {
            simThread = game.startEngineOnly();
        }
        menu.hide();
        screen = Screen.PLAYING;
    }

    private void showPauseMenu() {
        screen = Screen.PAUSED;
        game.runOnSimThread(() -> game.getLogic().setGamePaused(true));
        menu.show(game.getTitle(), "paused", java.util.List.of(
                new MenuOverlay.Item("Resume", this::resumeGame),
                new MenuOverlay.Item("Settings", () -> showSettingsMenu(Screen.PAUSED)),
                new MenuOverlay.Item("Quit to Desktop", this::stop)));
    }

    private void resumeGame() {
        game.runOnSimThread(() -> game.getLogic().setGamePaused(false));
        menu.hide();
        screen = Screen.PLAYING;
    }

    // ---- settings ----

    private void showSettingsMenu(Screen returnTo) {
        settingsReturn = returnTo;
        screen = Screen.SETTINGS;
        boolean fullscreen = PREFS.getBoolean("fullscreen", false);
        int resIndex = PREFS.getInt("resIndex", 0);
        int volume = PREFS.getInt("volume", 100);
        menu.show(game.getTitle(), "settings", java.util.List.of(
                new MenuOverlay.Item("Fullscreen: " + (fullscreen ? "ON" : "OFF"), () -> {
                    PREFS.putBoolean("fullscreen", !fullscreen);
                    applyDisplaySettings();
                }),
                new MenuOverlay.Item("Resolution: " + RESOLUTIONS[resIndex][0] + "×" + RESOLUTIONS[resIndex][1],
                        () -> {
                            PREFS.putInt("resIndex", (resIndex + 1) % RESOLUTIONS.length);
                            applyDisplaySettings();
                        }),
                new MenuOverlay.Item("Volume: " + volume + "%", () -> {
                    int next = VOLUMES[(indexOf(VOLUMES, volume) + 1) % VOLUMES.length];
                    PREFS.putInt("volume", next);
                    applyVolume();
                    showSettingsMenu(settingsReturn); // refresh the label
                }),
                new MenuOverlay.Item("Back", this::leaveSettings)));
    }

    private void leaveSettings() {
        if (settingsReturn == Screen.PAUSED) {
            showPauseMenu();
        } else {
            showMainMenu();
        }
    }

    /** Apply resolution/fullscreen by restarting the display context. */
    private void applyDisplaySettings() {
        int resIndex = PREFS.getInt("resIndex", 0);
        settings.setResolution(RESOLUTIONS[resIndex][0], RESOLUTIONS[resIndex][1]);
        settings.setFullscreen(PREFS.getBoolean("fullscreen", false));
        setSettings(settings);
        restart(); // reshape() rebuilds the menu at the new size
    }

    private void applyVolume() {
        listener.setVolume(PREFS.getInt("volume", 100) / 100f);
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        if (hud == null) {
            return; // not initialised yet
        }
        hud.setLocalTranslation(10, height - 10f, 0);
        buildMenu.setLocalTranslation(10, height - 40f, 0);
        var grid = game.getTerrain();
        float worldW = grid == null ? 700f : grid.getWidth() * grid.getCellSize();
        minimapX = width - worldW * minimapScale - 12f;
        minimapNode.setLocalTranslation(minimapX, minimapY, 0);
        // menus are sized to the screen — rebuild the current one
        menu.destroy();
        menu = new MenuOverlay(guiFont, assetManager, guiNode, width, height);
        switch (screen) {
            case MENU -> showMainMenu();
            case PAUSED -> showPauseMenu();
            case SETTINGS -> showSettingsMenu(settingsReturn);
            case PLAYING -> menu.hide();
        }
    }

    private void buildTerrain() {
        var grid = game.getTerrain();
        float worldW = grid == null ? 700f : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? 450f : grid.getHeight() * grid.getCellSize();

        var ground = new Geometry("ground", new Quad(worldW, worldH));
        ground.setMaterial(lit(new ColorRGBA(0.16f, 0.22f, 0.13f, 1f)));
        ground.rotate(-FastMath.HALF_PI, 0, 0);
        ground.setLocalTranslation(0, 0, worldH);
        rootNode.attachChild(ground);

        if (grid == null) {
            return;
        }
        float cell = grid.getCellSize();
        var rockMat = lit(new ColorRGBA(0.25f, 0.23f, 0.20f, 1f));
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (!grid.isBlocked(cx, cy)) {
                    continue;
                }
                var rock = new Geometry("rock", new Box(cell / 2f, 3f, cell / 2f));
                rock.setMaterial(rockMat);
                rock.setLocalTranslation((cx + 0.5f) * cell, 3f, (cy + 0.5f) * cell);
                rootNode.attachChild(rock);
            }
        }
    }

    private void centerCameraOnOwnBase() {
        // Aim at the first own unit once the first snapshot arrives; start mid-map.
        var grid = game.getTerrain();
        camTarget.set(grid == null ? 350f : grid.getWidth() * grid.getCellSize() / 2f, 0,
                grid == null ? 225f : grid.getHeight() * grid.getCellSize() / 2f);
    }

    // ---- input ----

    private void installInput() {
        inputManager.addMapping("Select", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("Order", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping("PanUp", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("PanLeft", new KeyTrigger(KeyInput.KEY_A), new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping("PanDown", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("PanRight", new KeyTrigger(KeyInput.KEY_D), new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping("Shift", new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
        inputManager.addMapping("Halt", new KeyTrigger(KeyInput.KEY_H));
        inputManager.addMapping("Pause", new KeyTrigger(KeyInput.KEY_P));
        inputManager.addMapping("Deselect", new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping("ZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping("ZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        int[] buildKeys = {KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3, KeyInput.KEY_4,
                KeyInput.KEY_5, KeyInput.KEY_6, KeyInput.KEY_7, KeyInput.KEY_8, KeyInput.KEY_9};
        for (int i = 0; i < buildKeys.length; i++) {
            inputManager.addMapping("Build" + (i + 1), new KeyTrigger(buildKeys[i]));
        }

        var shiftHeld = new boolean[1];
        ActionListener actions = (name, pressed, tpf) -> {
            switch (name) {
                case "Shift" -> shiftHeld[0] = pressed;
                case "PanUp" -> pan[0] = pressed;
                case "PanLeft" -> pan[1] = pressed;
                case "PanDown" -> pan[2] = pressed;
                case "PanRight" -> pan[3] = pressed;
                case "Select" -> {
                    if (pressed && menu.isVisible()) {
                        menu.click(inputManager.getCursorPosition());
                    } else if (pressed && screen == Screen.PLAYING) {
                        if (!minimapClick()) {
                            select(shiftHeld[0]);
                        }
                    }
                }
                case "Order" -> {
                    if (pressed && screen == Screen.PLAYING) {
                        order();
                    }
                }
                case "Halt" -> {
                    if (pressed && screen == Screen.PLAYING) {
                        haltSelected();
                    }
                }
                case "Pause" -> {
                    if (pressed && screen == Screen.PLAYING) {
                        game.togglePause();
                    }
                }
                case "Deselect" -> {
                    if (!pressed) {
                        break;
                    }
                    switch (screen) {
                        case PLAYING -> {
                            if (selected.isEmpty()) {
                                showPauseMenu();
                            } else {
                                selected.clear();
                            }
                        }
                        case PAUSED -> resumeGame();
                        case SETTINGS -> leaveSettings();
                        case MENU -> {
                        }
                    }
                }
                default -> {
                    if (pressed && screen == Screen.PLAYING && name.startsWith("Build")) {
                        queueBuild(Integer.parseInt(name.substring(5)) - 1);
                    }
                }
            }
        };
        inputManager.addListener(actions, "Select", "Order", "PanUp", "PanLeft", "PanDown", "PanRight",
                "Shift", "Halt", "Pause", "Deselect",
                "Build1", "Build2", "Build3", "Build4", "Build5", "Build6", "Build7", "Build8", "Build9");

        AnalogListener zoom = (name, value, tpf) -> {
            camDistance *= name.equals("ZoomIn") ? 0.92f : 1.09f;
            camDistance = FastMath.clamp(camDistance, 40f, 400f);
        };
        inputManager.addListener(zoom, "ZoomIn", "ZoomOut");
    }

    /** The unit under the mouse cursor, or {@code null}. */
    private UnitNode pickUnit() {
        var click = inputManager.getCursorPosition();
        var near = cam.getWorldCoordinates(new Vector2f(click.x, click.y), 0f);
        var far = cam.getWorldCoordinates(new Vector2f(click.x, click.y), 1f);
        var results = new CollisionResults();
        unitsNode.collideWith(new Ray(near, far.subtract(near).normalizeLocal()), results);
        for (var result : results) {
            for (Spatial s = result.getGeometry(); s != null; s = s.getParent()) {
                Integer id = s.getUserData("unitId");
                if (id != null) {
                    return unitNodes.get(id);
                }
            }
        }
        return null;
    }

    /** Where the mouse ray hits the ground plane, or {@code null}. */
    private Vector3f pickGround() {
        var click = inputManager.getCursorPosition();
        var near = cam.getWorldCoordinates(new Vector2f(click.x, click.y), 0f);
        var dir = cam.getWorldCoordinates(new Vector2f(click.x, click.y), 1f).subtract(near).normalizeLocal();
        if (Math.abs(dir.y) < 1e-6f) {
            return null;
        }
        float t = -near.y / dir.y;
        return t < 0 ? null : near.add(dir.mult(t));
    }

    private void select(boolean add) {
        if (!add) {
            selected.clear();
        }
        var hit = pickUnit();
        if (hit != null && hit.view.selectable() && hit.view.playerIndex() == game.getLocalPlayerIndex()) {
            selected.add(hit.view.id());
        }
    }

    private void order() {
        var units = selectedIds();
        if (units.isEmpty()) {
            return;
        }
        int local = game.getLocalPlayerIndex();
        var enemy = pickUnit();
        if (enemy != null && enemy.view.playerIndex() != local && enemy.view.playerIndex() != 0) {
            game.postCommand(new GameMessage.AttackObject(local, units, new ObjectId(enemy.view.id())));
            return;
        }
        var ground = pickGround();
        if (ground == null) {
            return;
        }
        // a selected factory takes the click as its rally point, not a move order
        var producer = selectedProducer();
        if (producer != null) {
            game.postCommand(new GameMessage.SetRallyPoint(local,
                    new ObjectId(producer.id()), new Coord3D(ground.x, ground.z, 0f)));
            return;
        }
        // formation: spread the group in a grid around the click, one order per
        // unit, so they don't all fight for the same spot
        int columns = (int) Math.ceil(Math.sqrt(units.size()));
        float spacing = 5f;
        for (int i = 0; i < units.size(); i++) {
            float offsetX = (i % columns - (columns - 1) / 2f) * spacing;
            float offsetY = (i / columns - (units.size() / columns) / 2f) * spacing;
            game.postCommand(new GameMessage.MoveTo(local, List.of(units.get(i)),
                    new Coord3D(ground.x + offsetX, ground.z + offsetY, 0f)));
        }
    }

    /** The single selected own production structure, or {@code null}. */
    private UnitView selectedProducer() {
        if (selected.size() != 1) {
            return null;
        }
        for (var unit : snapshot.units()) {
            if (selected.contains(unit.id())) {
                return unit.producer() && unit.playerIndex() == game.getLocalPlayerIndex() ? unit : null;
            }
        }
        return null;
    }

    /** Queue the {@code index}-th entry of the selected factory's build menu. */
    private void queueBuild(int index) {
        var producer = selectedProducer();
        if (producer == null) {
            return;
        }
        var options = game.getBuildOptions(producer.templateName());
        if (index >= 0 && index < options.size()) {
            game.postCommand(new GameMessage.QueueProduction(game.getLocalPlayerIndex(),
                    new ObjectId(producer.id()), options.get(index).templateName()));
        }
    }

    private void haltSelected() {
        var units = selectedIds();
        if (!units.isEmpty()) {
            game.postCommand(new GameMessage.StopMoving(game.getLocalPlayerIndex(), units));
        }
    }

    private List<ObjectId> selectedIds() {
        return snapshot.units().stream()
                .filter(u -> selected.contains(u.id()))
                .map(u -> new ObjectId(u.id()))
                .toList();
    }

    // ---- per-frame sync ----

    @Override
    public void simpleUpdate(float tpf) {
        snapshot = game.getSnapshot();
        if (menu.isVisible()) {
            menu.updateHover(inputManager.getCursorPosition());
        }
        if (screen == Screen.MENU) {
            hud.setText("");
            buildMenu.setText("");
            return; // the world starts when the player presses Play
        }
        updateCamera(tpf);
        syncUnits();
        handleEvents();
        syncMinimap();
        updateHud();
        updateBanner();
    }

    private void updateCamera(float tpf) {
        float speed = camDistance * 0.9f * tpf;
        if (pan[0]) {
            camTarget.z -= speed;
        }
        if (pan[2]) {
            camTarget.z += speed;
        }
        if (pan[1]) {
            camTarget.x -= speed;
        }
        if (pan[3]) {
            camTarget.x += speed;
        }
        var offset = new Vector3f(0, camDistance * 0.82f, camDistance * 0.57f);
        cam.setLocation(camTarget.add(offset));
        cam.lookAt(camTarget, Vector3f.UNIT_Y);
    }

    private void syncUnits() {
        var seen = new HashSet<Integer>();
        for (var view : snapshot.units()) {
            seen.add(view.id());
            var node = unitNodes.computeIfAbsent(view.id(), id -> createUnitNode(view));
            updateUnitNode(node, view);
        }
        var gone = unitNodes.entrySet().iterator();
        while (gone.hasNext()) {
            var entry = gone.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            entry.getValue().root.removeFromParent();
            selected.remove(entry.getKey());
            gone.remove();
        }
    }

    /**
     * React to what happened this frame, as opposed to what merely is.
     *
     * <p>A unit leaving the snapshot used to be all the client had to go on, so it
     * guessed: gone while badly hurt meant dead, gone while healthy meant fog.
     * That was wrong at both ends. The simulation now says outright what died and
     * what fired, already filtered through fog of war.
     */
    private void handleEvents() {
        if (snapshot == lastEventedSnapshot) {
            return; // the sim has not produced a new frame; do not replay this one
        }
        lastEventedSnapshot = snapshot;
        for (var event : snapshot.events()) {
            if (event instanceof ObjectDied died) {
                playSound(visuals.of(died.templateName()).dieSound,
                        new Vector3f(died.position().x(), 0f, died.position().y()));
            } else if (event instanceof WeaponFired fired) {
                var node = unitNodes.get(fired.shooter().value());
                if (node != null) {
                    node.flashUntil = timer.getTimeInSeconds() + MUZZLE_FLASH_SECONDS;
                    playSound(visuals.of(node.view.templateName()).fireSound,
                            node.root.getLocalTranslation());
                }
            }
        }
    }

    private UnitNode createUnitNode(UnitView view) {
        var visual = visuals.of(view.templateName());
        var node = new UnitNode();
        node.root = new Node("unit-" + view.id());
        node.root.setUserData("unitId", view.id());

        Spatial body = null;
        if (visual.modelPath != null) {
            try {
                body = assetManager.loadModel(visual.modelPath);
                body.setLocalScale(visual.scale);
                body.setLocalTranslation(0, visual.yOffset, 0);
                body.setLocalRotation(new Quaternion().fromAngles(0,
                        FastMath.DEG_TO_RAD * visual.facingDegrees, 0));
                node.composer = findControl(body, AnimComposer.class);
                var legacy = findControl(body, AnimControl.class);
                if (node.composer == null && legacy != null) {
                    node.legacyChannel = legacy.createChannel();
                }
            } catch (RuntimeException e) {
                warnOnce(visual.modelPath, "model");
                body = null;
            }
        }
        if (body == null) {
            body = buildPrimitive(view);
        }
        node.root.attachChild(body);

        node.ring = buildSelectionRing(view);
        node.root.attachChild(node.ring);
        buildHealthBar(node, view);
        node.flash = buildMuzzleFlash(view);
        node.root.attachChild(node.flash);

        unitsNode.attachChild(node.root);
        return node;
    }

    /** A clean placeholder in the player's colour when no model is assigned. */
    private Spatial buildPrimitive(UnitView view) {
        var color = toColor(game.getColor(view.playerIndex()));
        var group = new Node("primitive");
        if (view.structure()) {
            var box = new Geometry("b", new Box(2.6f, 1.8f, 2.6f));
            box.setMaterial(lit(color));
            box.setLocalTranslation(0, 1.8f, 0);
            group.attachChild(box);
        } else {
            var hull = new Geometry("h", new Cylinder(2, 16, 1.1f, 2.4f, true));
            hull.setMaterial(lit(color));
            hull.rotate(FastMath.HALF_PI, 0, 0);
            hull.setLocalTranslation(0, 1.2f, 0);
            group.attachChild(hull);
            var barrel = new Geometry("barrel", new Box(1.1f, 0.18f, 0.18f));
            barrel.setMaterial(lit(color.mult(1.4f)));
            barrel.setLocalTranslation(1.2f, 1.4f, 0);
            group.attachChild(barrel);
        }
        return group;
    }

    private Geometry buildSelectionRing(UnitView view) {
        float radius = view.structure() ? 4.2f : 2.2f;
        var ring = new Geometry("ring", new Cylinder(2, 24, radius, 0.06f, true));
        ring.setMaterial(unshaded(new ColorRGBA(0.4f, 1f, 0.4f, 1f)));
        ring.rotate(FastMath.HALF_PI, 0, 0);
        ring.setLocalTranslation(0, 0.06f, 0);
        ring.setCullHint(Spatial.CullHint.Always);
        return ring;
    }

    private void buildHealthBar(UnitNode node, UnitView view) {
        node.healthBar = new Node("hp");
        var back = new Geometry("hp-back", new Quad(3.6f, 0.45f));
        back.setMaterial(unshaded(new ColorRGBA(0.1f, 0.1f, 0.1f, 1f)));
        back.setLocalTranslation(-1.8f, 0, -0.01f);
        node.healthFill = new Geometry("hp-fill", new Quad(3.5f, 0.35f));
        node.healthFill.setMaterial(unshaded(ColorRGBA.Green));
        node.healthFill.setLocalTranslation(-1.75f, 0.05f, 0f);
        node.healthBar.attachChild(back);
        node.healthBar.attachChild(node.healthFill);
        node.healthBar.addControl(new BillboardControl());
        node.healthBar.setLocalTranslation(0, view.structure() ? 6.5f : 4.5f, 0);
        node.healthBar.setCullHint(Spatial.CullHint.Always);
        node.root.attachChild(node.healthBar);
    }

    private Geometry buildMuzzleFlash(UnitView view) {
        var flash = new Geometry("flash", new Sphere(8, 8, 0.5f));
        flash.setMaterial(unshaded(new ColorRGBA(1f, 0.85f, 0.3f, 1f)));
        flash.setLocalTranslation(view.structure() ? 3.2f : 2.6f, view.structure() ? 2.5f : 1.5f, 0);
        flash.setCullHint(Spatial.CullHint.Always);
        return flash;
    }

    private void updateUnitNode(UnitNode node, UnitView view) {
        node.view = view;
        node.root.setLocalTranslation(view.x(), 0, view.y());
        node.root.setLocalRotation(new Quaternion().fromAngles(0, -view.orientation(), 0));

        node.ring.setCullHint(selected.contains(view.id())
                ? Spatial.CullHint.Never : Spatial.CullHint.Always);

        boolean damaged = view.isDamaged();
        node.healthBar.setCullHint(damaged ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (damaged) {
            float fraction = view.healthFraction();
            node.healthFill.setLocalScale(Math.max(0.02f, fraction), 1, 1);
            node.healthFill.getMaterial().setColor("Color",
                    fraction > 0.5f ? ColorRGBA.Green : fraction > 0.25f ? ColorRGBA.Orange : ColorRGBA.Red);
        }

        // The flash is lit by an actual shot, not by a timer running while "attacking".
        node.flash.setCullHint(timer.getTimeInSeconds() < node.flashUntil
                ? Spatial.CullHint.Never : Spatial.CullHint.Always);

        animate(node, view);
    }

    private void animate(UnitNode node, UnitView view) {
        var visual = visuals.of(view.templateName());
        String wanted = view.attacking() && visual.attackAnim != null ? visual.attackAnim
                : view.moving() && visual.walkAnim != null ? visual.walkAnim
                : visual.idleAnim;
        if (wanted == null || wanted.equals(node.currentAnim)) {
            return;
        }
        try {
            if (node.composer != null) {
                node.composer.setCurrentAction(wanted);
                node.currentAnim = wanted;
            } else if (node.legacyChannel != null) {
                node.legacyChannel.setAnim(wanted, 0.2f);
                node.currentAnim = wanted;
            }
        } catch (IllegalArgumentException e) {
            warnOnce(view.templateName() + "/" + wanted, "animation");
            node.currentAnim = wanted; // don't retry every frame
        }
    }

    private void updateHud() {
        String power = snapshot.localPlayerPowerSurplus() >= 0
                ? "+" + snapshot.localPlayerPowerSurplus()
                : String.valueOf(snapshot.localPlayerPowerSurplus());
        hud.setText("$ %d    power %s    t=%.1fs    selected %d%s".formatted(
                snapshot.localPlayerMoney(), power, snapshot.gameTimeSeconds(),
                selected.size(), snapshot.paused() ? "    [PAUSED]" : ""));

        var producer = selectedProducer();
        if (producer == null) {
            buildMenu.setText("");
            return;
        }
        var text = new StringBuilder("BUILD (press number; right-click sets rally):\n");
        var options = game.getBuildOptions(producer.templateName());
        for (int i = 0; i < options.size() && i < 9; i++) {
            var option = options.get(i);
            text.append("  [").append(i + 1).append("] ").append(option.displayName())
                    .append("  $").append(option.cost()).append('\n');
        }
        if (options.isEmpty()) {
            text.append("  (nothing — set Builds in this structure's Produce capability)\n");
        }
        text.append("  queue: ").append(producer.productionQueue());
        buildMenu.setText(text.toString());
    }

    private void updateBanner() {
        if (!snapshot.hasBanner()) {
            banner.setText("");
            return;
        }
        banner.setText(snapshot.banner());
        banner.setLocalTranslation(
                (cam.getWidth() - banner.getLineWidth()) / 2f,
                cam.getHeight() / 2f + banner.getLineHeight() / 2f, 0);
    }

    // ---- assets & helpers ----

    private void playSound(String assetPath, Vector3f position) {
        if (assetPath == null || missingAssets.contains(assetPath)) {
            return;
        }
        var audio = audioCache.computeIfAbsent(assetPath, path -> {
            try {
                var node = new AudioNode(assetManager, path, AudioData.DataType.Buffer);
                node.setPositional(true);
                node.setRefDistance(40f);
                rootNode.attachChild(node);
                return node;
            } catch (RuntimeException e) {
                warnOnce(path, "sound");
                return null;
            }
        });
        if (audio == null) {
            missingAssets.add(assetPath);
            return;
        }
        audio.setLocalTranslation(position);
        audio.playInstance();
    }

    private void warnOnce(String asset, String kind) {
        if (missingAssets.add(asset)) {
            LOG.warning(() -> "could not load " + kind + " '" + asset + "' — using fallback");
        }
    }

    private static <T extends com.jme3.scene.control.Control> T findControl(Spatial spatial, Class<T> type) {
        var found = new Object[1];
        spatial.depthFirstTraversal(s -> {
            var control = s.getControl(type);
            if (control != null && found[0] == null) {
                found[0] = control;
            }
        });
        return type.cast(found[0]);
    }

    private Material lit(ColorRGBA color) {
        var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", color);
        material.setColor("Ambient", color.mult(0.7f));
        return material;
    }

    private Material unshaded(ColorRGBA color) {
        var material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        return material;
    }

    private static ColorRGBA toColor(java.awt.Color color) {
        return new ColorRGBA(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1f);
    }
}
