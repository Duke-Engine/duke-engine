package uz.dukeengine.game;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.network.CommandCodec;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.core.thing.Titled;
import uz.dukeengine.core.thing.WorldTemplate;
import uz.dukeengine.game.swing.GameWindow;
import uz.dukeengine.game.view.WorldSnapshot;

/**
 * The Unity-style entry point of duke-engine: build a playable RTS in a few
 * lines, batteries included — window, camera, unit selection, right-click
 * orders, fog of war, economy and combat all built in.
 *
 * <pre>{@code
 * var game = DukeGame.create("My RTS")
 *         .loadUnits(DukeGame.STARTER_UNITS)
 *         .map(60, 40);
 *
 * var you = game.addPlayer("USA", Color.CYAN);
 * var foe = game.addPlayer("China", Color.RED);
 * game.enemies(you, foe).localPlayer(you).money(you, 1000);
 *
 * game.spawn("Barracks", you, 60, 60);
 * game.spawn("Tank", foe, 400, 250);
 *
 * game.start(); // opens the window and runs until closed
 * }</pre>
 *
 * <p>Everything configured before {@link #start()} is applied when the engine
 * boots; callbacks ({@link #onTick}, {@link #everySeconds}, …) then run on the
 * simulation thread, where it is safe to call {@link #spawn} and
 * {@link #getLogic()} directly. {@link #runHeadless} runs the same game without
 * a window — for tests, balancing sims, or servers.
 */
public final class DukeGame {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(DukeGame.class.getName());

    /**
     * Shown when the worlds diverge. Plain ASCII on purpose: the 3D client draws
     * banners with a bitmap font that has no glyph for a dash it has never seen.
     */
    static final String DESYNC_MESSAGE = "Synchronization lost - game stopped";

    private final String title;
    private final List<UnitText> unitTexts = new ArrayList<>();
    private final List<ThingTemplate> units = new ArrayList<>();
    private final List<uz.dukeengine.rts.module.Weapon> weapons = new ArrayList<>();
    private final List<uz.dukeengine.rts.player.Upgrade> upgrades = new ArrayList<>();
    private final List<uz.dukeengine.rts.module.WeaponBonus> weaponBonuses = new ArrayList<>();
    private final List<GamePlayer> players = new ArrayList<>();
    private final List<Runnable> scenario = new ArrayList<>();
    private final List<Consumer<DukeGame>> startCallbacks = new ArrayList<>();
    private final List<Consumer<DukeGame>> tickCallbacks = new ArrayList<>();
    private final List<BiConsumer<uz.dukeengine.core.thing.GameObject, uz.dukeengine.core.thing.GameObject>>
            producedCallbacks = new ArrayList<>();
    private final List<BiConsumer<uz.dukeengine.core.thing.GameObject, uz.dukeengine.core.thing.GameObject>>
            constructedCallbacks = new ArrayList<>();
    private final List<Consumer<uz.dukeengine.core.thing.GameObject>> soldCallbacks = new ArrayList<>();
    private final List<Consumer<GameMessage.GameOrder>> orderCallbacks = new ArrayList<>();
    private final List<Consumer<uz.dukeengine.core.event.ObjectDied>> diedCallbacks = new ArrayList<>();
    private final List<double[]> intervalSeconds = new ArrayList<>(); // [seconds, callbackIndex]
    private final List<Consumer<DukeGame>> intervalCallbacks = new ArrayList<>();
    private final List<BiConsumer<DukeGame, GamePlayer>> defeatCallbacks = new ArrayList<>();
    private final List<BiConsumer<DukeGame, GamePlayer>> leftCallbacks = new ArrayList<>();

    private final List<Consumer<uz.dukeengine.core.module.ModuleFactory>> moduleCustomizers = new ArrayList<>();
    private final List<Consumer<ThingTemplateLoader>> templateCustomizers = new ArrayList<>();
    private PathGrid terrain;
    /** The map record {@code terrain} was laid from, for a client that wants more of the map than its cells. */
    private Object mapRecord;
    private WorldTemplate world;
    private GamePlayer localPlayer;
    private int windowWidth = 1120;
    private int windowHeight = 720;
    private int maxFps = GameConstants.DEFAULT_MAX_FPS;

    private RtsLogic logic;
    private RtsClient client;
    private RtsGameEngine engine;
    private volatile boolean started;

    private MultiplayerSession multiplayer;
    private volatile java.net.ServerSocket hostingSocket;
    private uz.dukeengine.core.replay.ReplayRecorder recorder;
    private uz.dukeengine.core.replay.Replay replay;

    private DukeGame(String title) {
        this.title = title;
    }

    /** Begin building a game. */
    public static DukeGame create(String title) {
        return new DukeGame(title);
    }

    public String getTitle() {
        return title;
    }

    private String subtitle = "";

