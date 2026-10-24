package uz.dukeengine.client3d;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.combat.message.CombatOrder;

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
     * Whether a modifier is down, so the letter under it can mean something else.
     *
     * <p>An array because the listener is a lambda and a lambda may not write to a
     * local -- the same trick {@code shiftHeld} beside it uses, for the same
     * reason.
     */
    private final boolean[] ctrlHeld = {false};

    /**
     * Persisted display/audio settings, shared by every duke-engine game.
     */
    static final java.util.prefs.Preferences PREFS =
            java.util.prefs.Preferences.userRoot().node("duke-engine/game");

    /** The match being drawn: the one the window opened with, or the last the game started. */
    private DukeGame game;
    /** The match whose simulation was started, which is never started again. */
    private DukeGame played;
    private final Visuals visuals;
    private final Shell shell;
    /** The game's own drawing, and its first look at the input — see {@link Painter} and {@link CanvasInput}. */
    private final Painter painter;
    private final CanvasInput canvasInput;
    /** The game's ear for how far a match has got loading — see {@link Duke3D#onLoading}. */
    private final java.util.function.IntConsumer loadingEar;
    /** Whether a loaded match waits for the game to let it start — see {@link Duke3D#holdMatchStart}. */
    private boolean holdStart;
    /** The match being made ready, while it is. */
    private MatchLoad matchLoad;
    /** The game's recipe for the match behind its front end, and that match — see {@link Duke3D#backdrop}. */
    private final java.util.function.Supplier<DukeGame> backdropRecipe;
    /** The game's ear for how far the backdrop being made has got — see {@link Duke3D#onBackdropLoading}. */
    private final java.util.function.IntConsumer backdropEar;
    /** The game's ear for what the players say — see {@link Duke3D#onChat}. */
    private final java.util.function.Consumer<uz.dukeengine.core.network.ChatLine> chatEar;
    private Backdrop backdrop;
    /** The part of the window the world is drawn in — see {@link Duke3D#worldView} — and what it was last laid as. */
    private WorldRegion worldRegion = WorldRegion.WHOLE;
    private WorldRegion laidRegion;
    private int laidRegionFor;
    /** What clears the window round a world drawn in part of it: black, under the game's canvas. */
    private com.jme3.renderer.ViewPort surround;
    /** The game's own drawing over everything, and its movies — see {@link GameCanvas}. */
    private GameCanvas canvas;
    /** The player's camera as the game frames it — see {@link CameraFrame}. */
    private final CameraFrame cameraFrame;
    /**
     * Keys this game claimed for itself, over and above the standard controls.
     */
    private final Hotkeys hotkeys;
    private final CountDownLatch stopped = new CountDownLatch(1);

    /** What is on the screen, and the client's own menus — see {@link Screens}. */
    private Screens screens;
    private Thread simThread; // started when the player presses Play

    private final Node unitsNode = new Node("units");
    /**
     * Terrain lives in its own node so a new world can replace it wholesale. It
     * used to hang straight off the root, which meant it could be built but never
     * rebuilt — and a game that lays out a new world (a roguelike starting a fresh
     * run) kept the old one on screen while everything else moved on.
     */
    private final Node terrainNode = new Node("terrain");
    /** The world's ground as it is drawn, and what lies on it — see {@link WorldGround}. */
    private WorldGround ground;
    /** The looks in force, the floor's and its cells' — see {@link Looks}. */
    private final Looks looks;
    /** What the player has seen, and the dark over the rest — see {@link FogOfWar}. */
    private FogOfWar fog;
    /** How the ground and what lies on it are drawn, and every material that reads the fog — see {@link GroundMaterials}. */
    private GroundMaterials groundLooks;
    /** The sun, the ambient and the sun's shadows the scene is lit by — see {@link SceneLight}. */
    private final SceneLight light;
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
     * somewhere else entirely -- see {@link WorldEvents}.
     */
    private SkillEffects skillEffects;
    /**
     * The effects drawn from layers -- see {@link LayeredEffects}. Built beside the
     * other two and borrowing their lights rather than bringing its own, so the
     * light budget is one budget.
     */
    private LayeredEffects layered;
    /** The game's particle systems, burning, and drawn — see {@link Visuals#particleSystems}. */
    private Particles particles;
    private ParticleDrawing particleDrawing;
    /** The game's effect lists, played where the world's moments happen — see {@link Visuals#effectLists}. */
    private EffectLists lists;
    /** What those lists play besides particle systems: a sound, a light, the camera, a mark, a streak. */
    private ListShow listShow;
    private final Node listNode = new Node("effect-lists");
    /** The beams the simulation owns, drawn as the game's lasers — see {@link Lasers}. */
    private Lasers lasers;
    /** The particle systems the simulation keeps going on its things until it ends them. */
    private RidingEffects ridingEffects;
    private RallyMarks rallyMarks;
    private OcclusionMarks occlusionMarks;
    /** The streams things ride, drawn as ribbons, once the game names a look for one — see {@link Visuals#stream}. */
    private Ribbons ribbons;
    private final Map<String, Material> particleMaterials = new HashMap<>();
    /**
     * Whoever of theirs was just hit, going white -- see {@link HitFlash}.
     */
    private HitFlash hitFlash;
    /** The things of the world as they are drawn, one node each — see {@link UnitScene}. */
    private UnitScene unitScene;
    /** What the pointer is on in the world — see {@link WorldPointer}. */
    private WorldPointer pointer;
    /** What the player has selected and what he orders it to do — see {@link Orders}. */
    private Orders orders;
    /** The game's own keys, pressed, armed, held and cast — see {@link SkillKeys}. */
    private SkillKeys skillKeys;
    /** The game's command buttons, on the bar and armed to aim — see {@link CommandButtons}. */
    private CommandButtons buttons;
    /** What happened in the world this frame, drawn and heard — see {@link WorldEvents}. */
    private WorldEvents events;
    /** The sounds a look names by file, each read once — see {@link SoundNodes}. */
    private SoundNodes soundNodes;

    /** Each kind of game's part of the window on the classpath — see {@link HudPart}. */
    private final List<HudPart> parts = HudPart.found();

    /** What the parts are handed of the window. */
    private final HudPart.Window window = new HudPart.Window() {
        @Override
        public com.jme3.asset.AssetManager assets() {
            return assetManager;
        }

        @Override
        public com.jme3.renderer.RenderManager renderManager() {
            return renderManager;
        }

        @Override
        public Node gui() {
            return guiNode;
        }

        @Override
        public com.jme3.font.BitmapFont font() {
            return guiFont;
        }

        @Override
        public com.jme3.font.BitmapFont titleFont() {
            return fontOrDefault(visuals.getMenuStyle().titleFont());
        }

        @Override
        public Visuals visuals() {
            return visuals;
        }

        @Override
        public DukeGame game() {
            return game;
        }

        @Override
        public Spatial body(Visuals.UnitVisual visual, java.util.Collection<String> alsoWanted) {
            return bodies.buildBody(visual, alsoWanted);
        }

        @Override
        public void sound(String cue) {
            if (noises != null) {
                noises.moment(cue, timer.getTimeInSeconds());
            }
        }

        @Override
        public float now() {
            return timer.getTimeInSeconds();
        }
    };

    /** The floor's half of this frame's status line, read once for the bars and the moments. */
    private UnitBarReading barReading = UnitBarReading.NOTHING;

    /** What is selected, in the order it was selected: the order the game is told it in. */
    private final Set<Integer> selected = new java.util.LinkedHashSet<>();
    /** The files the game named that would not load, each said once. */
    private final MissingAssets missing = new MissingAssets();
    /** Things' bodies as the game's looks make them — built once there is an asset manager. */
    private Bodies bodies;
    /** Where the art read ahead of a match is kept — see {@link ArtLoad}. */
    private ArtLoad.Store artStore;
    /**
     * Every noise the game makes, and what it makes them for.
     */
    private GameSounds noises;
    /**
     * What the player set, kept in a file beside him — see {@link GameSettings}.
     */
    private final GameSettings preferences = new GameSettings();
    /** The window itself, asked and moved where jME keeps no answer — see {@link WindowDisplay}. */
    private final WindowDisplay windowDisplay = new WindowDisplay(this::getContext, () -> settings, preferences,
            missing);
    /** What the player chose on the settings screen, put into effect — see {@link PlayerChoices}. */
    private PlayerChoices choices;
    /** The controls bound, and the keys the player put them on — see {@link KeyBindings}. */
    private KeyBindings keyBindings;
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
    private final CameraFocus camera = new CameraFocus();
    /** The player's eye, steered and placed — see {@link CameraRig}. */
    private CameraRig cameraRig;
    /** The client's own controls on the game's keys — see {@link KeyMap} and {@link Controls}. */
    private Controls controls;
    /** Shift and Alt down, beside Ctrl: a key means one thing with them and another without. */
    private boolean shiftDown;
    private boolean altDown;
    private com.jme3.app.state.ScreenshotAppState screenshots;
    private BitmapText hud;
    private SceneVeil veil;

    /** Whether a match is what is going on: played, with no menu over it. */
    private boolean inPlay() {
        return screens.now() == Screen.PLAYING && !screens.menu().isVisible();
    }

    /** What the menus ask of this window — see {@link Screens.Host}. */
    private final Screens.Host menusHost = new Screens.Host() {
        @Override
        public DukeGame game() {
            return game;
        }

        @Override
        public DukeGame frontGame() {
            return DukeRtsApp.this.frontGame();
        }

        @Override
        public WorldSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public void startGame() {
            DukeRtsApp.this.startGame();
        }

        @Override
        public void quitToTheMainMenu() {
            DukeRtsApp.this.quitToTheMainMenu();
        }

        @Override
        public void quitToTheDesktop() {
            stop();
        }

        @Override
        public void takeTheFrontMatchBack() {
            DukeRtsApp.this.takeTheFrontMatchBack();
        }

        @Override
        public void paused(boolean paused) {
            setSimulationPaused(paused);
        }

        @Override
        public void later(Runnable task) {
            enqueue(task);
        }

        @Override
        public boolean musicByGame() {
            return musicByGame;
        }
    };

    /** Whether a scene the game plays keeps the player's hands off the world — see {@code GameCamera.lockInput}. */
    private boolean sceneLocked() {
        return game != null && game.camera().screen().inputLocked();
    }
    private BitmapText buildMenu;
    private BannerPanel banner;
    /** The minimap, in the corner or in a part's socket — see {@link MinimapView}. */
    private MinimapView minimapView;

    /**
     * The box the player is dragging, drawn over the world while the button is down.
     */
    private DragBox dragRectangle;

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
    /** Orders answered with the game's own model, where it names one — see {@link OrderMark#model}. */
    private ModelMarks modelMarks;

    /**
     * The numbers that come off a creature as it is hurt or healed.
     */
    private FloatingNumbers hitNumbers;
    /** Texts floated up from points of the world — see {@link FloatingTexts}. */
    private FloatingTexts floatingTexts;
    private PictureStrips pictureStrips;

    /**
     * The bars over everybody's heads. Screen-space and pooled — see UnitBars.
     */
    private UnitBars unitBars;

    DukeRtsApp(DukeGame game, Visuals visuals, Shell shell, Hotkeys hotkeys, Painter painter,
            CanvasInput canvasInput, java.util.function.IntConsumer loadingEar,
            java.util.function.Supplier<DukeGame> backdropRecipe, java.util.function.IntConsumer backdropEar,
            java.util.function.Consumer<uz.dukeengine.core.network.ChatLine> chatEar) {
        this.game = game;
        this.visuals = visuals;
        this.shell = shell;
        this.painter = painter;
        this.canvasInput = canvasInput;
        this.loadingEar = loadingEar;
        this.backdropRecipe = backdropRecipe;
        this.backdropEar = backdropEar;
        this.chatEar = chatEar;
        listenForChat(game);
        this.hotkeys = hotkeys == null ? Hotkeys.none() : hotkeys;
        this.controls = new Controls(this.hotkeys.keyMap());
        this.cameraFrame = visuals == null ? CameraFrame.NONE : visuals.getCameraFrame();
        camera.frame(cameraFrame);
        this.light = new SceneLight(sunlightOfTheGame(), rootNode);
        this.looks = new Looks(visuals, missing);
    }

    /** The sun the game lights its world by now — see {@link SceneLight#adopt}. */
    private Sunlight sunlightOfTheGame() {
        return visuals == null ? Sunlight.DEFAULT : visuals.getSunlight();
    }

    /**
     * The simulation thread, if the player ever pressed Play.
     */
    Thread getSimThread() {
        return simThread;
    }

    /** The match drawn now — the last the game started, or the one the window opened with. */
    DukeGame currentGame() {
        return game;
    }

    private Runnable leaving;

    void awaitStop() throws InterruptedException {
        stopped.await();
    }

    /** Run once before this client lets its match go — see {@link Duke3D#onLeaving}. */
    void onLeaving(Runnable hook) {
        this.leaving = hook;
    }

    /** Whether the game has been told it is losing the match now played — see {@link #leave}. */
    private boolean left;

    /** The game told it is about to lose its match, with the world still, once a match, if there is one to lose. */
    void leave() {
        if (left || leaving == null || game.getLogic() == null) {
            return;
        }
        left = true;
        setSimulationPaused(true);
        leaving.run();
    }

    @Override
    public void destroy() {
        // Abandon, Quit and the window closing all come here. What the settings screen applied is kept: a player
        // who chose a window and closed it by its X has chosen a window.
        preferences.keep();
        leave();
        if (backdrop != null) {
            backdrop.stop();
        }
        // Before the application goes, because it owns the render manager a part's viewport may stand in.
        parts.forEach(HudPart::close);
        sceneSlot.detach();
        super.destroy();
        stopped.countDown();
    }

    // ---- scene setup ----

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        choices = new PlayerChoices(preferences, visuals, listener, () -> noises, () -> listShow);
        anisotropy(anisotropy); // slanted ground and roofs kept sharp: the camera sees all of them at a slant
        // The whole picture, the interface over it too, drawn through the game's gamma: last of all.
        gammaPass = new GammaPass(assetManager);
        gammaPass.setGamma(gamma);
        guiViewPort.addProcessor(gammaPass);
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
        viewPort.setBackgroundColor(!visuals.drawsFog()
                ? new ColorRGBA(0.05f, 0.07f, 0.10f, 1f)
                : visuals.getFog().tintColour());
        // The ground's overlays in their layers' order, under every other see-through thing.
        OverlayOrder.install(viewPort);

        // project assets folder (Studio Play); exported games use the classpath
        if (visuals.getAssetRoot() != null) {
            assetManager.registerLocator(visuals.getAssetRoot(),
                    com.jme3.asset.plugins.FileLocator.class);
        }

        // Before the terrain, which builds materials that read it.
        // The dark, as a picture of the map that the ground's materials read by world position; none for a game that
        // never asked to be discovered, and then the ground is drawn plainly.
        var fogMap = visuals.drawsFog() ? new FogMap(visuals.getFog()) : null;
        groundLooks = new GroundMaterials(assetManager, visuals, light, fogMap, camera, () -> game,
                () -> ground == null ? null : ground.builtFrom(), () -> snapshot);
        fog = new FogOfWar(visuals, fogMap, groundLooks, looks, camera, viewPort, renderer, () -> game,
                () -> snapshot);
        // Needs the locators above, so it cannot be built with the app itself.
        ground = new WorldGround(assetManager, terrainNode, listNode, visuals, groundLooks, looks,
                new KitTiles(assetManager, missing, groundLooks, light, looks::activeKit), () -> game, () -> noises,
                this::floorHeightAt);

        light.install(assetManager, viewPort, visuals.getThingSuns());

        // Before the terrain, because rebuilding a world clears what is burning in
        // it and there has to be something there to clear.
        var budget = visuals.getEffectBudget();
        effects = new ProjectileEffects(assetManager, rootNode, visuals, budget.lights());
        bodies = new Bodies(assetManager, missing, effects);
        soundNodes = new SoundNodes(assetManager, rootNode, missing);
        skillEffects = new SkillEffects(visuals, budget.distance());
        layered = new LayeredEffects(assetManager, rootNode, visuals, effects.lights(),
                surroundings(), visuals.getParticleBudget(), budget.distance());
        // Beside the layered effects, and started wherever a name that is one of them is given in an effect's
        // place. Seeded from nothing the simulation knows: sparks are this machine's own.
        particles = new Particles(visuals::particleSystemNamed, new java.util.Random().nextLong(),
                (x, y) -> floorHeightAt(x, -y), visuals.getParticleSystemMost(),
                visuals.getParticleSystemNeverRefusedFrom());
        particleDrawing = new ParticleDrawing(particles, this::particleMaterial);
        rootNode.attachChild(particleDrawing.node());
        layered.drawsSystemsWith(particles);
        hangTheSky();
        listShow = new ListShow(assetManager, listNode, effects.lights(), (cue, at) -> {
            if (noises != null) {
                noises.sounds().play(cue, at, timer.getTimeInSeconds());
            }
        }, () -> new Vector3f(camera.targetX(), 0f, camera.targetZ()), this::floorHeightAt, bodies::debrisPiece,
                (name, at) -> lists.play(name, EffectLists.Cue.at(at)), this::paintThrown, ground.scorches());
        choices.applyShake();
        rootNode.attachChild(listNode);
        lists = new EffectLists(visuals::effectListNamed, particles, listShow, new java.util.Random().nextLong());
        lasers = new Lasers(assetManager, listNode, visuals::laserNamed, this::floorHeightAt);
        ridingEffects = new RidingEffects(particles, rootNode);
        rallyMarks = new RallyMarks(assetManager, listNode, path -> bodies.asFiled(path, "order mark"));
        var groundPictures = new GroundPictures(assetManager, listNode);
        var stealthLook = new StealthLook(assetManager);
        layered.drawsListsWith(lists);
        hitFlash = new HitFlash(visuals.getHitFlash());
        unitScene = new UnitScene(assetManager, visuals, bodies, looks, groundLooks, light, effects, layered, particles,
                hitFlash, groundPictures, stealthLook, missing, fog, rootNode, unitsNode, cam, timer, () -> game,
                () -> snapshot, selected, this::floorHeightAt, id -> events.criticalThisFrame(id),
                shot -> events.shotEnded(shot));
        pointer = new WorldPointer(cam, inputManager, unitScene, () -> game, () -> scheme, () -> worldRegion,
                this::discovery, this::floorHeightAt);

        rootNode.attachChild(terrainNode);
        buildTerrain();
        rootNode.attachChild(unitsNode);
        rootNode.attachChild(markerNode);
        chevrons = new Chevrons(assetManager, markerNode, visuals.getOrderMark());
        attackFlash = new AttackFlash(assetManager, markerNode, visuals.getOrderMark());
        modelMarks = new ModelMarks(markerNode, path -> bodies.asFiled(path, "order mark"));
        hitNumbers = new FloatingNumbers(guiFont, guiNode, visuals.getHitNumbers());
        floatingTexts = new FloatingTexts(guiFont, guiNode, visuals.getFloatingText());
        pictureStrips = new PictureStrips(guiNode, this::stripPicture, visuals::stripNamed,
                picture -> Materials.picture(assetManager, picture));
        // The display face a boss's name is set in is the one the game already
        // named for its menus. A second field naming the same file would be a
        // second thing to keep in step with it, for no second decision.
        unitBars = new UnitBars(assetManager, guiFont,
                fontOrDefault(visuals.getMenuStyle().rowFont()));
        unitBars.look(visuals.getUnitBars());
        guiNode.attachChild(unitBars.node());
        var rangeRings = new RangeRings(assetManager, markerNode, visuals.getRangeLook());
        veil = new SceneVeil(assetManager);
        guiNode.attachChild(veil.node());
        artStore = new ArtLoad.Store(assetManager, renderManager, bodies, rootNode, terrainNode,
                soundNodes::soundNode, missing);

        cameraRig = new CameraRig(cam, camera, cameraFrame, visuals, inputManager, windowDisplay, veil, skillEffects,
                listShow, timer, () -> game, () -> snapshot, () -> scheme, this::inPlay, this::held,
                this::sceneLocked,
                () -> canvasInput != null && canvasInput.holdsKeys() || controls.isHeld(KeyMap.Control.FREE_POINTER),
                this::floorHeightAt);
        cameraRig.centreOnTheMap();
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

        for (var part : parts) {
            part.open(window, cam.getWidth(), cam.getHeight());
        }

        minimapView = new MinimapView(assetManager, visuals, () -> game, () -> snapshot, camera, cam, renderer, parts,
                this::discovery, groundLooks::groundTexture, unitScene::colourOf, timer);
        minimapView.build(guiNode);
        readTheNewGrid();
        buildDragRectangle();
        orders = new Orders(() -> game, () -> snapshot, selected, visuals, hotkeys, controls, parts, () -> noises,
                () -> mouse, pointer, unitScene, minimapView::click, orderMarkers, modelMarks, rallyMarks,
                dragRectangle, cam, inputManager, timer, () -> screens.now(), this::floorHeightAt);
        skillKeys = new SkillKeys(hotkeys, visuals, parts, orders, pointer, () -> scheme, rangeRings, () -> noises,
                () -> game, () -> snapshot, () -> screens.now(), inputManager, timer, this::floorHeightAt);
        screens = new Screens(menusHost, shell, visuals, hotkeys, preferences, choices, windowDisplay, keyBindings,
                controls, () -> noises, parts, () -> settings, assetManager, guiNode, guiFont, this::fontOrDefault, timer,
                cam.getWidth(), cam.getHeight());
        buttons = new CommandButtons(assetManager, guiNode, markerNode, visuals, looks, bodies, screens,
                this::fontOrDefault, cam, inputManager, orders, pointer, skillKeys, () -> game, () -> snapshot,
                this::clientHud, this::floorHeightAt);
        loading = new LoadingOverlay(guiFont, assetManager, guiNode,
                cam.getWidth(), cam.getHeight());
        buildSounds();
        events = new WorldEvents(visuals, unitScene, looks, layered, lists, skillEffects, controls, parts, noises,
                soundNodes, ground, fog, hitNumbers, floatingTexts, pictureStrips, new HurtMoments(visuals::hurtRule),
                cam, timer, () -> game, () -> snapshot, () -> barReading, this::floorHeightAt);
        buildCanvas();
        screens.showMainMenu();
        choices.applyVolume();
    }

    /** The game's sky, hung behind everything; none where it names none or its pictures will not load. */
    private void hangTheSky() {
        var pictures = visuals.getSkybox();
        if (pictures.isEmpty()) {
            return;
        }
        try {
            var sky = pictures.size() == 1
                    ? com.jme3.util.SkyFactory.createSky(assetManager, pictures.getFirst(),
                            com.jme3.util.SkyFactory.EnvMapType.CubeMap)
                    : com.jme3.util.SkyFactory.createSky(assetManager, assetManager.loadTexture(pictures.getFirst()),
                            assetManager.loadTexture(pictures.get(1)), assetManager.loadTexture(pictures.get(2)),
                            assetManager.loadTexture(pictures.get(3)), assetManager.loadTexture(pictures.get(4)),
                            assetManager.loadTexture(pictures.get(5)));
            rootNode.attachChild(sky);
        } catch (RuntimeException notThere) {
            LOG.warning(() -> "the sky names pictures that will not load: " + pictures);
        }
    }

    /** The things shown behind what stands in front this frame — see {@link Visuals#occlusionMarks}. */
    private void markTheHidden() {
        if (!visuals.getOcclusionMarks() || snapshot == null) {
            if (occlusionMarks != null) {
                occlusionMarks.mark(Map.of());
            }
            return;
        }
        if (occlusionMarks == null) {
            occlusionMarks = new OcclusionMarks(assetManager);
            viewPort.getProcessors().add(0, occlusionMarks); // before a filter takes the picture and its depth
        }
        int local = game.getLocalPlayerIndex();
        var marked = new java.util.HashMap<Spatial, ColorRGBA>();
        for (var view : snapshot.units()) {
            var node = unitScene.node(view.id());
            if (node == null || view.structure()
                    || view.playerIndex() != local && !visuals.getOcclusionMarked().test(view)) {
                continue;
            }
            marked.put(node.root, Materials.colour(game.getColor(view.playerIndex())));
        }
        occlusionMarks.mark(marked);
    }

    /**
     * The game's canvas, over everything else in the GUI, and its first look at the input — before any control of
     * the client's own, since the input manager hands a consumed event to nothing after its raw listeners.
     */
    private void buildCanvas() {
        canvas = new GameCanvas(assetManager, guiNode, cam, painter, parts, canvasWorld, () -> game, () -> snapshot,
                () -> noises, missing);
        surround = renderManager.createPreView("world-surround", new com.jme3.renderer.Camera(cam.getWidth(),
                cam.getHeight()));
        surround.setBackgroundColor(ColorRGBA.Black);
        surround.setClearFlags(true, true, true);
        surround.setEnabled(false);
        if (backdropRecipe != null) {
            backdrop = new Backdrop(backdropRecipe, this::artLoadFor, backdropEar == null ? _ -> { } : backdropEar);
        }
        // First of all: a title card that takes the input takes it before the game's canvas or any control.
        inputManager.addRawInputListener(new CardInput(canvas::takesInput, canvas::skip));
        if (canvasInput != null) {
            canvasInputs = new CanvasInputs(canvasInput, () -> cam.getHeight());
            inputManager.addRawInputListener(canvasInputs);
        }
    }

    /** Which button selects and which commands — see {@link Duke3D#mouse}. */
    private Mouse mouse = Mouse.RIGHT_COMMANDS;
    /** The game's first look at the input, where it asked for one — see {@link CanvasInputs}. */
    private CanvasInputs canvasInputs;
    /** The gamma the whole picture is drawn through — see {@link Duke3D#gamma}. */
    private float gamma = 1f;
    private GammaPass gammaPass;

    void mouse(Mouse chosen) {
        this.mouse = chosen == null ? Mouse.RIGHT_COMMANDS : chosen;
    }

    /** Controls of a kind the client has not got itself — see {@link Duke3D#controls}. */
    private ControlScheme scheme = ControlScheme.CLASSIC;

    /** What grows on the ground round the camera — see {@link Duke3D#groundCover}. */
    void groundCover(GroundCover cover) {
        ground.cover(cover);
    }
    void controls(ControlScheme controls) {
        this.scheme = controls == null ? ControlScheme.CLASSIC : controls;
        scheme.attach(schemeContext);
    }

    /** The game's own scene in the world — see {@link Duke3D#scene}. */
    private final SceneSlot sceneSlot = new SceneSlot(rootNode);

    void scene(SceneLayer layer) {
        sceneSlot.attach(layer, assetManager, cam, inputManager, renderManager);
    }

    /** The player's own eyes in place of the RTS camera — see {@link Duke3D#firstPerson}. */
    void firstPerson(FirstPersonCamera eyes) {
        controls(eyes);
        cameraRig.firstPerson(eyes);
    }

    /** How far the unit's weapon reaches, read off the world. */
    private float reachOf(uz.dukeengine.core.thing.ObjectId id) {
        var logic = game.getLogic();
        var unit = logic == null ? null : logic.findObject(id);
        var weapon = unit == null ? null : unit.findModule(uz.dukeengine.combat.module.WeaponUpdate.class);
        return weapon == null ? 0f : weapon.reach();
    }

    /** What a scheme reads and works through: the window, as the client sees it. */
    private final ControlScheme.Context schemeContext = new ControlScheme.Context() {
        @Override
        public DukeGame game() {
            return game;
        }

        @Override
        public uz.dukeengine.core.view.WorldSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public boolean playing() {
            return inPlay() && !(canvasInput != null && canvasInput.holdsKeys())
                    && windowDisplay.focused();
        }

        @Override
        public List<uz.dukeengine.core.thing.ObjectId> selected() {
            return orders.selectedIds();
        }

        @Override
        public float[] drawnAt(int id) {
            var node = unitScene.node(id);
            if (node == null) {
                return null;
            }
            var at = node.root.getLocalTranslation();
            return new float[] {at.x, at.z};
        }

        @Override
        public float topOf(int id) {
            var node = unitScene.node(id);
            return node == null || node.body == null ? 0f : UnitPlacement.topOf(node.body);
        }

        @Override
        public uz.dukeengine.core.view.UnitView pointedUnit() {
            var unit = pointer.pickUnit();
            return unit == null ? null : unit.view;
        }

        @Override
        public float[] pointedGround() {
            var click = inputManager.getCursorPosition();
            var ground = pointer.groundUnder(click.x, click.y);
            return ground == null ? null : new float[] {ground.x, ground.z, ground.y};
        }

        @Override
        public float floorHeight(float x, float y) {
            return floorHeightAt(x, y);
        }

        @Override
        public boolean keyHeld(char key) {
            return windowDisplay.keyHeld(key);
        }

        @Override
        public boolean leftHeld() {
            return windowDisplay.buttonHeld(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }

        @Override
        public float time() {
            return timer.getTimeInSeconds();
        }

        @Override
        public float yaw() {
            return camera.yaw();
        }

        @Override
        public float pitch() {
            return camera.pitch();
        }

        @Override
        public float[] lookingAt() {
            return new float[] {camera.targetX(), camera.targetZ()};
        }

        @Override
        public void lookAt(float x, float y) {
            camera.lookAt(x, y);
        }

        @Override
        public void turnBy(float radians) {
            camera.turnBy(radians);
        }

        @Override
        public void tilt(float radians, float base, float band) {
            camera.tilt(radians, base, band);
        }

        @Override
        public void holdMouse(boolean held) {
            cameraRig.holdMouse(held);
        }

        @Override
        public boolean mouseHeld() {
            return cameraRig.mouseHeld();
        }

        @Override
        public float[] mouseMoved() {
            return cameraRig.mouseMoved();
        }

        @Override
        public com.jme3.scene.Node gui() {
            return guiNode;
        }

        @Override
        public com.jme3.font.BitmapFont font() {
            return guiFont;
        }

        @Override
        public Vector3f screenOf(float x, float height, float y) {
            return cam.getScreenCoordinates(new Vector3f(x, height, y));
        }

        @Override
        public void castArmed() {
            skillKeys.aimArmedKey();
        }

        @Override
        public float reachOf(uz.dukeengine.core.thing.ObjectId id) {
            return DukeRtsApp.this.reachOf(id);
        }
    };

    /** The player's own scroll speed — see {@link Duke3D#scrollSpeed}. */
    void scrollSpeed(float share) {
        cameraRig.scrollSpeed(share);
    }

    /**
     * The weather falling round the camera, and the game's frame it last fell to: the match's where its game set one
     * ({@link uz.dukeengine.game.DukeGame#weather}), else the client's ({@link Duke3D#weather}).
     */
    private Weather weather;
    private int weatherFrame = -1;
    private WeatherLook clientWeather;
    private uz.dukeengine.core.view.FallingWeather matchWeather;

    void weather(WeatherLook look) {
        clientWeather = look;
        if (matchWeather == null) {
            fall(look);
        }
    }

    private void fall(WeatherLook look) {
        if (weather != null) {
            weather.remove();
        }
        weather = look == null ? null : new Weather(look);
        weatherFrame = -1;
    }

    /** The weather falls by the frames of the game gone by since the last — none while paused — round the eye. */
    private void letItFall() {
        var set = game.getWeather();
        if (set != matchWeather) {
            matchWeather = set;
            fall(set != null ? WeatherLook.of(set) : clientWeather);
        }
        if (weather == null || snapshot == null) {
            return;
        }
        if (weatherFrame >= 0) {
            weather.step(snapshot.frame() - weatherFrame);
        }
        weatherFrame = snapshot.frame();
        var eye = cam.getLocation();
        float viewHeight = cam.getHeight() * (cam.getViewPortTop() - cam.getViewPortBottom());
        weather.show(assetManager, rootNode, eye.x, eye.y, eye.z, viewHeight,
                cam.getFrustumTop() / cam.getFrustumNear());
    }

    void shadows(boolean on) {
        light.shadows(on);
    }

    void shadowColour(java.awt.Color colour) {
        light.shadowColour(colour);
    }

    void gamma(float gamma) {
        this.gamma = gamma;
        if (gammaPass != null) {
            gammaPass.setGamma(gamma);
        }
    }

    /** Draw the world in this part of the window from the next frame — see {@link Duke3D#worldView}. */
    void worldView(WorldRegion region) {
        this.worldRegion = region;
    }

    /**
     * The camera laid into the world's part of the window, again whenever that part or the window changes: its
     * viewport and its shape, and the black round it.
     */
    private void layTheWorldRegion() {
        int size = cam.getWidth() * 100_000 + cam.getHeight();
        if (worldRegion.equals(laidRegion) && size == laidRegionFor) {
            return;
        }
        worldRegion.applyTo(cam, cameraFrame.fieldOfView());
        camera.viewShape(cam.getFrustumTop() / cam.getFrustumNear());
        laidRegion = worldRegion;
        laidRegionFor = size;
        surround.getCamera().resize(cam.getWidth(), cam.getHeight(), false);
        surround.setEnabled(!worldRegion.isWhole());
        var bottom = worldRegion.bottomPixel(cam.getHeight());
        unitBars.within(worldRegion.leftPixel(cam.getWidth()), bottom, worldRegion.rightPixel(cam.getWidth()),
                worldRegion.topPixel(cam.getHeight()));
    }

    /** Play a movie — see {@link Duke3D#playMovie}; one playing is stopped first. */
    void playMovie(Movie wanted, Runnable ended) {
        canvas.playMovie(wanted, ended);
    }

    /** Stop the movie playing, and its sound, now. */
    void stopMovie() {
        canvas.stopMovie();
    }

    /** Where the world's points, things' bars and the bottom panel are on the screen, for the game's drawing. */
    private final CanvasFrame.World canvasWorld = new CanvasFrame.World() {
        @Override
        public Canvas.Point screenOf(float x, float y, float height) {
            return worldShown() ? CanvasFrame.onScreen(cam, x, y, height) : null;
        }

        @Override
        public Canvas.Box barOf(int id) {
            return worldShown() ? CanvasFrame.fromTheBottom(unitBars.barOf(id), cam.getHeight()) : null;
        }

        @Override
        public Canvas.Box bottomPanel() {
            return firstOfTheParts(HudPart::panel);
        }
    };

    /** How sharply slanted pictures are read — see {@link Duke3D#anisotropy}. */
    private int anisotropy = 8;

    /** Every picture the world draws read at {@code level} of anisotropic filtering, from the next frame. */
    void anisotropy(int level) {
        anisotropy = level;
        if (renderer != null) {
            renderer.setDefaultAnisotropicFilter(anisotropyFor(level,
                    renderer.getLimits().get(com.jme3.renderer.Limits.TextureAnisotropy)));
        }
    }

    /** {@code asked} as the card takes it: 1, 2, 4, 8 or 16, the highest not above it, nor above the card's own. */
    static int anisotropyFor(int asked, Integer cardMost) {
        int level = 1;
        while (level * 2 <= Math.min(16, asked)) {
            level *= 2;
        }
        return cardMost == null || cardMost < 1 ? level : Math.min(level, cardMost);
    }

    /** The window made so, where the monitor shows it, and kept as the player's choice — see {@link Duke3D#display}. */
    void display(int width, int height, boolean fullscreen, java.util.function.Consumer<Boolean> told) {
        windowDisplay.display(width, height, fullscreen, told);
    }

    /** The window's sample count for the next window made: kept with the player's settings. */
    void samplesNextLaunch(int samples) {
        preferences.set("samples", samples);
        preferences.save();
    }

    /** Whether the client's own HUD is up: in play, for a game that does not draw its own. */
    private boolean clientHud() {
        return screens.now() == Screen.PLAYING && !shell.isDrawnByTheGame();
    }

    /**
     * Whether the world is drawn: always, but behind a game's own front end or load screen, where there is no world
     * to draw but the one the last match left.
     */
    private boolean worldShown() {
        return backdropShown()
                || !shell.isDrawnByTheGame() || (screens.now() != Screen.FRONT && screens.now() != Screen.LOADING);
    }

    /** Whether the match behind the front end is what is drawn. */
    private boolean backdropShown() {
        return onTheFrontEnd() && backdrop != null && backdrop.running() != null && game == backdrop.running();
    }

    /**
     * Whether the screen up is the front end's, behind which the backdrop runs: the game's own, or — for a game that
     * keeps the client's menus — its front menu, the questions it opens and the settings opened from it.
     */
    private boolean onTheFrontEnd() {
        return onTheFrontEnd(screens.now(), shell.isDrawnByTheGame(), screens.settingsReturn());
    }

    static boolean onTheFrontEnd(Screen screen, boolean drawnByTheGame, Screen settingsFrom) {
        return screen == Screen.FRONT
                || !drawnByTheGame && (screen == Screen.MENU
                        || screen == Screen.SETTINGS && settingsFrom == Screen.MENU);
    }

    /**
     * The match the client's front menu plays — kept aside while the backdrop runs in its place, for a game whose
     * front menu is the client's (see {@link #keepTheBackdropGoing}); null while it is not aside.
     */
    private DukeGame frontMatch;

    /** The match the front menu is about: the one it would play, whatever is drawn behind it. */
    private DukeGame frontGame() {
        return frontMatch != null && backdropShown() ? frontMatch : game;
    }

    /** The front menu's match put back in place of the backdrop, which is put away: it is about to be played. */
    private void takeTheFrontMatchBack() {
        if (frontMatch == null) {
            return;
        }
        if (backdrop != null) {
            backdrop.stop();
        }
        forgetTheWorld();
        camera.resetView();
        game = frontMatch;
        frontMatch = null;
        screens.menu().liveBehind(false);
    }

    /** What the game does as its backdrop becomes the played match — see {@link Duke3D#onBackdropAdopted}. */
    private java.util.function.Consumer<DukeGame> backdropAdopted;

    void onBackdropAdopted(java.util.function.Consumer<DukeGame> adopted) {
        this.backdropAdopted = adopted;
    }

    /** The backdrop made the played match, as it runs — see {@link Duke3D#adoptBackdrop}. */
    void adoptBackdrop() {
        if (backdrop == null || !backdropShown()) {
            LOG.warning("no backdrop runs behind the front end to adopt; start the match with Duke3D.startMatch");
            return;
        }
        var match = backdrop.running();
        simThread = backdrop.release();
        frontMatch = null;
        screens.menu().liveBehind(false);
        game = match;
        played = match;
        artIsReady = true; // its art was read as it was made
        left = false;
        listenForChat(match);
        screens.menu().hide();
        if (!shell.isDrawnByTheGame() && !musicByGame) {
            choices.playChosenMusic();
        }
        screens.set(Screen.PLAYING);
        var told = backdropAdopted;
        if (told != null) {
            match.runOnSimThread(() -> told.accept(match));
        }
    }

    /** Whether the match behind the front end waits to be made — see {@link Duke3D#holdBackdrop}. */
    void holdBackdrop(boolean hold) {
        if (backdrop != null) {
            backdrop.hold(hold);
        }
    }

    /** Keep the match behind the front end going, and draw it once there is one. */
    private void keepTheBackdropGoing() {
        if (!onTheFrontEnd() || backdrop == null) {
            return;
        }
        var behind = backdrop.frame();
        if (behind != null && game != behind) {
            if (!shell.isDrawnByTheGame() && frontMatch == null) {
                frontMatch = game; // what the client's front menu plays, kept for when it does
            }
            forgetKeepingTheMenu(screens.menu()::isVisible, this::forgetTheWorld, screens.menu()::reveal);
            game = behind;
        }
        screens.menu().liveBehind(backdropShown());
    }

    /**
     * Forget the world as a backdrop takes its place, and leave the menu that was up still up: forgetting hides it,
     * which is right when a match ends and wrong here, where the menu is what the player is reading over the scene.
     */
    static void forgetKeepingTheMenu(java.util.function.BooleanSupplier menuUp, Runnable forget, Runnable reveal) {
        boolean wasUp = menuUp.getAsBoolean();
        forget.run();
        if (wasUp) {
            reveal.run();
        }
    }

    /** Whether the game chooses all the music, the client none of its own — see {@link Duke3D#musicByGame}. */
    private boolean musicByGame;

    void musicByGame(boolean byGame) {
        this.musicByGame = byGame;
    }

    /** A flat loop the game started — see {@link Duke3D#loop}. */
    void loop(String cue, float fadeIn) {
        noises.sounds().loop(cue, fadeIn);
    }

    /** A loop at a place of the world, under the game's key — see {@link Duke3D#loopAt}. */
    void loopAt(String key, String cue, float x, float y) {
        if (noises != null) {
            noises.sounds().loopAt(key, cue, new Vector3f(x, floorHeightAt(x, y), y));
        }
    }

    void stopLoopAt(String key, float fadeOut) {
        if (noises != null) {
            noises.sounds().stopLoopAt(key, fadeOut);
        }
    }

    /** The loop the game started on {@code cue}, faded out — see {@link Duke3D#stopLoop}. */
    void stopLoop(String cue, float fadeOut) {
        noises.sounds().stopLoop(cue, fadeOut);
    }

    /** The game's own music by name — see {@link Duke3D#music}. */
    void music(String track, float fadeOutSeconds, float fadeInSeconds) {
        noises.sounds().music(track, fadeOutSeconds, fadeInSeconds);
    }

    /** The game's own tracks in turn — see {@link Duke3D#playlist}. */
    void playlist(List<String> tracks, float fadeOutSeconds, float fadeInSeconds) {
        noises.sounds().playlist(tracks, fadeOutSeconds, fadeInSeconds);
    }

    /** What the players of this match say reaches the game's ear on this thread. */
    private void listenForChat(DukeGame match) {
        if (chatEar != null) {
            match.onChat(line -> enqueue(() -> chatEar.accept(line)));
        }
    }

    /**
     * Play this match — see {@link Duke3D#startMatch}. Whatever runs is stopped and forgotten first, and the match
     * is built and its art read as the first one was.
     */
    void startMatch(DukeGame match) {
        if (backdrop != null) {
            backdrop.stop(); // torn down when a real match starts, made fresh when the front end comes back
        }
        frontMatch = null;
        screens.menu().liveBehind(false);
        endTheMatch();
        forgetTheWorld();
        camera.resetView(); // unturned and at its start, whatever the last match or the backdrop left it at
        game = match;
        listenForChat(match);
        artIsReady = false;
        startGame();
    }

    /** Stop the match and show the front end — see {@link Duke3D#frontEnd}. */
    void backToFrontEnd() {
        endTheMatch();
        forgetTheWorld();
        screens.showMainMenu();
    }

    /**
     * Stop whatever match is running and wait for its last frame: its simulation ends, and a network session with it
     * — the engine's thread lets go of it as it leaves. A load under way is abandoned where it stands.
     */
    private void endTheMatch() {
        if (matchLoad != null) {
            matchLoad = null;
            artLoad = null;
            loading.hide();
        }
        game.stop();
        if (simThread != null) {
            try {
                simThread.join(5000);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            simThread = null;
        }
    }

    /**
     * Put away everything drawn from a match that is over: its things, what was burning, what was selected and what
     * was about to be heard. The ground is laid again from whatever match comes next.
     */
    private void forgetTheWorld() {
        unitScene.forgetTheWorld();
        selected.clear();
        clearWhatIsBurning();
        noises.forget();
        snapshot = WorldSnapshot.EMPTY;
        events.forgetTheWorld();
        lookChanged = true; // the next match's ground is laid on its first frame, whatever it is
        buttons.hide();
        setSimulationPaused(false);
        screens.menu().hide();
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
                grid == null ? uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE
                        : grid.getCellSize(),
                // A thing sounds hurt where it starts to look hurt: its look's own words and shares.
                template -> visuals.of(template).whenHurt, visuals.getVoicedWhen());
        ground.hear();
        // What happens where he does not see, for the sounds fog does not hide.
        game.hearThroughFog(visuals.getSounds().hearsThroughFog());
        if (visuals.getSounds().hearing().placesTheListener()) {
            var atTheEye = stateManager.getState(com.jme3.audio.AudioListenerState.class);
            if (atTheEye != null) {
                stateManager.detach(atTheEye); // the game places the listener: see placeTheListener
            }
        }
        choices.applyVolume();
    }

    /** A cue the game plays itself, flat — see {@link Duke3D#sound}. */
    void playFlat(String cue) {
        noises.sounds().flat(cue, timer.getTimeInSeconds());
    }

    /** The same, and told when it has played out — see {@link Duke3D#sound(String, Runnable)}. */
    void playFlat(String cue, Runnable ended) {
        noises.sounds().flat(cue, timer.getTimeInSeconds(), ended);
    }

    /** See {@link Duke3D#cueVolume}. */
    void cueVolume(String cue, float multiplier) {
        noises.sounds().cueVolume(cue, multiplier);
    }

    /** See {@link Duke3D#volume}. */
    void gameVolume(SoundBank.Channel channel, float zeroToOne) {
        noises.sounds().gameVolume(channel, zeroToOne);
    }

    /** The whole world's picture the game asked for, at most how many texels a side, and whom it is handed to. */
    private int worldPictureMost;
    private java.util.function.Consumer<Picture> worldPictureHanded;
    private java.util.function.Predicate<Object> worldPictureFor = _ -> true;
    private WorldPicture worldPicture;

    /** See {@link Duke3D#worldPicture}. */
    void worldPicture(int mostTexels, java.util.function.Predicate<Object> forMap,
            java.util.function.Consumer<Picture> handed) {
        this.worldPictureMost = Math.max(1, mostTexels);
        this.worldPictureFor = forMap == null ? _ -> true : forMap;
        this.worldPictureHanded = handed;
        makeTheWorldPicture(game.getTerrain());
    }

    /** What else is read off a new world's grid with its minimap: the span the pointer's ray walks, and its picture. */
    private void readTheNewGrid() {
        var grid = game.getTerrain();
        pointer.readGrid(grid);
        makeTheWorldPicture(grid);
    }

    /** The game's marks on the minimap — see {@link Duke3D#minimapMarks}. */
    void minimapMarks(java.util.function.Supplier<java.util.List<MinimapMark>> marks) {
        minimapView.marks(marks);
    }

    /** The whole world's picture for the world laid now, where the game asked for one, painted and handed over. */
    private void makeTheWorldPicture(uz.dukeengine.core.pathfind.PathGrid grid) {
        if (worldPictureHanded == null || grid == null || !worldPictureFor.test(game.getMapRecord())) {
            worldPicture = null;
            return;
        }
        worldPicture = new WorldPicture(grid, worldPictureMost, visuals.drawsFog(), visuals.getFog().tintColour());
        worldPicture.paint(discovery());
        worldPictureHanded.accept(worldPicture.picture());
    }

    /**
     * The selection box: an outline, so it never hides what is being selected.
     */
    private void buildDragRectangle() {
        dragRectangle = new DragBox(visuals.getDragBox(), Materials.unshaded(assetManager, ColorRGBA.White));
        guiNode.attachChild(dragRectangle.geometry());
    }

    /**
     * Draw the order markers — three arrowheads closing on the spot that was
     * clicked. See {@link Chevrons} for the drawing and {@link OrderMark} for the
     * movement.
     */
    private void syncOrderMarkers() {
        float now = timer.getTimeInSeconds();
        lasers.show(snapshot.beams(), cam.getLocation(), now); // the simulation's beams, where it moved them
        if (ribbons == null && !visuals.getStreams().isEmpty()) {
            ribbons = new Ribbons(assetManager, rootNode, visuals.getStreams());
        }
        if (ribbons != null) {
            ribbons.show(snapshot.streams(), cam.getLocation());
        }
        ridingEffects.show(snapshot.effects(), id -> {
            var node = unitScene.node(id);
            return node == null ? null : node.root;
        });
        orders.showRallies();
        var look = visuals.getOrderMark();
        orderMarkers.prune(now, look.seconds());
        modelMarks.update(now, look);
        // A move the game answers with its own model has no arrowheads; an attack it answers with nothing, no ring.
        chevrons.show(look.model() == null ? orderMarkers.markers() : java.util.List.of(), now, this::floorHeightAt);
        attackFlash.show(orderMarkers.markers(), now, this::whereThatUnitIsNow, this::floorHeightAt);
        skillKeys.syncSkillRange(now);
        events.drawTheBlows(now, screens.now() == Screen.PLAYING);
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

    private void startGame() {
        takeTheFrontMatchBack();
        left = false; // a new match: the game is told again when this one is let go
        if (game == played && simThread == null) {
            // A match that has been played and stopped cannot be played again: the next is a fresh DukeGame.
            LOG.warning("this match has been played; start the next with Duke3D.startMatch");
            return;
        }
        if (!artIsReady) {
            // Assembled first, run later. What a match can ever draw is decided by what is in it, and
            // that is only known once the match is built — so the world is built now, not one frame of it
            // stepped, and the art plan is read off it. See DukeGame.templatesThisMatchCanDraw.
            beginLoading();
            return; // the world waits until there is something to draw it with
        }
        if (simThread == null) {
            simThread = game.startEngineOnly();
            played = game;
        }
        // Open on the player's own units rather than the middle of the map, which
        // left him hunting the map for whatever he is supposed to be controlling.
        camera.requestOwnUnit();
        screens.menu().hide();
        if (!shell.isDrawnByTheGame() && !musicByGame) {
            // Underneath everything from here on, until he goes back to the menu. A game that draws its own
            // screens, or chooses its own music, plays its own.
            choices.playChosenMusic();
        }
        screens.set(Screen.PLAYING);
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
    private void beginLoading() {
        matchLoad = new MatchLoad(game, built -> artLoad = artLoadFor(built),
                percent -> {
                    if (loadingEar != null) {
                        loadingEar.accept(percent);
                    }
                }, holdStart);
        screens.menu().hide();
        if (!shell.isDrawnByTheGame()) {
            loading.show(game.getTitle()); // a game that draws its own screens draws its own load screen
        }
        screens.set(Screen.LOADING);
    }

    /** The art a match built can draw, read ahead of its first frame — see {@link ArtLoad}. */
    private ArtLoad artLoadFor(DukeGame built) {
        return new ArtLoad(built.templatesThisMatchCanDraw(), visuals, artStore);
    }

    private void advanceLoadingArt() {
        matchLoad.frame();
        var failure = matchLoad.failure();
        if (failure != null) {
            LOG.log(java.util.logging.Level.SEVERE, "the match could not be built", failure);
            matchLoad = null;
            artLoad = null;
            loading.hide();
            screens.showMainMenu();
            return;
        }
        loading.progress(matchLoad.percent() / 100f, artLoad == null ? "" : artLoad.what());
        if (!matchLoad.ready()) {
            return;
        }
        artIsReady = true;
        artLoad = null;
        matchLoad = null;
        loading.hide();
        startGame();
    }

    /** Whether a loaded match waits for the game — see {@link Duke3D#holdMatchStart}. */
    void holdMatchStart(boolean hold) {
        this.holdStart = hold;
    }

    /** Let a held match start — see {@link Duke3D#releaseMatchStart}. */
    void releaseMatchStart() {
        if (matchLoad != null) {
            matchLoad.release();
        }
    }

    /** The match let go, the game told first, and the front end shown. */
    private void quitToTheMainMenu() {
        leave();
        backToFrontEnd();
    }

    private com.jme3.font.BitmapFont fontOrDefault(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return guiFont;
        }
        try {
            return assetManager.loadFont(assetPath);
        } catch (RuntimeException _) {
            missing.warn(assetPath, "font");
            return guiFont;
        }
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
        for (var part : parts) {
            part.resized(width, height);
        }
        minimapView.place();
        screens.rebuild(width, height);
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

    /**
     * Whether the look has moved since the world was last built.
     *
     * <p>Noted rather than acted on, so that a change of look and a change of
     * floor -- which arrive together -- are one rebuild and not two. It also
     * covers the first frame of all: the world is built when the window opens,
     * before any snapshot has said what it is made of.
     */
    private boolean lookChanged;

    /** Take up the look the game says this floor wears — see {@link Looks#adopt} — and its dark. */
    private void adoptTheLookTheGameNames() {
        if (!looks.adopt(snapshot)) {
            return;
        }
        var tint = looks.floorFogTint();
        if (tint != null) {
            fog.tint(tint);
        }
        lookChanged = true;
        // Every creature is drawn again, because some of them are drawn
        // differently now and the ones that are not cost a node each.
        unitScene.forgetAll();
    }

    /** Build (or rebuild) the ground and its dark for the world as it stands now. */
    private void buildTerrain() {
        // First: every material built from here on reads the sun, and this is the world it is for.
        light.adopt(sunlightOfTheGame());
        clearWhatIsBurning();
        ground.build();
        fog.newWorld(ground.builtFrom(), game.getMapRecord());
    }

    /** A new world is a world with nothing burning in it yet. */
    private void clearWhatIsBurning() {
        if (layered != null) {
            layered.clear();
        }
        if (particles != null) {
            particles.clear();
            particleDrawing.clear();
        }
        if (listShow != null) {
            listShow.clear();
        }
        if (hitFlash != null) {
            hitFlash.clear();
        }
        effects.clear();
        if (skillEffects != null) {
            skillEffects.clear();
        }
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
        if (game.getTerrain() == ground.builtFrom() && !lookChanged) {
            return;
        }
        // A rebuilt world has nothing burning in it, and whoever is in it has just
        // arrived: even a hero whose id and floor match the last run's, and even when
        // the floor's look catches up with the floor a moment late -- which rebuilds
        // it again, and puts out the light he arrived in.
        events.newWorld();
        parts.forEach(HudPart::newWorld);
        lookChanged = false;
        buildTerrain();
        minimapView.rebuild();
        readTheNewGrid();
        noises.forget(); // a new floor; nothing about the last one is news
        orderMarkers.clear(); // orders given in the old world mean nothing here
        if (modelMarks != null) {
            modelMarks.clear();
        }
        chevrons.clear();
        attackFlash.clear();
        lasers.clear();
        ridingEffects.clear();
        rallyMarks.clear();
        unitScene.newWorld();
        unitBars.clear();
        camera.requestOwnUnit(); // his units are somewhere else entirely now
        // And the hero he had selected is not this floor's hero: his skills need him picked out.
        orders.newWorld();
    }

    // ---- input ----

    private void installInput() {
        keyBindings = new KeyBindings(inputManager, hotkeys, controls, preferences);
        // What each key does is worked out in one place, pressKey.
        ActionListener actions = (name, pressed, _) -> {
            if (sceneLocked() && pressed && !name.startsWith(KeyBindings.KEY) && inPlay()) {
                return; // a scene the game plays: no picking, selecting or ordering until it lets go
            }
            switch (name) {
                case "Select" -> {
                    if (screens.menu().isVisible()) {
                        if (!pressed) {
                            screens.menu().release(); // letting go lets go of the slider
                        } else if (screens.menu().click(inputManager.getCursorPosition())) {
                            noises.moment("menu_click", timer.getTimeInSeconds());
                        }
                    } else if (screens.now() == Screen.PLAYING) {
                        if (!pressed && skillKeys.holdingASkill()) {
                            skillKeys.letGoOfHeldSkill(true); // he pressed its slot; this is the cast
                            break;
                        }
                        if (pressed && skillKeys.clickedASkillSlot()) {
                            // The bar took it; nothing else may have it.
                            break;
                        }
                        if (skillKeys.holdingASkill()) {
                            break; // the mouse has no part in a skill being held
                        }
                        if (pressed && buttons.commandBarClick()) {
                            // The bar took it -- before anything armed does, so pressing a
                            // second button is that button and not a place for the first.
                        } else if (pressed && !pointer.pointerOnTheWorld()) {
                            // Off the world's part of the window: nothing of the world is there to take it.
                        } else if (pressed && buttons.armed()) {
                            buttons.aimArmedButton(); // this click is the armed button's place or thing
                        } else if (!pressed && buttons.placing()) {
                            buttons.releaseArmedButton(); // let go: put down where it went down, turned as dragged
                        } else if (pressed && skillKeys.armed()) {
                            // This click belongs to the armed key, not to selection.
                            // The release is swallowed with it, or letting go would
                            // end a drag that never began.
                            skillKeys.aimArmedKey();
                        } else if (scheme.left(pressed)) {
                            // the scheme's: a lock, a swing or a strike ahead, and no selecting by the left button
                        } else if (pressed) {
                            orders.beginDrag();
                        } else if (orders.dragging()) {
                            orders.endDrag(controls.isHeld(KeyMap.Control.ADD_TO_SELECTION));
                        }
                    }
                }
                case "Turn" -> cameraRig.turnByTheMiddleButton(pressed);
                case "Order" -> {
                    if (screens.now() != Screen.PLAYING) {
                        break;
                    }
                    var over = inputManager.getCursorPosition();
                    if (visuals.getRightDrag().wanted()) {
                        // As the game framed it, whichever button commands: held, the right button scrolls, and it
                        // acts only as it is let go as a click — the reference's SCROLL_RMB, and its alternate
                        // mouse's order given on the click's release.
                        float y = cam.getHeight() - over.y;
                        if (pressed) {
                            cameraRig.rightDown(over.x, y);
                        } else if (cameraRig.rightUp(over.x, y)) {
                            if (buttons.armed() || skillKeys.armed() || mouse.rightClickLetsGo()) {
                                secondThoughts();
                            } else if (pointer.pointerOnTheWorld() && !onAPanel(over.x, over.y)) {
                                orders.order();
                            }
                        }
                    } else if (pressed && (buttons.armed() || skillKeys.armed() || mouse.rightClickLetsGo())) {
                        secondThoughts();
                    } else if (pressed && pointer.pointerOnTheWorld() && !onAPanel(over.x, over.y)
                            && scheme.rightOrders(snapshot.contextOrder() != null, pointer.pickUnit() != null)) {
                        orders.order(); // a right-click on the bar, or off the world, is not an order to the world
                    }
                }
                default -> {
                    if (name.startsWith(KeyBindings.KEY)) {
                        pressKey(Integer.parseInt(name.substring(KeyBindings.KEY.length())), pressed);
                        return;
                    }
                    if (screens.now() != Screen.PLAYING) {
                        return;
                    }
                    if (!pressed) {
                        // Letting go of a skill that was drawn while it was held.
                        // See SkillRange.castOnRelease.
                        if (name.startsWith(KeyBindings.HOTKEY)
                                && skillKeys.armedWith(name.charAt(KeyBindings.HOTKEY.length()))) {
                            if (scheme.aiming() && scheme.castsOnRelease()) {
                                skillKeys.aimArmedKey(); // aimed from him, cast as the key is let go
                                return;
                            }
                            skillKeys.letGoOfHeldSkill(false);
                        }
                        return;
                    }
                    if (name.startsWith(KeyBindings.HOTKEY)) {
                        char letter = name.charAt(KeyBindings.HOTKEY.length());
                        if (ctrlHeld[0]) {
                            // ★ Ctrl and the letter spends a level on that skill,
                            // which is Warcraft's arrangement and is worth copying
                            // for the reason it was chosen there: the hand is
                            // already on the letter. A modifier rather than a key
                            // of its own means nothing new to learn and nothing to
                            // press by accident -- Q alone still casts, and always
                            // will.
                            skillKeys.raiseSkill(letter);
                        } else {
                            skillKeys.pressHotkey(letter, false);
                        }
                    }
                }
            }
        };
        keyBindings.install(actions);

        AnalogListener zoom = (name, _, _) -> {
            if (screens.now() == Screen.PLAYING && snapshot.camera() == null && !sceneLocked()) {
                camera.wheel(name.equals("ZoomIn")); // nobody's to zoom while the game has it
            }
        };
        inputManager.addListener(zoom, "ZoomIn", "ZoomOut");
    }

    /**
     * A key, down or up. On a menu it walks the menu. In play: Escape first gives up an aim; a letter the game
     * claimed for itself is the game's; then whatever the game's key map puts on it; then the command bar button
     * showing it; then, if the digits are free, a build.
     */
    private void pressKey(int code, boolean pressed) {
        switch (code) {
            case KeyInput.KEY_LSHIFT, KeyInput.KEY_RSHIFT -> shiftDown = pressed;
            case KeyInput.KEY_LCONTROL, KeyInput.KEY_RCONTROL -> ctrlHeld[0] = pressed;
            case KeyInput.KEY_LMENU, KeyInput.KEY_RMENU -> altDown = pressed;
            default -> {
            }
        }
        if (!pressed) {
            controls.release(code);
            return;
        }
        var key = new KeyMap.Key(code, ctrlHeld[0], shiftDown, altDown);
        if (screens.menu().isVisible() || screens.now() != Screen.PLAYING) {
            screens.key(code, key);
            return;
        }
        if (sceneLocked() && code != KeyInput.KEY_ESCAPE) {
            return; // the scene's: Esc alone still reaches the menu
        }
        if (code == KeyInput.KEY_ESCAPE && (buttons.armed() || skillKeys.armed())) {
            if (buttons.armed()) {
                buttons.disarmButton(); // mid-aim with a bar button: one keypress deep
            } else {
                skillKeys.disarm(); // mid-aim with a key: the same
            }
            return;
        }
        char letter = KeyBindings.letterOf(code);
        if (hotkeys.claims(code)) {
            return; // the game's own key, answered by its own mapping
        }
        var press = controls.press(key, timer.getTimeInSeconds(), controlScene);
        if (press.taken()) {
            if (press.control() != null) {
                clientControl(press.control());
            }
            return;
        }
        if (letter != 0 && buttons.pressBarKey(letter)) {
            return;
        }
        int digit = code - KeyInput.KEY_1 + 1;
        if (digit >= 1 && digit <= 9 && !controls.map().takesDigits()) {
            orders.queueBuild(digit - 1);
        }
    }

    /** A control of the client's own that is not about the world: the menu, the bar, chat, a picture, the clock. */
    private void clientControl(KeyMap.Control control) {
        switch (control) {
            case OPTIONS -> {
                if (!shell.isDrawnByTheGame()) {
                    screens.showPauseMenu(); // a game that draws its own screens has its own for this
                }
            }
            case TOGGLE_COMMAND_BAR -> buttons.toggle();
            case CHAT_ALL -> hotkeys.chat(game, true);
            case CHAT_ALLIES -> hotkeys.chat(game, false);
            case SCREENSHOT -> {
                if (screenshots == null) {
                    screenshots = new com.jme3.app.state.ScreenshotAppState("", "screenshot");
                    stateManager.attach(screenshots);
                }
                screenshots.takeScreenshot();
            }
            // Set here rather than queued for the simulation: a paused engine does not step, so a task asking it
            // to resume would never be reached and the pause could not be lifted.
            case PAUSE -> setSimulationPaused(!game.getLogic().isGamePaused());
            case FULLSCREEN -> windowDisplay.toggleFullscreen();
            default -> {
            }
        }
    }

    /** What the client's controls are handed: the world as this client draws it. */
    private final Controls.Scene controlScene = new Controls.Scene() {
        @Override
        public List<uz.dukeengine.core.view.UnitView> units() {
            return snapshot == null ? List.of() : snapshot.units();
        }

        @Override
        public int localPlayer() {
            return game.getLocalPlayerIndex();
        }

        @Override
        public Set<Integer> selection() {
            return selected;
        }

        @Override
        public boolean onScreen(uz.dukeengine.core.view.UnitView unit) {
            // The world's part of the window, as the reference's AcrossScreen passes read its tactical view.
            var at = cam.getScreenCoordinates(new Vector3f(unit.x(), floorHeightAt(unit.x(), unit.y()), unit.y()));
            return at.z < 1f && worldRegion.contains(at.x, at.y, cam.getWidth(), cam.getHeight());
        }

        @Override
        public boolean hasKind(uz.dukeengine.core.view.UnitView unit, String kind) {
            var template = game.getLogic().getThingFactory().findTemplate(unit.templateName());
            return template != null && uz.dukeengine.core.thing.Classified.of(template)
                    .contains(uz.dukeengine.core.thing.Kind.of(kind));
        }

        @Override
        public float sizeOf(uz.dukeengine.core.view.UnitView unit) {
            var template = game.getLogic().getThingFactory().findTemplate(unit.templateName());
            return template == null ? 0f : uz.dukeengine.core.thing.Solid.of(template).footprintRadius();
        }

        @Override
        public CameraFocus camera() {
            return camera;
        }

        @Override
        public void stop(List<Integer> units) {
            if (!units.isEmpty()) {
                game.postCommand(new CombatOrder.StopMoving(game.getLocalPlayerIndex(),
                        units.stream().map(ObjectId::new).toList()));
            }
        }

        @Override
        public void move(Map<Integer, Coord3D> destinations) {
            destinations.forEach((unit, to) -> game.postCommand(new CombatOrder.MoveTo(game.getLocalPlayerIndex(),
                    List.of(new ObjectId(unit)), to)));
        }

        @Override
        public void answer(int unit) {
            orders.answerSelection(unit);
        }
    };

    /** Whether a point of the window is on a part's panel. */
    private boolean onAPanel(float x, float y) {
        return parts.stream().anyMatch(part -> part.covers(x, y));
    }

    /** The first answer of the parts that has one, or {@code null}. */
    private <T> T firstOfTheParts(java.util.function.Function<HudPart, T> asked) {
        for (var part : parts) {
            var answer = asked.apply(part);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    // ---- per-frame sync ----

    @Override
    public void simpleUpdate(float tpf) {
        frameOf(tpf);
        interfaceHiding.apply(interfaceHidden.getAsBoolean(), guiViewPort, guiNode,
                List.of(veil.node(), canvas.node()), markerNode);
    }

    /** Whether the game has its interface put away now — see {@link Duke3D#interfaceHidden}. */
    private java.util.function.BooleanSupplier interfaceHidden = () -> false;
    private final InterfaceHiding interfaceHiding = new InterfaceHiding();

    void interfaceHidden(java.util.function.BooleanSupplier hidden) {
        this.interfaceHidden = hidden == null ? () -> false : hidden;
    }

    /** One frame of the window. */
    private void frameOf(float tpf) {
        noises.sounds().update(tpf);
        frame(tpf);
        canvas.frame(tpf);
    }

    private void frame(float tpf) {
        keepTheBackdropGoing();
        rootNode.setCullHint(worldShown() ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        followTheWindowSize();
        layTheWorldRegion();
        showOnlyWhilePlaying();
        snapshot = game.getSnapshot();
        // What the pointer is on, so the next snapshot says whether an attack on it would be taken.
        boolean pointing = inPlay();
        var pointedAt = pointing ? pointer.pickUnit() : null;
        var pointedGround = pointing && pointedAt == null ? pointer.pickGround() : null;
        game.setPointedAt(pointedAt == null || pointedAt.view == null ? -1 : pointedAt.view.id(),
                pointedGround == null ? null : new Coord3D(pointedGround.x, pointedGround.z, pointedGround.y),
                pointedGround == null || discovery() == null || discovery().everSeen(pointedGround.x, pointedGround.z));
        // Before every early return below, not after them. The menu and the
        // loading screen are screens too, and a pointer that only appears once
        // the world does leaves the player clicking Play with the operating
        // system's arrow -- which is the first thing he sees of the game.
        showTheRightPointer();
        if (screens.menu().isVisible()) {
            // A held slider follows the hand every frame; nothing else does, and
            // it makes no noise about it — a knob being pulled would be forty
            // clicks a second.
            if (screens.menu().isDragging()) {
                screens.menu().drag(inputManager.getCursorPosition());
            } else if (screens.menu().hover(inputManager.getCursorPosition())) {
                noises.moment("menu_hover", timer.getTimeInSeconds());
            }
        }
        if ((screens.now() == Screen.MENU || screens.now() == Screen.FRONT) && !backdropShown()) {
            hud.setText("");
            buildMenu.setText("");
            return; // the world starts when the player presses Play
        }
        if (screens.now() == Screen.LOADING) {
            advanceLoadingArt();
            return; // and it waits until there is art to start it with
        }
        // Before the world is rebuilt, not after: a new floor and the look it
        // wears arrive in the same snapshot, and terrain built from the last
        // floor's kit would be a floor of the wrong stone until the next one.
        adoptTheLookTheGameNames();
        refreshWorldIfChanged(); // a new run lays out a new world; redraw it
        ground.stream(camera.targetX(), camera.targetZ()); // the ground as far as the camera sees, on a wide world
        if (fog.sync(tpf)) {
            var seen = fog.discovery();
            ground.applyDiscovery(seen);
            minimapView.paintWhatIsKnown();
            if (worldPicture != null) {
                worldPicture.heard(seen);
                worldPicture.paint(seen);
            }
        }
        light.step(tpf);
        fog.followTheLookUnderTheCamera(tpf);
        camera.focusOnOwnUnit(snapshot.units(), game.getLocalPlayerIndex());
        cameraRig.frame(tpf);
        sceneSlot.update(tpf);
        game.setViewRays(worldRegion.raysThrough(cam));
        // Before the units, not after: a death has to take the body out of the
        // live list before anything decides it merely vanished. It also means a
        // shot lights its muzzle on the frame it was fired rather than the next.
        events.read();
        ground.layWhatTheGameLaid();
        // What this frame is worth hearing. Reads the same snapshot everything
        // else does and writes nothing back -- see GameSounds.
        placeTheListener();
        letItFall();
        noises.frame(snapshot, game.getLocalPlayerIndex(),
                timer.getTimeInSeconds());
        for (var event : snapshot.unseenEvents()) {
            if (event instanceof uz.dukeengine.core.event.SoundPlayed sound
                    && noises.sounds().throughFog(sound.cue())) {
                events.heard(sound); // where he does not see: heard, as fog does not hide it, and nothing shown
            }
        }
        orders.keepHisOwnSelected();
        orders.tellTheGameWhatHeIsLookingAt();
        unitScene.sync();
        unitScene.seatTheRiders();
        unitScene.flashTheNewlySelected(tpf);
        showUnitBars();
        // After the units, because a burst lit this frame has to reach the stone
        // this frame — the terrain reads its lights off a material parameter, not
        // out of the scene, so nothing tells it but this.
        skillEffects.update(tpf);
        hitFlash.update(tpf);
        layered.update(tpf, cam);
        particles.step(tpf);
        listShow.update(tpf);
        unitScene.update(tpf);
        particleDrawing.draw(cam);
        groundLooks.carryTheLightsToTheStone(effects.allLights());
        groundLooks.hazeRoundTheCamera();
        groundLooks.slideTheShades();
        if (snapshot != null) {
            ground.see(snapshot);
        }
        markTheHidden();
        ground.colourTheBorder(viewPort.getBackgroundColor()); // the unexplored colour, whatever the theme makes it
        unitScene.reapTheDead();
        minimapView.follow();
        minimapView.syncDots();
        minimapView.syncMarks();
        minimapView.outline(worldRegion, pointer::groundUnder);
        orders.syncDragRectangle();
        syncOrderMarkers();
        updateHud(tpf);
        orders.keepTheSelectionInStep();
        buttons.updateCommandBar();
        buttons.followTheAim(); // the bar's aim or the game's, whether or not the client's bar is up
        updateBanner();
        minimapView.place();
    }

    /**
     * The one creature a panel describes, or nobody: the one selected, while the HUD is up. The same rule the card
     * itself is built from — see {@link Orders#tellTheGameWhatHeIsLookingAt}.
     */
    private UnitView portraitSubject() {
        if (!clientHud() || selected.size() != 1) {
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

    /** Whether a held control of the client's is held now, and play is what is going on. */
    private boolean held(KeyMap.Control control) {
        return inPlay() && controls.isHeld(control);
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
    private void showOnlyWhilePlaying() {
        var hint = clientHud() ? Spatial.CullHint.Never : Spatial.CullHint.Always;
        minimapView.show(clientHud());
        if (controlsHint != null) {
            controlsHint.setCullHint(hint);
        }
        if (!clientHud()) {
            // Said here rather than beside the rest of the parts' work because the menu and the loading screen leave
            // this method before reaching that, and a viewport nobody switches off goes on drawing.
            parts.forEach(HudPart::hide);
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
        var over = whatThePointerIsOver();
        if (over.scrolling() == null && !over.overPanel() && buttons.refusesTheThingPointedAt(over.overUnit())) {
            cursors.showFirst(java.util.List.of(Cursors.DENY)); // the game will not take this thing for the armed button
            return;
        }
        var aimPointer = buttons.aimPointer();
        if (aimPointer != null && over.scrolling() == null && !over.overPanel()) {
            cursors.show(aimPointer); // the pointer the game named for this aim, over the world
            return;
        }
        cursors.showFirst(Cursors.overCanvas(canvasPointer, canvasInputs != null && canvasInputs.pointerTaken(),
                Cursors.situationsFor(over)));
    }

    /** The pointer the game named for while the pointer is over its canvas, or null for the client's own choice. */
    private String canvasPointer;

    /** See {@link Duke3D#canvasPointer}. */
    void canvasPointer(String situation) {
        this.canvasPointer = situation == null || situation.isBlank() ? null : situation;
    }

    /**
     * The six facts about the screen the choice is made from.
     */
    private Cursors.Over whatThePointerIsOver() {
        boolean playing = inPlay();
        if (!playing) {
            return new Cursors.Over(false, null, false, false, false, false);
        }
        var at = inputManager.getCursorPosition();
        var armed = skillKeys.armedAim();
        // Armed is not aiming -- see Hotkeys.Aim.needsPointing. A skill that goes
        // off round him is held to be looked at, and while it is the pointer goes
        // on answering the ordinary questions.
        boolean aiming = armed != null && armed.needsPointing();
        boolean canReach;
        if (aiming) {
            canReach = armed != Hotkeys.Aim.OPEN_GROUND
                    || pointer.isOpenAndSeen(pointer.groundUnder(at.x, at.y));
        } else {
            canReach = pointer.couldStandThere(pointer.groundUnder(at.x, at.y));
        }
        var over = aiming ? null : pointer.pickUnit();
        // Over a window of the game's own canvas, or off the world's part of the window, is over the panel: the
        // arrow, as the reference shows it over any window of its interface.
        boolean overPanel = onAPanel(at.x, at.y) || minimapView.covers(at)
                || canvasInputs != null && canvasInputs.pointerTaken() || !pointer.pointerOnTheWorld();
        var own = orders.selectedOwn();
        return new Cursors.Over(true, armed, canReach, overPanel,
                over != null, over != null && over.view.playerIndex() == game.getLocalPlayerIndex(),
                snapshot.attackable(), !own.isEmpty(), snapshot.contextOrder(), // a thing's, or the ground's
                cameraRig.scrolling(),
                Orders.groundTakes(game.getLocalPlayerIndex(), own, game.namesGroundOrders(), parts),
                controls.isHeld(KeyMap.Control.FORCE_ATTACK));
    }

    /**
     * A right click's second thoughts: an armed button given up, wherever the pointer is — a bar's or the game's — or
     * an armed key, the way a right-click always means; or, on the reference's left-click mouse, the selection let go.
     */
    private void secondThoughts() {
        if (buttons.armed()) {
            buttons.disarmButton();
        } else if (skillKeys.armed()) {
            skillKeys.disarm();
        } else if (mouse.rightClickLetsGo()) {
            selected.clear();
        }
    }

    /** Whether no bar is laid over the world: behind a game's front end, or the match a menu shows behind it. */
    static boolean barsHidden(Screen screen, boolean backdropShown) {
        return screen == Screen.FRONT || backdropShown;
    }

    /** The bars over the things drawn, but behind a front end — see {@link UnitScene#showBars}. */
    private void showUnitBars() {
        if (barsHidden(screens.now(), backdropShown())) {
            unitBars.clear(); // behind a front end: a picture, with nothing of play laid over it
            return;
        }
        barReading = unitScene.showBars(unitBars, visuals.getUnitBars().plain() == null ? null : pointer.pickUnit());
    }

    /**
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
                : terrain.groundHeight(new uz.dukeengine.core.math.Coord3D(worldX, worldY, 0f));
    }

    /** What the player has seen, or null for a game that is never discovered, or before the window opened. */
    private Discovery discovery() {
        return fog == null ? null : fog.discovery();
    }

    /** A strip's picture by its whole path, or null where it will not load — said once. */
    private com.jme3.texture.Texture stripPicture(String path) {
        try {
            return assetManager.loadTexture(path);
        } catch (RuntimeException notThere) {
            if (missingStripPictures.add(path)) {
                LOG.warning(() -> "a picture strip names a picture that will not load: " + path);
            }
            return null;
        }
    }

    private final java.util.Set<String> missingStripPictures = new java.util.HashSet<>();

    /**
     * Where the sounds are heard from this frame: where the game places the listener — on the line from the ground
     * point the view looks at to the eye, facing as the view faces, with no speed ({@code AudioManager::update}) —
     * else at the eye, as jME keeps it; and how far the eye is from it.
     */
    private void placeTheListener() {
        var hearing = visuals.getSounds().hearing();
        var eye = cam.getLocation();
        if (!hearing.placesTheListener()) {
            noises.sounds().hear(eye.clone(), 0f);
            return;
        }
        var ground = new Vector3f(camera.targetX(), floorHeightAt(camera.targetX(), camera.targetZ()),
                camera.targetZ());
        var at = Sounds.listenerAt(ground, eye, hearing.listenerHeight(), hearing.listenerShare());
        listener.setLocation(at);
        listener.setRotation(cam.getRotation());
        listener.setVelocity(Vector3f.ZERO); // the view moving bends no sound's pitch
        noises.sounds().hear(at, eye.distance(at));
    }

    /**
     * One material a picture and a blend, shared by every particle system drawn with both: the picture times
     * each particle's colour and alpha, blended as the system says — added, by alpha, by an alpha test, or
     * multiplied with what is behind it.
     */
    private Material particleMaterial(uz.dukeengine.core.content.ParticleSystem system) {
        return particleMaterials.computeIfAbsent(system.texture() + "|" + system.blend(), _ -> {
            var material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            material.setBoolean("VertexColor", true);
            if (system.texture() != null) {
                try {
                    material.setTexture("ColorMap", assetManager.loadTexture(system.texture()));
                } catch (RuntimeException notThere) {
                    LOG.warning(() -> "the particle system '" + system.name() + "' names a picture that will not"
                            + " load: " + system.texture() + " (" + notThere.getMessage() + ")");
                }
            }
            var state = material.getAdditionalRenderState();
            state.setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
            switch (system.blend()) {
                case ADDITIVE -> {
                    state.setBlendMode(com.jme3.material.RenderState.BlendMode.Additive);
                    state.setDepthWrite(false);
                }
                case ALPHA -> {
                    state.setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
                    state.setDepthWrite(false);
                }
                case ALPHA_TEST -> material.setFloat("AlphaDiscardThreshold", 0.5f);
                case MULTIPLY -> {
                    state.setBlendMode(com.jme3.material.RenderState.BlendMode.Modulate);
                    state.setDepthWrite(false);
                }
            }
            return material;
        });
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
                var node = unitScene.node(unitId);
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
                return fog.canSee(x, z);
            }
        };
    }

    /** A thrown piece's house-colour meshes in the colour the thing it was thrown for wears, dead or alive. */
    private void paintThrown(Spatial piece, Spatial thing) {
        Integer wears = thing.getUserData("wears");
        if (visuals.getHouseColour() != null && wears != null) {
            Bodies.paintOwner(piece, visuals.getHouseColour(), Materials.colour(game.getColor(wears)));
        }
    }

    private void updateHud(float tpf) {
        // With a menu up there is no panel and no figures along the top: the
        // menu is what he is looking at, and a bar left showing underneath it
        // has a hole in it where the minimap was culled.
        if (!clientHud()) {
            parts.forEach(HudPart::hide);
            hud.setText("");
            buildMenu.setText("");
            return;
        }
        String power = snapshot.localPlayerPowerSurplus() >= 0
                ? "+" + snapshot.localPlayerPowerSurplus()
                : String.valueOf(snapshot.localPlayerPowerSurplus());
        // A game whose status the panel can draw gets it drawn; anything else is
        // still written out along the top, which is what every game got before.
        var cursor = inputManager.getCursorPosition();
        var frame = new HudPart.Frame(snapshot, tpf, !screens.menu().isVisible() && !snapshot.paused(),
                portraitSubject(), cursor.x, cursor.y, screens.now() == Screen.PLAYING);
        boolean drawn = false;
        for (var part : parts) {
            drawn |= part.show(frame);
        }
        // Money, power and a selection count are an RTS's figures, and a game
        // that draws its own panel has already said what it wants said. Writing
        // them along the top of a dungeon anyway is the client talking over the
        // game about a resource the game does not have.
        hud.setText(drawn ? "" : "$ %d    power %s    t=%.1fs    selected %d%s%s%s".formatted(
                snapshot.localPlayerMoney(), power, snapshot.gameTimeSeconds(),
                selected.size(), orders.selectedHealth(),
                snapshot.hasStatus() ? "    " + snapshot.status() : "",
                snapshot.paused() ? "    [PAUSED]" : ""));

        var producer = orders.selectedProducer();
        if (producer == null) {
            buildMenu.setText("");
            return;
        }
        var text = new StringBuilder();
        for (var part : parts) {
            text.append(part.buildMenu(game, producer));
        }
        buildMenu.setText(text.toString());
    }

    private void updateBanner() {
        banner.show(snapshot.hasBanner() && !shell.isDrawnByTheGame() ? snapshot.banner() : null,
                cam.getWidth(), cam.getHeight());
    }

    /** A move the game gave, answered as the player's own click there is — see {@link Duke3D#answerMove}. */
    void answerMove(Coord3D place) {
        orders.answerMove(place);
    }

    /** Arm a button of the game's own canvas as the bar arms its own — see {@link Duke3D#aim}. */
    void armFromGame(uz.dukeengine.core.view.CommandButton button, float radius, String pointer, AimDecal decal,
            java.util.function.Consumer<AimOutcome> told) {
        buttons.armFromGame(button, radius, pointer, decal, told);
    }

    /** The armed button given up by the game — see {@link Duke3D#giveUpAim}. */
    void giveUpAimFromGame() {
        buttons.giveUpAimFromGame();
    }

    /** The armed button used by the game at {@code place} — see {@link Duke3D#useAim}. */
    void useAimFromGame(Coord3D place) {
        buttons.useAimFromGame(place);
    }

    void hazeColour(int rgb, float seconds) {
        fog.hazeColour(rgb, seconds);
    }

    void lightScale(float times) {
        if (layered != null) {
            layered.lightScale(times);
        }
    }

    /** The colour over the world alone, drawn in a view of its own after the world's and before the interface's. */
    private Geometry worldTintQuad;

    void worldTint(int argb) {
        float alpha = ((argb >>> 24) & 0xFF) / 255f;
        if (worldTintQuad == null) {
            if (alpha <= 0f) {
                return;
            }
            var view = renderManager.createMainView("world-tint", cam);
            view.setClearFlags(false, false, false);
            var tintNode = new Node("world-tint");
            worldTintQuad = new Geometry("world-tint", new com.jme3.scene.shape.Quad(1f, 1f));
            var material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            material.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
            material.getAdditionalRenderState().setDepthTest(false);
            worldTintQuad.setMaterial(material);
            worldTintQuad.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
            worldTintQuad.setCullHint(Spatial.CullHint.Never);
            tintNode.attachChild(worldTintQuad);
            view.attachScene(tintNode);
        }
        worldTintQuad.setLocalScale(cam.getWidth(), cam.getHeight(), 1f);
        var colour = Materials.rgb(argb);
        colour.a = alpha;
        worldTintQuad.getMaterial().setColor("Color", colour);
        worldTintQuad.setCullHint(alpha <= 0f ? Spatial.CullHint.Always : Spatial.CullHint.Never);
        var root = worldTintQuad.getParent();
        root.updateLogicalState(0f);
        root.updateGeometricState();
    }

    /** The pictures laid over all the ground shown or put away — see {@link Duke3D#groundShades}. */
    void groundShades(boolean shown) {
        groundLooks.groundShades(shown);
    }

    void sunlight(Sunlight light, float seconds) {
        this.light.sunlight(light, seconds);
    }
}
