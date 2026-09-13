package uz.duke.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.tween.action.BlendableAction;
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

import java.util.ArrayList;
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

    /**
     * Prefix of the input mapping for a key the game claimed.
     */
    private static final String HOTKEY = "Hotkey";

    /**
     * Whether a modifier is down, so the letter under it can mean something else.
     *
     * <p>An array because the listener is a lambda and a lambda may not write to a
     * local -- the same trick {@code shiftHeld} beside it uses, for the same
     * reason.
     */
    private final boolean[] ctrlHeld = {false};

    private enum Screen {MENU, LOADING, PLAYING, PAUSED, SETTINGS}

    /**
     * Persisted display/audio settings, shared by every duke-engine game.
     */
    static final java.util.prefs.Preferences PREFS =
            java.util.prefs.Preferences.userRoot().node("duke-engine/game");
    private static final int[][] RESOLUTIONS = {{1280, 720}, {1600, 900}, {1920, 1080}};
    private static final int[] VOLUMES = {100, 75, 50, 25, 0};

    private final DukeGame game;
    private final Visuals visuals;
    private final Shell shell;
    /**
     * Keys this game claimed for itself, over and above the standard controls.
     */
    private final Hotkeys hotkeys;
    private final CountDownLatch stopped = new CountDownLatch(1);

    private Screen screen = Screen.MENU;
    private Screen settingsReturn = Screen.MENU;
    private StoneMenu menu;
    /**
     * The lettering the game asked its menus to be set in.
     */
    private StoneCraft craft;
    private Thread simThread; // started when the player presses Play

    private final Node unitsNode = new Node("units");
    /**
     * Terrain lives in its own node so a new world can replace it wholesale. It
     * used to hang straight off the root, which meant it could be built but never
     * rebuilt — and a game that lays out a new world (a roguelike starting a fresh
     * run) kept the old one on screen while everything else moved on.
     */
    private final Node terrainNode = new Node("terrain");
    /**
     * Built at init rather than construction: a modular kit needs the asset manager.
     */
    private TerrainScene terrain;
    /**
     * The dark, as a picture of the map that the terrain's material reads by world
     * position — see {@link FogMap}. {@code null} for a game that never asked to be
     * discovered, and then the terrain is drawn plainly.
     */
    private FogMap fogMap;
    /**
     * Every material that samples the fog. Kept because the one thing in them that
     * changes with the world is its size, and a new floor is a different size.
     */
    private final List<Material> fogged = new ArrayList<>();
    /**
     * What the things in flight look like. Built once; see {@link ProjectileEffects}.
     */
    private ProjectileEffects effects;

    /**
     * What a skill looks like going off: rings across the floor, and the knock.
     *
     * <p>Beside the projectiles' effects rather than inside them, because they
     * answer different questions. That one is asked "this thing is flying / this
     * thing landed"; this one is asked "the player pressed W", which arrives from
     * somewhere else entirely -- see {@link #skillsCastThisFrame}.
     */
    private SkillEffects skillEffects;
    /**
     * The effects drawn from layers -- see {@link LayeredEffects}. Built beside the
     * other two and borrowing their lights rather than bringing its own, so the
     * light budget is one budget.
     */
    private LayeredEffects layered;
    /**
     * Whoever of theirs was just hit, going white -- see {@link HitFlash}.
     */
    private HitFlash hitFlash;
    /**
     * Shots that ended this frame, waiting for its blows to say whether they struck.
     */
    private final List<Landing.Gone> endedShots = new ArrayList<>();
    /** A level, the boss down, a new floor -- see RunMoments. */
    private final RunMoments runMoments = new RunMoments();
    /** The floor's half of this frame's status line, read once for the bars and the moments. */
    private UnitBarReading barReading = UnitBarReading.NOTHING;

    /**
     * The frame of the last cast this client drew, so one cast is drawn once.
     */
    private int lastCastFrameDrawn = Integer.MIN_VALUE;
    /**
     * The grid the terrain was built from — a different instance means a new world.
     */
    private uz.duke.core.pathfind.PathGrid builtFrom;

    /**
     * What the player has seen, when the game asked to be discovered rather than
     * shown. {@code null} for every other game, and then nothing below runs.
     */
    private Discovery discovery;
    /**
     * The discovering template's {@code VisionRange}, resolved once the game is up.
     */
    private float discoveryRadius = -1f;
    /**
     * Whose sight the radius above was read from — see syncDiscovery.
     */
    private String discoveryEyes;
    /**
     * Minimap cells, one per grid cell, recoloured by what the player knows.
     */
    private Geometry[] minimapCells = new Geometry[0];
    /**
     * Minimap colours per state, made once: black, remembered, and in sight.
     */
    private Material[] minimapPalette;
    /**
     * Floor tones by state and storey: [remembered|in sight][storey].
     */
    private Material[][] minimapFloors;
    /**
     * The highest storey anywhere on this map — how many planes a click has to try.
     */
    private int mapStoreys;
    private final Map<Integer, UnitNode> unitNodes = new HashMap<>();
    private final Set<Integer> selected = new HashSet<>();
    private final Map<String, AudioNode> audioCache = new HashMap<>();
    private final Set<String> missingAssets = new HashSet<>();
    /**
     * Every noise the game makes, and what it makes them for.
     */
    private GameSounds noises;
    /**
     * What the player set, kept in a file beside him — see { GameSettings}.
     */
    private final GameSettings preferences = new GameSettings();
    /**
     * The line of controls along the bottom, which belongs to play and not to a menu.
     */
    private BitmapText controlsHint;
    /**
     * Up while the game's art is being read; see {@link ArtLoad}.
     */
    private LoadingOverlay loading;
    private ArtLoad artLoad;
    /**
     * Set once every file the game named has been read and handed to the card.
     */
    private boolean artIsReady;

    private WorldSnapshot snapshot = WorldSnapshot.EMPTY;
    private WorldSnapshot lastEventedSnapshot = WorldSnapshot.EMPTY;
    private final CameraFocus camera = new CameraFocus();
    /**
     * How high the camera is looking, eased toward the ground under its target.
     */
    private float cameraHeight;
    private final boolean[] pan = new boolean[4]; // W A S D
    private BitmapText hud;
    private BitmapText buildMenu;
    private BannerPanel banner;
    private HeroPanel heroPanel;
    /**
     * The live creature in the panel's frame.
     *
     * <p>Held here rather than by the panel, and the reason is the resize: the bar
     * is thrown away and built again at every new window width, and a render
     * target that went with it would be a new frame buffer every time the window
     * was dragged. This outlives the panel and is simply hung on the next one.
     */
    private HeroPortrait portrait = HeroPortrait.none();

    // minimap: fixed-size overlay in the bottom-right corner
    private static final float MINIMAP_SIZE = 190f;
    private final Node minimapNode = new Node("minimap");
    /**
     * Backdrop and rock cells — replaced as a unit when the world changes.
     */
    private final Node minimapTerrainNode = new Node("minimap-terrain");
    private final Map<Integer, Geometry> minimapDots = new HashMap<>();
    private MinimapProjection minimap = new MinimapProjection(700f, 450f, MINIMAP_SIZE);
    private float minimapX;       // screen position of the minimap's origin
    private float minimapY;
    /**
     * How much the minimap is shrunk to fit the hero bar's socket, or 1 when it
     * is drawn in the corner as an RTS draws it.
     *
     * <p>Scaled rather than rebuilt at the socket's size: the projection and the
     * cells are laid out once per world, and a window resize must not mean
     * rebuilding a few thousand quads.
     */
    private float minimapScale = 1f;
    /**
     * The camera's footprint, drawn as an outline over the minimap.
     */
    private Geometry viewportOutline;

    /**
     * The box the player is dragging, drawn over the world while the button is down.
     */
    private Geometry dragRectangle;

    /**
     * Brief flashes acknowledging orders, and the node they are drawn in.
     */
    private final OrderMarkers orderMarkers = new OrderMarkers();
    private final Node markerNode = new Node("order-markers");

    /**
     * Draws the marks in that node, reusing what it has made. See Chevrons.
     */
    private Chevrons chevrons;

    /**
     * And the red ring that flashes round whatever was ordered attacked.
     */
    private AttackFlash attackFlash;

    /**
     * The numbers that come off a creature as it is hurt or healed.
     */
    private FloatingNumbers hitNumbers;
    private final HealthWatch healthWatch = new HealthWatch();

    /**
     * Who was killed this frame, gathered as the events are read.
     *
     * <p>The one thing the snapshot cannot say. A creature that dies is taken out
     * of the world on the frame the blow lands, so the finishing blow never shows
     * up as health going down -- the creature is simply not there any more, which
     * from the outside looks exactly like one that walked into the dark.
     */
    private final List<HealthWatch.Death> killedThisFrame = new ArrayList<>();

    /**
     * The bars over everybody's heads. Screen-space and pooled — see UnitBars.
     */
    private UnitBars unitBars;

    /**
     * Everything the scene keeps per live unit.
     */
    private static final class UnitNode {
        Node root;
        Geometry ring;
        /**
         * How high its bar floats, measured off the body once.
         *
         * <p>Measured rather than assumed, because a creature kit's hero stands
         * three times taller than its rat — the old constant suited a capsule and
         * put the bar inside a hero's chest. Measured ONCE, because the answer
         * cannot change while the model does not, and re-measuring a bounding box
         * every frame for forty creatures is the sort of cost nobody goes looking
         * for afterwards.
         */
        float barTop;
        /**
         * Where and when it was first drawn. A thing that lay where it was put for a
         * while is a mark; a thing that moved is a shot -- see Landing.
         */
        float bornX;
        float bornZ;
        float bornAt;
        Spatial body;      // the shape a click has to hit
        AnimComposer composer;
        AnimChannel legacyChannel;
        String currentAnim = "";
        /**
         * Until when a one-shot clip owns the model — a blow, or a flinch.
         *
         * <p>What a unit <em>is doing</em> is a loop and what <em>happens to it</em>
         * is not, and the second has to be able to interrupt the first without the
         * first taking it straight back. So while this is in the future the state
         * machine keeps its hands off.
         */
        float actionUntil;
        /**
         * Until when what it carries is out of its hands, or 0 for never.
         *
         * <p>Separate from {@link #actionUntil} although they are usually the same
         * number: a gesture can own the model without the hands having to be
         * empty, and every one-shot clip in the game except a two-handed spell
         * does exactly that.
         */
        float carryingAgainAt;
        /**
         * Until when a gesture the GAME asked for owns the model, or 0 for never.
         *
         * <p>Not the same as {@link #actionUntil}, which any one-shot sets. This
         * one outranks them: see {@link #playOnce}.
         */
        float gestureUntil;
        /**
         * The health it had in the last snapshot: a drop is a blow that landed.
         */
        float lastHealth = Float.NaN;
        UnitView view;
    }

    DukeRtsApp(DukeGame game, Visuals visuals, Shell shell, Hotkeys hotkeys) {
        this.game = game;
        this.visuals = visuals;
        this.shell = shell;
        this.hotkeys = hotkeys == null ? Hotkeys.none() : hotkeys;
        var sun = visuals == null ? Sunlight.DEFAULT : visuals.getSunlight();
        this.sunDirection = sun.direction();
        this.sunColour = sun.sunColour();
        this.ambientColour = sun.ambientColour();
    }

    /**
     * The simulation thread, if the player ever pressed Play.
     */
    Thread getSimThread() {
        return simThread;
    }

    void awaitStop() throws InterruptedException {
        stopped.await();
    }

    @Override
    public void destroy() {
        // Before the application goes, because it owns the render manager the
        // portrait's viewport is standing in.
        portrait.close();
        super.destroy();
        stopped.countDown();
    }

    // ---- scene setup ----

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        // What the pointer looks like, and when -- see Cursors. A game that names
        // none keeps the system arrow.
        cursors = new Cursors(assetManager, inputManager, visuals.getPointers());
        // jME binds Escape to quit, in SimpleApplication, before a game gets a
        // say. Both bindings then fire and the quit wins -- which is why the
        // pause menu below has never once been seen. Taken off here rather than
        // worked around, because a game that pauses on Escape and a client that
        // exits on Escape cannot both be right.
        inputManager.deleteMapping(INPUT_MAPPING_EXIT);
        // What shows where nothing is drawn at all, which is every cell the
        // player has never been in. The fog's own colour exactly, not a shade of
        // it: unwalked ground inside the map is the sheet at full strength, and
        // the two meeting at the map's edge would otherwise draw its outline.
        viewPort.setBackgroundColor(visuals.getDiscoveryTemplate() == null
                ? new ColorRGBA(0.05f, 0.07f, 0.10f, 1f)
                : visuals.getFog().tintColour());

        // project assets folder (Studio Play); exported games use the classpath
        if (visuals.getAssetRoot() != null) {
            assetManager.registerLocator(visuals.getAssetRoot(),
                    com.jme3.asset.plugins.FileLocator.class);
        }

        // Before the terrain, which builds materials that read it.
        if (visuals.getDiscoveryTemplate() != null) {
            fogMap = new FogMap(visuals.getFog());
        }
        // Needs the locators above, so it cannot be built with the app itself.
        terrain = new TerrainScene(terrainNode,
                fogMap == null ? this::lit : this::foggedTerrain,
                visuals.getDiscoveryTemplate() != null, visuals.getTiles(), new KitTiles());

        rootNode.addLight(new DirectionalLight(sunDirection, sunColour));
        rootNode.addLight(new AmbientLight(ambientColour));

        // Before the terrain, because rebuilding a world clears what is burning in
        // it and there has to be something there to clear.
        var budget = visuals.getEffectBudget();
        effects = new ProjectileEffects(assetManager, rootNode, visuals,
                budget.lights(), budget.perEffect(), budget.bursts(), budget.distance());
        skillEffects = new SkillEffects(assetManager, rootNode, visuals, effects,
                visuals.getRangeLook(),
                visuals.getSkillRings(), budget.distance());
        layered = new LayeredEffects(assetManager, rootNode, visuals, effects.lights(),
                surroundings(), visuals.getParticleBudget(), budget.distance());
        hitFlash = new HitFlash(visuals.getHitFlash());

        rootNode.attachChild(terrainNode);
        buildTerrain();
        rootNode.attachChild(unitsNode);
        rootNode.attachChild(markerNode);
        chevrons = new Chevrons(assetManager, markerNode, visuals.getOrderMark());
        attackFlash = new AttackFlash(assetManager, markerNode, visuals.getOrderMark());
        hitNumbers = new FloatingNumbers(guiFont, guiNode, visuals.getHitNumbers());
        // The display face a boss's name is set in is the one the game already
        // named for its menus. A second field naming the same file would be a
        // second thing to keep in step with it, for no second decision.
        unitBars = new UnitBars(assetManager, guiFont,
                fontOrDefault(visuals.getMenuStyle().rowFont()));
        unitBars.look(visuals.getUnitBars());
        guiNode.attachChild(unitBars.node());
        rangeRings = new RangeRings(assetManager, markerNode, visuals.getRangeLook());
        warmNode.setCullHint(Spatial.CullHint.Always);
        rootNode.attachChild(warmNode);

        centerCameraOnMap();
        installInput();

        hud = new BitmapText(guiFont);
        hud.setLocalTranslation(10, cam.getHeight() - 10f, 0);
        guiNode.attachChild(hud);

        buildMenu = new BitmapText(guiFont);
        buildMenu.setLocalTranslation(10, cam.getHeight() - 40f, 0);
        buildMenu.setColor(new ColorRGBA(0.8f, 1f, 0.8f, 1f));
        guiNode.attachChild(buildMenu);

        banner = new BannerPanel(assetManager, guiFont, guiNode, visuals.getPanelSkin());

        controlsHint = new BitmapText(guiFont);
        var hint = controlsHint;
        // What the keys actually do, which depends on what the game took. A game
        // that claims WASD for its own orders leaves the camera on the arrows —
        // see bindKeys — and a line still promising WASD would be a lie the player
        // discovers by pressing one.
        boolean panKeysTaken = hotkeys.unclaimed(new int[]{
                KeyInput.KEY_A, KeyInput.KEY_S, KeyInput.KEY_D}).length < 3;
        hint.setText("LMB select   Shift+LMB add   RMB move/attack   "
                + (panKeysTaken ? "arrows pan" : "WASD pan") + "   Space centre"
                + "   wheel zoom   H halt   P pause   Esc menu");
        hint.setLocalTranslation(10, hint.getLineHeight() + 6f, 0);
        hint.setAlpha(0.6f);
        guiNode.attachChild(hint);

        heroPanel = new HeroPanel(assetManager, guiFont,
                fontOrDefault(visuals.getMenuStyle().titleFont()), guiNode, cam.getWidth(),
                visuals.getPanelSkin(), visuals.getRangeLook(), visuals.getIconLook());
        // Built the same way units are -- see buildBody -- so the face in the
        // frame is the creature that is on the floor and not a second version
        // of it.
        portrait = HeroPortrait.open(renderManager, visuals, this::buildBody);

        buildMinimap();
        buildDragRectangle();
        menu = buildMenu(cam.getWidth(), cam.getHeight());
        loading = new LoadingOverlay(guiFont, assetManager, guiNode,
                cam.getWidth(), cam.getHeight());
        buildSounds();
        showMainMenu();
        applyVolume();
    }

    /**
     * Wire up the game's noise, or wire up silence.
     *
     * <p>A machine with no audio device gets {@link SoundSink#SILENT} and
     * everything above it runs unchanged — which is what a headless build is, and
     * what a test suite is. The alternative is every caller asking first whether
     * there is a speaker, and one of them eventually forgetting to.
     */
    private void buildSounds() {
        var sink = audioRenderer == null
                ? SoundSink.SILENT : new AudioSink(assetManager, rootNode);
        var sounds = new Sounds(visuals.getSounds(), sink);
        sounds.voiceGap(visuals.getSounds().voiceGapSeconds());
        // What counts as landing on somebody: a cell of the map the pathfinder
        // already keeps, rather than a number invented here.
        var grid = game.getTerrain();
        noises = new GameSounds(sounds,
                grid == null ? uz.duke.core.pathfind.PathGrid.DEFAULT_CELL_SIZE
                        : grid.getCellSize());
        applyVolume();
    }

    /**
     * Assemble the minimap once: its terrain layer, then the viewport outline over it.
     */
    private void buildMinimap() {
        minimapY = 34f; // above the hint line, until a hero bar claims it
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
        mapStoreys = grid == null ? 0 : highestStorey(grid);
        // The camera may look at exactly what the minimap draws, and no further.
        // Until there is a map there is nothing to fence it into: the sizes above
        // are only something to draw an empty minimap at.
        camera.keepInside(grid == null ? 0f : worldW, grid == null ? 0f : worldH);
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
        placeMinimap();
        minimapPalette = null; // rebuilt lazily against the new grid
        minimapFloors = null;
    }

    /**
     * Put the minimap where the hero bar says, or back in the corner when there
     * is no bar.
     *
     * <p>The bar owns the socket because it owns the arrangement; the minimap owns
     * the picture. Neither has to know the other's arithmetic — one hands over a
     * rectangle in window pixels and the other fits itself to it.
     */
    private void placeMinimap() {
        if (heroPanel != null && heroPanel.isShowing()) {
            var socket = heroPanel.minimapRect();
            minimapScale = socket[2] / Math.max(1f, Math.max(minimap.widthPixels(),
                    minimap.heightPixels()));
            // Centred in its socket: a map that is not square leaves a margin, and
            // the margin belongs on both sides rather than all on one.
            minimapX = socket[0] + (socket[2] - minimap.widthPixels() * minimapScale) / 2f;
            minimapY = socket[1] + (socket[2] - minimap.heightPixels() * minimapScale) / 2f;
        } else {
            minimapScale = 1f;
            minimapX = cam.getWidth() - minimap.widthPixels() - 12f;
            minimapY = 34f;
        }
        minimapNode.setLocalScale(minimapScale);
        // Above the socket it sits in. The GUI bucket is drawn in order of depth,
        // and the socket's own floor is at 3 -- without this the map is behind the
        // hole cut for it, which looks exactly like a minimap that stopped working.
        minimapNode.setLocalTranslation(minimapX, minimapY, minimapScale < 1f ? 4f : 0f);
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
            // The two dark ends follow the fog, so the little map and the world
            // it stands for are the same colour of nothing.
            var dark = visuals.getFog().tintColour();
            var rememberedFloor = dark.mult(0.9f).add(new ColorRGBA(0.04f, 0.06f, 0.03f, 0f));
            var litFloor = new ColorRGBA(0.16f, 0.22f, 0.13f, 1f);
            // A floor per storey, each one paler than the one below it. The map is
            // a plan view and a plan view cannot show height at all — two rooms one
            // above the other are the same square of paper — so the only thing left
            // is to say it in tone, the way a contour map does.
            int storeys = mapStoreys;
            var remembered = new Material[storeys + 1];
            var lit = new Material[storeys + 1];
            for (int storey = 0; storey <= storeys; storey++) {
                float lift = 1f + 0.45f * storey;
                remembered[storey] = unshaded(rememberedFloor.mult(lift));
                lit[storey] = unshaded(litFloor.mult(lift));
            }
            minimapFloors = new Material[][]{remembered, lit};
            minimapPalette = new Material[]{
                    unshaded(dark.mult(0.45f)),                          // never been there
                    unshaded(dark.mult(0.9f).add(                        // remembered stone
                            new ColorRGBA(0.09f, 0.08f, 0.07f, 0f))),
                    unshaded(new ColorRGBA(0.35f, 0.32f, 0.26f, 1f)),   // stone in sight
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
            int storey = Math.clamp(grid.level(cx, cy), 0, minimapFloors[0].length - 1);
            cell.setMaterial(switch (discovery.stateAt(cx, cy)) {
                case UNSEEN -> minimapPalette[0];
                case REMEMBERED -> stone ? minimapPalette[1] : minimapFloors[0][storey];
                case VISIBLE -> stone ? minimapPalette[2] : minimapFloors[1][storey];
            });
        }
    }

    /**
     * The highest storey anywhere on this map, so the minimap has a tone for each.
     */
    private static int highestStorey(uz.duke.core.pathfind.PathGrid grid) {
        int highest = 0;
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                highest = Math.max(highest, grid.level(cx, cy));
            }
        }
        return highest;
    }

    /**
     * If the cursor is over the minimap, move the camera there. Returns true if handled.
     */
    private boolean minimapClick() {
        var cursor = inputManager.getCursorPosition();
        float localX = (cursor.x - minimapX) / minimapScale;
        float localY = (cursor.y - minimapY) / minimapScale;
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

    /**
     * The selection box: an outline, so it never hides what is being selected.
     */
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
     * Draw the order markers — three arrowheads closing on the spot that was
     * clicked. See {@link Chevrons} for the drawing and {@link OrderMark} for the
     * movement.
     */
    private void syncOrderMarkers() {
        float now = timer.getTimeInSeconds();
        orderMarkers.prune(now, visuals.getOrderMark().seconds());
        chevrons.show(orderMarkers.markers(), now, this::floorHeightAt);
        attackFlash.show(orderMarkers.markers(), now, this::whereThatUnitIsNow,
                this::floorHeightAt);
        syncSkillRange(now);
        syncHitNumbers(now);
    }

    /**
     * Throw a number off anything whose health has moved, and keep the ones
     * already in the air moving.
     *
     * <p>Off the snapshot rather than off an event -- see {@link HealthWatch} for
     * why that is the complete answer rather than the lazy one.
     */
    private void syncHitNumbers(float now) {
        var look = visuals.getHitNumbers();
        List<HealthWatch.Change> changes = List.of();
        if (screen == Screen.PLAYING) {
            changes = healthWatch.since(snapshot.units(), game.getLocalPlayerIndex(),
                    look.leastWorth(), killedThisFrame);
            for (var change : changes) {
                hitNumbers.add(change, now, look.height());
                if (!change.healed() && !change.his()) {
                    flash(change.unitId());
                }
            }
            playTheRunsMoments(now);
        }
        burstWhatStruck(changes);
        // Read once. handleEvents runs earlier in the frame and fills this; a
        // second reading would throw the finishing blow twice.
        killedThisFrame.clear();
        hitNumbers.update(now, cam, this::floorHeightAt);
    }

    /**
     * A level gained, the boss down, a new floor: each played on him in the look the
     * game gave it, and a level heard as well.
     *
     * <p>Off the line's own field for the hero rather than off the panel's card,
     * which is whoever happens to be selected -- see {@link RunMoments}. One look on
     * him at a time, the biggest: the blow that fells a boss is usually the one that
     * gives him his level too.
     */
    private void playTheRunsMoments(float now) {
        var died = new ArrayList<Integer>(killedThisFrame.size());
        for (var death : killedThisFrame) {
            died.add(death.unitId());
        }
        var moments = runMoments.since(barReading, died);
        for (var moment : moments) {
            if (Visuals.LEVEL_UP.equals(moment.name())) {
                noises.levelledUp(now);
            }
        }
        var drawn = RunMoments.biggestOnEach(moments, name -> {
            var look = visuals.getMoment(name);
            return look == null ? 0.0 : look.scale();
        });
        for (var moment : drawn) {
            var look = visuals.getMoment(moment.name());
            var node = unitNodes.get(moment.unitId());
            if (look == null || node == null || layered == null) {
                continue;
            }
            var at = node.root.getLocalTranslation();
            var foot = new Vector3f(at.x, floorHeightAt(at.x, at.z), at.z);
            layered.cast(look.effect(), new LayeredEffects.Moment(foot, null, null, 0f, 1f, 0f,
                    moment.unitId(), moment.unitId()), cam, look.scale());
        }
    }

    /**
     * The bursts of the shots that ended this frame, for the ones that struck.
     *
     * <p>The simulation's blast hurts only when a shot reaches a body, and round that
     * body; a shot that meets a wall or runs out of flight hurts nobody, and bursts
     * nowhere. The blows are this frame's, deaths included -- a finishing blow on
     * something with less left than a number is worth is still a blow.
     */
    private void burstWhatStruck(List<HealthWatch.Change> changes) {
        if (endedShots.isEmpty()) {
            return;
        }
        var blows = new ArrayList<Landing.Blow>();
        for (var change : changes) {
            if (!change.healed()) {
                blows.add(new Landing.Blow(change.x(), change.y(), change.his()));
            }
        }
        for (var death : killedThisFrame) {
            blows.add(new Landing.Blow(death.x(), death.y(),
                    death.playerIndex() == game.getLocalPlayerIndex()));
        }
        for (var shot : endedShots) {
            var at = Landing.burstAt(shot, blows, visuals.getStrikeWithin());
            if (at != null && layered != null) {
                layered.landed(shot.id(), shot.effect(), at, cam);
            }
        }
        endedShots.clear();
    }

    /**
     * One of theirs was hit: it goes white for an instant, from its own colour and
     * back to it. A blow that killed it finds nothing here -- the body was laid out
     * when the death was read, and a corpse has nothing left to flinch with.
     */
    private void flash(int unitId) {
        var node = unitNodes.get(unitId);
        if (hitFlash == null || node == null || node.view == null) {
            return;
        }
        var tint = visualFor(node.view.templateName()).tint;
        var own = (tint == null ? ColorRGBA.White : toColor(tint)).mult(CREATURE_AMBIENT);
        hitFlash.struck(unitId, node.root, own);
    }

    /**
     * Draw how far the armed skill reaches, and whether the click as it stands
     * would be obeyed.
     *
     * <p>Round the hero rather than round the selection: a skill is cast by the
     * man who has it whether or not the player has him clicked, so a ring drawn at
     * the selection would be in the wrong place exactly when he had picked a
     * monster out to look at.
     */
    private void syncSkillRange(float now) {
        var range = arming == null ? null : visuals.getSkillRange(arming);
        if (range == null || screen != Screen.PLAYING) {
            rangeRings.hide();
            return;
        }
        var hero = whereHisHeroIs();
        if (hero == null) {
            rangeRings.hide();
            return;
        }
        Coord3D pointer = null;
        boolean allowed = true;
        if (range.needsAiming()) {
            var at = inputManager.getCursorPosition();
            var ground = groundUnder(at.x, at.y);
            pointer = new Coord3D(ground.x, ground.z, 0f);
            // Out of reach is not a refusal -- the click is pulled back to the
            // edge, and the ring is what says so. What refuses is ground he
            // cannot aim at: stone, or somewhere he has never been.
            allowed = isOpenAndSeen(ground);
        }
        rangeRings.show(range, hero, pointer, allowed, now, this::floorHeightAt);
    }

    /**
     * Where a unit is standing this frame, or null once it has left the world.
     *
     * <p>Read off the snapshot, which is the only place this side has an answer:
     * a mark that follows a creature has to be told where it went, every frame,
     * and the creature itself lives on the other thread.
     */
    private Coord3D whereThatUnitIsNow(int unitId) {
        for (var view : snapshot.units()) {
            if (view.id() == unitId) {
                return new Coord3D(view.x(), view.y(), 0f);
            }
        }
        return null;
    }

    /**
     * Where the player's own unit is standing, or null before there is one.
     */
    private Coord3D whereHisHeroIs() {
        int local = game.getLocalPlayerIndex();
        for (var view : snapshot.units()) {
            if (view.playerIndex() == local && view.selectable()) {
                return new Coord3D(view.x(), view.y(), 0f);
            }
        }
        return null;
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
        var terrain = game.getTerrain();
        return groundHit(near, dir, terrain == null ? 0f : terrain.getLevelHeight(),
                mapStoreys, this::floorHeightAt);
    }

    /**
     * Where the ray under the cursor first meets the ground, whichever storey that
     * turns out to be.
     *
     * <p>Every storey is a level plane, so each one is tried in turn from the top
     * down — the camera looks down at the map, so a higher plane is met earlier
     * along the ray, and the first plane whose meeting point is really standing on
     * that storey is the surface the player is pointing at.
     *
     * <p>Starting at the ground floor and working up does not do it, which is what
     * was here before and what put the marker a pace beyond the cursor. A click on
     * a raised room, read against the plane at zero, carries on past the room and
     * lands behind it; the floor there is at zero as well, so the answer looks
     * settled and is wrong by however far the ray travelled underneath.
     *
     * @param floorAt how high the floor is at a point on the map, which for a
     *                stair is somewhere between two storeys — so the plane is met once more
     *                at that exact height rather than at the storey's
     */
    static Vector3f groundHit(Vector3f near, Vector3f dir, float storeyHeight, int storeys,
                              java.util.function.BiFunction<Float, Float, Float> floorAt) {
        for (int storey = Math.max(storeys, 0); storey >= 0; storey--) {
            float height = storey * storeyHeight;
            var hit = meetsAt(near, dir, height);
            float floor = floorAt.apply(hit.x, hit.z);
            if (storeyHeight <= 0f || Math.abs(floor - height) <= storeyHeight * 0.5f) {
                return floor == height ? hit : meetsAt(near, dir, floor);
            }
        }
        return meetsAt(near, dir, 0f);
    }

    /**
     * Where a ray meets a level plane at {@code height}.
     *
     * <p>A ray above the horizon never meets it, so it is followed a long way
     * instead and the projection clamps the result to the map — which is what the
     * player sees anyway: the view running off the edge of the world.
     */
    private static Vector3f meetsAt(Vector3f near, Vector3f dir, float height) {
        float t = Math.abs(dir.y) < 1e-6f ? -1f : (height - near.y) / dir.y;
        return near.add(dir.mult(t < 0 ? 10_000f : t));
    }

    /**
     * Live unit dots, coloured by player, sized up for structures.
     */
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
        if (noises != null) {
            noises.sounds().music(null); // the dungeon is behind him for now
        }
        screen = Screen.MENU;
        var items = new java.util.ArrayList<StoneMenu.Row>();
        for (var chosen : shell.entries()) {
            var action = actionFor(chosen.getKey());
            if (action != null) {
                items.add(new StoneMenu.Action(chosen.getValue(), action));
            }
        }
        menu.show(game.getTitle(), game.getSubtitle(), items,
                "UP DOWN choose    ENTER take", version(), false);
    }

    /**
     * What an entry does, or {@code null} when it would do nothing worth offering.
     */
    private Runnable actionFor(Shell.Entry entry) {
        return switch (entry) {
            // Play does not necessarily play. A game with something to ask first
            // asks it here — see showQuestion — and starts when it is answered.
            case PLAY -> shell.question() == null ? this::startGame : this::showQuestion;
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

    /**
     * The one thing the game wants settled before it will start.
     *
     * <p>Built from the same stone as every other menu, and answered the same way:
     * a column of words, one of which is taken. What it is <em>about</em> is the
     * game's — this client has never heard of a hero, a difficulty or a side.
     *
     * <p>There is no entry that skips it. That is the whole point of asking: a
     * question with a way past it is a setting, and the player would press Play,
     * get whatever a file happened to say, and never find out he had a choice.
     * Back returns to the front menu, which is a way out of the game rather than
     * a way into it without answering.
     *
     * <p>The answer is taken <em>before</em> the world runs a frame — the
     * simulation thread does not exist until {@link #startGame} — so what it does
     * needs no hopping between threads and cannot race the first frame.
     */
    private void showQuestion() {
        var question = shell.question();
        if (question == null) {
            startGame();
            return;
        }
        showQuestion(question, this::showMainMenu);
    }

    /**
     * One question of the path, and the way back out of it.
     *
     * <p>{@code back} is the screen before this one rather than the front menu,
     * so a path three deep is walked backwards a step at a time. Handed in rather
     * than remembered because that is exactly what it is: each screen already
     * knows what opened it, and a stack would be a second copy of the path that
     * could disagree with the first.
     */
    private void showQuestion(Shell.Question question, Runnable back) {
        screen = Screen.MENU;
        var items = new java.util.ArrayList<StoneMenu.Row>();
        for (var option : question.options()) {
            items.add(new StoneMenu.Action(option.label(), () -> {
                if (option.taken() != null) {
                    option.taken().run();
                }
                // Either it narrows the choice further or it was the last of them.
                if (option.next() != null) {
                    showQuestion(option.next(), () -> showQuestion(question, back));
                } else {
                    startGame();
                }
            }));
            if (!option.blurb().isBlank()) {
                // Under the name rather than beside it: what he is choosing is the
                // word above, and the line below says what taking it means.
                items.add(new StoneMenu.Words("", option.blurb()));
            }
        }
        items.add(new StoneMenu.Action("Back", back));
        menu.show(game.getTitle(), question.title(), items,
                question.hint(), version(), false);
    }

    /**
     * Choose the map and cycle each player's faction before playing.
     */
    private void showSkirmishMenu() {
        var maps = game.getMapChoices();
        var factions = game.getFactionChoices();
        if (chosenMap == null && !maps.isEmpty()) {
            chosenMap = maps.get(0);
        }
        while (chosenFactions.size() < 2 && !factions.isEmpty()) {
            chosenFactions.add(factions.get(chosenFactions.size() % factions.size()));
        }

        var items = new java.util.ArrayList<StoneMenu.Row>();
        items.add(new StoneMenu.Action("Map: " + chosenMap, () -> {
            chosenMap = maps.get((maps.indexOf(chosenMap) + 1) % maps.size());
            showSkirmishMenu();
        }));
        for (int p = 0; p < chosenFactions.size(); p++) {
            final int player = p;
            items.add(new StoneMenu.Action("Player " + (p + 1) + ": " + chosenFactions.get(p), () -> {
                int next = (factions.indexOf(chosenFactions.get(player)) + 1) % factions.size();
                chosenFactions.set(player, factions.get(next));
                showSkirmishMenu();
            }));
        }
        items.add(new StoneMenu.Action("Start match", () -> {
            game.selectSkirmish(chosenMap, chosenFactions);
            startGame();
        }));
        items.add(new StoneMenu.Action("Back", this::showMainMenu));
        menu.show(game.getTitle(), "skirmish setup", items,
                "UP DOWN choose    ENTER take    ESC back", version(), false);
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
                java.util.List.of(new StoneMenu.Action("Cancel", () -> {
                    game.cancelHosting();
                    showMainMenu();
                })), "", version(), false);
    }

    private void joinFlow() {
        menu.show(game.getTitle(), "joining…", java.util.List.of(), "", version(), false);
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
        if (!artIsReady) {
            beginLoadingArt();
            return; // the world waits until there is something to draw it with
        }
        if (simThread == null) {
            simThread = game.startEngineOnly();
        }
        // Open on the player's own units rather than the middle of the map, which
        // left him hunting the map for whatever he is supposed to be controlling.
        camera.requestOwnUnit();
        menu.hide();
        // Underneath everything from here on, until he goes back to the menu.
        playChosenMusic();
        screen = Screen.PLAYING;
    }

    // ---- reading the art before it is wanted ----

    /**
     * Read everything the game will draw, now, rather than in the frame it is
     * first drawn in.
     *
     * <p>What this fixes is a stall with a very specific shape: the game runs at
     * its full rate until the first monster of a kind arrives, then drops to
     * twenty for a second, then runs again. The cause is not the monster. It is
     * that drawing one means reading nine megabytes of model and seven of
     * animation library off the disc and handing a megabyte and a half of texture
     * to the graphics card — and all of that happens on the thread that draws, in
     * the frame that asked, because that is the frame in which the client first
     * learned it was needed.
     *
     * <p>It could not have learned earlier from the game, but it could have asked
     * {@link Visuals}, which has known since before the window opened.
     */
    private void beginLoadingArt() {
        artLoad = new ArtLoad();
        menu.hide();
        loading.show(game.getTitle());
        screen = Screen.LOADING;
    }

    private void advanceLoadingArt() {
        loading.progress(artLoad.done(), artLoad.what());
        if (!artLoad.step()) {
            return;
        }
        artIsReady = true;
        artLoad = null;
        loading.hide();
        startGame();
    }

    /**
     * One pass over every file the game named, in two halves.
     *
     * <p>The reading half runs on a thread of its own, because that is the slow
     * part and doing it here would freeze the very screen that exists to show it
     * happening. jME's asset manager is built for this: a loader off the render
     * thread parses into the shared cache, and nothing touches the scene graph.
     *
     * <p>The second half cannot be moved and does not need to be. Handing a mesh
     * or a texture to the card, and compiling the shader that will draw it, is
     * work only the render thread may do — so it is done here a piece at a time,
     * between frames, while the bar keeps moving. It is the half that matters:
     * a file read but never handed over stalls on first sight exactly as before.
     *
     * <p>Both halves keep what they load, and that is not tidiness. jME's asset
     * cache holds what it has parsed through a <em>weak</em> reference: an asset
     * nobody is holding is collected, and the next request for it reads the file
     * again. Load twenty megabytes, drop it on the floor, and the collector is
     * free to undo the entire exercise before the first monster walks in — with
     * no error, no warning, and a stall in exactly the frame this exists to spare.
     * So one instance of each is kept for the life of the window.
     */
    private final class ArtLoad {

        /**
         * How many pieces are handed to the card per frame, so the bar still moves.
         */
        private static final int WARM_PER_FRAME = 1;

        private final List<Preload.Job> files = Preload.plan(visuals);
        /**
         * One look per model, not one per creature: six kinds cut from one kit
         * share a file, and showing the card the same mesh six times teaches it
         * nothing it did not know after the first.
         */
        private final List<Visuals.UnitVisual> looks = visuals.allLooks().stream()
                .filter(look -> look.modelPath != null)
                .collect(java.util.stream.Collectors.toMap(look -> look.modelPath,
                        look -> look, (first, next) -> first, java.util.LinkedHashMap::new))
                .values().stream().toList();
        /**
         * Every sound, from both places a game may name one.
         *
         * <p>A unit's fire and death sounds are named on the unit; everything else
         * is in the bank. The music is left out on purpose — it is streamed from
         * disc as it plays rather than held, so reading it here would be reading a
         * megabyte to throw it away.
         */
        private final List<String> sounds = java.util.stream.Stream.concat(
                        files.stream().filter(job -> job.kind() == Preload.Kind.SOUND)
                                .map(Preload.Job::assetPath),
                        visuals.getSounds().all().stream()
                                .filter(cue -> cue.channel() != SoundBank.Channel.MUSIC)
                                .flatMap(cue -> cue.files().stream()))
                .distinct().toList();
        private final java.util.concurrent.atomic.AtomicInteger read =
                new java.util.concurrent.atomic.AtomicInteger();
        private volatile String reading = "";
        private int warmed;

        ArtLoad() {
            var reader = new Thread(this::readEverything, "duke-art");
            reader.setDaemon(true); // a window closed mid-load must still close
            reader.start();
        }

        private void readEverything() {
            for (var job : files) {
                reading = job.assetPath();
                try {
                    switch (job.kind()) {
                        case MODEL, TILE -> keep(assetManager.loadModel(job.assetPath()));
                        case ANIMATIONS -> animationLibraries.computeIfAbsent(job.assetPath(),
                                assetManager::loadModel);
                        case TEXTURE -> keep(assetManager.loadTexture(job.assetPath()));
                        // Sound is the render thread's: an audio node goes into the
                        // scene, and the scene is not this thread's to touch.
                        case SOUND -> {
                        }
                    }
                } catch (RuntimeException | LinkageError e) {
                    warnOnce(job.assetPath(), job.kind().name().toLowerCase(
                            java.util.Locale.ROOT));
                } finally {
                    // In a finally, because a thread that dies holding the count
                    // leaves the loading screen up for ever, and a game that will
                    // not start is worse than a game missing one texture.
                    read.incrementAndGet();
                }
            }
            reading = "";
        }

        /**
         * How far along, counting both halves as one.
         */
        float done() {
            return (read.get() + warmed) / (float) Math.max(1, total());
        }

        String what() {
            return reading;
        }

        private int total() {
            return files.size() + looks.size() + sounds.size() + 1;
        }

        /**
         * Do this frame's share of the work; {@code true} once there is none left.
         */
        boolean step() {
            if (read.get() < files.size()) {
                return false; // still reading; the bar is the only thing to do
            }
            for (int i = 0; i < WARM_PER_FRAME && warmed < looks.size() + sounds.size() + 1; i++) {
                warmOne();
                warmed++;
            }
            return warmed >= looks.size() + sounds.size() + 1;
        }

        private void warmOne() {
            if (warmed < looks.size()) {
                warmLook(looks.get(warmed));
            } else if (warmed < looks.size() + sounds.size()) {
                soundNode(sounds.get(warmed - looks.size()));
            } else {
                // The floor is already standing, and its several hundred tiles are
                // three meshes and one material the card has still never seen.
                renderManager.preloadScene(terrainNode);
            }
        }

        /**
         * Build one of this creature exactly as the game will, show it to the card,
         * and throw it away.
         *
         * <p>Thrown away and still worth doing, because what is kept is not the
         * model: it is the parsed file in the asset cache, the texture in graphics
         * memory, and the compiled shader for the one material every creature of
         * this kit shares. The next one built is the cheap one.
         */
        private void warmLook(Visuals.UnitVisual look) {
            reading = look.modelPath;
            try {
                var body = assetManager.loadModel(look.modelPath);
                if (look.modelPart != null) {
                    body = partOf(body, look.modelPart, look.modelPath);
                }
                dressModel(body, look);
                // Left in the scene, culled, for as long as the window lasts. A
                // clone is what holds a parsed model in jME's cache; drop it and
                // the file is read again the first time a creature needs it.
                warmNode.attachChild(body);
                rootNode.updateGeometricState();
                renderManager.preloadScene(body);
            } catch (RuntimeException e) {
                warnOnce(look.modelPath, "model");
            }
        }
    }

    /**
     * Where one of each creature stands, culled, for the life of the window.
     *
     * <p>In the scene rather than off to the side of it, because a spatial with no
     * parent has no world transform and jME says so with an assertion. Never
     * drawn — the node is culled outright — and never emptied, because these
     * are the references that hold the parsed models in the asset cache.
     */
    private final Node warmNode = new Node("warm");

    /**
     * Textures and pieces with nothing else holding them. See {@link ArtLoad}.
     */
    private final List<Object> artKeptAlive =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private void keep(Object asset) {
        artKeptAlive.add(asset);
    }

    private void showPauseMenu() {
        screen = Screen.PAUSED;
        // Set here rather than queued for the simulation thread, and it has to be:
        // a paused engine does not step, so the task that would unpause it never
        // runs and the game never starts again. The flag takes no part in the
        // checksum -- pausing changes when frames happen, not what is in them.
        setSimulationPaused(true);
        var rows = new java.util.ArrayList<StoneMenu.Row>();
        // What he stopped in the middle of. Read off the game's own status line
        // rather than counted here, so a game that says nothing shows nothing —
        // and parsed from the snapshot rather than taken from the panel, which
        // has been put away by the time a menu is up.
        var run = snapshot != null && snapshot.hasStatus()
                ? HeroPanel.Reading.parse(snapshot.status())
                : null;
        if (run != null) {
            rows.add(new StoneMenu.Words(run.depthWord().isEmpty() ? "DEPTH"
                    : run.depthWord(), run.depth()));
            rows.add(new StoneMenu.Words("RANK", run.rank()));
        }
        rows.add(new StoneMenu.Action("Resume", this::resumeGame));
        rows.add(new StoneMenu.Action("Settings", () -> showSettingsMenu(Screen.PAUSED)));
        // Marked, and asked about. It is the one thing on any of these screens
        // that costs him something he cannot get back.
        rows.add(new StoneMenu.Action("Abandon the run", this::confirmAbandon, true));
        menu.show("PAUSED", "", rows, "ESC resume", "", true);
    }

    /**
     * Ask before throwing a run away.
     *
     * <p>A second screen rather than a dialog over the first: this client has no
     * dialogs, and a menu that replaces a menu is the same mechanism doing the
     * same job. The safe answer is the one already lit.
     */
    private void confirmAbandon() {
        menu.show("ABANDON?", "everything on this floor is lost", java.util.List.of(
                        new StoneMenu.Action("Keep playing", this::showPauseMenu),
                        new StoneMenu.Action("Abandon", this::stop, true)),
                "ESC keep playing", "", true);
    }

    /**
     * What the client calls itself, for the corner of the front menu.
     */
    private String version() {
        var version = getClass().getPackage().getImplementationVersion();
        return version == null ? "" : "v" + version;
    }

    /**
     * The menu, built with whatever lettering the game asked for.
     *
     * <p>Rebuilt rather than resized when the window changes, because everything
     * in it is laid out from the screen it was built against — see
     * {@link #followTheWindowSize}.
     */
    private StoneMenu buildMenu(float width, float height) {
        if (craft == null) {
            craft = new StoneCraft(assetManager, guiFont);
        }
        var style = visuals.getMenuStyle();
        return new StoneMenu(craft, fontOrDefault(style.titleFont()),
                fontOrDefault(style.rowFont()), guiNode, width, height);
    }

    private com.jme3.font.BitmapFont fontOrDefault(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return guiFont;
        }
        try {
            return assetManager.loadFont(assetPath);
        } catch (RuntimeException e) {
            warnOnce(assetPath, "font");
            return guiFont;
        }
    }

    private void resumeGame() {
        setSimulationPaused(false);
        menu.hide();
        screen = Screen.PLAYING;
    }

    // ---- settings ----

    private void showSettingsMenu(Screen returnTo) {
        settingsReturn = returnTo;
        screen = Screen.SETTINGS;
        var rows = new java.util.ArrayList<StoneMenu.Row>();
        rows.add(new StoneMenu.Choice("Fullscreen", java.util.List.of("NO", "YES"),
                () -> preferences.flag("fullscreen", false) ? 1 : 0,
                to -> {
                    preferences.set("fullscreen", to == 1);
                    fillTheScreen(to == 1); // seen at once, like a volume is heard
                }));
        // The real ones, from the card, rather than three the client invented —
        // a player who picks a size his monitor cannot show gets a black screen.
        var sizes = screenSizes();
        rows.add(new StoneMenu.Opens("Size",
                sizes.stream().map(Size::shown).toList(),
                () -> indexOfSize(sizes, preferences.number("width", 0),
                        preferences.number("height", 0)),
                to -> {
                    preferences.set("width", sizes.get(to).width());
                    preferences.set("height", sizes.get(to).height());
                    applyDisplaySettings();
                }));
        rows.add(volumeRow("Volume", "volume", 100));
        // Only offered by a game that has any: this client draws three others and
        // a knob for a channel with nothing on it is a dead button.
        if (!visuals.getSounds().isEmpty()) {
            rows.add(volumeRow("Effects", "volEffects", 100));
            rows.add(volumeRow("Voice", "volVoice", 100));
            rows.add(volumeRow("Music", "volMusic", 60));
            var tracks = visuals.getSounds().musicCues();
            if (tracks.size() > 1) {
                // A list that drops open, not cells side by side: a track is
                // called "The Sentinel", and three of those across one row is
                // three names too small to read.
                rows.add(new StoneMenu.Opens("Track",
                        tracks.stream().map(SoundBank.Cue::shown).toList(),
                        () -> Math.clamp(preferences.number("musicTrack", 0), 0, tracks.size() - 1),
                        to -> {
                            preferences.set("musicTrack", to);
                            playChosenMusic();
                        }));
            }
        }
        // The two buttons, in sockets rather than as two more rows: they are not
        // settings, they are what happens to the settings.
        rows.add(new StoneMenu.Buttons("", "Save", () -> {
            preferences.save();
            menu.refresh();
        }, "Cancel", this::undoSettings,
                preferences.dirty() ? "unsaved changes" : ""));
        menu.show("SETTINGS", "", rows,
                "UP DOWN choose    LEFT RIGHT change    ESC back", "",
                settingsReturn == Screen.PAUSED);
    }

    /**
     * One knob, worded and wired the same way as the rest.
     *
     * <p>Applied as it moves, because a volume has to be heard to be chosen, and
     * put back by Cancel — which is the promise the two buttons underneath make.
     */
    private StoneMenu.Row volumeRow(String word, String key, int fallback) {
        return new StoneMenu.Level(word, () -> preferences.number(key, fallback),
                to -> {
                    preferences.set(key, to);
                    applyVolume();
                }, 5);
    }

    /**
     * Throw away what has not been saved, and put back what was already applied.
     *
     * <p>Everything on this screen takes effect while it is being chosen, so
     * cancelling is not only forgetting: the volume has to come back down and the
     * window has to go back to the shape it was.
     */
    private void undoSettings() {
        var pending = preferences.pending();
        preferences.cancel();
        if (pending.contains("fullscreen")) {
            fillTheScreen(preferences.flag("fullscreen", false));
        }
        if (pending.contains("width") || pending.contains("height")) {
            applyDisplaySettings();
        }
        applyVolume();
        if (pending.contains("musicTrack")) {
            playChosenMusic();
        }
        leaveSettings();
    }

    /**
     * One size a monitor will actually show.
     */
    private record Size(int width, int height, boolean native_) {
        String shown() {
            return width + " × " + height + (native_ ? "   MONITOR" : "");
        }
    }

    /**
     * The sizes this machine can really show, largest first.
     *
     * <p>Asked of the graphics device rather than written down here, because a
     * list written down is a list that offers somebody a resolution his monitor
     * refuses — and what that looks like is a black screen and a game that has to
     * be killed. Deduplicated by shape, since a mode exists per refresh rate and
     * a player is not choosing a refresh rate.
     */
    private java.util.List<Size> screenSizes() {
        var found = new java.util.LinkedHashSet<Size>();
        try {
            var device = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            var current = device.getDisplayMode();
            var modes = new java.util.ArrayList<>(java.util.List.of(device.getDisplayModes()));
            modes.sort(java.util.Comparator
                    .comparingInt(java.awt.DisplayMode::getWidth)
                    .thenComparingInt(java.awt.DisplayMode::getHeight).reversed());
            for (var mode : modes) {
                if (mode.getWidth() >= 1024 && mode.getHeight() >= 576) {
                    found.add(new Size(mode.getWidth(), mode.getHeight(),
                            mode.getWidth() == current.getWidth()
                                    && mode.getHeight() == current.getHeight()));
                }
            }
        } catch (RuntimeException e) {
            warnOnce("display modes", "screen");
        }
        if (found.isEmpty()) {
            found.add(new Size(1280, 720, false));
        }
        return java.util.List.copyOf(found);
    }

    /**
     * Which of them is set, or the one nearest the monitor's own.
     */
    private int indexOfSize(java.util.List<Size> sizes, int width, int height) {
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i).width() == width && sizes.get(i).height() == height) {
                return i;
            }
        }
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i).native_()) {
                return i;
            }
        }
        return 0;
    }

    private void leaveSettings() {
        if (settingsReturn == Screen.PAUSED) {
            showPauseMenu();
        } else {
            showMainMenu();
        }
    }

    /**
     * In and out of fullscreen, from anywhere, without going through the menu.
     *
     * <p>The same switch the settings menu throws, so the two cannot disagree and
     * the choice is remembered for the next launch either way. Everything that is
     * sized to the window — the hero's bar, the minimap in it, the menus — is laid
     * out again when the new size arrives.
     */
    private void toggleFullscreen() {
        boolean wanted = !preferences.flag("fullscreen", false);
        if (fillTheScreen(wanted)) {
            preferences.set("fullscreen", wanted);
            preferences.save(); // F11 is not a settings screen; there is no Save to press
            settings.setFullscreen(wanted);
        }
    }

    /**
     * Where the window was before it filled the screen: x, y, width, height.
     */
    private int[] windowedBounds;

    /**
     * Move the window between filling the screen and sitting in it — without
     * rebuilding anything.
     *
     * <p>The obvious way is {@code restart()}, and it does not work here: it tears
     * the display context down and stands another one up, and what comes back is a
     * window that renders a frozen picture of a game that has stopped. It is also
     * more than was asked for — the pictures, the meshes and the shaders are all
     * still perfectly good, and the only thing that needs to change is which
     * monitor the window belongs to.
     *
     * <p>So it is said to the window directly. GLFW moves it to the monitor and
     * back, keeps the GL context, and reports the new size the way it reports any
     * other resize — which the HUD already follows.
     *
     * <p><b>At the size it already is</b>, which is the part that took finding
     * out. Filling the screen at the monitor's own resolution looks like the right
     * answer and half works: the world is drawn at full sharpness, and the HUD
     * disappears. Everything on it is in the scene, in the right place and
     * unculled — the engine's GUI simply does not survive its window changing size
     * underneath it, and rebuilding every piece of it does not help either. The
     * same game <em>started</em> fullscreen is perfect, because then nothing
     * changed size.
     *
     * <p>So nothing changes size here either. The window keeps the framebuffer it
     * has and only moves onto the monitor, which switches the screen to that mode
     * for as long as the game is up — the ordinary bargain of an exclusive
     * fullscreen game, and exactly what starting fullscreen already did.
     *
     * @return whether there was a window to say it to
     */
    private boolean fillTheScreen(boolean fullscreen) {
        if (!(getContext() instanceof com.jme3.system.lwjgl.LwjglWindow display)) {
            return false;
        }
        long window = display.getWindowHandle();
        if (window == 0L) {
            return false;
        }
        if (fullscreen) {
            windowedBounds = boundsOf(window);
            long monitor = org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor();
            if (monitor == 0L) {
                return false;
            }
            org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, monitor, 0, 0,
                    windowedBounds[2], windowedBounds[3],
                    org.lwjgl.glfw.GLFW.GLFW_DONT_CARE);
            return true;
        }
        var back = windowedBounds != null ? windowedBounds : startingBounds();
        org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, 0L, back[0], back[1], back[2], back[3],
                org.lwjgl.glfw.GLFW.GLFW_DONT_CARE);
        return true;
    }

    private static int[] boundsOf(long window) {
        var x = new int[1];
        var y = new int[1];
        var width = new int[1];
        var height = new int[1];
        org.lwjgl.glfw.GLFW.glfwGetWindowPos(window, x, y);
        org.lwjgl.glfw.GLFW.glfwGetWindowSize(window, width, height);
        return new int[]{x[0], y[0], width[0], height[0]};
    }

    /**
     * Where a window that has never been anywhere else should go back to.
     */
    private int[] startingBounds() {
        return new int[]{60, 60, preferences.number("width", 1280),
                preferences.number("height", 720)};
    }

    /**
     * Remember the chosen resolution for the next launch.
     *
     * <p>It is not applied to the window that is open, and that is the honest
     * thing rather than a shortcut: a window that changes size takes the HUD with
     * it — see {@link #fillTheScreen} — so a resolution applied live would leave
     * the player looking at a game with no bar and no minimap. A new window is
     * built at the right size and everything is laid out once, which is what a
     * launch does.
     */
    private void applyDisplaySettings() {
        settings.setResolution(preferences.number("width", 1280),
                preferences.number("height", 720));
        settings.setFullscreen(preferences.flag("fullscreen", false));
        menu.refresh(); // the row says what it now is
    }

    /**
     * Put every knob where the player last left it.
     *
     * <p>Four of them, because one is not enough for the same person: keeping the
     * music low while still hearing what is behind you is an ordinary thing to
     * want, and so is silencing a hero who will not stop talking. The screen's own
     * sounds follow the effects knob rather than getting a fifth — a menu click is
     * an effect that happens to be on a menu.
     */
    private void applyVolume() {
        listener.setVolume(1f); // the knobs are per channel now; the listener is not
        if (noises == null) {
            return;
        }
        var sounds = noises.sounds();
        sounds.masterVolume(preferences.number("volume", 100) / 100f);
        float effects = preferences.number("volEffects", 100) / 100f;
        sounds.volume(SoundBank.Channel.EFFECTS, effects);
        sounds.volume(SoundBank.Channel.UI, effects);
        sounds.volume(SoundBank.Channel.VOICE, preferences.number("volVoice", 100) / 100f);
        sounds.volume(SoundBank.Channel.MUSIC, preferences.number("volMusic", 60) / 100f);
    }

    /**
     * The track the player chose, or the first the game listed.
     *
     * <p>Music is the one channel where the choice is his rather than the
     * moment's: an effect belongs to whatever just happened, and what plays
     * underneath a dungeon for an hour is taste.
     */
    private SoundBank.Cue chosenTrack() {
        var tracks = visuals.getSounds().musicCues();
        if (tracks.isEmpty()) {
            return null;
        }
        return tracks.get(Math.clamp(preferences.number("musicTrack", 0), 0,
                tracks.size() - 1));
    }

    private void playChosenMusic() {
        var track = chosenTrack();
        noises.sounds().music(track == null ? null : track.name());
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The window is a different size.
     *
     * <p>A window on its way between the desktop and a monitor passes through
     * being no size at all, and the engine reports that faithfully: a
     * {@code 0 x 0} reshape resizes the cameras to nothing, which gives the 3D
     * camera a frustum it can never see anything through again. Everything then
     * goes on running with an empty screen — the HUD still draws, because the GUI
     * camera is orthographic and survives it, which is exactly why the symptom
     * reads as "the world vanished" rather than as a resize going wrong.
     *
     * <p>So a size of nothing is not a size. What the window settles on arrives a
     * moment later, and {@link #followTheWindowSize()} catches it either way.
     */
    @Override
    public void reshape(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        super.reshape(width, height);
        layOutForTheWindow(width, height);
    }

    /**
     * The size everything on the HUD was last laid out for.
     */
    private int laidOutFor;

    /**
     * Put the HUD back together at whatever size the window is now.
     *
     * <p>Called from {@code reshape}, and again from the frame loop whenever the
     * camera turns out to be a different size from the one the HUD was built at.
     * The second is not belt and braces: coming back from a display restart, the
     * size the engine reshapes with and the size the camera ends up with are not
     * always the same one, and the difference is a menu drawn off the edge of the
     * screen. Comparing costs two integers a frame.
     */
    private void layOutForTheWindow(int width, int height) {
        if (hud == null) {
            return; // not initialised yet
        }
        laidOutFor = width * 100_000 + height;
        hud.setLocalTranslation(10, height - 10f, 0);
        buildMenu.setLocalTranslation(10, height - 40f, 0);
        // Everything drawn out of quads is rebuilt rather than moved. Laying the
        // hero's bar out again at the new size leaves it in the scene, in the
        // right place, unculled — and not on the screen: something in what the
        // engine holds about a piece of GUI does not survive the window changing
        // size under it. The menu has always been rebuilt for the same reason, and
        // is the reason it was the one thing that came back looking right.
        var armed = heroPanel.armedKey();
        heroPanel.destroy();
        heroPanel = new HeroPanel(assetManager, guiFont,
                fontOrDefault(visuals.getMenuStyle().titleFont()), guiNode, width,
                visuals.getPanelSkin(), visuals.getRangeLook(), visuals.getIconLook());
        heroPanel.arm(armed);
        placeMinimap();
        menu.destroy();
        menu = buildMenu(width, height);
        switch (screen) {
            case MENU -> showMainMenu();
            case PAUSED -> showPauseMenu();
            case SETTINGS -> showSettingsMenu(settingsReturn);
            case PLAYING -> menu.hide();
        }
    }

    /**
     * Keep the cameras and the HUD on the size the window really is.
     *
     * <p>Asked of the window rather than waited for, because the order the engine
     * reports a fullscreen switch in is not dependable — the sizes arrive, but
     * with a nothing-sized one among them and not always last. Reading the
     * framebuffer is the one answer that is true at the moment it is asked, and
     * comparing it with what the cameras have costs two integers a frame.
     */
    private void followTheWindowSize() {
        if (hud == null || cam == null) {
            return;
        }
        int width = cam.getWidth();
        int height = cam.getHeight();
        if (getContext() instanceof com.jme3.system.lwjgl.LwjglWindow display
                && display.getFramebufferWidth() > 0 && display.getFramebufferHeight() > 0) {
            width = display.getFramebufferWidth();
            height = display.getFramebufferHeight();
        }
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width != cam.getWidth() || height != cam.getHeight()) {
            super.reshape(width, height); // the cameras, which the engine may have missed
        }
        if (laidOutFor != width * 100_000 + height) {
            layOutForTheWindow(width, height);
        }
    }

    // ---- themes ----

    /**
     * The look the game last named, as it wrote it: a theme and a variation.
     */
    private String currentLook;
    private Visuals.Theme currentTheme;
    private Tileset currentKit;

    /**
     * Whether the look has moved since the world was last built.
     *
     * <p>Noted rather than acted on, so that a change of look and a change of
     * floor -- which arrive together -- are one rebuild and not two. It also
     * covers the first frame of all: the world is built when the window opens,
     * before any snapshot has said what it is made of.
     */
    private boolean lookChanged;

    /**
     * Take up the look the game says this floor wears.
     *
     * <p>The game names one of the themes it registered at launch, in the status
     * channel, and that is the whole of the conversation: the client holds every
     * look it was given and is told which of them is current. A game with no
     * themes never gets here, and one whose look has not changed does nothing.
     *
     * <p>Everything a theme changes is presentation — the kit the floor is built
     * from, the colour of the dark, and what some creatures are drawn as. None of
     * it reaches the simulation, which is what lets a floor's look be decided from
     * a seed without the world noticing.
     */
    private void adoptTheLookTheGameNames() {
        if (!visuals.hasThemes()) {
            return;
        }
        var named = lookNamedInStatus();
        if (java.util.Objects.equals(named, currentLook)) {
            return;
        }
        currentLook = named;
        // Whatever the game wrote is the name of a theme it registered. The
        // client does not take the name apart: a look is one thing to it, and
        // whether the game builds that name out of a theme and a variation is
        // the game's own arrangement.
        currentTheme = visuals.getTheme(named);
        currentKit = currentTheme == null ? null : currentTheme.getTiles();
        if (currentTheme != null && currentTheme.getFogTint() != null && fogMap != null) {
            // The tint lives in the fog picture's own texels rather than in a
            // material, so a new colour is the next repaint — which the new floor
            // is about to ask for anyway.
            var tint = new ColorRGBA(
                    ((currentTheme.getFogTint() >> 16) & 0xFF) / 255f,
                    ((currentTheme.getFogTint() >> 8) & 0xFF) / 255f,
                    (currentTheme.getFogTint() & 0xFF) / 255f, 1f);
            fogMap.tint(tint);
            viewPort.setBackgroundColor(tint);
        }
        lookChanged = true;
        // Every creature is drawn again, because some of them are drawn
        // differently now and the ones that are not cost a node each.
        for (var node : unitNodes.values()) {
            node.root.removeFromParent();
        }
        unitNodes.clear();
    }

    /**
     * The {@code look=} field of the status line, or {@code null}.
     */
    private String lookNamedInStatus() {
        if (snapshot == null || !snapshot.hasStatus()) {
            return null;
        }
        var status = snapshot.status();
        int at = status.indexOf("|look=");
        if (at < 0) {
            return null;
        }
        var field = status.substring(at + "|look=".length());
        int end = field.indexOf('|');
        return end < 0 ? field : field.substring(0, end);
    }

    /**
     * How a creature is drawn: what the current theme says, or what the game said
     * about it outside any theme.
     * The kit in force: the theme's, or the one the game started with.
     */
    private Tileset activeKit() {
        return currentKit != null ? currentKit : visuals.getTiles();
    }

    private Visuals.UnitVisual visualFor(String templateName) {
        var themed = currentTheme == null ? null : currentTheme.of(templateName);
        return themed != null ? themed : visuals.of(templateName);
    }

    /**
     * Hand the burning things to the terrain shader.
     *
     * <p>The terrain is the one surface in the scene that does not read jME's
     * light list — it carries its own sun and its own ambient, because for most of
     * its life those were the only lights there were and a shader that spoke the
     * whole lighting protocol would have been a copy of it for a scene with one
     * light in it. That was a good trade until something burning flew down a
     * corridor: creatures lit up, drawn as they are with the engine's lighting,
     * and the floor under them did not.
     *
     * <p>So the lights go across as four pairs of vectors, every frame, to every
     * material built from that shader. An unused slot goes across black, which
     * costs the arithmetic and contributes nothing — cheaper than a branch, and
     * the same cost every frame, which is the property worth having.
     */
    private void carryTheLightsToTheStone() {
        if (fogged.isEmpty()) {
            return;
        }
        var lights = effects.allLights();
        for (int i = 0; i < TERRAIN_LIGHTS; i++) {
            var light = i < lights.size() ? lights.get(i) : null;
            var colour = light == null ? ColorRGBA.BlackNoAlpha : light.getColor();
            var at = light == null ? Vector3f.ZERO : light.getPosition();
            float radius = light == null ? 0f : light.getRadius();
            // Written into the slots the materials already hold rather than
            // replaced, so a material made before this frame is looking at the
            // same two arrays as one made after it.
            terrainLightColours[i].set(colour.r, colour.g, colour.b, 1f);
            terrainLightPlaces[i].set(at.x, at.y, at.z, radius <= 0f ? 0f : 1f / radius);
        }
        for (var material : fogged) {
            material.setParam("PointLightColours",
                    com.jme3.shader.VarType.Vector4Array, terrainLightColours);
            material.setParam("PointLightPositions",
                    com.jme3.shader.VarType.Vector4Array, terrainLightPlaces);
        }
    }

    /**
     * As many as the terrain shader declares; see {@code FoggedTerrain.frag}.
     */
    private static final int TERRAIN_LIGHTS = 8;
    /**
     * The two arrays handed to every terrain material, filled with darkness to
     * begin with.
     *
     * <p>Filled rather than empty, and set on a material the moment it is made:
     * an array uniform the shader reads and nothing ever wrote is a different
     * thing on every driver, and the one frame it would go wrong on is the first.
     */
    private final com.jme3.math.Vector4f[] terrainLightColours = darkness();
    private final com.jme3.math.Vector4f[] terrainLightPlaces = darkness();

    private static com.jme3.math.Vector4f[] darkness() {
        var slots = new com.jme3.math.Vector4f[TERRAIN_LIGHTS];
        java.util.Arrays.setAll(slots, i -> new com.jme3.math.Vector4f());
        return slots;
    }

    /**
     * Build (or rebuild) the ground and rocks for the world as it stands now.
     */
    private void buildTerrain() {
        builtFrom = game.getTerrain();
        // A new world is a world with nothing burning in it yet.
        if (layered != null) {
            layered.clear();
        }
        if (hitFlash != null) {
            hitFlash.clear();
        }
        effects.clear();
        if (skillEffects != null) {
            skillEffects.clear();
        }
        terrain.rebuild(builtFrom, currentKit);
        if (visuals.getDiscoveryTemplate() == null) {
            return;
        }
        fogMap.resize(builtFrom);
        // The one thing in a fogged material that a new world changes.
        for (var material : fogged) {
            material.setVector2("FogSize", fogMap.worldSize());
        }
        // A new floor is a floor nobody has walked: memory belongs to one world,
        // and carrying it over would open rooms in a dungeon nobody has entered.
        if (discovery == null) {
            discovery = new Discovery(builtFrom, visuals.getFog());
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
    private void syncDiscovery(float tpf) {
        if (discovery == null) {
            return;
        }
        // Re-read when the eyes change, not only the first time. A game may say
        // late — or differently — whose sight opens the map: the dungeon lets the
        // player choose who he is, and a knight sees a shorter way than an archer.
        // Cached once, the second hero would walk about inside the first one's
        // circle, which looks like the fog being wrong rather than stale.
        var eyes = visuals.getDiscoveryTemplate();
        if (discoveryRadius < 0f || !eyes.equals(discoveryEyes)) {
            var template = game.getLogic() == null ? null : game.getLogic().findTemplate(eyes);
            if (template == null) {
                return; // the game has not finished booting; the map stays black
            }
            discoveryRadius = template.getVisionRange();
            discoveryEyes = eyes;
        }
        discovery.reveal(snapshot.units(), game.getLocalPlayerIndex(), discoveryRadius,
                visuals.getDiscoveryTemplate());
        // What is open is decided above; how it is drawn eases toward that, so the
        // edge sweeps rather than switching. Seconds, not frames.
        discovery.soften(tpf);
        // The picture is what the player actually sees the dark as; the terrain is
        // only told what is so far behind it that drawing it is waste.
        fogMap.update(discovery);
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
        if (game.getTerrain() == builtFrom && !lookChanged) {
            return;
        }
        // A rebuilt world has nothing burning in it, and whoever is in it has just
        // arrived: even a hero whose id and floor match the last run's, and even when
        // the floor's look catches up with the floor a moment late -- which rebuilds
        // it again, and puts out the light he arrived in.
        runMoments.forget();
        lookChanged = false;
        buildTerrain();
        rebuildMinimapTerrain();
        noises.forget(); // a new floor; nothing about the last one is news
        orderMarkers.clear(); // orders given in the old world mean nothing here
        chevrons.clear();
        attackFlash.clear();
        hitNumbers.clear();
        unitBars.clear();
        healthWatch.forget(); // new creatures, new ids; nobody here was just hit
        camera.requestOwnUnit(); // his units are somewhere else entirely now
        // And the hero he had selected is not this floor's hero. See
        // keepHisOwnSelected: his skills need him picked out.
        selected.clear();
        findHimInTheNewWorld = true;
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

    /**
     * Every control this client has bound, so that one thing can be listened to.
     *
     * <p>★ Kept because forgetting is invisible. A mapping added and left out of
     * the listener's list is a key that is bound, reaches nothing and says
     * nothing about it: Ctrl shipped that way and simply did not exist, and there
     * is no error, no warning and nothing on screen to notice. The list is now
     * built by the binding rather than typed out beside it, so the two cannot
     * come apart.
     */
    private final List<String> bound = new ArrayList<>();

    private void installInput() {
        bound.clear();
        map("Select", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        map("Order", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        bindKeys("PanUp", KeyInput.KEY_W, KeyInput.KEY_UP);
        bindKeys("PanLeft", KeyInput.KEY_A, KeyInput.KEY_LEFT);
        bindKeys("PanDown", KeyInput.KEY_S, KeyInput.KEY_DOWN);
        bindKeys("PanRight", KeyInput.KEY_D, KeyInput.KEY_RIGHT);
        map("Shift", new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
        // Held rather than pressed, like Shift above: it changes what the NEXT
        // key means rather than meaning anything itself.
        map("Ctrl", new KeyTrigger(KeyInput.KEY_LCONTROL), new KeyTrigger(KeyInput.KEY_RCONTROL));
        bindKeys("Halt", KeyInput.KEY_H);
        bindKeys("Pause", KeyInput.KEY_P);
        map("Fullscreen", new KeyTrigger(KeyInput.KEY_F11));
        map("Deselect", new KeyTrigger(KeyInput.KEY_ESCAPE));
        map("Take", new KeyTrigger(KeyInput.KEY_RETURN),
                new KeyTrigger(KeyInput.KEY_NUMPADENTER), new KeyTrigger(KeyInput.KEY_SPACE));
        map("ZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        map("ZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        int[] buildKeys = {KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3, KeyInput.KEY_4,
                KeyInput.KEY_5, KeyInput.KEY_6, KeyInput.KEY_7, KeyInput.KEY_8, KeyInput.KEY_9};
        for (int i = 0; i < buildKeys.length; i++) {
            map("Build" + (i + 1), new KeyTrigger(buildKeys[i]));
        }

        var shiftHeld = new boolean[1];
        boolean[] ctrlHeld = this.ctrlHeld;
        ActionListener actions = (name, pressed, tpf) -> {
            switch (name) {
                case "Shift" -> shiftHeld[0] = pressed;
                case "Ctrl" -> ctrlHeld[0] = pressed;
                // The same four keys steer the camera in the world and the
                // choice on a menu. They cannot do both at once, and which one
                // they are doing is never ambiguous: a menu is up or it is not.
                case "PanUp" -> {
                    if (steer(pressed, menu::up)) {
                        pan[0] = pressed;
                    }
                }
                case "PanLeft" -> {
                    if (steer(pressed, menu::left)) {
                        pan[1] = pressed;
                    }
                }
                case "PanDown" -> {
                    if (steer(pressed, menu::down)) {
                        pan[2] = pressed;
                    }
                }
                case "PanRight" -> {
                    if (steer(pressed, menu::right)) {
                        pan[3] = pressed;
                    }
                }
                case "Take" -> {
                    if (!pressed) {
                        break;
                    }
                    if (menu.isVisible()) {
                        if (menu.enter()) {
                            noises.moment("menu_click", timer.getTimeInSeconds());
                        }
                    } else if (screen == Screen.PLAYING) {
                        // Back to the hero, wherever the pan keys have got to. The
                        // same one-shot request a new run makes, so the camera is
                        // his again the moment it lands.
                        camera.requestOwnUnit();
                    }
                }
                case "Select" -> {
                    if (menu.isVisible()) {
                        if (!pressed) {
                            menu.release(); // letting go lets go of the slider
                        } else if (menu.click(inputManager.getCursorPosition())) {
                            noises.moment("menu_click", timer.getTimeInSeconds());
                        }
                    } else if (screen == Screen.PLAYING) {
                        if (!pressed && holdingASkill()) {
                            letGoOfHeldSkill(true); // he pressed its slot; this is the cast
                            break;
                        }
                        if (pressed && clickedASkillSlot()) {
                            // The bar took it; nothing else may have it.
                            break;
                        }
                        if (holdingASkill()) {
                            break; // the mouse has no part in a skill being held
                        }
                        if (pressed && arming != null) {
                            // This click belongs to the armed key, not to selection.
                            // The release is swallowed with it, or letting go would
                            // end a drag that never began.
                            aimArmedKey();
                        } else if (pressed) {
                            beginDrag();
                        } else if (dragFrom != null) {
                            endDrag(shiftHeld[0]);
                        }
                    }
                }
                case "Order" -> {
                    if (!pressed || screen != Screen.PLAYING) {
                        break;
                    }
                    var over = inputManager.getCursorPosition();
                    if (arming == null && heroPanel.contains(over.x, over.y)) {
                        break; // a right-click on the bar is not an order to the world
                    }
                    if (arming != null) {
                        disarm(); // second thoughts, the way a right-click always means
                    } else {
                        order();
                    }
                }
                case "Halt" -> {
                    if (pressed && screen == Screen.PLAYING) {
                        haltSelected();
                    }
                }
                case "Fullscreen" -> {
                    if (pressed) {
                        toggleFullscreen();
                    }
                }
                case "Pause" -> {
                    // Set here rather than queued for the simulation: a paused
                    // engine does not step, so a task asking it to resume would
                    // never be reached and the pause could not be lifted.
                    if (pressed && screen == Screen.PLAYING) {
                        setSimulationPaused(!game.getLogic().isGamePaused());
                    }
                }
                case "Deselect" -> {
                    if (!pressed) {
                        break;
                    }
                    switch (screen) {
                        case PLAYING -> {
                            // Escape opens the menu. It used to clear the selection
                            // first, so a player who had a hero selected -- which
                            // is a player who is playing -- had to press it twice
                            // to stop. Letting go of a unit is what clicking the
                            // floor is for; Escape is for leaving.
                            if (arming != null) {
                                disarm(); // he is mid-aim, and that is one keypress deep
                            } else {
                                showPauseMenu();
                            }
                        }
                        case PAUSED -> {
                            if (!menu.escape()) {
                                resumeGame();
                            }
                        }
                        case SETTINGS -> {
                            // The open list first, then the screen. Escaping the
                            // screen throws away what was not saved -- and puts
                            // back what had already been applied, which is the
                            // promise a Cancel button makes and Escape is the
                            // same answer.
                            if (!menu.escape()) {
                                undoSettings();
                            }
                        }
                        case MENU -> {
                        }
                    }
                }
                default -> {
                    if (screen != Screen.PLAYING) {
                        return;
                    }
                    if (!pressed) {
                        // Letting go of a skill that was drawn while it was held.
                        // See SkillRange.castOnRelease.
                        if (name.startsWith(HOTKEY) && arming != null
                                && arming == name.charAt(HOTKEY.length())) {
                            letGoOfHeldSkill(false);
                        }
                        return;
                    }
                    if (name.startsWith("Build")) {
                        int index = Integer.parseInt(name.substring(5)) - 1;
                        queueBuild(index);
                    } else if (name.startsWith(HOTKEY)) {
                        char letter = name.charAt(HOTKEY.length());
                        if (ctrlHeld[0]) {
                            // ★ Ctrl and the letter spends a level on that skill,
                            // which is Warcraft's arrangement and is worth copying
                            // for the reason it was chosen there: the hand is
                            // already on the letter. A modifier rather than a key
                            // of its own means nothing new to learn and nothing to
                            // press by accident -- Q alone still casts, and always
                            // will.
                            raiseSkill(letter);
                        } else {
                            pressHotkey(letter, false);
                        }
                    }
                }
            }
        };
        for (var key : hotkeys.all().keySet()) {
            int code = Hotkeys.codeOf(key);
            if (code < 0) {
                continue;
            }
            String mapping = HOTKEY + key;
            inputManager.deleteMapping(mapping);
            map(mapping, new KeyTrigger(code));
        }
        // Everything that was bound, in one call. Not a list typed out here: see
        // the note on `bound`.
        inputManager.addListener(actions, bound.toArray(new String[0]));

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
        map(mapping, triggers);
    }

    /**
     * Bind a control, and remember that it is one the listener has to hear.
     */
    private void map(String mapping, com.jme3.input.controls.Trigger... triggers) {
        inputManager.addMapping(mapping, triggers);
        bound.add(mapping);
    }

    /**
     * The unit under the mouse cursor, or {@code null}.
     * The unit under the cursor, found by the space it occupies rather than by its
     * triangles.
     *
     * <p>Triangle-accurate picking stopped working the day the creatures got
     * models. A skinned mesh is deformed on the graphics card; the copy this side
     * keeps is the pose it was modelled in, so the shape the ray is tested against
     * is not the shape on screen. Clicking a monster missed it and the click fell
     * through to the floor behind — which reads as an order given to the wrong
     * place rather than as a click that hit nothing.
     *
     * <p>Its bounding box does not care how a thing is posed. It is a more
     * generous target than the mesh, which is the right way to be wrong: in a
     * dungeon the cost of a click landing on the monster you meant is nothing, and
     * the cost of it landing on the floor behind him is a hero who walks into a
     * room instead of shooting into it.
     *
     * <p>Only things the game says are selectable, so an arrow crossing in front
     * of a monster cannot be clicked instead of it.
     */
    private UnitNode pickUnit() {
        var click = inputManager.getCursorPosition();
        var near = cam.getWorldCoordinates(new Vector2f(click.x, click.y), 0f);
        var ray = new Ray(near, cam.getWorldCoordinates(new Vector2f(click.x, click.y), 1f)
                .subtract(near).normalizeLocal());
        UnitNode nearest = null;
        float closest = Float.MAX_VALUE;
        for (var node : unitNodes.values()) {
            if (node.view == null || !node.view.selectable() || node.body == null) {
                continue;
            }
            var bound = node.body.getWorldBound();
            if (bound == null || !bound.intersects(ray)) {
                continue;
            }
            // Nearest to the camera wins, so clicking a monster standing in front
            // of another picks the one you can see.
            float away = bound.getCenter().distance(near);
            if (away < closest) {
                closest = away;
                nearest = node;
            }
        }
        return nearest;
    }

    /**
     * Where the mouse is pointing at the ground.
     *
     * <p>One way of answering this, used by everything that asks — orders,
     * markers, aimed skills, the minimap's outline. There were two: this one met
     * the plane at zero and was what every click actually went through, while the
     * one that knew about storeys was only being asked by the outline. So the
     * click kept landing a pace beyond the cursor upstairs however carefully the
     * other was fixed.
     */
    private Vector3f pickGround() {
        var click = inputManager.getCursorPosition();
        return groundUnder(click.x, click.y);
    }

    /**
     * Click a unit to select it — anyone's, not only the player's own.
     *
     * <p>Clicking something that is not his is <em>inspecting</em> it: the panel
     * describes whatever is selected, and being able to read a monster's health
     * and what it hits for is most of what makes a dungeon's bar worth looking at.
     * Nothing can be ordered with it — see {@link #selectedIds} — so an enemy in
     * the selection is a thing being looked at rather than a thing being
     * commanded.
     *
     * <p>Which is also why it never joins a group: shift-clicking a skeleton onto
     * a selection of his own units would make "what is selected" mean two
     * different things at once, and every order after it would have to decide
     * which half it applied to. One enemy on its own, or his own units.
     */
    private void select(boolean add) {
        var hit = pickUnit();
        boolean mine = hit != null && hit.view.playerIndex() == game.getLocalPlayerIndex();
        if (!add || !mine) {
            selected.clear();
        }
        if (hit != null && hit.view.selectable()) {
            boolean isNew = selected.add(hit.view.id());
            if (isNew && mine) {
                noises.moment("vo.select", timer.getTimeInSeconds());
            }
        }
    }

    // ---- drag selection ----

    /**
     * Where the left button went down, or {@code null} when it is not down.
     */
    private Vector2f dragFrom;

    /**
     * The game key that has been pressed and is waiting to be pointed at something.
     */
    private Character arming;

    /**
     * Whether it was armed by clicking its slot rather than by pressing its key.
     *
     * <p>Only a held skill cares, and it cares a great deal: what casts it is
     * letting go of the same thing that armed it. Without this, arming with the
     * keyboard and then releasing a mouse button that happened to be down would
     * fire it.
     */
    private boolean armedByMouse;

    /**
     * Draws how far an armed skill reaches. See RangeRings.
     */
    private RangeRings rangeRings;

    /**
     * A game key was pressed.
     *
     * <p>Most act at once. One that needs pointing at something instead goes
     * quiet and waits for the next click — pressed again it thinks better of it,
     * and so does a right-click or escape. All any of this may do in the end is
     * post a command: the render thread has no business in the simulation.
     */
    private void pressHotkey(char key, boolean byMouse) {
        var binding = hotkeys.all().get(key);
        if (binding == null) {
            return;
        }
        var range = visuals.getSkillRange(key);
        if (heroPanel.isAnOrder(key) && !heroPanel.ordersAreHis()) {
            // The four orders belong to whatever is selected, and what is selected
            // is not his -- so there is nothing to give the order to. The panel
            // draws them dim for the same reason; this is the half that stops the
            // key going round the outside of the dim button.
            return;
        }
        if (range != null && !heroPanel.isAnOrder(key) && selectedIds().isEmpty()) {
            // A skill is something one of his creatures does, so it needs that
            // creature picked out — pressing Q with nothing selected, or with a
            // skeleton selected to look at it, used to cast anyway. The orders
            // beside them are deliberately not like this: they are the player
            // talking to whoever he owns, and are meant to work with an empty
            // selection.
            //
            // ★ Which is why the order has to be asked about by name. It used to
            // be enough that only a skill had a reach to draw — until the attack
            // order was given one too, and it is worth having: the ring says how
            // far he hits from, which is the question a player is asking when he
            // reaches for that key.
            return;
        }
        if (binding.aim() == Hotkeys.Aim.NOW) {
            if (range != null && range.castOnRelease()) {
                // Held rather than spent. A skill with nothing to point at used to
                // go off the instant the key went down, which left the player no
                // way to ask how far it reaches except by spending it. Holding
                // shows him; letting go casts, so a tap is still a cast and all
                // that has changed is that looking is now free.
                if (heroPanel.readyToCast(key)) {
                    arm(key, byMouse);
                } else {
                    sayWhyNot(key);
                }
                return;
            }
            disarm();
            hotkeys.pressNow(game, key);
            return;
        }
        if (arming != null && arming == key) {
            disarm();
            return;
        }
        // Refusing here rather than arming and going quiet: a slot the panel is
        // already showing as spent would take a click and do nothing with it.
        if (!heroPanel.readyToCast(key)) {
            sayWhyNot(key);
            return;
        }
        arm(key, byMouse);
    }

    /**
     * Answer a key that was refused, when the answer is not already on its slot.
     *
     * <p>Only for want of mana. A slot that is reloading is swept and counting,
     * and one he has not bought says so across its face -- both are answers he is
     * already looking at. Being broke is written on a bar at the other end of the
     * panel, so it is the one refusal that needs saying where he pressed.
     */
    private void sayWhyNot(char key) {
        if (heroPanel.refusedForMana(key)) {
            heroPanel.denyForMana(timer.getTimeInSeconds());
        }
    }

    /**
     * Arm a key: the panel lights its slot, and its reach is drawn on the floor.
     */
    private void arm(char key, boolean byMouse) {
        arming = key;
        armedByMouse = byMouse;
        heroPanel.arm(key);
    }

    /**
     * Letting go of a held skill casts it.
     *
     * <p>Whichever way it was armed and only that way: the key it was pressed
     * with, or the mouse, if he pressed its slot on the bar. Otherwise releasing
     * the mouse after arming from the keyboard would fire it before the player had
     * aimed his eyes, never mind his hand.
     */
    private void letGoOfHeldSkill(boolean byMouse) {
        if (!holdingASkill() || armedByMouse != byMouse) {
            return;
        }
        var binding = hotkeys.all().get(arming);
        disarm();
        if (binding != null) {
            binding.run().accept(game, null);
        }
    }

    /**
     * Whether what is armed is drawn while held and cast when let go.
     */
    private boolean holdingASkill() {
        if (arming == null) {
            return false;
        }
        var range = visuals.getSkillRange(arming);
        return range != null && range.castOnRelease();
    }

    private void disarm() {
        arming = null;
        armedByMouse = false;
        heroPanel.arm(null);
        rangeRings.hide();
    }

    /**
     * The click that completes an armed key. Clicking nothing usable drops it,
     * rather than leaving the player armed and wondering why nothing happened.
     */
    private void aimArmedKey() {
        // ★ Read BEFORE disarming, which nulls it. The clamp below asks what the
        // armed key reaches, and asking after the disarm got null every time --
        // so the mark it was written to put in the right place was left at the
        // cursor, which is the one place it was written not to be.
        char aimed = arming == null ? 0 : arming;
        var binding = hotkeys.all().get(arming);
        disarm();
        if (binding == null) {
            return;
        }
        boolean mayBeACreature = binding.aim() == Hotkeys.Aim.UNIT
                || binding.aim() == Hotkeys.Aim.UNIT_OR_GROUND;
        if (mayBeACreature) {
            var unit = pickUnit();
            if (unit != null) {
                binding.run().accept(game, new Hotkeys.Aimed(unit.view.id(), null));
                markOrder(unit.view.x(), unit.view.y(), unit.view.id(),
                        OrderMarkers.Kind.ATTACK);
                noises.moment("vo.attack", timer.getTimeInSeconds());
                return;
            }
            if (binding.aim() == Hotkeys.Aim.UNIT) {
                return; // it had to be a creature, and the click found none
            }
        }
        var ground = pickGround();
        if (ground == null) {
            return;
        }
        if (binding.aim() == Hotkeys.Aim.OPEN_GROUND && !isOpenAndSeen(ground)) {
            return; // stone, or somewhere he has never been — nothing is sent
        }
        var spot = new Coord3D(ground.x, ground.z, 0f);
        binding.run().accept(game, new Hotkeys.Aimed(0, spot));
        // ★ The mark goes where the SKILL goes, not where the mouse was. A click
        // past the ring is read by the simulation as "as far that way as I can"
        // -- it clamps to the edge -- so a mark left at the cursor would stand a
        // long way from where the thing actually landed and the picture would be
        // a lie in exactly the case the player most needs it to be true.
        //
        // An ORDER is not clamped, and the difference is real rather than an
        // exception: the ring on an order says how far he HITS from, which is
        // worth knowing and is not a fence. He may be sent to fight his way
        // across the whole floor.
        var landing = heroPanel.isAnOrder(aimed) ? spot : clampedToReach(aimed, spot);
        markOrder(landing.x(), landing.y(), binding.aim() == Hotkeys.Aim.UNIT_OR_GROUND
                ? OrderMarkers.Kind.ATTACK_MOVE : OrderMarkers.Kind.MOVE);
    }

    /**
     * A spot pulled back to the edge of what the armed skill can reach.
     *
     * <p>The same rule {@code SkillBook.withinReach} applies on the far side,
     * drawn from the same {@link SkillRange} the ring on the floor is drawn from
     * -- so the mark, the ring and the cast all agree about where "as far that
     * way as I can" is. A skill with no ring, or a click already inside it, is
     * left exactly where it was.
     */
    private Coord3D clampedToReach(char key, Coord3D wanted) {
        var range = key == 0 ? null : visuals.getSkillRange(key);
        var hero = whereHisHeroIs();
        if (range == null || hero == null) {
            return wanted;
        }
        return range.within(hero, wanted);
    }

    /**
     * Whether that spot is somewhere the player could stand and has already seen.
     *
     * <p>Asked here rather than of the simulation because both halves are the
     * client's to answer. The map's shape it has; what the player has <em>seen</em>
     * of it only it has — that is a fact about this screen, not about the world,
     * and the simulation would be wrong to hold it.
     *
     * <p>A game with no discovery at all has seen everything, which is the right
     * answer rather than a special case: with the whole map on screen there is no
     * such thing as aiming into the dark.
     */
    private boolean isOpenAndSeen(Vector3f ground) {
        var grid = game.getTerrain();
        if (grid == null) {
            return true;
        }
        int cx = grid.toCellX(new Coord3D(ground.x, ground.z, 0f));
        int cy = grid.toCellY(new Coord3D(ground.x, ground.z, 0f));
        if (!grid.inBounds(cx, cy) || grid.isTerrainBlocked(cx, cy)) {
            return false;
        }
        return discovery == null || discovery.stateAt(cx, cy) != Discovery.State.UNSEEN;
    }

    /**
     * Whether that spot is somewhere he could put his feet, as far as the player
     * knows.
     *
     * <p>A different question from {@link #isOpenAndSeen}, and the difference is
     * the whole of what each is for. A skill has to be <em>aimed</em>, so it may
     * only be sent somewhere the player has actually seen. A walking order is not
     * aimed — it may be given anywhere on the map, the dark included, and how far
     * he gets is the simulation's business. See {@link Destination}.
     *
     * <p>So the dark reads as open here. Not as a kindness: a pointer that turned
     * away over undiscovered stone would be reading the map out to the player
     * through the shape of his own cursor.
     *
     * <p>What it does say no to is a place he plainly cannot stand — a wall, or
     * the barrel in the middle of the room. That is a warning and not a veto: the
     * click still goes, and he walks as near it as the floor allows.
     */
    private boolean couldStandThere(Vector3f ground) {
        var grid = game.getTerrain();
        if (grid == null) {
            return true;
        }
        var at = new Coord3D(ground.x, ground.z, 0f);
        int cx = grid.toCellX(at);
        int cy = grid.toCellY(at);
        if (!grid.inBounds(cx, cy)) {
            return false;
        }
        if (discovery != null && discovery.stateAt(cx, cy) == Discovery.State.UNSEEN) {
            return true; // unknown ground is not known to be bad
        }
        return !grid.isBlocked(cx, cy);
    }

    /**
     * A click on a skill slot casts it, exactly as pressing its key would.
     *
     * <p>Down the same road, deliberately: the click ends in {@link #pressHotkey},
     * so a skill that needs pointing at something arms and waits for the next
     * click just as the keyboard's does, and a skill that does not goes off at
     * once. Anything else would be a second copy of the rules for casting, and the
     * two would drift.
     *
     * <p>A slot that is cooling or locked eats the click and does nothing, which
     * is what a stone slot with a shadow over it looks like it should do.
     * Spend a level on a slot, from the keyboard.
     *
     * <p>Refused in silence when there is nothing to spend or nowhere to spend
     * it, and that is a requirement rather than an omission: Ctrl is held for
     * other reasons, and a key that made a noise every time the player happened
     * to be holding it would be a key he learns to dread. The panel is asked
     * rather than the simulation because the panel is what is showing him a
     * badge -- if there is no badge, the press means nothing and says nothing.
     *
     * @return whether the bar took the click
     */
    private void raiseSkill(char key) {
        if (!heroPanel.canRaise(key)) {
            return;
        }
        hotkeys.raiseSkill(game, key);
        noises.moment("power_taken", (float) timer.getTimeInSeconds());
    }

    private boolean clickedASkillSlot() {
        var cursor = inputManager.getCursorPosition();
        // The badge before the slot, because it hangs over the slot's own corner
        // and a click there means the badge. It is only ever there when a point
        // may go into that slot, so the corner of a socket with nothing to buy
        // arms the skill exactly as it always did.
        var raising = heroPanel.badgeAt(cursor.x, cursor.y);
        if (raising != null) {
            hotkeys.raiseSkill(game, raising);
            noises.moment("power_taken", timer.getTimeInSeconds());
            return true;
        }
        var key = heroPanel.slotAt(cursor.x, cursor.y);
        if (key != null) {
            pressHotkey(key, true);
            return true;
        }
        // The rest of the bar swallows clicks too. Without this, clicking the
        // portrait sends the hero walking to wherever the bar happens to cover.
        return heroPanel.contains(cursor.x, cursor.y);
    }

    /**
     * The unit the game was last told about, so it is only told when it changes.
     * Whether a new world is still waiting for its hero to be picked out.
     */
    private boolean findHimInTheNewWorld;

    /**
     * Select his own unit once, when a world he has just arrived in produces one.
     *
     * <p>The other half of "a skill needs its caster selected" — see
     * {@link #pressHotkey}. On its own that rule is a trap rather than a rule: a
     * run begins with nothing selected and every new floor hands him a <em>new</em>
     * hero whose id the old selection does not name, so his keys would quietly
     * stop working until he remembered to click himself, on every floor, with no
     * hint that clicking was what was wanted.
     *
     * <p><b>Once per world, and only then.</b> Anything more would be a client
     * that refuses to be told: pressing escape to clear the selection is
     * deliberate, and so is clicking a skeleton to read its card — the panel's
     * empty state and its creature card both exist because the player asked for
     * them, and a frame later this would have taken both away.
     */
    private void keepHisOwnSelected() {
        if (!findHimInTheNewWorld || screen != Screen.PLAYING) {
            return;
        }
        for (var view : snapshot.units()) {
            if (view.playerIndex() == game.getLocalPlayerIndex() && view.selectable()) {
                selected.clear();
                selected.add(view.id());
                findHimInTheNewWorld = false;
                return;
            }
        }
    }

    private int watching = -1;

    /**
     * Say which single unit the player has picked out, when that changes.
     *
     * <p>Selection belongs here and the simulation has none — but what a creature
     * is worth does not belong here and the simulation is the only thing that
     * knows it. A monster's damage is its template times what this floor
     * multiplies by, and this side has never seen a template; reading one across
     * the thread that is mutating it would be worse than not knowing.
     *
     * <p>So the fact travels the way every other fact from this side does: as a
     * command, on a frame boundary. Sent on change rather than every frame,
     * because forty a second of "still that one" is a command stream nobody can
     * read and a replay nobody can search.
     *
     * <p>One unit only. A panel that describes a creature cannot describe nine,
     * and {@code -1} — nothing, or a whole box of them — puts it back to
     * describing the player's own.
     */
    private void tellTheGameWhatHeIsLookingAt() {
        if (!hotkeys.watches()) {
            return;
        }
        int single = selected.size() == 1 ? selected.iterator().next() : -1;
        if (single != watching) {
            watching = single;
            hotkeys.watch(game, single);
        }
    }

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

    /**
     * Every unit in the snapshot, projected to where it is drawn on screen.
     */
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

    /**
     * Redraw the box while the button is held, as an outline over the world.
     */
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
            markOrder(enemy.view.x(), enemy.view.y(), enemy.view.id(), OrderMarkers.Kind.ATTACK);
            // His own orders only. In a game with more than one player at it,
            // each hears his own hero answer and nobody hears anyone else's.
            noises.moment("vo.attack", (float) timer.getTimeInSeconds());
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
        // Pointed at somewhere he cannot get to, he goes as near it as he can
        // rather than nowhere at all. See Destination.
        var target = asNearAsTheyCanGet(ground);
        // Spread the group around the click so they don't all fight for one spot.
        var spots = Formation.spread(units.size(), target.x(), target.y());
        for (int i = 0; i < units.size(); i++) {
            game.postCommand(new GameMessage.MoveTo(local, List.of(units.get(i)),
                    new Coord3D(spots.get(i).x(), spots.get(i).y(), 0f)));
        }
        // One mark for the order, not one per unit: it was a single decision. And
        // put WHERE HE CLICKED rather than where they will end up. The mark is an
        // answer to the click -- "that, understood" -- and moving it to the place
        // they can reach answers a question the player did not ask and hides the
        // one thing he wants to see, which is whether he clicked where he meant
        // to. How far they actually get is theirs to work out on the way.
        markOrder(ground.x, ground.z, OrderMarkers.Kind.MOVE);
        // And one answer, for the same reason. His own orders only: in a game
        // with more than one player at it each hears his own hero and nobody
        // hears anyone else's.
        noises.moment("vo.move", (float) timer.getTimeInSeconds());
    }

    /**
     * The click, pulled back to the nearest place the selected units can reach.
     *
     * <p>Measured from the first of them. A dungeon holds one hero, and even with
     * a party behind him the map is the same map — a click into stone is out of
     * everyone's reach, and which of them worked that out does not show.
     */
    private Coord3D asNearAsTheyCanGet(Vector3f ground) {
        var wanted = new Coord3D(ground.x, ground.z, 0f);
        for (var view : snapshot.units()) {
            if (selected.contains(view.id())) {
                return Destination.asCloseAsHeCanGet(game.getTerrain(),
                        new Coord3D(view.x(), view.y(), 0f), wanted);
            }
        }
        return wanted;
    }

    /**
     * Acknowledge an order where the player clicked. Presentation only.
     *
     * <p>The tick goes here rather than beside each of the four places an order
     * can be given, so a mark and its sound cannot come apart — they are one
     * acknowledgement, and a game that names no {@code order_mark} sound simply
     * gets the silent half.
     */
    private void markOrder(float worldX, float worldY, OrderMarkers.Kind kind) {
        markOrder(worldX, worldY, OrderMarkers.NOBODY, kind);
    }

    private void markOrder(float worldX, float worldY, int unitId, OrderMarkers.Kind kind) {
        float now = timer.getTimeInSeconds();
        orderMarkers.add(worldX, worldY, unitId, kind, now);
        noises.moment("order_mark", now);
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

    /**
     * The single selected own production structure, or {@code null}.
     */
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

    /**
     * Queue the {@code index}-th entry of the selected factory's build menu.
     */
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

    /**
     * The selected units an order may be given to: his own, and only his own.
     *
     * <p>The selection may hold something that is not his -- clicking a monster
     * is how its health and its damage are read off the bar -- and an order must
     * never reach it. Filtered here rather than at the click, so that inspecting
     * a creature and commanding one stay two different questions with two
     * different answers.
     */
    private List<ObjectId> selectedIds() {
        return snapshot.units().stream()
                .filter(u -> selected.contains(u.id())
                        && u.playerIndex() == game.getLocalPlayerIndex())
                .map(u -> new ObjectId(u.id()))
                .toList();
    }

    // ---- per-frame sync ----

    @Override
    public void simpleUpdate(float tpf) {
        followTheWindowSize();
        showOnlyWhilePlaying();
        snapshot = game.getSnapshot();
        // Before every early return below, not after them. The menu and the
        // loading screen are screens too, and a pointer that only appears once
        // the world does leaves the player clicking Play with the operating
        // system's arrow -- which is the first thing he sees of the game.
        showTheRightPointer();
        if (menu.isVisible()) {
            // A held slider follows the hand every frame; nothing else does, and
            // it makes no noise about it — a knob being pulled would be forty
            // clicks a second.
            if (menu.isDragging()) {
                menu.drag(inputManager.getCursorPosition());
            } else if (menu.hover(inputManager.getCursorPosition())) {
                noises.moment("menu_hover", (float) timer.getTimeInSeconds());
            }
        }
        if (screen == Screen.MENU) {
            hud.setText("");
            buildMenu.setText("");
            return; // the world starts when the player presses Play
        }
        if (screen == Screen.LOADING) {
            advanceLoadingArt();
            return; // and it waits until there is art to start it with
        }
        // Before the world is rebuilt, not after: a new floor and the look it
        // wears arrive in the same snapshot, and terrain built from the last
        // floor's kit would be a floor of the wrong stone until the next one.
        adoptTheLookTheGameNames();
        refreshWorldIfChanged(); // a new run lays out a new world; redraw it
        syncDiscovery(tpf);
        camera.focusOnOwnUnit(snapshot.units(), game.getLocalPlayerIndex());
        updateCamera(tpf);
        // Before the units, not after: a death has to take the body out of the
        // live list before anything decides it merely vanished. It also means a
        // shot lights its muzzle on the frame it was fired rather than the next.
        handleEvents();
        // What this frame is worth hearing. Reads the same snapshot everything
        // else does and writes nothing back -- see GameSounds.
        noises.frame(snapshot, game.getLocalPlayerIndex(),
                (float) timer.getTimeInSeconds());
        keepHisOwnSelected();
        tellTheGameWhatHeIsLookingAt();
        syncUnits();
        showUnitBars();
        // After the units, because a burst lit this frame has to reach the stone
        // this frame — the terrain reads its lights off a material parameter, not
        // out of the scene, so nothing tells it but this.
        effects.update(tpf);
        skillEffects.update(tpf, this::floorHeightAt, this::whereUnitIs);
        hitFlash.update(tpf);
        layered.update(tpf, cam);
        carryTheLightsToTheStone();
        reapTheDead();
        syncMinimap();
        syncViewportOutline();
        syncDragRectangle();
        syncOrderMarkers();
        noises.status(heroPanel.reading(), (float) timer.getTimeInSeconds());
        // Before the bar is drawn, because the bar decides between the live
        // picture and the drawing and has to be told which it has.
        drawThePortrait(tpf);
        updateHud();
        updateBanner();
        updateHover();
        placeMinimap();
    }

    /**
     * Keep the panel's frame filled with whoever is selected, alive.
     *
     * <p>The level it is told is the one the bar read <em>last</em> frame, and
     * that is deliberate rather than sloppy. Nothing here draws a level; the only
     * use it is put to is noticing that it changed, and a change noticed one frame
     * late is still noticed exactly once.
     *
     * <p>A menu is a different room and the portrait stops with everything else in
     * it.
     */
    private void drawThePortrait(float tpf) {
        var reading = heroPanel.reading();
        portrait.show(portraitSubject(), reading == null ? "" : reading.rank());
        portrait.update(tpf, screen == Screen.PLAYING && !menu.isVisible()
                && !snapshot.paused());
        heroPanel.live(portrait.texture());
    }

    /**
     * The one creature the bar is describing, or nobody.
     *
     * <p>The same rule the card itself is built from — see
     * {@link #tellTheGameWhatHeIsLookingAt} — because the frame is part of the
     * card. A portrait that showed the hero while the name beside it said
     * "Skeleton" would be the panel disagreeing with itself.
     */
    private UnitView portraitSubject() {
        if (screen != Screen.PLAYING || selected.size() != 1) {
            return null;
        }
        int only = selected.iterator().next();
        for (var view : snapshot.units()) {
            if (view.id() == only) {
                return view;
            }
        }
        return null;
    }

    /**
     * Freeze or resume the simulation.
     *
     * <p>Set directly rather than queued for the simulation thread, and it has to
     * be: a paused engine does not step, so a task queued for the next frame would
     * never run and the game could not be started again. The flag is a plain
     * boolean, it takes no part in the checksum, and pausing changes when frames
     * happen rather than what is in them.
     */
    private void setSimulationPaused(boolean paused) {
        var logic = game.getLogic();
        if (logic != null && logic.isGamePaused() != paused) {
            logic.setGamePaused(paused);
        }
    }

    /**
     * Hide what belongs to playing while something else is on the screen.
     *
     * <p>A menu is a different room. The minimap, the list of controls and the
     * hero's own bar are all answers to questions a player is asking of the world,
     * and none of them is being asked while he is deciding whether to quit — they
     * are just left over, sitting behind the menu, saying the client forgot to
     * put them away.
     */
    /**
     * Send a direction to the menu, or let it through to the camera.
     *
     * @return whether the world should have it — false once a menu has taken it,
     * which also stops the camera being left drifting when a menu opens
     * mid-press and the release never reaches it
     */
    private boolean steer(boolean pressed, Runnable move) {
        if (!menu.isVisible()) {
            return true;
        }
        if (pressed) {
            move.run();
            noises.moment("menu_hover", (float) timer.getTimeInSeconds());
        }
        return false;
    }

    private void showOnlyWhilePlaying() {
        var hint = screen == Screen.PLAYING
                ? Spatial.CullHint.Never : Spatial.CullHint.Always;
        minimapNode.setCullHint(hint);
        if (controlsHint != null) {
            controlsHint.setCullHint(hint);
        }
        if (heroPanel != null && screen != Screen.PLAYING) {
            heroPanel.hide();
            // And the frame stops being redrawn with it. Said here rather than
            // beside the rest of the portrait's work because the menu and the
            // loading screen leave this method before reaching that, and a
            // viewport nobody switches off goes on drawing.
            portrait.rest();
        }
    }

    private Cursors cursors;

    /**
     * Say what the pointer is over, by changing what it looks like.
     *
     * <p>Half of an RTS's controls are "the right-click means something different
     * here", and the pointer is the only place that can be said <em>before</em>
     * the click. A white arrow says the same thing over a skeleton as over a wall.
     *
     * <p>The order matters and is the order of what overrides what. A menu is on
     * top of everything. An armed skill is the next loudest thing on screen — it
     * is the reason the next click will not do what a click usually does — and
     * while one is waiting the pointer says only whether this is somewhere it can
     * go. Then the bar, which takes clicks and gives no orders. Then the world:
     * something to attack, one of his own, or ground.
     */
    private void showTheRightPointer() {
        if (cursors == null || !cursors.any()) {
            return;
        }
        cursors.show(Cursors.situationFor(whatThePointerIsOver()));
    }

    /**
     * The six facts about the screen the choice is made from.
     */
    private Cursors.Over whatThePointerIsOver() {
        boolean playing = screen == Screen.PLAYING && !menu.isVisible();
        if (!playing) {
            return new Cursors.Over(false, null, false, false, false, false);
        }
        var at = inputManager.getCursorPosition();
        var binding = arming == null ? null : hotkeys.all().get(arming);
        var armed = binding == null ? null : binding.aim();
        // Armed is not aiming -- see Hotkeys.Aim.needsPointing. A skill that goes
        // off round him is held to be looked at, and while it is the pointer goes
        // on answering the ordinary questions.
        boolean aiming = armed != null && armed.needsPointing();
        boolean canReach;
        if (aiming) {
            canReach = armed != Hotkeys.Aim.OPEN_GROUND
                    || isOpenAndSeen(groundUnder(at.x, at.y));
        } else {
            canReach = couldStandThere(groundUnder(at.x, at.y));
        }
        var over = aiming ? null : pickUnit();
        return new Cursors.Over(true, armed, canReach,
                heroPanel.contains(at.x, at.y) || overTheMinimap(at),
                over != null, over != null && over.view.playerIndex() == game.getLocalPlayerIndex());
    }

    /**
     * Whether a screen point is inside the minimap, which takes its own clicks.
     */
    private boolean overTheMinimap(Vector2f at) {
        float wide = minimap.widthPixels() * minimapScale;
        float tall = minimap.heightPixels() * minimapScale;
        return at.x >= minimapX && at.x <= minimapX + wide
                && at.y >= minimapY && at.y <= minimapY + tall;
    }

    /**
     * Light whatever skill slot the cursor is resting on.
     */
    private void updateHover() {
        var cursor = inputManager.getCursorPosition();
        heroPanel.hover(screen == Screen.PLAYING ? heroPanel.slotAt(cursor.x, cursor.y) : null);
    }

    private void updateCamera(float tpf) {
        float speed = camera.panSpeed() * tpf;
        float dx = (pan[3] ? speed : 0f) - (pan[1] ? speed : 0f);
        float dz = (pan[2] ? speed : 0f) - (pan[0] ? speed : 0f);
        var shove = edgeShove(speed);
        camera.panBy(dx + shove.x, dz + shove.y);

        // The camera rides up with the ground under what it is looking at, or a
        // room on the second storey is a room seen from underneath. Eased rather
        // than stepped: a storey is a whole body's height and arriving at one in a
        // single frame reads as the world jumping.
        float wanted = floorHeightAt(camera.targetX(), camera.targetZ());
        cameraHeight += (wanted - cameraHeight) * Math.min(1f, tpf * 6f);

        float distance = camera.distance();
        var target = new Vector3f(camera.targetX(), cameraHeight, camera.targetZ());
        // The knock from whatever just landed, added to where the camera was
        // going to be rather than replacing it -- so the shake never argues with
        // the pan, the zoom or the storey it is riding up.
        //
        // Only the EYE is moved and not what it is looking at. Shaking both is a
        // camera being carried about; shaking one is the ground being hit, which
        // is what is actually happening. It also keeps the thing the player is
        // watching in the middle of the screen while the world rattles round it.
        var knock = skillEffects == null ? Vector3f.ZERO : skillEffects.shakeNow();
        cam.setLocation(target.add(new Vector3f(0, distance * 0.82f, distance * 0.57f))
                .addLocal(knock));
        cam.lookAt(target, Vector3f.UNIT_Y);
    }

    /**
     * How far the cursor shoves the camera this frame, resting against an edge.
     *
     * <p>Added to whatever the keys are doing rather than replacing it, so both
     * hands work at once. It is measured as a share of the keys' own speed, which
     * is itself a function of how far out the camera is — so shoving at full zoom
     * moves the same amount of <em>screen</em> as shoving up close.
     *
     * <p>Nothing happens while a menu is up: that is a moment when the cursor is
     * being used for something else, and a view that
     * drifted out from under a choice would be its own kind of bug.
     *
     * <p>All four edges are the screen's own, the bottom one included. The bar
     * covers the foot of the screen, but the band that scrolls is a few pixels
     * deep and the skill slots sit well above it — so pushing the cursor to the
     * very bottom still means "look further down", which is where a hand goes for
     * it, and reaching for a slot still means reaching for a slot.
     */
    private Vector2f edgeShove(float keySpeed) {
        var wanted = visuals.getEdgeScroll();
        var still = new Vector2f(0f, 0f);
        if (!wanted.wanted() || screen != Screen.PLAYING || menu.isVisible()) {
            return still;
        }
        var cursor = inputManager.getCursorPosition();
        float margin = wanted.marginPixels();
        float speed = keySpeed * wanted.speedPercent() / 100f;
        float width = cam.getWidth();
        float height = cam.getHeight();
        // jME's cursor y grows upward, so the top of the screen is the far side of
        // the map — the same direction the up key sends the camera.
        return new Vector2f(
                (cursor.x >= width - margin ? speed : 0f) - (cursor.x <= margin ? speed : 0f),
                (cursor.y <= margin ? speed : 0f)
                        - (cursor.y >= height - margin ? speed : 0f));
    }

    /**
     * The bar over each creature that is being drawn.
     *
     * <p>Off the nodes rather than off the snapshot, so that whatever is not on
     * screen has already been left out once: {@code syncUnits} drops anything
     * behind a wall, and the snapshot itself was built from what the player can
     * see. What is left is filtered again by the edge of the screen inside
     * {@link UnitBars}, which is the one of the three that the camera decides.
     */
    private void showUnitBars() {
        var standing = new ArrayList<UnitBars.Standing>(unitNodes.size());
        int mine = game.getLocalPlayerIndex();
        for (var node : unitNodes.values()) {
            if (node.view == null || node.view.maxHealth() <= 0f) {
                continue; // a prop or an arrow: nothing with a life to show
            }
            standing.add(new UnitBars.Standing(node.view, node.barTop,
                    node.root.getWorldTranslation().y,
                    node.view.playerIndex() == mine));
        }
        barReading = UnitBarReading.read(snapshot.status());
        unitBars.update(cam, standing, barReading);
    }

    private void syncUnits() {
        var seen = new HashSet<Integer>();
        for (var view : snapshot.units()) {
            if (outOfSight(view)) {
                continue; // behind a wall: not drawn, and taken away if it was
            }
            seen.add(view.id());
            boolean isNew = !unitNodes.containsKey(view.id());
            var node = unitNodes.computeIfAbsent(view.id(), id -> createUnitNode(view));
            updateUnitNode(node, view);
            var look = visualFor(view.templateName());
            if (isNew) {
                effects.appeared(view.id(), look, node.root,
                        node.root.getWorldTranslation().clone(), cam.getLocation());
                layered.flying(view.id(), look.effect, node.root,
                        new Vector3f(look.effectForward, look.yOffset, 0f), cam);
            } else if (look.effect != null) {
                effects.moved(view.id(), look, node.root);
            }
        }
        var gone = unitNodes.entrySet().iterator();
        while (gone.hasNext()) {
            var entry = gone.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            // Its old recipe's light and sparks go back in the box whether it arrived
            // or merely walked out of the light: that pool must not have to know
            // which. Its layers do -- see Landing.
            effects.gone(entry.getKey());
            layered.grounded(entry.getKey());
            var node = entry.getValue();
            var at = node.root.getLocalTranslation();
            if (Landing.arrived(node.view, game.getLocalPlayerIndex(),
                    discovery == null || discovery.canSee(at.x, at.z))) {
                // ★ Here, and not on ObjectDied. An arrow, a fireball and the mark a
                // meteor falls on have no body, and the core removes a thing with no
                // body without ever posting that it died. Gone is the moment it
                // ended; whether it ended in a strike is only known once this
                // frame's blows are read -- see burstWhatStruck.
                endedShots.add(new Landing.Gone(entry.getKey(),
                        visualFor(node.view.templateName()).effect,
                        node.view.playerIndex() == game.getLocalPlayerIndex(),
                        node.bornX, node.bornZ, node.bornAt, at.x, at.z,
                        timer.getTimeInSeconds()));
            }
            node.root.removeFromParent();
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
     * Whether something is standing where the player cannot see it.
     *
     * <p>The engine's fog is a circle and does not know about walls, so a monster
     * in the next room is in the snapshot and would be drawn standing in the dark.
     * Discovery already knows which cells he can see into; this asks it the same
     * question about a creature.
     *
     * <p>His own things are always drawn — they are his, and an arrow of his own
     * that vanished mid-flight would be a bug rather than a fog.
     * How high the floor stands at a place on the map.
     *
     * <p>Read off the same grid the simulation walks on rather than sent in the
     * snapshot. The snapshot carries what a unit is and where it stands on the
     * ground plane; how far up that ground is, is a fact about the map, and the
     * client already has the map — it draws the walls from it.
     */
    private float floorHeightAt(float worldX, float worldY) {
        var terrain = game.getTerrain();
        return terrain == null ? 0f
                : terrain.groundHeight(new uz.duke.core.math.Coord3D(worldX, worldY, 0f));
    }

    private boolean outOfSight(UnitView view) {
        return discovery != null
                && view.playerIndex() != game.getLocalPlayerIndex()
                && !discovery.canSee(view.x(), view.y());
    }

    private void handleEvents() {
        if (snapshot == lastEventedSnapshot) {
            return; // the sim has not produced a new frame; do not replay this one
        }
        lastEventedSnapshot = snapshot;
        killedThisFrame.clear();
        skillsCastThisFrame(snapshot);
        for (var event : snapshot.events()) {
            if (event instanceof ObjectDied died) {
                killedThisFrame.add(new HealthWatch.Death(died.object().value(),
                        died.position().x(), died.position().y(), died.playerIndex()));
                // Told outright, and it has to be: a dead creature is gone from
                // the next snapshot, so by the time the frame could notice there
                // would be nothing left to play a death on.
                portrait.died(died.object().value());
                var where = new Vector3f(died.position().x(), 0f, died.position().y());
                playSound(visualFor(died.templateName()).dieSound, where);
                // "Destroyed" is what this event means, so it is the arrow landing
                // as much as the monster falling — and the arrow is the one that
                // wants a burst where it struck.
                var look = visualFor(died.templateName());
                effects.landed(look.effect,
                        where.setY(floorHeightAt(where.x, where.z) + look.yOffset),
                        cam.getLocation());
                // And a ring, if the thing that just stopped existing asked for
                // one. Nothing does but a meteor arriving -- a skeleton's look is
                // its eye sockets and has no SHOCKWAVE in it -- so this costs
                // every other death in the game precisely nothing, and it means
                // the biggest blast in the game is drawn by the thing that
                // ARRIVED rather than by the man who called for it a second and a
                // half ago and may well be dead.
                skillEffects.cast(look.effect,
                        new uz.duke.core.math.Coord3D(died.position().x(),
                                died.position().y(), 0f),
                        cam.getLocation(), 0f, this::floorHeightAt);
                layOut(died.object().value());
            } else if (event instanceof WeaponFired fired) {
                var node = unitNodes.get(fired.shooter().value());
                if (node != null) {
                    playSound(visualFor(node.view.templateName()).fireSound,
                            node.root.getLocalTranslation());
                    // The swing, on the frame the weapon let go. Nothing else in
                    // the snapshot says when that was.
                    playOnce(node, visualFor(node.view.templateName()).attackAnim);
                }
            }
        }
    }

    /**
     * What was cast this frame, out of the status line.
     *
     * <p><b>Why the status line and not an event.</b> The engine's event channel
     * carries {@code WeaponFired}, and every cast already posts one -- but it says
     * only WHO fired and where, which is all a muzzle flash needs and not nearly
     * enough to tell a nova from a blink. The event lives in {@code rts}, which is
     * shared with games that have never heard of a skill, so widening it there to
     * carry a key would be the dungeon's idea put somewhere it does not belong.
     *
     * <p>The status line is the game talking to its own client and is exactly the
     * seam for this: a string the engine carries and never reads. It already
     * carries the things a dungeon has that an RTS does not -- the note, which
     * stone the floor is built from -- and this is one more.
     *
     * <p>Read from the snapshot rather than from the panel, because the panel is
     * about what is SELECTED: the moment the player clicks a skeleton his own
     * card is gone and so would his effects be. The frame number is what keeps
     * one cast drawn once, since the same line is sent again until something
     * changes.
     *
     * <p>Format is {@code cast=<recipe>,<frame>,<x>,<y>,<radius>,<whose>}. A line
     * that does not parse is dropped in silence: a missing ring is the cheapest
     * possible failure, and an effect that threw would take the frame with it.
     */
    int skillsCastThisFrame(WorldSnapshot snapshot) {
        if (skillEffects == null || !snapshot.hasStatus()) {
            return 0;
        }
        var casts = SkillEffects.castsIn(snapshot.status(), lastCastFrameDrawn);
        for (var cast : casts) {
            lastCastFrameDrawn = Math.max(lastCastFrameDrawn, cast.frame());
            skillEffects.cast(cast.look(), cast.at(), cam.getLocation(), cast.radius(),
                    cast.on(), this::floorHeightAt);
            // And the caster is seen doing it, if the game named a gesture for
            // this recipe. Nothing did until a mage needed both hands.
            castGesture(unitNodes.get(cast.by()), visuals.getCastAnim(cast.look()));
        }
        drawLayers(casts);
        return casts.size();
    }

    /**
     * The layered half of this frame's casts.
     *
     * <p>A run and a blink arrive as two marks of one look in one frame -- the spot
     * he left, which belongs to the floor, and the spot he arrived at, which is his
     * -- and a layer laid along the run or flashed at both ends needs them as ONE
     * moment with two ends. Everything else is one mark and one moment, facing the
     * way from whoever cast it to where it went off.
     */
    private void drawLayers(List<SkillEffects.Cast> casts) {
        if (layered == null) {
            return;
        }
        for (int i = 0; i < casts.size(); i++) {
            var cast = casts.get(i);
            var next = i + 1 < casts.size() ? casts.get(i + 1) : null;
            var spot = new Vector3f(cast.at().x(), 0f, cast.at().y());
            boolean run = next != null && next.look().equals(cast.look())
                    && next.frame() == cast.frame() && cast.on() == SkillEffects.NOBODY
                    && next.on() != SkillEffects.NOBODY;
            if (run) {
                var to = new Vector3f(next.at().x(), 0f, next.at().y());
                layered.cast(cast.look(), new LayeredEffects.Moment(to, spot, to,
                        to.x - spot.x, to.z - spot.z, next.radius(), next.on(), next.by()), cam);
                i++;
                continue;
            }
            float facingX = 0f;
            float facingZ = 0f;
            var caster = unitNodes.get(cast.by());
            if (caster != null) {
                var from = caster.root.getLocalTranslation();
                facingX = spot.x - from.x;
                facingZ = spot.z - from.z;
            }
            layered.cast(cast.look(), new LayeredEffects.Moment(spot, null, null, facingX,
                    facingZ, cast.radius(), cast.on(), cast.by()), cam);
        }
    }

    /**
     * What the layered effects may know about the world: the floor, where a
     * creature stands, who is whose enemy, and what the player can see.
     *
     * <p>Read off the nodes and the snapshot the player is already looking at,
     * never out of the simulation -- so an effect can learn nothing the screen does
     * not already show.
     */
    private LayeredEffects.Surroundings surroundings() {
        return new LayeredEffects.Surroundings() {
            @Override
            public float floorAt(float x, float z) {
                return floorHeightAt(x, z);
            }

            @Override
            public Vector3f whereIs(int unitId) {
                var node = unitNodes.get(unitId);
                if (node == null) {
                    return null;
                }
                var at = node.root.getLocalTranslation();
                return new Vector3f(at.x, floorHeightAt(at.x, at.z), at.z);
            }

            @Override
            public int[] enemiesNear(float x, float z, float radius, int caster) {
                int side = game.getLocalPlayerIndex();
                for (var view : snapshot.units()) {
                    if (view.id() == caster) {
                        side = view.playerIndex();
                        break;
                    }
                }
                final int his = side;
                return snapshot.units().stream()
                        .filter(view -> view.playerIndex() != his && view.maxHealth() > 0f
                                && !view.structure()
                                && (view.x() - x) * (view.x() - x)
                                + (view.y() - z) * (view.y() - z) <= radius * radius)
                        .mapToInt(UnitView::id)
                        .toArray();
            }

            @Override
            public boolean canSee(float x, float z) {
                return discovery == null || discovery.canSee(x, z);
            }
        };
    }

    /**
     * Where a creature is standing this frame, or {@code null} if it is no longer
     * on the field.
     *
     * <p>What a mark laid on somebody asks every frame so that it can keep itself
     * under him. Taken off the node the player is looking at rather than out of
     * the snapshot, so the disc and the model can never disagree about where he
     * is — whatever ends up between the two later, a walk smoothed across frames
     * among it. Only where on the floor: the ring reads the height itself.
     */
    private Coord3D whereUnitIs(int id) {
        var node = unitNodes.get(id);
        if (node == null) {
            return null;
        }
        var where = node.root.getLocalTranslation();
        return new Coord3D(where.x, where.z, 0f);
    }

    /**
     * Lift one named mesh out of a loaded model and stand it on its own.
     *
     * <p>Taken out of its parents entirely rather than hidden among them: the
     * piece is wanted at the origin, pointing along its own axis, and it arrives
     * carrying wherever it sat on the character — in a hand, at an angle, at head
     * height. Dropping that transform is what makes an arrow an arrow rather than
     * an arrow held by an invisible archer.
     *
     * <p>A part that is not there leaves the whole model, which is visibly wrong
     * and says so in the log — better than a unit that quietly disappears.
     */
    private Spatial partOf(Spatial model, String partName, String path) {
        var found = new Spatial[1];
        model.depthFirstTraversal(spatial -> {
            if (found[0] == null && spatial instanceof Geometry
                    && partName.equals(spatial.getName())) {
                found[0] = spatial;
            }
        });
        if (found[0] == null) {
            warnOnce(path + "#" + partName, "model part");
            return model;
        }
        var piece = found[0];
        piece.removeFromParent();
        piece.setLocalTransform(new com.jme3.math.Transform()); // its own origin
        var holder = new Node(partName);
        holder.attachChild(piece);
        return holder;
    }

    /**
     * A body playing out its death, and when to take it away.
     */
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
        var clipName = visualFor(node.view.templateName()).dieAnim;
        var clip = clipName == null || node.composer == null
                ? null : node.composer.getAnimClip(clipName);
        if (clip == null) {
            node.root.removeFromParent(); // nothing to play; it simply goes
            return;
        }
        // The trappings of something alive. The bar over its head goes without
        // being told: it is drawn from the snapshot, and a corpse is not in one.
        node.ring.removeFromParent();

        // Once through, not looping: a corpse that gets up and dies again forever
        // is worse than one that never fell over.
        node.composer.setCurrentAction(clipName, AnimComposer.DEFAULT_LAYER, false);
        dying.add(new Dying(node.root,
                (float) (timer.getTimeInSeconds() + clip.getLength() + CORPSE_LINGER)));
    }

    /**
     * How long a body stays after its death animation has played out.
     */
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
        var visual = visualFor(view.templateName());
        var node = new UnitNode();
        node.root = new Node("unit-" + view.id());
        node.bornX = view.x();
        node.bornZ = view.y();
        node.bornAt = timer.getTimeInSeconds();
        node.root.setUserData("unitId", view.id());

        Spatial body = buildBody(visual, java.util.List.of());
        if (body != null) {
            node.composer = findControl(body, AnimComposer.class);
            var legacy = findControl(body, AnimControl.class);
            if (node.composer == null && legacy != null) {
                node.legacyChannel = legacy.createChannel();
            }
            snap(node.composer, visual.attackAnim);
            snap(node.composer, visual.hurtAnim);
        }
        if (body == null) {
            // A fireball has no model and should not be given the capsule-with-a-
            // gun-barrel every other modelless thing gets. Its effect is its body.
            // It comes back already standing where its own fire burns from, and
            // at the width the recipe asked for: the scale on this visual is for
            // the plain shape a modelless thing falls back to, and an orb is
            // given its size in world units.
            body = effects.bodyFor(visual);
        }
        if (body == null) {
            body = buildPrimitive(view);
            // Size applies to a shape as much as to a model. Without this a game
            // could say how big a thing is only by shipping art for it, and
            // anything small — a dart, a spark, a rat — came out unit-sized.
            body.setLocalScale(visual.scale);
            body.setLocalTranslation(0, visual.yOffset, 0);
        }
        node.body = body;
        node.root.attachChild(body);

        node.ring = buildSelectionRing(view);
        node.root.attachChild(node.ring);
        node.barTop = heightOf(body, view) + visuals.getUnitBars().lift();

        unitsNode.attachChild(node.root);
        return node;
    }

    /**
     * A creature's body: its model, its skin, its tint, whatever it carries, and
     * the clips it was named — or {@code null} for a thing that has no model, or
     * whose model would not load.
     *
     * <p>Pulled out of {@link #createUnitNode} because the hero panel's live
     * portrait wants the same body built the same way, and a second copy of this
     * paragraph would be a portrait of somebody slightly else — a different tint,
     * an unarmed archer, a model that is lit and one that is not. Everything about
     * what a creature <em>is</em> is here; where it stands and what it is doing
     * belong to whoever asked for it.
     *
     * <p>Every material is its own, never shared with another body. A skinned
     * material holds the pose of the skeleton driving it, so two things on one
     * material both stand in whichever pose was written last — which is why the
     * portrait needs a body of its own rather than the one in the world.
     *
     * @param alsoWanted clips beyond the creature's own five. A unit asks for
     *                   none; the portrait asks for the ones only it plays, and nothing else
     *                   would ever fetch them off the library
     */
    private Spatial buildBody(Visuals.UnitVisual visual, java.util.Collection<String> alsoWanted) {
        if (visual.modelPath == null) {
            return null;
        }
        try {
            var body = assetManager.loadModel(visual.modelPath);
            if (visual.modelPart != null) {
                body = partOf(body, visual.modelPart, visual.modelPath);
            }
            body.setLocalScale(visual.scale);
            body.setLocalTranslation(0, visual.yOffset, 0);
            body.setLocalRotation(new Quaternion().fromAngles(0,
                    FastMath.DEG_TO_RAD * visual.facingDegrees, 0));
            dressModel(body, visual);
            // After being dressed, because dressing replaces every material on
            // it and would put the fire out again.
            effects.lightThePartsOf(body, visual.effect);
            putInHisHand(body, visual);
            borrowAnimations(body, visual, alsoWanted);
            return body;
        } catch (RuntimeException e) {
            warnOnce(visual.modelPath, "model");
            return null;
        }
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
        com.jme3.texture.Texture skin = null;
        if (visual.texturePath != null) {
            try {
                skin = assetManager.loadTexture(visual.texturePath);
            } catch (RuntimeException e) {
                warnOnce(visual.texturePath, "texture");
            }
        }
        var tint = visual.tint == null ? ColorRGBA.White : toColor(visual.tint);
        var named = skin;
        body.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                geometry.setMaterial(creatureMaterial(
                        named != null ? named : skinOf(geometry.getMaterial()), tint));
            }
        });
    }

    /**
     * Hang the thing this unit carries on the bone that is there to carry it.
     *
     * <p>jME builds the attachment node itself and parents it under the joint, so
     * the weapon is moved by the same clip that moves the hand and there is
     * nothing to keep in step each frame. What it does <em>not</em> do is dress
     * it: a weapon loads with the same PBR material the body does, and this client
     * cannot light one, so an undressed bow is a black bow. Nor does it know which
     * way round the model goes — see {@link Visuals.UnitVisual#heldTurn}.
     *
     * <p>A bone the rig does not have is a warning rather than a failure. The unit
     * is drawn empty-handed, which is a thing you can see and think about; a
     * missing model is not worth a black screen.
     */
    private void putInHisHand(Spatial body, Visuals.UnitVisual visual) {
        if (visual.carried.isEmpty()) {
            return;
        }
        var skin = findControl(body, com.jme3.anim.SkinningControl.class);
        if (skin == null) {
            return; // nothing rigged to hang anything on
        }
        for (var one : visual.carried) {
            hang(skin, one, visual);
        }
    }

    /**
     * One carried thing, on one bone. Each failure is its own and loses only itself.
     */
    private void hang(com.jme3.anim.SkinningControl skin, Visuals.UnitVisual.Carried one,
                      Visuals.UnitVisual visual) {
        if (one.path == null || one.bone == null) {
            return;
        }
        if (skin.getArmature().getJoint(one.bone) == null) {
            warnOnce(one.path + "@" + one.bone, "bone");
            return;
        }
        Spatial held;
        try {
            held = assetManager.loadModel(one.path);
        } catch (RuntimeException e) {
            warnOnce(one.path, "model");
            return;
        }
        held.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                geometry.setMaterial(creatureMaterial(skinOf(geometry.getMaterial()),
                        visual.tint == null ? ColorRGBA.White : toColor(visual.tint)));
            }
        });
        held.setLocalScale(one.scale);
        held.setLocalRotation(new Quaternion().fromAngles(
                FastMath.DEG_TO_RAD * one.pitch,
                FastMath.DEG_TO_RAD * one.yaw,
                FastMath.DEG_TO_RAD * one.roll));
        held.setLocalTranslation(one.x, one.y, one.z);
        skin.getAttachmentsNode(one.bone).attachChild(held);
    }

    /**
     * The colour map a loaded material already holds.
     *
     * <p>Creatures come with a skin beside them and the game names it; kit props
     * do not — a barrel carries its colours inside its own glTF, pointing at the
     * pack's atlas. Naming no texture therefore means <em>the one it came
     * with</em>, not <em>none</em>. Reading it as none is how a barrel and a bare
     * tree came out plain white: the right shape, lit correctly, wearing the
     * tint over nothing at all.
     */
    private com.jme3.texture.Texture skinOf(Material material) {
        if (material == null) {
            return null;
        }
        for (var param : material.getParams()) {
            if (param.getValue() instanceof com.jme3.texture.Texture texture) {
                return texture;
            }
        }
        return null;
    }

    /**
     * How much of its own colour a creature keeps where no light reaches it --
     * which is also where a hit flash starts from and fades back to.
     */
    private static final float CREATURE_AMBIENT = 0.55f;

    /**
     * Flat lighting over a kit's own colour map, tinted.
     */
    private Material creatureMaterial(com.jme3.texture.Texture skin, ColorRGBA tint) {
        var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", tint);
        material.setColor("Ambient", tint.mult(CREATURE_AMBIENT));
        material.setColor("Specular", ColorRGBA.Black); // kit art has no highlights
        material.setFloat("Shininess", 1f);
        if (skin != null) {
            material.setTexture("DiffuseMap", skin);
        }
        return material;
    }

    /**
     * How long the old clip is faded out under a new one, on the legacy path.
     */
    private static final float BLEND_SECONDS = 0.2f;

    /**
     * How long a <em>blow</em> is allowed to fade in over what came before.
     *
     * <p>jME cross-fades into every clip and gives it four tenths of a second to
     * do it, which is right for settling from a walk into a stand and quite wrong
     * for a punch: the clip is one second long, so nearly half of it was spent
     * arriving, and the strike had no snap in it at all. A blow is a moment and
     * has to start on the frame it starts.
     */
    private static final float SNAP_SECONDS = 0.08f;

    /**
     * Let this clip start at once rather than easing in over the default fade.
     */
    private static void snap(AnimComposer composer, String clipName) {
        if (composer == null || clipName == null || composer.getAnimClip(clipName) == null) {
            return;
        }
        if (composer.action(clipName) instanceof BlendableAction blendable) {
            blendable.setTransitionLength(SNAP_SECONDS);
        }
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
    private void borrowAnimations(Spatial body, Visuals.UnitVisual visual,
                                  java.util.Collection<String> alsoWanted) {
        var wanted = new java.util.ArrayList<String>();
        for (var name : new String[]{visual.idleAnim, visual.walkAnim,
                visual.attackAnim, visual.hurtAnim, visual.dieAnim}) {
            if (name != null) {
                wanted.add(name);
            }
        }
        for (var name : alsoWanted) {
            if (name != null && !wanted.contains(name)) {
                wanted.add(name);
            }
        }
        // And whatever else the game said this one needs -- a gesture it casts
        // with, say. A clip nobody asked for by name is not copied, and then
        // nothing can play it: see Visuals.UnitVisual.alsoAnimation.
        for (var name : visual.otherAnims) {
            if (!wanted.contains(name)) {
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

    /**
     * Animation libraries, loaded once each and shared by everything that borrows.
     */
    private final Map<String, Spatial> animationLibraries =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * What colour to draw this unit: the type's own if the game gave it one,
     * otherwise its player's.
     *
     * <p>Player colour says whose it is, which is all an RTS usually needs. A
     * game fielding several kinds of thing per side needs to say what it is too,
     * and with no models yet there is nothing else to say it with.
     */
    private ColorRGBA colourOf(UnitView view) {
        var own = visualFor(view.templateName()).colour;
        return toColor(own != null ? own : game.getColor(view.playerIndex()));
    }

    /**
     * A clean placeholder in the player's colour when no model is assigned.
     */
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

    /**
     * The ring under a selected unit — green for his own, red for anything else.
     *
     * <p>Not decoration. Something that is not his can be selected, because that
     * is how its health and its damage are read off the bar, and it cannot be
     * ordered. A green ring under a skeleton would promise a command that the
     * next right-click is not going to give.
     */
    private Geometry buildSelectionRing(UnitView view) {
        float radius = view.structure() ? 4.2f : 2.2f;
        var ring = new Geometry("ring", new Cylinder(2, 24, radius, 0.06f, true));
        ring.setMaterial(unshaded(view.playerIndex() == game.getLocalPlayerIndex()
                ? new ColorRGBA(0.4f, 1f, 0.4f, 1f) : new ColorRGBA(1f, 0.36f, 0.3f, 1f)));
        ring.rotate(FastMath.HALF_PI, 0, 0);
        ring.setLocalTranslation(0, 0.06f, 0);
        ring.setCullHint(Spatial.CullHint.Always);
        return ring;
    }

    /**
     * A colour that is drawn over the scene rather than into it.
     */
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

    private void updateUnitNode(UnitNode node, UnitView view) {
        node.view = view;
        node.root.setLocalTranslation(view.x(), floorHeightAt(view.x(), view.y()), view.y());
        node.root.setLocalRotation(new Quaternion().fromAngles(0, -view.orientation(), 0));

        node.ring.setCullHint(selected.contains(view.id())
                ? Spatial.CullHint.Never : Spatial.CullHint.Always);

        flinch(node, view);
        animate(node, view);
    }

    /**
     * A short flinch when something has taken health off it.
     *
     * <p>Read off the health in the snapshot rather than off whatever fired,
     * which is not the same moment and sometimes not the same thing at all: an
     * arrow is loosed a third of a second before it arrives, and splash, a spell
     * or a floor's own trap have no shot to listen for. A drop between two
     * snapshots is not a guess about what happened — it is the thing that
     * happened, and it is already in hand.
     *
     * <p>Without it a monster absorbs a blow with no sign that it landed, and a
     * fight reads as two things standing near each other.
     */
    private void flinch(UnitNode node, UnitView view) {
        float before = node.lastHealth;
        node.lastHealth = view.health();
        // Nothing on the first sight of a unit, and nothing on the blow that
        // killed it: that one has a death to play and this would talk over it.
        if (!Float.isNaN(before) && view.health() < before && view.health() > 0f) {
            playOnce(node, visualFor(view.templateName()).hurtAnim);
        }
    }

    /**
     * Choose the clip that matches what the unit is doing.
     *
     * <p>What a unit is <em>doing</em> is only ever moving or standing. Attacking
     * used to be here as a third state and it was never a state: a snapshot's
     * {@code attacking} means the unit <em>has a target</em>, which stays true for
     * the whole engagement — the walk in, the reload, the standing about between
     * blows. A clip chosen from it therefore ran on a loop at its own tempo, so a
     * monster threw punches continuously and none of them was the blow. Worse, the
     * loop's rate is the clip's: the heavy one that strikes every 1.6 seconds threw
     * a one-second punch, so it punched half again as often as it hit.
     *
     * <p>The swing is a moment and is played as one, on the shot — see
     * {@link #playOnce}. Between blows a unit that has run out of things to do is
     * standing, which is what it looks like.
     */
    private void animate(UnitNode node, UnitView view) {
        if (node.carryingAgainAt > 0f && timer.getTimeInSeconds() >= node.carryingAgainAt) {
            carrying(node, true);
            node.carryingAgainAt = 0f;
        }
        if (timer.getTimeInSeconds() < node.actionUntil) {
            return; // a blow or a flinch has the model; it will hand it back
        }
        var visual = visualFor(view.templateName());
        String wanted = view.moving() && visual.walkAnim != null
                ? visual.walkAnim : visual.idleAnim;
        if (wanted == null || wanted.equals(node.currentAnim)) {
            return;
        }
        play(node, view.templateName(), wanted, true);
    }

    /**
     * Play a clip once, over whatever the unit was doing, and give the model back
     * when it has finished.
     *
     * <p>For the things that <em>happen</em> — a blow struck, a blow taken. They
     * are moments, and a moment played on a loop is not the same thing slower: it
     * is a different thing entirely, and it is what made a monster in a fight look
     * like a monster shadow-boxing.
     *
     * <p><b>A gesture the game named outranks this.</b> A skill with a wind-up
     * fires the weapon too — a meteor is a shot that lands late — so the same
     * frame that starts the two-handed cast also carries a {@code WeaponFired},
     * and the ordinary swing was landing on top of the cast and winning. What the
     * player saw was a mage reaching out one hand, which is the shooting clip, and
     * no sign of the animation that had just been asked for.
     *
     * <p>The cost is that a flinch is swallowed for as long as a gesture runs. It
     * is the right way round: the gesture is a thing the game asked for by name
     * and the flinch is one the client supplies, and losing the whole of the first
     * to half a second of the second is the worse trade.
     */
    private void playOnce(UnitNode node, String clipName) {
        if (clipName == null || node.composer == null) {
            return;
        }
        if (timer.getTimeInSeconds() < node.gestureUntil) {
            return; // a cast the game named is already being made
        }
        var clip = node.composer.getAnimClip(clipName);
        if (clip == null) {
            return;
        }
        play(node, node.view.templateName(), clipName, false);
        node.actionUntil = (float) (timer.getTimeInSeconds() + clip.getLength());
    }

    /**
     * The gesture a spell is cast with, on the creature that cast it.
     *
     * <p>Timed rather than simply played. A spell with a wind-up on it -- a
     * meteor takes a second and a half to arrive -- wants a gesture that ENDS as
     * it lands: the same clip at its own speed either finishes early and leaves
     * him standing there waiting, or runs on past the impact and reads as
     * somebody waving at a hole in the ground. How long it should take is the
     * game's to say, since the game is the one that knows about the wind-up.
     *
     * <p>And his hands come empty. This one is two-handed, and a mage casting it
     * with a staff in one fist and a book in the other is a mage doing two things
     * at once -- see {@link #carrying}.
     */
    private void castGesture(UnitNode node, Visuals.CastAnim gesture) {
        if (node == null || node.composer == null || gesture == null) {
            return;
        }
        var clip = node.composer.getAnimClip(gesture.clip());
        if (clip == null) {
            warnOnce(node.view.templateName() + "/" + gesture.clip(), "cast animation");
            return;
        }
        float length = (float) clip.getLength();
        float seconds = gesture.seconds() > 0f ? gesture.seconds() : length;
        play(node, node.view.templateName(), gesture.clip(), false);
        var action = node.composer.getCurrentAction();
        if (action != null && seconds > 0f) {
            action.setSpeed(length / seconds);
        }
        node.actionUntil = (float) (timer.getTimeInSeconds() + seconds);
        node.gestureUntil = node.actionUntil;
        carrying(node, false);
        node.carryingAgainAt = node.actionUntil;
    }

    /**
     * Put what a creature carries into its hands, or take it out of them.
     *
     * <p>Culled rather than detached and rebuilt: a weapon hangs on the bone's own
     * attachments node, so hiding it is one flag and getting it back is the same
     * flag -- where taking it off and hanging it again would reload the model and
     * lose the tint and the turn that were measured onto it.
     */
    private void carrying(UnitNode node, boolean shown) {
        var skin = node.body == null ? null
                : AnimationLibrary.findControl(node.body, com.jme3.anim.SkinningControl.class);
        if (skin == null) {
            return;
        }
        var visual = visualFor(node.view.templateName());
        for (var one : visual.carried) {
            if (one.bone == null || skin.getArmature().getJoint(one.bone) == null) {
                continue;
            }
            skin.getAttachmentsNode(one.bone).setCullHint(
                    shown ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }

    private void play(UnitNode node, String templateName, String clipName, boolean loop) {
        try {
            if (node.composer != null) {
                node.composer.setCurrentAction(clipName, AnimComposer.DEFAULT_LAYER, loop);
            } else if (node.legacyChannel != null) {
                node.legacyChannel.setAnim(clipName, BLEND_SECONDS);
                node.legacyChannel.setLoopMode(loop
                        ? com.jme3.animation.LoopMode.Loop : com.jme3.animation.LoopMode.DontLoop);
            } else {
                return;
            }
            node.currentAnim = clipName;
        } catch (IllegalArgumentException e) {
            warnOnce(templateName + "/" + clipName, "animation");
            node.currentAnim = clipName; // don't retry every frame
        }
    }

    private void updateHud() {
        // With a menu up there is no panel and no figures along the top: the
        // menu is what he is looking at, and a bar left showing underneath it
        // has a hole in it where the minimap was culled.
        if (screen != Screen.PLAYING) {
            heroPanel.hide();
            hud.setText("");
            buildMenu.setText("");
            return;
        }
        String power = snapshot.localPlayerPowerSurplus() >= 0
                ? "+" + snapshot.localPlayerPowerSurplus()
                : String.valueOf(snapshot.localPlayerPowerSurplus());
        // A game whose status the panel can draw gets it drawn; anything else is
        // still written out along the top, which is what every game got before.
        boolean drawn = heroPanel.show(snapshot.hasStatus() ? snapshot.status() : null,
                (float) timer.getTimeInSeconds());
        // A key he pressed that could not be paid for. The panel knows WHEN, since
        // it is the thing that reads the line; this knows WHAT, since it owns the
        // game's noises. A game that names no such sound makes none, and the bar
        // flashing is still the answer.
        if (heroPanel.takeRefusal()) {
            noises.moment("no_mana", (float) timer.getTimeInSeconds());
        }
        // Money, power and a selection count are an RTS's figures, and a game
        // that draws its own panel has already said what it wants said. Writing
        // them along the top of a dungeon anyway is the client talking over the
        // game about a resource the game does not have.
        hud.setText(drawn ? "" : "$ %d    power %s    t=%.1fs    selected %d%s%s%s".formatted(
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
        banner.show(snapshot.hasBanner() ? snapshot.banner() : null,
                cam.getWidth(), cam.getHeight());
    }

    // ---- assets & helpers ----

    private void playSound(String assetPath, Vector3f position) {
        var audio = soundNode(assetPath);
        if (audio == null) {
            return;
        }
        audio.setLocalTranslation(position);
        audio.playInstance();
    }

    /**
     * This sound, ready to play, or {@code null} if it is not there.
     *
     * <p>Kept rather than built per shot, and built ahead of the first shot by
     * {@link ArtLoad}: decoding a sound is not much work, but it is work in the
     * frame the arrow leaves the bow, which is the frame that can least afford it.
     */
    private AudioNode soundNode(String assetPath) {
        if (assetPath == null || missingAssets.contains(assetPath)) {
            return null;
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
        }
        return audio;
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
     * Loads a modular kit's pieces.
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
     * three meshes. They are cloned without cloning materials, and there is
     * exactly <em>one</em> material for the whole kit: how dark a piece looks is
     * decided by where its fragments stand, in the shader, so no piece needs a
     * material of its own to be dimmer than its neighbour.
     */
    private final class KitTiles implements TileSource {

        private final Map<String, Spatial> masters = new HashMap<>();

        /**
         * The atlas kit's shared materials, one per picture and cast.
         *
         * <p>Per tint because a tone may ask for the same stone in a colder cast,
         * and one skin for the lot would give whichever floor was built first the
         * casting vote over every later one.
         *
         * <p>And per texture, because a kit is not always one atlas. A floor of
         * beaten earth with a line of trees standing along its edges is two packs
         * and two pictures, and one skin over both hands the trees the floor's
         * atlas — every leaf then reads its colour from whatever happens to sit at
         * those coordinates on the other sheet, which came out as a row of grey
         * mushrooms.
         */
        private final Map<String, Material> skins = new HashMap<>();

        /**
         * One ready piece, dressed the way its kit wants and holding the fog.
         *
         * <p>Kits come in two sorts and the difference is not cosmetic. One is
         * drawn on a single colour atlas: every piece is the same picture, so one
         * material serves the lot and sharing it is most of what keeps a floor of
         * six hundred tiles cheap. The other ships no texture at all and says in
         * its own materials what colour each part of each piece is; give that one
         * a single skin and the whole kit turns one flat grey.
         *
         * <p>Either way the material has to be one that reads the fog, or the
         * pieces would be lit and never darkened. So the second sort is not left
         * with what it came with — it is rebuilt, one fogged material per material
         * it brought, keeping the colour and gaining the dark.
         *
         * <p>Cached by kit and tint rather than by path alone, because the same
         * piece under two tints is two different masters and every copy of one
         * shares its materials.
         */
        @Override
        public Spatial piece(String assetPath) {
            return piece(assetPath, 0xFFFFFF);
        }

        /**
         * The same, under a colour of this piece's own on top of the kit's.
         *
         * <p>Everything the plain call does, and it costs nothing more: the masters
         * and the skins were already kept per tint, so a second tint is a second
         * entry in maps that exist. The lid over the rock and the floors of two
         * different storeys are then three materials rather than one, over a floor
         * that is several hundred pieces.
         */
        @Override
        public Spatial piece(String assetPath, int packedRgb) {
            var kit = activeKit();
            boolean own = kit != null && kit.keepsOwnMaterials();
            int tint = blend(kit == null ? 0xFFFFFF : kit.getTint(), packedRgb);
            String key = assetPath + "#" + Integer.toHexString(tint);
            var master = masters.get(key);
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
                if (own) {
                    refogOwnMaterials(master, toColor(new java.awt.Color(tint)));
                } else {
                    // One skin per picture the kit draws on, and per tint over it.
                    var atlas = textureOf(master);
                    master.setMaterial(skins.computeIfAbsent(
                            nameOf(atlas) + "#" + Integer.toHexString(tint),
                            ignored -> tileMaterial(atlas, toColor(new java.awt.Color(tint)))));
                }
                masters.put(key, master);
            }
            return master.clone(false); // share the mesh and the material
        }

        /**
         * Rebuild every material a model brought as one that reads the fog,
         * keeping the colour it was given and multiplying the kit's tint over it.
         */
        private void refogOwnMaterials(Spatial model, ColorRGBA tint) {
            if (model instanceof Geometry geometry) {
                var was = geometry.getMaterial();
                var colour = colourOf(was).mult(tint);
                geometry.setMaterial(fogMap == null
                        ? litKitMaterial(colour, textureOf(geometry))
                        : fogged(colour, ambientColour.mult(KIT_AMBIENT), textureOf(geometry)));
                return;
            }
            if (model instanceof Node node) {
                for (var child : node.getChildren()) {
                    refogOwnMaterials(child, tint);
                }
            }
        }

        /**
         * What colour a loaded material says its surface is; white if it says nothing.
         */
        private ColorRGBA colourOf(Material material) {
            if (material == null) {
                return ColorRGBA.White;
            }
            for (var name : new String[]{"Diffuse", "Color", "BaseColor"}) {
                var param = material.getParam(name);
                if (param != null && param.getValue() instanceof ColorRGBA colour) {
                    return colour.clone();
                }
            }
            return ColorRGBA.White;
        }

        private Material litKitMaterial(ColorRGBA colour, com.jme3.texture.Texture atlas) {
            var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            material.setBoolean("UseMaterialColors", true);
            material.setColor("Diffuse", colour);
            material.setColor("Ambient", KIT_AMBIENT.mult(colour));
            if (atlas != null) {
                material.setTexture("DiffuseMap", atlas);
            }
            return material;
        }

        /**
         * How much of the ambient the kit's palette art returns.
         */
        private static final ColorRGBA KIT_AMBIENT = new ColorRGBA(0.55f, 0.55f, 0.62f, 1f);

        private Material tileMaterial(com.jme3.texture.Texture atlas, ColorRGBA tint) {
            if (fogMap != null) {
                return fogged(tint, ambientColour.mult(KIT_AMBIENT).mult(tint), atlas);
            }
            var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            material.setBoolean("UseMaterialColors", true);
            material.setColor("Diffuse", tint);
            material.setColor("Ambient", KIT_AMBIENT.mult(tint));
            if (atlas != null) {
                material.setTexture("DiffuseMap", atlas);
            }
            return material;
        }

        /**
         * Two packed colours multiplied, channel by channel — white leaves the other alone.
         */
        private static int blend(int over, int under) {
            if (under == 0xFFFFFF) {
                return over;
            }
            int red = (over >> 16 & 0xFF) * (under >> 16 & 0xFF) / 255;
            int green = (over >> 8 & 0xFF) * (under >> 8 & 0xFF) / 255;
            int blue = (over & 0xFF) * (under & 0xFF) / 255;
            return red << 16 | green << 8 | blue;
        }

        /**
         * What a texture is called, for keying a skin by it; untextured pieces share one name.
         */
        private String nameOf(com.jme3.texture.Texture atlas) {
            var key = atlas == null ? null : atlas.getKey();
            return key == null ? "" : key.getName();
        }

        /**
         * The kit's colour atlas, taken off whatever material the loader made.
         */
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

    }

    private Material lit(ColorRGBA color) {
        var material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", color);
        material.setColor("Ambient", color.mult(0.7f));
        return material;
    }

    /**
     * The one sun and the one flat ambient the whole scene is lit by, as the game
     * asked for them — see {@link Sunlight}.
     *
     * <p>Read once and kept, rather than asked of the visuals at each of the four
     * places that want them: they are read while materials are built, which is
     * often, and the answer cannot change while a scene stands.
     *
     * <p>They were three constants here until a player said the map looked flat.
     * It did, and this is where: a sun far enough from vertical is the only thing
     * that tells a floor from the top of a wall, since the two are the same tile
     * with the same normal, and how far is a decision about how a game should look
     * rather than a fact about drawing one.
     */
    private final Vector3f sunDirection;
    private final ColorRGBA sunColour;
    private final ColorRGBA ambientColour;

    /**
     * How much of the ambient plain terrain returns — what {@link #lit} asks for.
     */
    private static final float PLAIN_AMBIENT = 0.7f;

    private Material foggedTerrain(ColorRGBA color) {
        return fogged(color, ambientColour.mult(PLAIN_AMBIENT), null);
    }

    /**
     * Terrain that reads the dark out of {@link FogMap} at the place each fragment
     * stands.
     *
     * <p>The lighting is written out here rather than handed to the engine's
     * because the shader is only ever used on terrain and the terrain is lit by
     * exactly one sun and one flat ambient. Passing them as material parameters is
     * a few lines; a shader that spoke the engine's light protocol would be a copy
     * of its whole lighting pipeline, for a scene that has one light in it.
     *
     * @param ambient how much of the ambient the surface returns, already
     *                multiplied by the ambient light itself
     */
    private Material fogged(ColorRGBA color, ColorRGBA ambient,
                            com.jme3.texture.Texture atlas) {
        var material = new Material(assetManager, "MatDefs/duke/FoggedTerrain.j3md");
        material.setColor("Color", color);
        material.setColor("Ambient", ambient);
        material.setColor("Sun", sunColour);
        material.setVector3("SunDirection", sunDirection);
        material.setTexture("FogMap", fogMap.texture());
        material.setVector2("FogSize", fogMap.worldSize());
        if (atlas != null) {
            material.setTexture("ColorMap", atlas);
        }
        material.setParam("PointLightColours",
                com.jme3.shader.VarType.Vector4Array, terrainLightColours);
        material.setParam("PointLightPositions",
                com.jme3.shader.VarType.Vector4Array, terrainLightPlaces);
        fogged.add(material);
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
