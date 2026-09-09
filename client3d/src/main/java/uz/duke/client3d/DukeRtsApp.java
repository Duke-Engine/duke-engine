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
import com.jme3.renderer.queue.RenderQueue;
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

    /** Prefix of the input mapping for a key the game claimed. */
    private static final String HOTKEY = "Hotkey";

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
    /** Keys this game claimed for itself, over and above the standard controls. */
    private final Hotkeys hotkeys;
    private final CountDownLatch stopped = new CountDownLatch(1);

    private Screen screen = Screen.MENU;
    private Screen settingsReturn = Screen.MENU;
    private MenuOverlay menu;
    private Thread simThread; // started when the player presses Play

    private final Node unitsNode = new Node("units");
    /**
     * Terrain lives in its own node so a new world can replace it wholesale. It
     * used to hang straight off the root, which meant it could be built but never
     * rebuilt — and a game that lays out a new world (a roguelike starting a fresh
     * run) kept the old one on screen while everything else moved on.
     */
    private final Node terrainNode = new Node("terrain");
    /** Built at init rather than construction: a modular kit needs the asset manager. */
    private TerrainScene terrain;
    /** The grid the terrain was built from — a different instance means a new world. */
    private uz.duke.core.pathfind.PathGrid builtFrom;

    /**
     * What the player has seen, when the game asked to be discovered rather than
     * shown. {@code null} for every other game, and then nothing below runs.
     */
    private Discovery discovery;
    /** The discovering template's {@code VisionRange}, resolved once the game is up. */
    private float discoveryRadius = -1f;
    /** Minimap cells, one per grid cell, recoloured by what the player knows. */
    private Geometry[] minimapCells = new Geometry[0];
    /** Minimap colours per state, made once: black, remembered, and in sight. */
    private Material[] minimapPalette;
    private final Map<Integer, UnitNode> unitNodes = new HashMap<>();
    private final Set<Integer> selected = new HashSet<>();
    private final Map<String, AudioNode> audioCache = new HashMap<>();
    private final Set<String> missingAssets = new HashSet<>();

    private WorldSnapshot snapshot = WorldSnapshot.EMPTY;
    private WorldSnapshot lastEventedSnapshot = WorldSnapshot.EMPTY;
    private final CameraFocus camera = new CameraFocus();
    private final boolean[] pan = new boolean[4]; // W A S D
    private BitmapText hud;
    private BitmapText buildMenu;
    private BitmapText banner;

    // minimap: fixed-size overlay in the bottom-right corner
    private static final float MINIMAP_SIZE = 190f;
    private final Node minimapNode = new Node("minimap");
    /** Backdrop and rock cells — replaced as a unit when the world changes. */
    private final Node minimapTerrainNode = new Node("minimap-terrain");
    private final Map<Integer, Geometry> minimapDots = new HashMap<>();
    private MinimapProjection minimap = new MinimapProjection(700f, 450f, MINIMAP_SIZE);
    private float minimapX;       // screen position of the minimap's origin
    private float minimapY;
    /** The camera's footprint, drawn as an outline over the minimap. */
    private Geometry viewportOutline;

    /** The box the player is dragging, drawn over the world while the button is down. */
    private Geometry dragRectangle;

    /** Brief flashes acknowledging orders, and the node they are drawn in. */
    private final OrderMarkers orderMarkers = new OrderMarkers();
    private final Node markerNode = new Node("order-markers");

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

    DukeRtsApp(DukeGame game, Visuals visuals, Shell shell, Hotkeys hotkeys) {
        this.game = game;
        this.visuals = visuals;
        this.shell = shell;
        this.hotkeys = hotkeys == null ? Hotkeys.none() : hotkeys;
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

        // Needs the locators above, so it cannot be built with the app itself.
        terrain = new TerrainScene(terrainNode, this::lit,
                visuals.getDiscoveryTemplate() != null, visuals.getTiles(), new KitTiles());

        var sun = new DirectionalLight(new Vector3f(-0.4f, -1f, -0.5f).normalizeLocal(),
                new ColorRGBA(1f, 0.97f, 0.9f, 1f));
        rootNode.addLight(sun);
        rootNode.addLight(new AmbientLight(new ColorRGBA(0.45f, 0.45f, 0.5f, 1f)));

        rootNode.attachChild(terrainNode);
        buildTerrain();
        rootNode.attachChild(unitsNode);
        rootNode.attachChild(markerNode);

        centerCameraOnMap();
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
        buildDragRectangle();
        menu = new MenuOverlay(guiFont, assetManager, guiNode, cam.getWidth(), cam.getHeight());
        showMainMenu();
        applyVolume();
    }

    /** Assemble the minimap once: its terrain layer, then the viewport outline over it. */
    private void buildMinimap() {
        minimapY = 34f; // above the hint line
        minimapNode.attachChild(minimapTerrainNode);
        rebuildMinimapTerrain();
        buildViewportOutline();
        guiNode.attachChild(minimapNode);
    }

    /**
     * The minimap's static layer — map bounds and blocked terrain — for the world
     * as it stands now. Like the 3D terrain it empties its node first, so a new
     * world replaces the old picture instead of being drawn over it.
     */
    private void rebuildMinimapTerrain() {
        minimapTerrainNode.detachAllChildren();
        var grid = game.getTerrain();
        float worldW = grid == null ? 700f : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? 450f : grid.getHeight() * grid.getCellSize();
        minimap = new MinimapProjection(worldW, worldH, MINIMAP_SIZE);
        minimapX = cam.getWidth() - minimap.widthPixels() - 12f;

        var backdrop = new Geometry("mm-bg", new Quad(minimap.widthPixels(), minimap.heightPixels()));
        backdrop.setMaterial(unshaded(new ColorRGBA(0.10f, 0.14f, 0.08f, 1f)));
        minimapTerrainNode.attachChild(backdrop);

        minimapCells = new Geometry[0];
        if (grid != null) {
            var rock = unshaded(new ColorRGBA(0.35f, 0.32f, 0.26f, 1f));
            float cellPx = grid.getCellSize() * minimap.scale();
            boolean discovered = visuals.getDiscoveryTemplate() != null;
            if (discovered) {
                minimapCells = new Geometry[grid.getWidth() * grid.getHeight()];
            }
            for (int cy = 0; cy < grid.getHeight(); cy++) {
                for (int cx = 0; cx < grid.getWidth(); cx++) {
                    // With discovery every cell gets a square, floor included: an
                    // undiscovered floor has to be as black as undiscovered stone,
                    // and the backdrop showing through would draw it as open ground.
                    if (!discovered && !grid.isBlocked(cx, cy)) {
                        continue;
                    }
                    var cell = new Geometry("mm-rock", new Quad(cellPx, cellPx));
                    cell.setMaterial(rock);
                    // minimap y grows up the screen, world y grows down the map
                    cell.setLocalTranslation(cx * cellPx, (grid.getHeight() - 1 - cy) * cellPx, 0);
                    minimapTerrainNode.attachChild(cell);
                    if (discovered) {
                        minimapCells[cy * grid.getWidth() + cx] = cell;
                    }
                }
            }
        }
        minimapNode.setLocalTranslation(minimapX, minimapY, 0);
        minimapPalette = null; // rebuilt lazily against the new grid
    }

    /**
     * Paint the minimap with what the player knows: black where they have not
     * been, dim where they have, bright where they are looking.
     *
     * <p>The unit dots need no help — the snapshot only ever carries what the
     * engine's own fog lets through, so a monster in a room the player walked out
     * of is already gone from it. That is the whole "where did it go?" of the
     * thing, and it comes free.
     */
    private void applyMinimapDiscovery(uz.duke.core.pathfind.PathGrid grid) {
        if (discovery == null || grid == null || minimapCells.length == 0) {
            return;
        }
        if (minimapPalette == null) {
            minimapPalette = new Material[] {
                unshaded(new ColorRGBA(0.02f, 0.02f, 0.03f, 1f)),   // never been there
                unshaded(new ColorRGBA(0.13f, 0.12f, 0.10f, 1f)),   // remembered stone
                unshaded(new ColorRGBA(0.06f, 0.08f, 0.05f, 1f)),   // remembered floor
                unshaded(new ColorRGBA(0.35f, 0.32f, 0.26f, 1f)),   // stone in sight
                unshaded(new ColorRGBA(0.16f, 0.22f, 0.13f, 1f)),   // floor in sight
            };
        }
        for (int index = 0; index < minimapCells.length; index++) {
            var cell = minimapCells[index];
            if (cell == null) {
                continue;
            }
            int cx = index % grid.getWidth();
            int cy = index / grid.getWidth();
            boolean stone = grid.isBlocked(cx, cy);
            cell.setMaterial(switch (discovery.stateAt(cx, cy)) {
                case UNSEEN -> minimapPalette[0];
                case REMEMBERED -> stone ? minimapPalette[1] : minimapPalette[2];
                case VISIBLE -> stone ? minimapPalette[3] : minimapPalette[4];
            });
        }
    }

    /** If the cursor is over the minimap, move the camera there. Returns true if handled. */
    private boolean minimapClick() {
        var cursor = inputManager.getCursorPosition();
        float localX = cursor.x - minimapX;
        float localY = cursor.y - minimapY;
        if (!minimap.contains(localX, localY)) {
            return false;
        }
        camera.lookAt(minimap.toWorldX(localX), minimap.toWorldY(localY));
        return true;
    }

    /**
     * The outline showing what the camera can see, drawn as an empty loop.
     *
     * <p>An outline rather than a filled box on purpose: the minimap's whole job is
     * showing where the rooms and the fighting are, and a translucent rectangle
     * over a third of it would dim exactly the part the player is looking at.
     */
    private void buildViewportOutline() {
        var mesh = new com.jme3.scene.Mesh();
        mesh.setMode(com.jme3.scene.Mesh.Mode.LineLoop);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, new float[VIEWPORT_CORNERS * 3]);
        mesh.setDynamic();
        mesh.updateBound();

        viewportOutline = new Geometry("mm-viewport", mesh);
        viewportOutline.setMaterial(unshaded(new ColorRGBA(1f, 1f, 1f, 0.85f)));
        viewportOutline.setLocalTranslation(0, 0, 2); // above the dots
        minimapNode.attachChild(viewportOutline);
    }

    private static final int VIEWPORT_CORNERS = 4;

    /** The selection box: an outline, so it never hides what is being selected. */
    private void buildDragRectangle() {
        var mesh = new com.jme3.scene.Mesh();
        mesh.setMode(com.jme3.scene.Mesh.Mode.LineLoop);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, new float[4 * 3]);
        mesh.setDynamic();
        mesh.updateBound();

        dragRectangle = new Geometry("drag-box", mesh);
        dragRectangle.setMaterial(unshaded(new ColorRGBA(0.5f, 1f, 0.5f, 0.9f)));
        dragRectangle.setCullHint(Spatial.CullHint.Always);
        guiNode.attachChild(dragRectangle);
    }

    /**
     * Draw the order markers, fading each one out over its short life.
     *
     * <p>Rebuilt from scratch each frame rather than kept and mutated: there are
     * only ever a handful, and emptying the node first is what stops a session's
     * worth of markers accumulating in the scene.
     */
    private void syncOrderMarkers() {
        float now = timer.getTimeInSeconds();
        orderMarkers.prune(now);
        markerNode.detachAllChildren();
        for (var marker : orderMarkers.markers()) {
            float fade = OrderMarkers.remaining(marker, now);
            var colour = marker.kind() == OrderMarkers.Kind.ATTACK
                    ? new ColorRGBA(1f, 0.35f, 0.3f, fade)
                    : new ColorRGBA(0.6f, 1f, 0.6f, fade);
            // Grows a little as it fades, so the eye catches it even mid-fight.
            float radius = 3f + (1f - fade) * 2.5f;
            var ring = new Geometry("order-mark", new Cylinder(2, 24, radius, 0.05f, true));
            ring.setMaterial(unshaded(colour));
            ring.rotate(FastMath.HALF_PI, 0, 0);
            ring.setLocalTranslation(marker.x(), 0.2f, marker.y());
            markerNode.attachChild(ring);
        }
    }

    /**
     * Redraw the viewport outline from where the camera is now — it moves and
     * resizes as the player pans and zooms.
     *
     * <p>The camera looks at the ground from an angle, so what it sees is a
     * trapezium rather than a rectangle: the far edge of the screen covers more
     * ground than the near one. Rather than approximate that with a box, each
     * screen corner is cast onto the ground and the true quadrilateral is drawn.
     */
    private void syncViewportOutline() {
        float w = cam.getWidth();
        float h = cam.getHeight();
        // Clockwise from the bottom-left of the screen, so the loop does not cross.
        float[][] corners = {{0, 0}, {w, 0}, {w, h}, {0, h}};
        var worldXs = new float[VIEWPORT_CORNERS];
        var worldYs = new float[VIEWPORT_CORNERS];
        for (int i = 0; i < VIEWPORT_CORNERS; i++) {
            var ground = groundUnder(corners[i][0], corners[i][1]);
            // World y is the ground plane's z: the map lies in x/z, y is height.
            worldXs[i] = ground.x;
            worldYs[i] = ground.z;
        }

        var outline = minimap.viewportOutline(worldXs, worldYs);
        var vertices = new float[VIEWPORT_CORNERS * 3];
        for (int i = 0; i < outline.length; i++) {
            vertices[i * 3] = outline[i].x();
            vertices[i * 3 + 1] = outline[i].y();
        }
        var mesh = viewportOutline.getMesh();
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, vertices);
        mesh.updateBound();
    }

    /**
     * Where the ray through a screen point meets the ground.
     *
     * <p>A corner above the horizon never meets it, so the ray is followed a long
     * way instead and the projection clamps the result to the map — which is what
     * the player sees anyway: the view running off the edge of the world.
     */
    private Vector3f groundUnder(float screenX, float screenY) {
        var near = cam.getWorldCoordinates(new Vector2f(screenX, screenY), 0f);
        var dir = cam.getWorldCoordinates(new Vector2f(screenX, screenY), 1f)
                .subtract(near).normalizeLocal();
        float t = Math.abs(dir.y) < 1e-6f ? -1f : -near.y / dir.y;
        return near.add(dir.mult(t < 0 ? 10_000f : t));
    }

    /** Live unit dots, coloured by player, sized up for structures. */
    private void syncMinimap() {
        var seen = new HashSet<Integer>();
        for (var view : snapshot.units()) {
            seen.add(view.id());
            var dot = minimapDots.computeIfAbsent(view.id(), id -> {
                float size = view.structure() ? 6f : 3.5f;
                var geometry = new Geometry("mm-dot", new Quad(size, size));
                geometry.setMaterial(unshaded(colourOf(view)));
                minimapNode.attachChild(geometry);
                return geometry;
            });
            var point = minimap.toMinimap(view.x(), view.y());
            dot.setLocalTranslation(point.x() - 2f, point.y() - 2f, 1);
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
        // Open on the player's own units rather than the middle of the map, which
        // left him hunting the map for whatever he is supposed to be controlling.
        camera.requestOwnUnit();
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
        minimapX = width - minimap.widthPixels() - 12f;
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

    /** Build (or rebuild) the ground and rocks for the world as it stands now. */
    private void buildTerrain() {
        builtFrom = game.getTerrain();
        terrain.rebuild(builtFrom);
        if (visuals.getDiscoveryTemplate() == null) {
            return;
        }
        // A new floor is a floor nobody has walked: memory belongs to one world,
        // and carrying it over would open rooms in a dungeon nobody has entered.
        if (discovery == null) {
            discovery = new Discovery(builtFrom);
        } else {
            discovery.reset(builtFrom);
        }
    }

    /**
     * Open the map around the player's own units and redraw what that changes.
     *
     * <p>Entirely a matter of what is drawn. It reads the snapshot the client is
     * already given and writes nothing back, so the simulation runs the same
     * whether anyone is looking at it or not — which is the only way fog can be
     * added to a deterministic game without becoming part of it.
     */
    private void syncDiscovery() {
        if (discovery == null) {
            return;
        }
        if (discoveryRadius < 0f) {
            var template = game.getLogic() == null
                    ? null : game.getLogic().findTemplate(visuals.getDiscoveryTemplate());
            if (template == null) {
                return; // the game has not finished booting; the map stays black
            }
            discoveryRadius = template.getVisionRange();
        }
        discovery.reveal(snapshot.units(), game.getLocalPlayerIndex(), discoveryRadius);
        terrain.applyDiscovery(discovery);
        applyMinimapDiscovery(builtFrom);
    }

    /**
     * Notice that the game has swapped in a different world and rebuild
     * everything that was drawn from the old one.
     *
     * <p>The simulation says so simply by having a different terrain grid: laying
     * out a new world replaces the grid instance, so comparing identity is enough
     * and the client never has to be told. Units need no help — they already
     * appear and vanish with the snapshot — but terrain, the minimap backdrop and
     * the camera were all built once and would otherwise keep showing the world
     * that has been left behind.
     */
    private void refreshWorldIfChanged() {
        if (game.getTerrain() == builtFrom) {
            return;
        }
        buildTerrain();
        rebuildMinimapTerrain();
        orderMarkers.clear(); // orders given in the old world mean nothing here
        camera.requestOwnUnit(); // his units are somewhere else entirely now
    }

    /**
     * Somewhere to look before there is anything to look at. The moment the game
     * starts, {@link CameraFocus#requestOwnUnit()} replaces this with the player's
     * own units — this is only what fills the screen until then.
     */
    private void centerCameraOnMap() {
        var grid = game.getTerrain();
        camera.lookAt(grid == null ? 350f : grid.getWidth() * grid.getCellSize() / 2f,
                grid == null ? 225f : grid.getHeight() * grid.getCellSize() / 2f);
    }

    // ---- input ----

    private void installInput() {
        inputManager.addMapping("Select", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("Order", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        bindKeys("PanUp", KeyInput.KEY_W, KeyInput.KEY_UP);
        bindKeys("PanLeft", KeyInput.KEY_A, KeyInput.KEY_LEFT);
        bindKeys("PanDown", KeyInput.KEY_S, KeyInput.KEY_DOWN);
        bindKeys("PanRight", KeyInput.KEY_D, KeyInput.KEY_RIGHT);
        inputManager.addMapping("Shift", new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
        bindKeys("Halt", KeyInput.KEY_H);
        bindKeys("Pause", KeyInput.KEY_P);
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
                    } else if (screen == Screen.PLAYING) {
                        if (pressed) {
                            beginDrag();
                        } else {
                            endDrag(shiftHeld[0]);
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
                    if (!pressed || screen != Screen.PLAYING) {
                        return;
                    }
                    if (name.startsWith("Build")) {
                        queueBuild(Integer.parseInt(name.substring(5)) - 1);
                    } else if (name.startsWith(HOTKEY)) {
                        // The game's own key. All it may do here is post a command;
                        // the render thread has no business in the simulation.
                        var action = hotkeys.all().get(name.charAt(HOTKEY.length()));
                        if (action != null) {
                            action.accept(game);
                        }
                    }
                }
            }
        };
        inputManager.addListener(actions, "Select", "Order", "PanUp", "PanLeft", "PanDown", "PanRight",
                "Shift", "Halt", "Pause", "Deselect",
                "Build1", "Build2", "Build3", "Build4", "Build5", "Build6", "Build7", "Build8", "Build9");

        for (var key : hotkeys.all().keySet()) {
            int code = Hotkeys.codeOf(key);
            if (code < 0) {
                continue;
            }
            String mapping = HOTKEY + key;
            inputManager.deleteMapping(mapping);
            inputManager.addMapping(mapping, new KeyTrigger(code));
            inputManager.addListener(actions, mapping);
        }

        AnalogListener zoom = (name, value, tpf) ->
                camera.zoomBy(name.equals("ZoomIn") ? 0.92f : 1.09f);
        inputManager.addListener(zoom, "ZoomIn", "ZoomOut");
    }

    /**
     * Bind a control to whichever of these keys the game has not claimed — see
     * {@link Hotkeys#unclaimed}.
     *
     * <p>Binding nothing is fine: the listener registers the name either way, and
     * a mapping with no trigger simply never fires.
     */
    private void bindKeys(String mapping, int... codes) {
        var free = hotkeys.unclaimed(codes);
        if (free.length == 0) {
            return;
        }
        var triggers = new com.jme3.input.controls.Trigger[free.length];
        for (int i = 0; i < free.length; i++) {
            triggers[i] = new KeyTrigger(free[i]);
        }
        inputManager.addMapping(mapping, triggers);
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

    // ---- drag selection ----

    /** Where the left button went down, or {@code null} when it is not down. */
    private Vector2f dragFrom;

    private void beginDrag() {
        if (minimapClick()) {
            return; // the minimap took the press; not a selection
        }
        var cursor = inputManager.getCursorPosition();
        dragFrom = new Vector2f(cursor.x, cursor.y);
    }

    /**
     * Finish a press: a box if the mouse travelled, the old single-unit pick if it
     * did not.
     *
     * <p>Falling back to the click matters — a click is a drag of zero pixels and a
     * real hand never quite manages zero, so without the distinction every click
     * would become a tiny empty box that selects nothing.
     */
    private void endDrag(boolean add) {
        var from = dragFrom;
        dragFrom = null;
        dragRectangle.setCullHint(Spatial.CullHint.Always);
        if (from == null) {
            return; // the press was taken by the minimap or a menu
        }
        var cursor = inputManager.getCursorPosition();
        if (!SelectionBox.isDrag(from.x, from.y, cursor.x, cursor.y)) {
            select(add);
            return;
        }
        if (!add) {
            selected.clear();
        }
        selected.addAll(SelectionBox.inside(from.x, from.y, cursor.x, cursor.y, onScreenUnits()));
    }

    /** Every unit in the snapshot, projected to where it is drawn on screen. */
    private List<SelectionBox.Candidate> onScreenUnits() {
        int local = game.getLocalPlayerIndex();
        var candidates = new java.util.ArrayList<SelectionBox.Candidate>();
        for (var view : snapshot.units()) {
            var screen = cam.getScreenCoordinates(new Vector3f(view.x(), 0f, view.y()));
            candidates.add(new SelectionBox.Candidate(view.id(), screen.x, screen.y,
                    view.playerIndex() == local, view.selectable()));
        }
        return candidates;
    }

    /** Redraw the box while the button is held, as an outline over the world. */
    private void syncDragRectangle() {
        if (dragFrom == null) {
            return;
        }
        var cursor = inputManager.getCursorPosition();
        if (!SelectionBox.isDrag(dragFrom.x, dragFrom.y, cursor.x, cursor.y)) {
            dragRectangle.setCullHint(Spatial.CullHint.Always);
            return;
        }
        float[] corners = {
            dragFrom.x, dragFrom.y, 0f,
            cursor.x, dragFrom.y, 0f,
            cursor.x, cursor.y, 0f,
            dragFrom.x, cursor.y, 0f,
        };
        var mesh = dragRectangle.getMesh();
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, corners);
        mesh.updateBound();
        dragRectangle.setCullHint(Spatial.CullHint.Never);
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
            markOrder(enemy.view.x(), enemy.view.y(), OrderMarkers.Kind.ATTACK);
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
            markOrder(ground.x, ground.z, OrderMarkers.Kind.MOVE);
            return;
        }
        // Spread the group around the click so they don't all fight for one spot.
        var spots = Formation.spread(units.size(), ground.x, ground.z);
        for (int i = 0; i < units.size(); i++) {
            game.postCommand(new GameMessage.MoveTo(local, List.of(units.get(i)),
                    new Coord3D(spots.get(i).x(), spots.get(i).y(), 0f)));
        }
        // One mark for the order, not one per unit: it was a single decision.
        markOrder(ground.x, ground.z, OrderMarkers.Kind.MOVE);
    }

    /** Acknowledge an order where the player clicked. Presentation only. */
    private void markOrder(float worldX, float worldY, OrderMarkers.Kind kind) {
        orderMarkers.add(worldX, worldY, kind, timer.getTimeInSeconds());
    }

    /**
     * The selected unit's health, when exactly one is selected.
     *
     * <p>Read from the snapshot like everything else here. It is worth showing
     * because a hero's maximum health is not fixed in every game — a roguelike
     * hero's rises as he levels, and the number moving is the player seeing that
     * happen.
     */
    private String selectedHealth() {
        if (selected.size() != 1) {
            return "";
        }
        for (var view : snapshot.units()) {
            if (selected.contains(view.id())) {
                return "    hp %.0f/%.0f".formatted(view.health(), view.maxHealth());
            }
        }
        return "";
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
        refreshWorldIfChanged(); // a new run lays out a new world; redraw it
        syncDiscovery();
        camera.focusOnOwnUnit(snapshot.units(), game.getLocalPlayerIndex());
        updateCamera(tpf);
        // Before the units, not after: a death has to take the body out of the
        // live list before anything decides it merely vanished. It also means a
        // shot lights its muzzle on the frame it was fired rather than the next.
        handleEvents();
        syncUnits();
        reapTheDead();
        syncMinimap();
        syncViewportOutline();
        syncDragRectangle();
        syncOrderMarkers();
        updateHud();
        updateBanner();
    }

    private void updateCamera(float tpf) {
        float speed = camera.panSpeed() * tpf;
        float dx = (pan[3] ? speed : 0f) - (pan[1] ? speed : 0f);
        float dz = (pan[2] ? speed : 0f) - (pan[0] ? speed : 0f);
        camera.panBy(dx, dz);

        float distance = camera.distance();
        var target = new Vector3f(camera.targetX(), 0f, camera.targetZ());
        cam.setLocation(target.add(new Vector3f(0, distance * 0.82f, distance * 0.57f)));
        cam.lookAt(target, Vector3f.UNIT_Y);
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
                layOut(died.object().value());
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

    /** A body playing out its death, and when to take it away. */
    private record Dying(Node root, float until) {
    }

    private final java.util.List<Dying> dying = new java.util.ArrayList<>();

    /**
     * Let something that has just died fall over before it goes.
     *
     * <p>Driven by the death <em>event</em> rather than by a unit leaving the
     * snapshot, because those are not the same thing: with fog, a monster walking
     * out of sight leaves the snapshot too, and a corpse dropped every time
     * something rounded a corner would be worse than none at all.
     *
     * <p>The body is taken out of the live units at once — it is no longer part of
     * the game, cannot be selected, and must not be given a walk animation because
     * the simulation says it is moving. What is left is a clip and a timer.
     */
    private void layOut(int unitId) {
        var node = unitNodes.remove(unitId);
        selected.remove(unitId);
        if (node == null) {
            return;
        }
        var clipName = visuals.of(node.view.templateName()).dieAnim;
        var clip = clipName == null || node.composer == null
                ? null : node.composer.getAnimClip(clipName);
        if (clip == null) {
            node.root.removeFromParent(); // nothing to play; it simply goes
            return;
        }
        // The trappings of something alive: a health bar on a corpse, and a
        // selection ring under one, both read as a thing still in the fight.
        node.healthBar.removeFromParent();
        node.ring.removeFromParent();
        node.flash.removeFromParent();

        // Once through, not looping: a corpse that gets up and dies again forever
        // is worse than one that never fell over.
        node.composer.setCurrentAction(clipName, AnimComposer.DEFAULT_LAYER, false);
        dying.add(new Dying(node.root,
                (float) (timer.getTimeInSeconds() + clip.getLength() + CORPSE_LINGER)));
    }

    /** How long a body stays after its death animation has played out. */
    private static final float CORPSE_LINGER = 1.5f;

    private void reapTheDead() {
        float now = (float) timer.getTimeInSeconds();
        dying.removeIf(body -> {
            if (now < body.until()) {
                return false;
            }
            body.root().removeFromParent();
            return true;
        });
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
                dressModel(body, visual);
                node.composer = findControl(body, AnimComposer.class);
                var legacy = findControl(body, AnimControl.class);
                if (node.composer == null && legacy != null) {
                    node.legacyChannel = legacy.createChannel();
                }
                borrowAnimations(body, visual);
            } catch (RuntimeException e) {
                warnOnce(visual.modelPath, "model");
                body = null;
            }
        }
        if (body == null) {
            body = buildPrimitive(view);
            // Size applies to a shape as much as to a model. Without this a game
            // could say how big a thing is only by shipping art for it, and
            // anything small — a dart, a spark, a rat — came out unit-sized.
            body.setLocalScale(visual.scale);
            body.setLocalTranslation(0, visual.yOffset, 0);
        }
        node.root.attachChild(body);

        node.ring = buildSelectionRing(view);
        node.root.attachChild(node.ring);
        buildHealthBar(node, view, body);
        node.flash = buildMuzzleFlash(view);
        node.root.attachChild(node.flash);

        unitsNode.attachChild(node.root);
        return node;
    }

    /**
     * Give a loaded model the colour map and tint the game asked for, on a
     * material this client can actually light.
     *
     * <p>The loader's own material is replaced rather than added to, and the
     * reason is the whole bug this fixes. jME's glTF loader builds a
     * <b>PBR</b> material, and physically based shading takes its ambient light
     * from an environment map — a light probe. This client has a sun and a flat
     * ambient and no probe, so a PBR model comes out black: not untextured, not
     * mis-scaled, simply unlit. It looks exactly like a missing texture, which is
     * what makes it worth a comment.
     *
     * <p>Plain lighting over the same colour map is what the tiles already use and
     * what these flat-shaded kits are drawn for. It carries {@code NumberOfBones}
     * and {@code BoneMatrices}, so skinning keeps working — the one thing the PBR
     * material was being kept for.
     *
     * <p>A material per geometry per unit, never shared: a skinned material holds
     * the pose of the skeleton driving it, so two monsters on one material would
     * both stand in whichever pose was written last.
     */
    private void dressModel(Spatial body, Visuals.UnitVisual visual) {
        if (visual.texturePath == null && visual.tint == null) {
            return;
        }
        com.jme3.texture.Texture skin = null;
        if (visual.texturePath != null) {
            try {
                skin = assetManager.loadTexture(visual.texturePath);
            } catch (RuntimeException e) {
                warnOnce(visual.texturePath, "texture");
            }
        }
        var tint = visual.tint == null ? ColorRGBA.White : toColor(visual.tint);
        var texture = skin;
        body.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                geometry.setMaterial(creatureMaterial(texture, tint));
            }
        });
    }

    /** Flat lighting over a kit's own colour map, tinted. */
    private Material creatureMaterial(com.jme3.texture.Texture skin, ColorRGBA tint) {
        var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", tint);
        material.setColor("Ambient", tint.mult(0.55f));
        material.setColor("Specular", ColorRGBA.Black); // kit art has no highlights
        material.setFloat("Shininess", 1f);
        if (skin != null) {
            material.setTexture("DiffuseMap", skin);
        }
        return material;
    }

    /**
     * Fetch this unit's animations out of a library file built on the same
     * skeleton, and put them on the model that has none.
     *
     * <p>The library is loaded once and kept: it is a large file and every monster
     * on the floor wants clips out of it. What each monster gets is its own,
     * though — a clip's tracks point straight at the joints they drive, so a clip
     * shared between two monsters would animate whichever of them was built first
     * and leave the other standing.
     */
    private void borrowAnimations(Spatial body, Visuals.UnitVisual visual) {
        var wanted = new java.util.ArrayList<String>();
        for (var name : new String[] {visual.idleAnim, visual.walkAnim,
                visual.attackAnim, visual.dieAnim}) {
            if (name != null) {
                wanted.add(name);
            }
        }
        for (var source : visual.animations) {
            var library = animationLibraries.get(source.assetPath());
            if (library == null) {
                try {
                    library = assetManager.loadModel(source.assetPath());
                } catch (RuntimeException e) {
                    warnOnce(source.assetPath(), "animation library");
                    continue;
                }
                animationLibraries.put(source.assetPath(), library);
            }
            boolean arrived = source.clipName() == null
                    ? AnimationLibrary.copy(library, body, wanted) > 0
                    : AnimationLibrary.copySingle(library, body, source.clipName());
            if (!arrived && missingAssets.add(source.assetPath() + "#clips")) {
                var path = source.assetPath();
                LOG.warning(() -> "no animation was taken from " + path
                        + " — either it holds none, or it and the model are on"
                        + " different skeletons");
            }
        }
    }

    /** Animation libraries, loaded once each and shared by everything that borrows. */
    private final Map<String, Spatial> animationLibraries = new HashMap<>();

    /**
     * What colour to draw this unit: the type's own if the game gave it one,
     * otherwise its player's.
     *
     * <p>Player colour says whose it is, which is all an RTS usually needs. A
     * game fielding several kinds of thing per side needs to say what it is too,
     * and with no models yet there is nothing else to say it with.
     */
    private ColorRGBA colourOf(UnitView view) {
        var own = visuals.of(view.templateName()).colour;
        return toColor(own != null ? own : game.getColor(view.playerIndex()));
    }

    /** A clean placeholder in the player's colour when no model is assigned. */
    private Spatial buildPrimitive(UnitView view) {
        var color = colourOf(view);
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

    /**
     * The health bar, floating clear of whatever it belongs to and drawn over it.
     *
     * <p>Both of those had to be said out loud once there were models. The height
     * used to be a constant that suited a capsule, and a creature kit's hero
     * stands three times taller than one — the bar ended up inside his chest.
     * It is measured off the body instead, so it clears a rat and a boss alike.
     *
     * <p>And it ignores the depth buffer. A bar at the right height is still lost
     * the moment the thing turns and an arm crosses in front of it, or another
     * monster walks between; a health bar is a readout rather than a thing in the
     * world, and it is worth nothing if it can be hidden by the creature it
     * describes.
     */
    private void buildHealthBar(UnitNode node, UnitView view, Spatial body) {
        node.healthBar = new Node("hp");
        var back = new Geometry("hp-back", new Quad(3.6f, 0.45f));
        back.setMaterial(overlay(new ColorRGBA(0.1f, 0.1f, 0.1f, 1f)));
        back.setLocalTranslation(-1.8f, 0, -0.01f);
        node.healthFill = new Geometry("hp-fill", new Quad(3.5f, 0.35f));
        node.healthFill.setMaterial(overlay(ColorRGBA.Green));
        node.healthFill.setLocalTranslation(-1.75f, 0.05f, 0f);
        node.healthBar.attachChild(back);
        node.healthBar.attachChild(node.healthFill);
        node.healthBar.addControl(new BillboardControl());
        node.healthBar.setLocalTranslation(0, heightOf(body, view) + 1.2f, 0);
        // Two buckets, one apiece, purely for the order they are drawn in.
        // Ignoring the depth buffer is what puts the bar over the world, but it
        // also stops the hair of clearance between the backdrop and the fill from
        // meaning anything — and then the sort inside a bucket is by distance, so
        // the dark backdrop won and the bar read as a black stripe. Buckets are
        // drawn in a fixed order, which is the one thing here that cannot tie.
        back.setQueueBucket(RenderQueue.Bucket.Transparent);
        node.healthFill.setQueueBucket(RenderQueue.Bucket.Translucent);
        node.healthBar.setCullHint(Spatial.CullHint.Always);
        node.root.attachChild(node.healthBar);
    }

    /** A colour that is drawn over the scene rather than into it. */
    private Material overlay(ColorRGBA colour) {
        var material = unshaded(colour);
        material.getAdditionalRenderState().setDepthTest(false);
        return material;
    }

    /**
     * How tall this unit stands, measured rather than assumed.
     *
     * <p>A model's height depends on the kit it came from and the scale the game
     * gave it, neither of which the client can guess. The fallback is the old
     * constant, for the primitives that have no bounds worth measuring.
     */
    private static float heightOf(Spatial body, UnitView view) {
        float fallback = view.structure() ? 6.5f : 4.5f;
        if (body == null) {
            return fallback;
        }
        body.updateModelBound();
        body.updateGeometricState();
        return body.getWorldBound() instanceof com.jme3.bounding.BoundingBox box
                ? Math.max(fallback, box.getYExtent() * 2f) : fallback;
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

    /**
     * Choose the clip that matches what the unit is doing.
     *
     * <p>Moving wins over attacking, and the order matters more than it looks.
     * A snapshot's {@code attacking} means the unit <em>has a target</em>, not
     * that it is swinging: a monster that has noticed the hero across a room is
     * attacking by that definition for the whole chase. Letting that win made
     * every monster in the dungeon slide toward the player throwing punches at
     * the air.
     *
     * <p>Attacking therefore reads as "engaging something and not going anywhere",
     * which is when a creature does actually swing.
     */
    private void animate(UnitNode node, UnitView view) {
        var visual = visuals.of(view.templateName());
        String wanted = view.moving() && visual.walkAnim != null ? visual.walkAnim
                : view.attacking() && visual.attackAnim != null ? visual.attackAnim
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
        hud.setText("$ %d    power %s    t=%.1fs    selected %d%s%s%s".formatted(
                snapshot.localPlayerMoney(), power, snapshot.gameTimeSeconds(),
                selected.size(), selectedHealth(),
                snapshot.hasStatus() ? "    " + snapshot.status() : "",
                snapshot.paused() ? "    [PAUSED]" : ""));

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

    /**
     * Loads a modular kit's pieces and shades them.
     *
     * <p>Two things happen here that are easy to miss until the dungeon comes out
     * black. First, jME's glTF loader gives every piece a <b>PBR</b> material, and
     * PBR takes its ambient light from an environment map — of which this client
     * has none, only a sun and a flat ambient, so a PBR scene renders nearly
     * unlit. Kenney's kits are flat-shaded palette art anyway, so the material is
     * rebuilt as plain lighting over the same texture, which is both correct and
     * what the art was drawn for.
     *
     * <p>Second, a floor of several hundred tiles is several hundred copies of
     * three meshes. They are cloned without cloning materials, and there are
     * exactly two materials for the whole kit — lit and remembered — so making a
     * cell dimmer is swapping which of the two it points at, not building one.
     */
    private final class KitTiles implements TileSource {

        private final Map<String, Spatial> masters = new HashMap<>();
        private Material litTile;
        private Material rememberedTile;

        @Override
        public Spatial piece(String assetPath) {
            var master = masters.get(assetPath);
            if (master == null) {
                try {
                    master = assetManager.loadModel(assetPath);
                } catch (RuntimeException e) {
                    // A missing piece is a missing file, not a broken client: draw
                    // the rest of the floor and say which one went missing.
                    if (missingAssets.add(assetPath)) {
                        LOG.warning(() -> "tile not found: " + assetPath + " (" + e.getMessage() + ")");
                    }
                    return null;
                }
                buildMaterials(master);
                masters.put(assetPath, master);
            }
            var copy = master.clone(false); // share the mesh and the material
            shade(copy, true);
            return copy;
        }

        /** One pair of materials for the whole kit, from the first piece's texture. */
        private void buildMaterials(Spatial master) {
            if (litTile != null) {
                return;
            }
            var atlas = textureOf(master);
            litTile = tileMaterial(atlas, ColorRGBA.White, new ColorRGBA(0.55f, 0.55f, 0.62f, 1f));
            rememberedTile = tileMaterial(atlas,
                    new ColorRGBA(0.22f, 0.22f, 0.26f, 1f), new ColorRGBA(0.10f, 0.10f, 0.13f, 1f));
        }

        private Material tileMaterial(com.jme3.texture.Texture atlas,
                ColorRGBA diffuse, ColorRGBA ambient) {
            var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            material.setBoolean("UseMaterialColors", true);
            material.setColor("Diffuse", diffuse);
            material.setColor("Ambient", ambient);
            if (atlas != null) {
                material.setTexture("DiffuseMap", atlas);
            }
            return material;
        }

        /** The kit's colour atlas, taken off whatever material the loader made. */
        private com.jme3.texture.Texture textureOf(Spatial model) {
            if (model instanceof Geometry geometry && geometry.getMaterial() != null) {
                for (var param : geometry.getMaterial().getParams()) {
                    if (param.getValue() instanceof com.jme3.texture.Texture texture) {
                        return texture;
                    }
                }
            }
            if (model instanceof Node node) {
                for (var child : node.getChildren()) {
                    var found = textureOf(child);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        @Override
        public void shade(Spatial piece, boolean isLit) {
            var material = isLit ? litTile : rememberedTile;
            if (material != null) {
                piece.setMaterial(material);
            }
        }
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