    /** A short line shown under the title on the game's main menu. */
    public DukeGame subtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
        return this;
    }

    public String getSubtitle() {
        return subtitle;
    }

    // ---- builder configuration (before start) ----

    private record UnitText(String text, String source) {
    }

    /** Unit and structure templates from the text of a {@code .duke} file: {@code Object} blocks, or the game's own. */
    public DukeGame loadUnits(String text) {
        requireNotStarted();
        unitTexts.add(new UnitText(text, "units"));
        return this;
    }

    /** Templates a game has already read, as a game that reads its files once for every world does. */
    public DukeGame addUnits(java.util.Collection<? extends ThingTemplate> templates) {
        requireNotStarted();
        units.addAll(templates);
        return this;
    }

    /**
     * The weapons the game's units link by name from their weapon sets — one block a weapon, however many
     * units carry it. Handed to the world when it boots.
     */
    public DukeGame addWeapons(java.util.Collection<uz.dukeengine.rts.module.Weapon> more) {
        requireNotStarted();
        weapons.addAll(more);
        return this;
    }

    /** The game's weapon bonus table — see {@link uz.dukeengine.rts.module.WeaponBonus}. */
    public DukeGame addWeaponBonuses(java.util.Collection<uz.dukeengine.rts.module.WeaponBonus> more) {
        requireNotStarted();
        weaponBonuses.addAll(more);
        return this;
    }

    /** The upgrades the game's buildings research by name — {@code ProductionUpdate.Data.researches}. */
    public DukeGame addUpgrades(java.util.Collection<uz.dukeengine.rts.player.Upgrade> more) {
        requireNotStarted();
        upgrades.addAll(more);
        return this;
    }

    /** Unit definitions from a {@code .duke} file. */
    public DukeGame loadUnitsFile(Path file) {
        try {
            requireNotStarted();
            unitTexts.add(new UnitText(Files.readString(file), file.toString()));
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException("could not read units file: " + file, e);
        }
    }

    /** An open map of {@code cellsWide × cellsHigh} pathfinding cells (10 world units each). */
    public DukeGame map(int cellsWide, int cellsHigh) {
        requireNotStarted();
        terrain = new PathGrid(cellsWide, cellsHigh);
        return this;
    }

    /** A map from ASCII art: {@code #} = impassable, anything else = open. */
    public DukeGame mapFromText(String asciiMap) {
        requireNotStarted();
        terrain = MapLoader.fromText(asciiMap);
        return this;
    }

    /** Add a player. The first player added is the local (viewing) player by default. */
    public GamePlayer addPlayer(String name, Color color) {
        requireNotStarted();
        var player = new GamePlayer(name, color);
        players.add(player);
        if (localPlayer == null && !observing) {
            localPlayer = player;
        }
        return player;
    }

    /** Whether this machine watches rather than plays; see {@link #observe}. */
    private boolean observing;

    private final GameCamera camera = new GameCamera();
    private Long randomSeed;

    /**
     * The camera as the game drives it, from its own code on the simulation thread — see {@link GameCamera}. Stepped
     * once a logic frame, before the game's own per-frame code.
     */
    public GameCamera camera() {
        return camera;
    }

    /**
     * Where the player is looking, told by the client from its own thread — what the game's camera follows while the
     * game is not driving it.
     */
    public void setCameraSeen(float x, float y, float angle) {
        camera.seen(new uz.dukeengine.game.view.CameraView(x, y, angle, Float.NaN, Float.NaN));
    }

    private final java.util.concurrent.atomic.AtomicReference<uz.dukeengine.core.math.Coord2D> viewMove =
            new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * Put the player's view over {@code (x, y)} — the reference's {@code TheTacticalView->lookAt} from its interface: a
     * press on the radar, the key that looks at the last event. From any thread: the client centres its own camera
     * there at its next frame, turned, pitched and as far back as it was, and the camera stays the player's, his
     * scrolling going on from there at once. Two before a frame land on the second; one while the game drives the
     * camera waits until it is let go.
     */
    public void moveView(float x, float y) {
        viewMove.set(new uz.dukeengine.core.math.Coord2D(x, y));
    }

    /** The view move asked for since the client last took one, or null: the client's, once a frame. */
    public uz.dukeengine.core.math.Coord2D takeViewMove() {
        return viewMove.getAndSet(null);
    }

    private volatile boolean alertsAreTheGames;
    private volatile uz.dukeengine.core.math.Coord2D latestAlert;

    /**
     * Where the key that looks at the latest alert looks — the reference's last radar event ({@code
     * Radar::getLastEventLoc}): an attack at most once in ten seconds, a building finished, a unit lost — and from now
     * on the client moves it no more on its own when a thing of the player's is hurt: the game says what an alert is.
     * Null for none yet. From any thread.
     */
    public void setLatestAlert(uz.dukeengine.core.math.Coord2D place) {
        latestAlert = place;
        alertsAreTheGames = true;
    }

    /** Whether the game keeps the latest alert itself ({@link #setLatestAlert}). */
    public boolean alertsAreTheGames() {
        return alertsAreTheGames;
    }

    /** The latest alert the game set, or null. */
    public uz.dukeengine.core.math.Coord2D latestAlert() {
        return latestAlert;
    }

    private volatile uz.dukeengine.game.view.ViewRays viewRays;

    /**
     * What the player's view covers, as the client last drew it — the eye and the rays through the corners of the
     * world's part of the window, see {@link uz.dukeengine.game.view.ViewRays} — for a radar of the game's own to
     * outline; null before the client has drawn a frame. From any thread.
     */
    public uz.dukeengine.game.view.ViewRays viewRays() {
        return viewRays;
    }

    /** What the player's view covers, told by the client from its own thread each frame it draws the world. */
    public void setViewRays(uz.dukeengine.game.view.ViewRays rays) {
        viewRays = rays;
    }

    /**
     * Draw this match's chance from {@code seed} — the same on every machine of a network game, and the same each
     * time for a match that is to play the same way every time. A match that names none draws from the engine's one
     * fixed seed.
     */
    public DukeGame randomSeed(long seed) {
        requireNotStarted();
        this.randomSeed = seed;
        return this;
    }

    /**
     * Watch rather than play: no player is this machine's, so everything is seen — through no player's fog — and
     * nothing is ordered. An observer's seat in a skirmish, and a match run behind a front end.
     */
    public DukeGame observe() {
        requireNotStarted();
        this.observing = true;
        this.localPlayer = null;
        return this;
    }

    /** Make two players mutual enemies. */
    public DukeGame enemies(GamePlayer a, GamePlayer b) {
        scenario.add(() -> setRelationship(a, b, Relationship.ENEMIES));
        return this;
    }

    /** Make two players mutual allies. */
    public DukeGame allies(GamePlayer a, GamePlayer b) {
        scenario.add(() -> setRelationship(a, b, Relationship.ALLIES));
        return this;
    }

    private void setRelationship(GamePlayer a, GamePlayer b, Relationship relationship) {
        var pa = logic.getPlayer(a.getIndex());
        var pb = logic.getPlayer(b.getIndex());
        pa.setRelationshipTo(pb, relationship);
        pb.setRelationshipTo(pa, relationship);
    }

    /** Choose whose point of view (and input) the window uses. */
    public DukeGame localPlayer(GamePlayer player) {
        this.localPlayer = player;
        this.observing = player == null;
        return this;
    }

    /**
     * A side a computer plays: what only a computer may make, it may
     * ({@code Prerequisites.Buildability.ONLY_BY_COMPUTER}).
     */
    public DukeGame computer(GamePlayer player) {
        scenario.add(() -> logic.getRtsPlayer(player.getIndex()).setComputer(true));
        return this;
    }

    /** Give a player starting money: in its balance, neither earned nor spent. */
    public DukeGame money(GamePlayer player, int amount) {
        scenario.add(() -> logic.getRtsPlayer(player.getIndex()).give(amount));
        return this;
    }

    /**
     * Place a unit or structure. Before {@link #start()} this schedules the spawn
     * for game boot; after start (from a callback, on the simulation thread) it
     * spawns immediately.
     */
    public DukeGame spawn(String templateName, GamePlayer owner, float x, float y) {
        if (started) {
            spawnNow(templateName, owner, x, y);
        } else {
            scenario.add(() -> spawnNow(templateName, owner, x, y));
        }
        return this;
    }

    private GameObject spawnNow(String templateName, GamePlayer owner, float x, float y) {
        var template = logic.getThingFactory().findTemplate(templateName);
        if (template == null) {
            throw new IllegalArgumentException("unknown unit template '" + templateName
                    + "' — did you loadUnits() its block?");
        }
        return logic.spawn(template, new Coord3D(x, y, 0f), owner.getIndex());
    }

    /**
     * Register custom engine modules (e.g. compiled {@code UnitScript}s) before
     * units are read — the extension point for user code:
     * {@code game.customModules(mf -> ScriptModule.registerScript(mf, Guard.Data.class, Guard::new))}.
     */
    public DukeGame customModules(Consumer<uz.dukeengine.core.module.ModuleFactory> customizer) {
        requireNotStarted();
        moduleCustomizers.add(customizer);
        return this;
    }

    /**
     * Register the game's own template block types before its units are read:
     * {@code game.templates(loader -> loader.type(Monster.class))}. An
     * {@code Object} block is already an RTS template, with a build cost and time.
     */
    public DukeGame templates(Consumer<ThingTemplateLoader> customizer) {
        requireNotStarted();
        templateCustomizers.add(customizer);
        return this;
    }

    /**
     * What the game's {@code World} block says of its world. The engine reads what it has a
     * use for: a {@link uz.dukeengine.core.thing.Layered} world lays every map at its storey height.
     */
    public DukeGame world(WorldTemplate world) {
        requireNotStarted();
        this.world = world;
        return this;
    }

    /** Window size in pixels (default 1120×720). */
    public DukeGame window(int width, int height) {
        requireNotStarted();
        this.windowWidth = width;
        this.windowHeight = height;
        return this;
    }

    /** Cap the render/engine loop rate (default 45, SAGE's). */
    public DukeGame maxFps(int maxFps) {
        this.maxFps = maxFps;
        return this;
    }

    // ---- callbacks ----

    /** Runs once, after the world is set up, just before the first frame. */
    public DukeGame onStart(Consumer<DukeGame> callback) {
        startCallbacks.add(callback);
        return this;
    }

    /** Runs every logic frame (30×/second) on the simulation thread. */
    public DukeGame onTick(Consumer<DukeGame> callback) {
        tickCallbacks.add(callback);
        return this;
    }

    /** Runs every {@code seconds} of game time on the simulation thread. */
    public DukeGame everySeconds(double seconds, Consumer<DukeGame> callback) {
        intervalSeconds.add(new double[] {seconds, intervalCallbacks.size()});
        intervalCallbacks.add(callback);
        return this;
    }

    /**
     * Runs on the simulation thread whenever a factory releases a unit, as {@code (factory, unit)} — the frame it
     * happens, in the order registered. See {@link uz.dukeengine.rts.RtsSimulation#onProduced}.
     */
    public DukeGame onProduced(BiConsumer<uz.dukeengine.core.thing.GameObject,
            uz.dukeengine.core.thing.GameObject> callback) {
        producedCallbacks.add(callback);
        return this;
    }

    /** Runs on the simulation thread whenever a building is finished, as {@code (builder, building)}. */
    public DukeGame onConstructed(BiConsumer<uz.dukeengine.core.thing.GameObject,
            uz.dukeengine.core.thing.GameObject> callback) {
        constructedCallbacks.add(callback);
        return this;
    }

    private final List<BiConsumer<uz.dukeengine.core.thing.GameObject, uz.dukeengine.rts.player.Upgrade>>
            researchedCallbacks = new ArrayList<>();

    private final List<java.util.function.LongSupplier> checksumParts = new ArrayList<>();

    /**
     * A number of the game's own mixed into the world's checksum on every checked frame — state it keeps outside the
     * engine, which every machine must agree on — asked on the simulation thread. Two machines whose numbers part are
     * told they desynced, as for any other difference. See {@code GameLogic.checksumAlso}.
     */
    public DukeGame checksumAlso(java.util.function.LongSupplier part) {
        checksumParts.add(part);
        return this;
    }

    /**
     * Runs on the simulation thread whenever research finishes at a building or a unit, as {@code (researcher,
     * upgrade)} — the frame it finishes, after the upgrade has taken effect, in the order registered; not for an
     * upgrade bought at once, nor research called off. See {@link uz.dukeengine.rts.RtsSimulation#onResearched}.
     */
    public DukeGame onResearched(BiConsumer<uz.dukeengine.core.thing.GameObject, uz.dukeengine.rts.player.Upgrade>
            callback) {
        researchedCallbacks.add(callback);
        return this;
    }

    /**
     * Told when a building its side sold is down and gone — taken down for its worth, not destroyed by an enemy — on
     * the simulation thread, the frame it goes, its refund paid.
     */
    public DukeGame onSold(Consumer<uz.dukeengine.core.thing.GameObject> callback) {
        soldCallbacks.add(callback);
        return this;
    }

    /**
     * Told every death as it is reaped, on the simulation thread: what died, whose it was, what killed it and whose
     * side that was — known even when the killer is gone too — beside the client's event rather than instead of it.
     * Where a game counts what each side destroyed. A building sold, or anything removed without dying, is not heard.
     */
    public DukeGame onDied(Consumer<uz.dukeengine.core.event.ObjectDied> callback) {
        diedCallbacks.add(callback);
        return this;
    }

    /**
     * Told every order of the game's own — a {@link GameMessage.GameOrder}, posted with {@link #postCommand} from any
     * thread — as it is applied: on the simulation thread, on the same frame and in the same order on every machine,
     * and again when a replay plays it. The game does what the order means there — grants a science, fires a power —
     * deterministically. The engine never reads its word or its number.
     */
    public DukeGame onOrder(Consumer<GameMessage.GameOrder> listener) {
        orderCallbacks.add(listener);
        return this;
    }

    /** Runs when a player who had units loses all of them (annihilation). */
    public DukeGame onPlayerDefeated(BiConsumer<DukeGame, GamePlayer> callback) {
        defeatCallbacks.add(callback);
        return this;
    }

    /**
     * Runs when a player drops out of a network game — their machine is gone, as
     * opposed to their army being destroyed.
     */
    public DukeGame onPlayerLeft(BiConsumer<DukeGame, GamePlayer> callback) {
        leftCallbacks.add(callback);
        return this;
    }

    // ---- skirmish (map + faction selection, the real-RTS flow) ----

    /** Builds the chosen scenario (terrain, bases, neutrals) at boot time. */
    public interface SkirmishAssembler {
        void assemble(DukeGame game, String mapName, List<String> playerFactions);
    }

    private SkirmishAssembler skirmishAssembler;
    private List<String> mapChoices = List.of();
    private List<String> factionChoices = List.of();
    private String chosenMap;
    private List<String> chosenFactions;
    private boolean placesEverythingAtSetup;

    /**
     * Register the skirmish catalogue: the maps and factions the player can
     * choose from, and the assembler that builds the match from a choice.
     * With a catalogue set, author-time spawns are usually unnecessary.
     */
    public DukeGame skirmish(List<String> maps, List<String> factions, SkirmishAssembler assembler) {
        requireNotStarted();
        this.mapChoices = List.copyOf(maps);
        this.factionChoices = List.copyOf(factions);
        this.skirmishAssembler = assembler;
        return this;
    }

    public List<String> getMapChoices() {
        return mapChoices;
    }

    private java.util.Map<String, String> mapPictures = java.util.Map.of();

    /**
     * A picture for each map the player may choose, by the map's name — what a menu draws beside the row
     * while it is the lit one.
     *
     * <p>The game's to say, because the game knows where its maps are: {@code MapPackage.previewResource()}
     * is the name of the one found beside the map's own file. A map named here with no picture, or no
     * picture at all, is a row of words, which is what every map was.
     */
    public DukeGame mapPictures(java.util.Map<String, String> byMapName) {
        this.mapPictures = byMapName == null ? java.util.Map.of() : java.util.Map.copyOf(byMapName);
        return this;
    }

    /** The picture for a map, or null. */
    public String getMapPicture(String mapName) {
        return mapPictures.get(mapName);
    }

    public List<String> getFactionChoices() {
        return factionChoices;
    }

    /** Pick the map and per-player factions before starting (menus call this). */
    public void selectSkirmish(String mapName, List<String> playerFactions) {
        requireNotStarted();
        this.chosenMap = mapName;
        this.chosenFactions = playerFactions == null ? null : List.copyOf(playerFactions);
    }

    public String getChosenMap() {
        return chosenMap;
    }

    /**
     * Say that this match places everything it will ever draw while it is set up — a front end's shell map, a mission
     * laid out whole — so {@link #templatesThisMatchCanDraw} plans from what stands in it, as it does for a map chosen
     * from the skirmish list, rather than the client reading every look the game registered.
     */
    public DukeGame placesEverythingAtSetup() {
        requireNotStarted();
        this.placesEverythingAtSetup = true;
        return this;
    }

    public List<String> getChosenFactions() {
        return chosenFactions;
    }

    /** Swap in the chosen map's terrain during skirmish assembly. */
    public void applyMapTerrain(PathGrid grid) {
        applyMapTerrain(grid, null);
    }

    /**
     * The same, keeping the map's own record beside the grid it was laid from.
     *
     * <p>A grid is what the simulation needs and the whole of it: cells, storeys, relief. A client needs
     * more — what the ground is painted with, where the water is — and those are questions core asks of the
     * record ({@code Painted}, {@code Zoned}) rather than of the grid. Kept as {@code Object} because the
     * record is the game's own and nothing here may know its type, which is the same bargain
     * {@code MapTerrain.of} strikes.
     */
    public void applyMapTerrain(PathGrid grid, Object map) {
        this.terrain = grid;
        this.mapRecord = map;
        if (logic != null) {
            logic.setPathGrid(grid);
        }
    }

    /** The record the current terrain was laid from, or null where the game never handed one over. */
    public Object getMapRecord() {
        return mapRecord;
    }

    /** Place a neutral object (resource pile, critter) — owner is nobody. */
    public DukeGame spawnNeutral(String templateName, float x, float y) {
        Runnable action = () -> {
            var template = logic.getThingFactory().findTemplate(templateName);
            if (template != null) {
                logic.spawn(template, new Coord3D(x, y, 0f), 0);
            }
        };
        if (started) {
            action.run();
        } else {
            scenario.add(action);
        }
        return this;
    }

    // ---- multiplayer (before start) ----

    /**
     * Host a LAN game for every player in the project, binding this machine to
     * the <b>first</b> of them. Blocks until all the others have joined.
     */
    public MultiplayerSession hostMultiplayer(int port) throws java.io.IOException {
        return hostMultiplayer(port, players.size(), null);
    }

    /**
     * Host a LAN game for {@code playerCount} players and wait for the other
     * {@code playerCount - 1} to join. Call before starting the engine; every
     * machine must run the same game definition.
     *
     * @param onGuestJoined told how many guests are in so far, for a lobby screen
     */
    public MultiplayerSession hostMultiplayer(int port, int playerCount,
            java.util.function.IntConsumer onGuestJoined) throws java.io.IOException {
        requireNotStarted();
        requireNetworkPlayers(playerCount);
        try (var server = new java.net.ServerSocket(port)) {
            hostingSocket = server;
            multiplayer = MultiplayerSession.host(server, playerCount, encodeSkirmishSpec(), onGuestJoined);
        } finally {
            hostingSocket = null;
        }
        localPlayer = players.get(multiplayer.getLocalPlayerIndex() - 1);
        return multiplayer;
    }

    /** The host's skirmish choice as a wire token: {@code map|faction1|faction2…}. */
    private String encodeSkirmishSpec() {
        if (chosenMap == null && chosenFactions == null) {
            return "";
        }
        var joiner = new java.util.StringJoiner("|");
        joiner.add(urlEncode(chosenMap == null ? "" : chosenMap));
        if (chosenFactions != null) {
            for (var faction : chosenFactions) {
                joiner.add(urlEncode(faction));
            }
        }
        return joiner.toString();
    }

    private void applySkirmishSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            return;
        }
        var parts = spec.split("\\|", -1);
        var map = urlDecode(parts[0]);
        var factions = new ArrayList<String>();
        for (int i = 1; i < parts.length; i++) {
            factions.add(urlDecode(parts[i]));
        }
        selectSkirmish(map.isEmpty() ? null : map, factions.isEmpty() ? null : factions);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Abort a pending {@link #hostMultiplayer} that is waiting for a guest. */
    public void cancelHosting() {
        var server = hostingSocket;
        if (server != null) {
            try {
                server.close();
            } catch (java.io.IOException ignored) {
                // best-effort cancel
            }
        }
    }

    /**
     * Join a hosted LAN game. The host says which player this machine is; the
     * call blocks until the host has everyone and starts the game.
     */
    public MultiplayerSession joinMultiplayer(String host, int port) throws java.io.IOException {
        requireNotStarted();
        requireNetworkPlayers(2);
        multiplayer = MultiplayerSession.join(host, port);
        requireNetworkPlayers(multiplayer.getPlayerCount());
        localPlayer = players.get(multiplayer.getLocalPlayerIndex() - 1);
        applySkirmishSpec(multiplayer.getScenarioSpec()); // play the host's chosen match
        return multiplayer;
    }

    /**
     * Play this match over a session made before it — for a game that runs its own lobby, hosts or joins with
     * {@link MultiplayerSession#host} and {@link MultiplayerSession#join}, reads the host's settings off
     * {@link MultiplayerSession#getScenarioSpec}, and builds the match from them. This machine plays the player the
     * session seated it as. Before starting.
     */
    public DukeGame multiplayer(MultiplayerSession session) {
        requireNotStarted();
        requireNetworkPlayers(session.getPlayerCount());
        this.multiplayer = session;
        this.observing = false;
        this.localPlayer = players.get(session.getLocalPlayerIndex() - 1);
        return this;
    }

    /** Whether this game is wired to a network session. */
    public boolean isMultiplayer() {
        return multiplayer != null;
    }

    /** Whether this match has been started — booted, and so never to be started again. */
    public boolean hasStarted() {
        return started;
    }

    // ---- replay ----

    /**
     * Write the game down as it is played, so it can be watched again — and so a
     * build can prove the simulation still produces the same world from the same
     * input. Call before starting.
     */
    public DukeGame recordReplay() {
        requireNotStarted();
        recorder = new uz.dukeengine.core.replay.ReplayRecorder(CommandCodec.INSTANCE);
        return this;
    }

    /** The recording so far. Empty if {@link #recordReplay()} was never called. */
    public String getReplayText() {
        return recorder == null ? "" : recorder.toText();
    }

    /**
     * Play a recording instead of taking input. The game is set up exactly as it
     * was, and the recorded commands are fed in on the frames they were taken
     * from; live input is ignored, since the recording already says what happened.
     */
    public DukeGame playReplay(String replayText) {
        requireNotStarted();
        replay = uz.dukeengine.core.replay.Replay.parse(replayText, CommandCodec.INSTANCE);
        return this;
    }

    /** The replay driving this game, or {@code null} if it is being played live. */
    public uz.dukeengine.core.replay.Replay getReplay() {
        return replay;
    }

    /** Whether the project has enough players for a network game. */
    public boolean supportsMultiplayer() {
        return players.size() >= 2;
    }

    /** The most players this game definition can seat over the network. */
    public int getMaxNetworkPlayers() {
        return players.size();
    }

    private void announcePlayerLeft(int playerIndex) {
        if (playerIndex < 1 || playerIndex > players.size()) {
            return;
        }
        var who = players.get(playerIndex - 1);
        for (var callback : leftCallbacks) {
            callback.accept(this, who);
        }
    }

    private void requireNetworkPlayers(int needed) {
        if (needed < 2) {
            throw new IllegalStateException("a network game needs at least two players");
        }
        if (players.size() < needed) {
            throw new IllegalStateException("this game defines only " + players.size()
                    + " players, but the match needs " + needed);
        }
    }

    // ---- running ----

    /**
     * Boot the engine and run the simulation on its own thread — without any
     * window. Presentation front-ends (e.g. the 3D client) drive their own
     * display against {@link #getSnapshot()} and call {@link #stop()} when
     * done. Returns the simulation thread so the caller can join it.
     */
    public Thread startEngineOnly() {
        boot();
        running = true;
        var engineThread = new Thread(() -> {
            engine.execute();
            if (multiplayer != null) {
                multiplayer.close(); // the match is over for this machine; the others are told it has gone
            }
        }, "duke-sim");
        engineThread.start();
        return engineThread;
    }

    /**
     * Build the world without running it: templates read, players added, the chosen match assembled — and
     * not one frame stepped.
     *
     * <p>Separate from {@link #startEngineOnly} so a client can look at the world before it moves. What a
     * match can ever draw is decided by what is in it, and that is not known until the match is assembled;
     * loading every look the game registered instead read 876 models for a match that could draw 63. Once
     * booted, {@link #templatesThisMatchCanDraw} says which. Nothing here reads a clock, so booting early
     * changes no frame.
     */
    public void boot() {
        boot(percent -> {
        });
    }

    /**
     * The same, saying how far it has got, 0 to 100, as it goes: templates read, the world laid, players seated,
     * what the game placed, the match the game assembled, the pathfinder's map of what reaches what, the game's own
     * start — and 100 when the world is built.
     */
    public void boot(java.util.function.IntConsumer progress) {
        if (!started) {
            setUp(progress);
        }
        progress.accept(100);
    }

    // ---- what the players say ----

    private final java.util.Queue<uz.dukeengine.core.network.ChatLine> toSay =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final List<Consumer<uz.dukeengine.core.network.ChatLine>> chatListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Say a line from this machine's player to these players — everyone, allies, a chosen few — from any thread. It
     * goes beside the match, never in it: no frame waits for it, and neither the replay nor the checksum sees it. In a
     * network game every addressed machine hears it; alone, the sender hears its own.
     */
    public void say(String text, java.util.Collection<Integer> to) {
        toSay.add(new uz.dukeengine.core.network.ChatLine(getLocalPlayerIndex(), List.copyOf(to), text));
    }

    /**
     * Told every line said to this machine's player — its own included — with who said it and to whom, in the order
     * each sender said them, on the simulation's thread. The 3D client's {@code Duke3D.onChat} hands them to the
     * window's.
     */
    public DukeGame onChat(Consumer<uz.dukeengine.core.network.ChatLine> listener) {
        chatListeners.add(listener);
        return this;
    }

    /** Send what was said, and take in what arrived: every turn of the loop, paused or waiting or not. */
    private void talk() {
        for (var line = toSay.poll(); line != null; line = toSay.poll()) {
            if (multiplayer != null) {
                multiplayer.say(line.text(), line.recipients());
            } else {
                heardChat(line);
            }
        }
        if (multiplayer != null) {
            multiplayer.listen();
        }
    }

    private void heardChat(uz.dukeengine.core.network.ChatLine line) {
        for (var listener : chatListeners) {
            listener.accept(line);
        }
    }

    /** How far this machine has got loading, as last shared — so the same figure is not said twice. */
    private int sharedProgress = -1;
    private final List<java.util.function.ObjIntConsumer<GamePlayer>> peerProgress = new ArrayList<>();

    /**
     * How far this machine has got loading the match, 0 to 100, told by whatever does the loading — the 3D client
     * does — on its own thread, once the match is built and until it starts. In a network game it is said to every
     * other machine, and what they have said is taken in: see {@link #onPeerLoadProgress}.
     */
    public void loadProgress(int percent) {
        if (multiplayer == null) {
            return;
        }
        if (percent != sharedProgress) {
            sharedProgress = percent;
            multiplayer.shareProgress(percent);
        } else {
            multiplayer.listenWhileLoading();
        }
    }

    /**
     * Told how far another machine of a network game has got loading — which player, what percent — for a load
     * screen that shows a bar for everybody. On the thread doing this machine's load until the match starts, and on
     * the simulation's thread after, for a slower machine still loading.
     */
    public DukeGame onPeerLoadProgress(java.util.function.ObjIntConsumer<GamePlayer> listener) {
        peerProgress.add(listener);
        return this;
    }

    private void heardProgress(int playerIndex, int percent) {
        if (playerIndex < 1 || playerIndex > players.size()) {
            return;
        }
        for (var listener : peerProgress) {
            listener.accept(players.get(playerIndex - 1), percent);
        }
    }

    /**
     * Every template this match can ever draw, or {@code null} where there is no match to plan from.
     *
     * <p>What stands in the world once it is assembled, everything those can produce, and what that can
     * produce in turn — followed through {@code ProductionUpdate}, the one module of the RTS library that
     * names other templates, so the engine needs no help from the game to follow it. A barracks that
     * trains riflemen brings the rifleman in; a factory that builds a war factory brings in everything
     * the war factory builds.
     *
     * <p>Null before {@link #boot}, and for a game that chose no match and did not say it
     * {@link #placesEverythingAtSetup} — a dungeon spawns its monsters floor by floor, long after booting, and
     * planning from its first empty world would miss all of them. Such a game is planned from everything it
     * registered, as it always was.
     *
     * <p>A thing that turns up by some other road — debris, a crate's reward — is not in it, and is read
     * when it first appears. That is the stall preloading exists to prevent, and the right trade for a
     * thing that is rare.
     */
    public java.util.Set<String> templatesThisMatchCanDraw() {
        if (!started || chosenMap == null && !placesEverythingAtSetup) {
            return null;
        }
        var seen = new java.util.LinkedHashSet<String>();
        var next = new java.util.ArrayDeque<String>();
        for (var object : logic.getObjects()) {
            next.add(object.getTemplate().name());
        }
        while (!next.isEmpty()) {
            var name = next.poll();
            var template = logic.getThingFactory().findTemplate(name);
            if (template == null || !seen.add(name)) {
                continue; // a name nothing answers to draws nothing
            }
            for (var module : template.modules()) {
                if (module instanceof uz.dukeengine.rts.module.ProductionUpdate.Data line) {
                    next.addAll(line.builds());
                }
            }
            if (brings != null) {
                next.addAll(brings.apply(name));
            }
        }
        return java.util.Collections.unmodifiableSet(seen);
    }

    /** What else each template may bring into the world, by name — see {@link #brings}. */
    private java.util.function.Function<String, ? extends java.util.Collection<String>> brings;

    /**
     * Name, per template, the other templates it may bring into the world — what its death leaves (a hulk, rubble,
     * a burning soldier), a crate it drops — followed as a factory's build list is, so the art of what only a death
     * makes is read with the match rather than the first time one dies in the middle of a battle. Before
     * {@link #start}; an empty list for none.
     */
    public DukeGame brings(java.util.function.Function<String, ? extends java.util.Collection<String>> rule) {
        requireNotStarted();
        this.brings = rule;
        return this;
    }

    /**
     * Boot the engine, open the window, and run the game. Blocks until the
     * window is closed (or {@link #stop()} is called).
     */
    public void start() {
        var engineThread = startEngineOnly();

        var windowRef = new GameWindow[1];
        SwingUtilities.invokeLater(() -> {
            windowRef[0] = new GameWindow(this, title, windowWidth, windowHeight);
            windowRef[0].setVisible(true);
        });

        try {
            engineThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        SwingUtilities.invokeLater(() -> {
            if (windowRef[0] != null) {
                windowRef[0].dispose();
            }
        });
    }

    /**
     * Run the same game without a window for {@code frames} attempts. In
     * multiplayer an attempt that stalls (peer input not yet in) does not
     * advance the simulation — exactly like the windowed engine loop.
     */
    public void runHeadless(int frames) {
        if (!started) {
            setUp(percent -> {
            });
        }
        for (int i = 0; i < frames; i++) {
            talk();
            if (frameReady()) {
                logic.update();
            }
            client.update(); // keeps snapshots flowing for assertions
        }
    }

    /**
     * Where a frame's input comes from, as the engine's loop asks it: a replay's is known and fed now, a network
     * game's waits for every player, and alone it is always there.
     */
    private boolean frameReady() {
        if (replay != null) {
            return replay.beforeStep(logic);
        }
        return multiplayer == null || multiplayer.beforeStep(logic);
    }

    /** Boot the engine and apply everything configured on this builder, saying how far it has got. */
    private void setUp(java.util.function.IntConsumer progress) {
        if (started) {
            throw new IllegalStateException("game already started");
        }
        if (players.isEmpty()) {
            throw new IllegalStateException("add at least one player before starting");
        }

        logic = new RtsLogic();
        producedCallbacks.forEach(logic::onProduced);
        constructedCallbacks.forEach(logic::onConstructed);
        researchedCallbacks.forEach(logic::onResearched);
        checksumParts.forEach(logic::checksumAlso);
        soldCallbacks.forEach(logic::onSold);
        orderCallbacks.forEach(logic::onOrder);
        diedCallbacks.forEach(logic::onDied);
        client = new RtsClient(logic);
        client.setMomentWords(momentWords);
        client.remember(remembers);
        client.keepOutOfSight(keepFrames, keepDeadFrames);
        client.setCommands(this::buttonsNow);
        client.setAimAnswer(this::aimAnswerNow);
        client.setAttackable(this::attackableNow);
        client.setContextOrder(this::contextOrderNow);
        engine = new RtsGameEngine(logic, client);
        if (recorder != null) {
            logic.setFrameLog(recorder);
        }
        if (replay != null) {
            engine.setReplay(replay);
        }
        if (multiplayer != null) {
            logic.setSession(multiplayer);   // local commands go over the wire
            engine.setSession(multiplayer);  // frames wait for every player's input
            multiplayer.onPlayerLeft(this::announcePlayerLeft);
            multiplayer.onPeerProgress(this::heardProgress);
            multiplayer.onChat(this::heardChat);
            // A cut-off peer and a peer waiting on a slow one look identical from
            // the outside — both stopped — so say which this is.
            multiplayer.onConnectionLost(() -> setBanner("CONNECTION LOST"));
            // The simulation has already been stopped by the gate; all that is left
            // is to say why, so the screen does not simply freeze. The detail is
            // logged by the gate itself, which is where the two worlds were compared.
            multiplayer.onDesync(desync -> setBanner(DESYNC_MESSAGE));
        }
        engine.setMaxFps(maxFps);
        engine.everyTurn(this::talk);
        engine.init(); // note: engine init resets subsystems — apply scenario after
        if (randomSeed != null) {
            logic.setRandomSeed(randomSeed);
        }
        client.setCamera(camera::shown);
        progress.accept(5);

        // custom modules must exist before a unit's block names them
        for (var customizer : moduleCustomizers) {
            customizer.accept(logic.getThingFactory().getModuleFactory());
        }
        var loader = uz.dukeengine.rts.RtsTemplate.register(new ThingTemplateLoader(logic.getThingFactory()));
        for (var customizer : templateCustomizers) {
            customizer.accept(loader);
        }
        for (var template : units) {
            logic.getThingFactory().addTemplate(template);
        }
        logic.addWeapons(weapons);
        logic.addUpgrades(upgrades);
        logic.setWeaponBonuses(weaponBonuses);
        for (var text : unitTexts) {
            loader.load(text.text(), text.source());
        }
        progress.accept(15);
        logic.setWorld(world);
        if (terrain != null) {
            logic.setPathGrid(terrain);
        }
        progress.accept(20);

        for (var player : players) {
            player.bind(logic.getPlayerList().addPlayer(player.getName()).getIndex());
        }
        client.setViewerPlayer(localPlayer == null ? RtsClient.EVERYONE : localPlayer.getIndex());
        progress.accept(30);

        started = true; // spawn() from here on is immediate
        for (var action : scenario) {
            action.run();
        }
        progress.accept(40);

        if (commandHandler != null) {
            logic.setGameCommandHandler(commandHandler);
        }
        // The camera first: a move the game's code orders in a frame takes its first step in the next.
        logic.addTickCallback(camera::step);
        for (var callback : tickCallbacks) {
            logic.addTickCallback(() -> callback.accept(this));
        }
        for (var interval : intervalSeconds) {
            var callback = intervalCallbacks.get((int) interval[1]);
            int frames = Math.max(1, (int) Math.round(interval[0] * GameConstants.LOGICFRAMES_PER_SECOND));
            logic.addIntervalCallback(frames, () -> callback.accept(this));
        }
        logic.setDefeatListener(playerIndex -> {
            var player = playerByIndex(playerIndex);
            if (player != null) {
                for (var callback : defeatCallbacks) {
                    callback.accept(this, player);
                }
            }
            updateOutcomeBanner(playerIndex);
        });

        // the skirmish assembler builds the chosen match (terrain, bases, neutrals)
        if (skirmishAssembler != null) {
            skirmishAssembler.assemble(this, chosenMap, chosenFactions);
        }
        progress.accept(70);
        // What reaches what, worked out now rather than by the first order given: a big map's zones are a pass over
        // every cell, and the load is where a pass over every cell belongs.
        logic.zones();
        progress.accept(80);

        logic.setInGame(true);
        for (var callback : startCallbacks) {
            callback.accept(this);
        }
        progress.accept(90);
    }

    /**
     * The standard RTS outcome, shown as a big banner: losing your own last
     * unit is DEFEAT; the last enemy falling is VICTORY. Runs on the sim
     * thread inside the defeat event.
     */
    private void updateOutcomeBanner(int defeatedPlayer) {
        int local = getLocalPlayerIndex();
        if (local < 0) {
            return;
        }
        if (defeatedPlayer == local) {
            setBanner("DEFEAT");
            return;
        }
        for (var object : logic.getObjects()) {
            if (!object.isEffectivelyDead()
                    && logic.getRelationship(local, object.getPlayerIndex())
                            == uz.dukeengine.core.player.Relationship.ENEMIES) {
                return; // an enemy still stands
            }
        }
        setBanner("VICTORY");
    }

    private volatile uz.dukeengine.game.view.MomentWords momentWords;

    /**
     * Words for its things' moments — moving, attacking, each weapon slot firing, between shots and reloading, a turret
     * turning — added to the words each holds while they last, so its looks choose their model, clip and pieces by them
     * too; the reference's MOVING, FIRING_A and the rest. Null says none, as before.
     */
    public DukeGame momentWords(uz.dukeengine.game.view.MomentWords words) {
        this.momentWords = words;
        if (client != null) {
            client.setMomentWords(words);
        }
        return this;
    }

    private volatile java.util.function.Predicate<GameObject> remembers;
    private volatile int keepFrames;
    private volatile int keepDeadFrames;

    /**
     * Which things a viewer goes on seeing once seen: the view he last had of each while it was in sight, marked
     * remembered, for as long as the ground under it is seen but not in sight — drawn through the fog as it was, not
     * picked and with no bar; dropped once that ground is in sight again, and never shown on ground never seen. The
     * reference's ghosts of still things ({@code W3DGhostObject}): a tree, a house, an enemy's base that no longer
     * blink out as its looker leaves. Asked on the simulation thread; null, the default, for none.
     */
    public DukeGame remember(java.util.function.Predicate<GameObject> which) {
        this.remembers = which;
        if (client != null) {
            client.remember(which);
        }
        return this;
    }

    /**
     * How many frames a thing that goes out of a viewer's sight stays in his view as it is, after it was last in sight,
     * and a dead one — the reference's 60 and 150 ({@code GameClient::update}: "planes that pop out of the shroud,
     * fire, then head back"). 0, the default, for not at all.
     */
    public DukeGame keepOutOfSight(int frames, int deadFrames) {
        this.keepFrames = frames;
        this.keepDeadFrames = deadFrames;
        if (client != null) {
            client.keepOutOfSight(frames, deadFrames);
        }
        return this;
    }

    /** Show (or clear with "") a big centered message in the game window. */
    public void setBanner(String banner) {
        if (client != null) {
            client.setBanner(banner);
        }
    }

    /**
     * Put the game's own figures on the HUD: a hero's level, a wave number, a
     * countdown — whatever this particular game counts that the engine has no
     * name for.
     *
     * <p>The engine carries the line to the client and never reads it. Without
     * this a game could only reach the screen through the banner, which is a
     * different thing: the banner interrupts, this reports.
     */
    public void setStatus(String status) {
        if (client != null) {
            client.setStatus(status);
        }
    }

    /** Whether the simulation's thread was ever started. */
    private volatile boolean running;

    /**
     * Ask the engine loop to exit; {@link #start()} then returns. A network match that was never run lets go of its
     * session here, as a run one does when its loop ends.
     */
    public void stop() {
        if (engine != null) {
            engine.setQuitting(true);
        }
        if (!running && multiplayer != null) {
            multiplayer.close();
        }
    }

    // ---- runtime access (used by the window, callbacks, and tests) ----

    /** The most recent frame of the world; safe from any thread. */
    public WorldSnapshot getSnapshot() {
        return client == null ? WorldSnapshot.EMPTY : client.getSnapshot();
    }

    /** The navigation grid, or {@code null} for open terrain. */
    public PathGrid getTerrain() {
        return terrain;
    }

    /** The display colour for a player index (neutral is gray). */
    public Color getColor(int playerIndex) {
        for (var player : players) {
            if (player.isBound() && player.getIndex() == playerIndex) {
                return player.getColor();
            }
        }
        return playerIndex == 0 ? Color.GRAY : Color.MAGENTA;
    }

    /** The index of the viewing player (whose units the window commands). */
    public int getLocalPlayerIndex() {
        return !watching && localPlayer != null && localPlayer.isBound() ? localPlayer.getIndex() : -1;
    }

    /** Whether this machine has taken a watcher's seat mid-match — see {@link #watch}. */
    private volatile boolean watching;

    /**
     * Reveal the whole map to {@code player} for the rest of the match — the reference's {@code MAP_REVEAL_ALL_PERM},
     * and what a player beaten while the others fight on gets to watch the end by. From code on the simulation thread
     * (a defeat callback, a script), so every machine does it on the same frame; it is in the checksum. That player's
     * view has no fog and no shroud from the next frame; nobody else's changes.
     */
    public DukeGame revealMapTo(GamePlayer player) {
        logic.revealMapTo(player.getIndex());
        return this;
    }

    /**
     * Make this machine a watcher from now on, mid-match: no player of its own, so nothing selected and no order sent
     * — the whole map seen, through nobody's fog. The machine goes on taking part in the match's lock-step, so the
     * others play on. For the machine whose player was beaten ({@code getLocalPlayerIndex()} before the call); what
     * {@link #observe} is before a match starts.
     */
    public DukeGame watch() {
        watching = true;
        if (client != null) {
            client.setViewerPlayer(RtsClient.EVERYONE);
        }
        return this;
    }

    /** Whether this machine is a watcher: from the start ({@link #observe}), or since {@link #watch}. */
    public boolean isWatching() {
        return watching || observing;
    }

    private GamePlayer playerByIndex(int index) {
        for (var player : players) {
            if (player.isBound() && player.getIndex() == index) {
                return player;
            }
        }
        return null;
    }

    /** One entry of a production structure's build menu. */
    public record BuildOption(String templateName, String displayName, int cost) {
    }

    /**
     * The build menu of a production structure's template, for UIs. Safe from
     * any thread: templates are immutable once loaded.
     */
    public List<BuildOption> getBuildOptions(String factoryTemplateName) {
        var factoryTemplate = logic.getThingFactory().findTemplate(factoryTemplateName);
        if (factoryTemplate == null) {
            return List.of();
        }
        for (var entry : factoryTemplate.modules()) {
            if (entry instanceof uz.dukeengine.rts.module.ProductionUpdate.Data data) {
                var options = new ArrayList<BuildOption>();
                for (var name : data.builds()) {
                    var unit = logic.getThingFactory().findTemplate(name);
                    if (unit != null) {
                        var local = logic.getRtsPlayer(getLocalPlayerIndex());
                        options.add(new BuildOption(name,
                                Titled.of(unit),
                                local == null ? uz.dukeengine.rts.Buildable.costOf(unit) : local.priceOf(unit)));
                    }
                }
                return options;
            }
        }
        return List.of();
    }

    /** Thread-safe: queue a command into the simulation (what the UI uses). */
    public void postCommand(GameMessage command) {
        logic.post(command);
    }

    /**
     * The same, for a command the game declared itself — see {@link #onCommand}.
     *
     * <p>Separate from the overload above only so the standard orders keep their
     * exact type; both end up in the same queue, on the same frame boundary, in
     * the same replay log.
     */
    public void postCommand(Command command) {
        logic.post(command);
    }

    /**
     * Handle commands outside the standard RTS set — the ones this game invented.
     *
     * <p>The engine's contract has always been that a game declares its own
     * command set ({@link Command}); {@link GameMessage} is the RTS library's set,
     * not the only one a game on it might want. A roguelike's "cast the third
     * ability" is not an RTS order and never will be, but it has to travel the
     * same road: queued from the input thread, applied at the start of a frame,
     * written to the replay log. That is what this hook is for.
     *
     * <p>The handler runs on the simulation thread inside the frame, so it must be
     * deterministic — the same rule every other simulation callback follows.
     *
     * <p>A game that never calls this is unaffected: a foreign command is still
     * logged and ignored, exactly as before.
     *
     * <p>A command of the game's own class stays on this machine: the wire and the
     * replay speak the RTS set. An order that must reach every machine and every
     * replay is a {@link GameMessage.GameOrder}, heard through {@link #onOrder}.
     */
    public DukeGame onCommand(Consumer<Command> handler) {
        this.commandHandler = handler;
        if (logic != null) {
            logic.setGameCommandHandler(handler);
        }
        return this;
    }

    /** Held until boot, like every other callback: the simulation exists only then. */
    private Consumer<Command> commandHandler;

    // ---- the command bar ----

    private volatile List<Integer> selection = List.of();
    private java.util.function.Function<List<Integer>,
            List<uz.dukeengine.game.view.CommandButton>> commandBar;
    private Consumer<uz.dukeengine.game.view.CommandPress> commandPressed;

    /**
     * What the player may do with whatever he has selected, asked once a frame as the snapshot is built.
     *
     * <p>Asked <b>on the simulation thread</b>, which is what makes it safe to read live state inside it:
     * what a barracks can train, what this player can afford, whether the power is on. What crosses to the
     * window is the answer — a list of buttons — rather than the objects it was worked out from, exactly
     * as everything else in a snapshot does.
     *
     * <p>The selection it is given is the one the window last reported ({@link #setSelection}), in the
     * order the window holds it. The engine knows what none of the buttons mean.
     */
    public DukeGame commandBar(java.util.function.Function<List<Integer>,
            List<uz.dukeengine.game.view.CommandButton>> buttons) {
        this.commandBar = buttons;
        if (client != null) {
            client.setCommands(this::buttonsNow);
        }
        return this;
    }

    private List<uz.dukeengine.game.view.CommandButton> buttonsNow() {
        return commandBar == null ? List.of() : commandBar.apply(selection);
    }

    /**
     * What the window has selected, so the next snapshot's buttons are about it.
     *
     * <p>Thread-safe and one-way: the window writes, the simulation reads. Selection is the window's own
     * idea — the simulation has never had one and does not gain one here, because two players selecting
     * different things must still reach the same frame.
     */
    public void setSelection(List<Integer> unitIds) {
        this.selection = unitIds == null ? List.of() : List.copyOf(unitIds);
    }

    public List<Integer> getSelection() {
        return selection;
    }

    /** The selection the game last asked the client for — see {@link #select}. */
    private final java.util.concurrent.atomic.AtomicReference<Selecting> selectionWanted =
            new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * Select these things in the client, from the game's own code on any thread: a portrait of a multi-selection
     * clicked, a double-click choosing all of a kind on the game's own bar. The client takes it up at its next frame,
     * in this order, under its own rule of what may be selected together — the player's own things, or one of
     * someone else's alone; what is gone or hidden is left out — and tells the selection back as it tells any other
     * ({@link #getSelection}).
     */
    public void select(List<uz.dukeengine.core.thing.ObjectId> ids) {
        select(ids, false);
    }

    /**
     * The same, {@code answered} asking the client to answer it as a click's is, with the select voice of a thing it
     * took in — as the reference's own every-unit keys, which a game that selects on the screen first takes itself.
     */
    public void select(List<uz.dukeengine.core.thing.ObjectId> ids, boolean answered) {
        selectionWanted.set(new Selecting(List.copyOf(ids), answered));
    }

    private volatile java.util.Map<Integer, List<Integer>> controlGroups = java.util.Map.of();

    /**
     * Thread-safe: the player's control groups as the client keeps them — each group that holds anyone by its number,
     * the ids of its members — told whenever they change, for a game that draws a group's number by a thing as the
     * reference does ({@code Drawable::drawUIText}).
     */
    public void setControlGroups(java.util.Map<Integer, List<Integer>> groups) {
        var copy = new java.util.TreeMap<Integer, List<Integer>>();
        groups.forEach((number, members) -> copy.put(number, List.copyOf(members)));
        this.controlGroups = java.util.Collections.unmodifiableMap(copy);
    }

    /** The player's control groups, as the client last told them: by number, the ids of each one's members. */
    public java.util.Map<Integer, List<Integer>> getControlGroups() {
        return controlGroups;
    }

    /** A selection the game asked for: what, and whether it is answered with a voice. */
    public record Selecting(List<uz.dukeengine.core.thing.ObjectId> ids, boolean answered) {
    }

    /** The selection the game last asked for, taken once by the client that shows it; null when none waits. */
    public List<uz.dukeengine.core.thing.ObjectId> takeSelectionRequest() {
        var taken = takeSelecting();
        return taken == null ? null : taken.ids();
    }

    /** The same, with whether it is answered; null when none waits. */
    public Selecting takeSelecting() {
        return selectionWanted.getAndSet(null);
    }

    /**
     * What to do when one of the bar's buttons is pressed: which, what was selected, and where or at what
     * for one that aims — see {@link uz.dukeengine.game.view.CommandPress}.
     *
     * <p>The handler's job is to turn that into one of the game's own {@link Command}s and
     * {@link #postCommand} it, which is the road every order already travels — queued from the input
     * thread, applied at the start of a frame, written to the replay log. Doing anything to the world
     * here instead would be doing it off the simulation thread and out of the log.
     */
    public DukeGame onCommandPressed(Consumer<uz.dukeengine.game.view.CommandPress> pressed) {
        this.commandPressed = pressed;
        return this;
    }

    /** Thread-safe: the player pressed a button of the bar that needs nothing more. */
    public void pressCommand(String id) {
        pressCommand(id, null, 0f, -1);
    }

    /**
     * Thread-safe: the player pressed a button of the bar and then clicked where, or at what — and, for a
     * place, which way the thing put there faces.
     */
    public void pressCommand(String id, Coord3D place, float facing, int target) {
        var handler = commandPressed;
        if (handler != null && id != null) {
            handler.accept(new uz.dukeengine.game.view.CommandPress(id, selection, place, facing, target));
        }
    }

    private Consumer<uz.dukeengine.game.view.CommandPress> pressRefused;

    /**
     * What to do when an armed button is pressed where the simulation will not take it — its ghost red ({@link
     * #aimFits}): which button, what was selected, where and facing which way. Nothing is sent and the button stays
     * armed; the game may say why in its own words and voice, as the reference's {@code
     * InGameUI::displayCantBuildMessage} does. A press taken is heard by {@link #onCommandPressed} as ever, and not
     * here.
     */
    public DukeGame onPressRefused(Consumer<uz.dukeengine.game.view.CommandPress> refused) {
        this.pressRefused = refused;
        return this;
    }

    /** Thread-safe: an armed button pressed at {@code place}, where the simulation would not take it. */
    public void refusePress(String id, Coord3D place, float facing) {
        var handler = pressRefused;
        if (handler != null && id != null) {
            handler.accept(new uz.dukeengine.game.view.CommandPress(id, selection, place, facing, -1));
        }
    }

    // ---- aiming ----

    /** Whether an armed button's place, faced one way, would do — see {@link #aimFits}. */
    @FunctionalInterface
    public interface AimFits {
        boolean test(String button, Coord3D place, float facing);
    }

    /** What the window last reported of an armed button: which, where, and facing which way. */
    private record Aim(String button, Coord3D place, float facing) {
    }

    private volatile Aim aim;
    private AimAnswering aimAnswering;

    /** The game's answer about an armed button's place, faced one way — see {@link #aimAnswer}. */
    @FunctionalInterface
    public interface AimAnswering {
        uz.dukeengine.game.view.AimAnswer answer(String button, Coord3D place, float facing);
    }

    /**
     * Whether an armed button's place would do, asked as the cursor moves — for a ghost to be drawn green or
     * red. The game answers from the simulation's side: for a building, {@code RtsSimulation.fits}.
     *
     * <p>Asked with the facing the ghost has at that moment, because for anything but a round footprint the
     * answer depends on it: a long building turned across a slope is a different question from the same
     * building along it, and green has to be the answer for the footprint the player is looking at.
     *
     * <p>Asked on the simulation thread as the snapshot is built, like the bar itself, so the answer may read
     * the world as it stands; the window only shows it. What decides whether a place will do is the order
     * that is sent, which is judged again when it is applied.
     */
    public DukeGame aimFits(AimFits fits) {
        return aimAnswer(fits == null ? null
                : (button, place, facing) -> new uz.dukeengine.game.view.AimAnswer(fits.test(button, place, facing),
                        List.of()));
    }

    /**
     * The same, the answer carrying besides yes or no the rectangles of ground to mark — what stands in the way, as the
     * reference lays a red bib under it ({@code BuildAssistant::isLocationLegalToBuild}) — each laid until the next
     * answer ({@code WorldSnapshot.aimMarks}). Asked where {@link #aimFits} is.
     */
    public DukeGame aimAnswer(AimAnswering answering) {
        this.aimAnswering = answering;
        if (client != null) {
            client.setAimAnswer(this::aimAnswerNow);
        }
        return this;
    }

    /**
     * Thread-safe: the button the window has armed, where its cursor stands and which way the ghost faces —
     * or a null button when none is armed. One write of one value, so the simulation never reads a place
     * from one moment with a facing from another.
     */
    public void setAim(String buttonId, Coord3D place, float facing) {
        this.aim = buttonId == null || place == null ? null : new Aim(buttonId, place, facing);
    }

    private uz.dukeengine.game.view.AimAnswer aimAnswerNow() {
        var now = aim;
        return now == null || aimAnswering == null ? uz.dukeengine.game.view.AimAnswer.YES
                : aimAnswering.answer(now.button(), now.place(), now.facing());
    }

    // ---- what the pointer is on ----

    /**
     * What the window's pointer is on: a thing, or -1 and the point of the ground under it (null for none), and whether
     * the local player has ever seen that ground.
     */
    private record Pointed(int unit, Coord3D ground, boolean seen) {
    }

    private volatile Pointed pointed = new Pointed(-1, null, true);

    /**
     * Thread-safe: the thing under the window's pointer, or -1 for none — so the next snapshot can say whether
     * an attack on it by what is selected would be taken ({@link uz.dukeengine.game.view.WorldSnapshot#attackable}).
     * One-way, like the selection: the window writes, the simulation reads.
     */
    public void setPointedAt(int unitId) {
        setPointedAt(unitId, null);
    }

    /**
     * The same, with the point of the ground the pointer is on where it is on no thing — for the game's
     * {@link #groundOrder} to answer. One write, so a thing from one moment never comes with ground from another.
     */
    public void setPointedAt(int unitId, Coord3D ground) {
        setPointedAt(unitId, ground, true);
    }

    /**
     * The same, saying whether the local player has ever seen that ground, as the client's discovery has it — for the
     * game's ground rule, which the simulation thread asks and which cannot read the client ({@link SeenGroundOrder}).
     */
    public void setPointedAt(int unitId, Coord3D ground, boolean seen) {
        this.pointed = new Pointed(unitId, unitId >= 0 ? null : ground, seen);
    }

    /**
     * The thing the window last said its pointer is on ({@link #setPointedAt}), or -1 for none, whatever is selected —
     * for a game's own canvas to show what is under the pointer. Presentation only: nothing in the simulation reads
     * it. From any thread.
     */
    public int getPointedAt() {
        return pointed.unit();
    }

    /**
     * Whether an attack on what the pointer is on would be taken: refused only where the selection holds
     * something armed of the local player's and not one of its weapons may be fired at it. Nothing under the
     * pointer, or nothing armed selected, is no refusal — so a game whose weapons name no classes draws its
     * pointer exactly as it did.
     */
    private boolean attackableNow() {
        int at = pointed.unit();
        var world = logic;
        var victim = at < 0 || world == null ? null : world.findObject(new uz.dukeengine.core.thing.ObjectId(at));
        if (victim == null) {
            return true;
        }
        boolean armed = false;
        for (var id : selection) {
            var unit = world.findObject(new uz.dukeengine.core.thing.ObjectId(id));
            if (unit == null || unit.getPlayerIndex() != getLocalPlayerIndex()) {
                continue;
            }
            var weapon = unit.findModule(uz.dukeengine.rts.module.WeaponUpdate.class);
            if (weapon == null) {
                continue;
            }
            armed = true;
            if (weapon.canFireAt(victim)) {
                return true;
            }
        }
        return !armed;
    }

    // ---- the order a click would give ----

    /** The word of the order a click on a thing would give what is selected — see {@link #contextOrder}. */
    @FunctionalInterface
    public interface ContextOrder {
        /**
         * @param selection the local player's own things that are selected, in the order they were chosen
         * @param target    the thing under the pointer
         * @return the order's word — {@code Enter}, {@code Dock}, {@code Repair}, {@code Capture} — or {@code null}
         *         where a click on it gives what a click gives anyway
         */
        String orderOn(List<GameObject> selection, GameObject target);
    }

    private ContextOrder contextOrder;

    /** The word of the order a click on the ground would give what is selected — see {@link #groundOrder}. */
    @FunctionalInterface
    public interface GroundOrder {
        /**
         * @param selection the local player's own things that are selected, in the order they were chosen
         * @param place     the point of the ground under the pointer
         * @return the order's word — {@code Steer} — or {@code null} where a click there moves what is selected
         */
        String orderAt(List<GameObject> selection, Coord3D place);
    }

    /** The same, told whether the local player has ever seen the point — see {@link #groundOrder(SeenGroundOrder)}. */
    @FunctionalInterface
    public interface SeenGroundOrder {
        /**
         * @param seen whether the local player has ever seen {@code place}, as the client's discovery has it: the
         *             reference steers a power only over ground its player has seen ({@code
         *             canOverrideSpecialPowerDestination}, {@code CELLSHROUD_SHROUDED})
         */
        String orderAt(List<GameObject> selection, Coord3D place, boolean seen);
    }

    private SeenGroundOrder groundOrder;

    /**
     * What a click on the ground means where it is not a move: the reference's
     * {@code MSG_DO_SPECIAL_POWER_OVERRIDE_DESTINATION}, a particle beam or a gunship steered by any click on the
     * ground. Asked as {@link #contextOrder} is, with the point under the pointer where the pointer is on no thing; the
     * snapshot carries the word, the pointer shows its picture, and the click sends {@code GameMessage.GameOrder(player,
     * word, selection, place, null, 0)} instead of a move. A click on a thing still selects it or gives what
     * {@link #contextOrder} says.
     */
    public DukeGame groundOrder(GroundOrder rule) {
        this.groundOrder = rule == null ? null : (selection, place, seen) -> rule.orderAt(selection, place);
        return this;
    }

    /**
     * The same, the rule told whether the local player has ever seen the ground it is asked about: a beam steered only
     * over ground its player has seen, never into the black of the map. Over ground never seen a rule answering no word
     * leaves the click what it is with none.
     */
    public DukeGame groundOrder(SeenGroundOrder rule) {
        this.groundOrder = rule;
        return this;
    }

    /**
     * Whether this game names what a click on the ground orders — {@link #groundOrder} — and so decides rally points
     * itself: a click its rule answers with no word is a move, never a rally point of the client's own.
     */
    public boolean namesGroundOrders() {
        return groundOrder != null;
    }

    /**
     * What a click on a thing means beyond selecting it and attacking it: the reference's context commands, the
     * game's to name. Asked on the simulation thread as the snapshot is built, for the thing under the pointer and
     * what the local player has selected — a transport its infantry may board is {@code Enter}, a supply dock its
     * truck works at {@code Dock}. The snapshot carries the word ({@code WorldSnapshot.contextOrder}), the pointer
     * shows the word's picture, and the click sends {@code GameMessage.GameOrder(player, word, selection, place,
     * target, 0)} for the game's {@link #onOrder} to carry out. The engine never reads the word.
     */
    public DukeGame contextOrder(ContextOrder rule) {
        this.contextOrder = rule;
        return this;
    }

    private String contextOrderNow() {
        var now = pointed;
        var world = logic;
        if (world == null) {
            return null;
        }
        if (now.unit() < 0) {
            var rule = groundOrder;
            var selected = rule == null || now.ground() == null ? List.<GameObject>of() : selectedOwn(world, null);
            if (selected.isEmpty()) {
                return null;
            }
            var cells = world.getSightCells(); // the simulation's cells, where it keeps them, over the client's word
            boolean seen = cells == null ? now.seen() : cells.everSeen(getLocalPlayerIndex(), now.ground());
            return rule.orderAt(selected, now.ground(), seen);
        }
        var rule = contextOrder;
        var target = world.findObject(new uz.dukeengine.core.thing.ObjectId(now.unit()));
        if (rule == null || target == null) {
            return null;
        }
        var selected = selectedOwn(world, target);
        return selected.isEmpty() ? null : rule.orderOn(selected, target);
    }

    /** The local player's own selected things, but {@code except}. */
    private List<GameObject> selectedOwn(RtsSimulation world, GameObject except) {
        var selected = new java.util.ArrayList<GameObject>();
        for (var id : selection) {
            var unit = world.findObject(new uz.dukeengine.core.thing.ObjectId(id));
            if (unit != null && unit != except && unit.getPlayerIndex() == getLocalPlayerIndex()) {
                selected.add(unit);
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Float a short text up from a point of the world, in a colour with its alpha — money earned there, a bounty paid —
     * as the reference's floating text: seen by each machine's player whose view of the point is clear, drawn rising
     * and fading as the client's look says. From any thread, shown from the next frame; the simulation's own code may
     * post a {@link uz.dukeengine.core.event.TextFloated} itself. Not part of the checksum: it is an event.
     *
     * @param z    the point's height
     * @param argb its colour, alpha included
     */
    public void floatText(String text, float x, float y, float z, int argb) {
        var world = logic;
        world.postTask(() -> world.post(new uz.dukeengine.core.event.TextFloated(world.getFrame(),
                new Coord3D(x, y, z), text, argb)));
    }

    /**
     * The same, rising from a thing and shown only to its owner and to whoever may see it — a hidden building's income
     * told to no enemy who has not found it. From any thread; a thing gone by then floats nothing.
     */
    public void floatTextAbout(uz.dukeengine.core.thing.ObjectId thing, String text, int argb) {
        var world = logic;
        world.postTask(() -> {
            var about = world.findObject(thing);
            if (about != null) {
                world.post(uz.dukeengine.core.event.TextFloated.about(world.getFrame(), about, text, argb));
            }
        });
    }

    /**
     * Play the effect of that name at a point of the world, turned to {@code facing} — a moment, an effect list, an
     * effect or a particle system the game named — from any thread, as {@link #floatText}: the simulation's own code
     * calls {@code World.effect}, which this posts. Not part of the checksum: it is an event.
     */
    public void effect(String name, float x, float y, float z, float facing) {
        var world = logic;
        world.postTask(() -> world.effect(name, new Coord3D(x, y, z), facing));
    }

    /** A mark the map burns into its ground — see {@link #markGround}. */
    public record GroundMark(String picture, float x, float y, float radius) {
    }

    private final List<GroundMark> groundMarks = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * A mark of {@code picture} burnt into the ground at a place, {@code radius} each way — as the reference lays a
     * map's own scorches as it loads ({@code W3DTerrainVisual::load}, {@code addScorch}): drawn with the ground
     * whatever any player sees, as an effect's scorch is drawn only where its player could see it played, and in the
     * budget the effects' scorches share, the oldest let go. At load or at any time, from any thread; drawing only.
     */
    public DukeGame markGround(String picture, float x, float y, float radius) {
        groundMarks.add(new GroundMark(picture, x, y, radius));
        return this;
    }

    /** Every mark the game has burnt into the ground, in the order it laid them. */
    public List<GroundMark> groundMarks() {
        return java.util.Collections.unmodifiableList(groundMarks);
    }

    /**
     * A piece of a picture laid along the ground — a road's straight, its curve, its crossing — its corners going round
     * it, {@code a} to {@code b} along the picture from {@code u0} to {@code u1} and {@code a} to {@code d} across it
     * from {@code v0} to {@code v1}, a corner's height nothing; drawn in its {@code layer}, over an earlier one.
     */
    public record StripPiece(String picture, float u0, float v0, float u1, float v1, Coord3D a, Coord3D b, Coord3D c,
            Coord3D d, int layer) {
    }

    private final List<StripPiece> stripPieces = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Pieces of pictures laid along the ground — the reference's roads, pavements, tracks and painted marks ({@code
     * W3DRoadBuffer}) — each laid over the ground's rise and fall as the reference lays its roads, lit and shrouded as
     * the ground, a later layer over an earlier one. How the pieces of a road meet — its curves, its crossings, the
     * picture each is cut from — is the game's to work out. At load or at any time, from any thread; drawing only.
     */
    public DukeGame layStrips(List<StripPiece> pieces) {
        stripPieces.addAll(pieces);
        return this;
    }

    /** Every piece the game has laid along the ground, in the order it laid them. */
    public List<StripPiece> stripPieces() {
        return java.util.Collections.unmodifiableList(stripPieces);
    }

    /** Thread-safe: run work on the simulation thread next frame. */
    public void runOnSimThread(Runnable task) {
        logic.postTask(task);
    }

    /** Thread-safe: toggle the simulation pause state. */
    public void togglePause() {
        runOnSimThread(() -> logic.setGamePaused(!logic.isGamePaused()));
    }

    /** The full simulation — the escape hatch to everything the engine can do. */
    public RtsSimulation getLogic() {
        return logic;
    }

    private void requireNotStarted() {
        if (started) {
            throw new IllegalStateException("configure the game before start()");
        }
    }

    // ---- starter content ----

    /**
     * A small, balanced starter faction so a first game needs no data files:
     * a power plant, a barracks that builds riflemen, a rifleman and a tank.
     */
    public static final String STARTER_UNITS = """
            Object
              Name = PowerPlant
              DisplayName = Power Plant
              KindOf = [STRUCTURE, SELECTABLE, POWERED]
              Geometry = Box
                MajorRadius = 18
                MinorRadius = 14
                Height = 16
              End
              BuildCost = 600
              BuildTime = 4.0
              VisionRange = 30
              Modules = [
                ActiveBody
                  MaxHealth = 400
                End,
                PowerModule
                  Produces = 10
                End
              ]
            End
            Object
              Name = Barracks
              DisplayName = Barracks
              KindOf = [STRUCTURE, SELECTABLE]
              Geometry = Box
                MajorRadius = 20
                MinorRadius = 16
                Height = 14
              End
              BuildCost = 500
              BuildTime = 5.0
              VisionRange = 35
              Modules = [
                ActiveBody
                  MaxHealth = 600
                End,
                ProductionUpdate
                  Builds = [Rifleman, Tank]
                End,
                PowerModule
                  Consumes = 3
                End,
                ; Stall the line when the base outgrows its plants. Asked for here
                ; rather than assumed by the engine — a game with no notion of
                ; capacity simply leaves this off.
                CapacityGate
                End
              ]
            End
            Object
              Name = Rifleman
              DisplayName = Rifleman
              KindOf = [INFANTRY, SELECTABLE, CAN_ATTACK]
              Geometry = Cylinder
                Radius = 3
                Height = 9
              End
              BuildCost = 120
              BuildTime = 1.5
              VisionRange = 40
              Modules = [
                ActiveBody
                  MaxHealth = 80
                End,
                MoveUpdate
                  Speed = 14
                End,
                WeaponUpdate
                  Damage = 9
                  AttackRange = 22
                  ReloadFrames = 12
                End,
                ExperienceModule
                  ExperienceValue = 30
                  ExperienceRequired = [60, 180, 360]
                  LevelDamageBonus = [1.1, 1.2, 1.3]
                  HealOnPromotion = Yes
                End
              ]
            End
            Object
              Name = Tank
              DisplayName = Battle Tank
              KindOf = [VEHICLE, SELECTABLE, CAN_ATTACK]
              Geometry = Box
                MajorRadius = 8
                MinorRadius = 5
                Height = 6
              End
              BuildCost = 700
              BuildTime = 6.0
              VisionRange = 45
              Modules = [
                ActiveBody
                  MaxHealth = 300
                End,
                MoveUpdate
                  Speed = 20
                  TurnRate = 120
                End,
                WeaponUpdate
                  Damage = 40
                  AttackRange = 30
                  ReloadFrames = 45
                  SplashRadius = 6
                  DamageType = EXPLOSION
                End,
                ExperienceModule
                  ExperienceValue = 100
                  ExperienceRequired = [200, 500, 1000]
                  LevelDamageBonus = [1.1, 1.2, 1.3]
                  HealOnPromotion = Yes
                End
              ]
            End
            """;
}
