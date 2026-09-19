package uz.duke.game;

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
import uz.duke.core.GameConstants;
import uz.duke.rts.RtsSimulation;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.network.CommandCodec;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.core.thing.ThingTemplateLoader;
import uz.duke.core.thing.Titled;
import uz.duke.core.thing.WorldTemplate;
import uz.duke.game.swing.GameWindow;
import uz.duke.game.view.WorldSnapshot;

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
    private final List<GamePlayer> players = new ArrayList<>();
    private final List<Runnable> scenario = new ArrayList<>();
    private final List<Consumer<DukeGame>> startCallbacks = new ArrayList<>();
    private final List<Consumer<DukeGame>> tickCallbacks = new ArrayList<>();
    private final List<double[]> intervalSeconds = new ArrayList<>(); // [seconds, callbackIndex]
    private final List<Consumer<DukeGame>> intervalCallbacks = new ArrayList<>();
    private final List<BiConsumer<DukeGame, GamePlayer>> defeatCallbacks = new ArrayList<>();
    private final List<BiConsumer<DukeGame, GamePlayer>> leftCallbacks = new ArrayList<>();

    private final List<Consumer<uz.duke.core.module.ModuleFactory>> moduleCustomizers = new ArrayList<>();
    private final List<Consumer<ThingTemplateLoader>> templateCustomizers = new ArrayList<>();
    private PathGrid terrain;
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
    private uz.duke.core.replay.ReplayRecorder recorder;
    private uz.duke.core.replay.Replay replay;

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
        if (localPlayer == null) {
            localPlayer = player;
        }
        return player;
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
        return this;
    }

    /** Give a player starting money. */
    public DukeGame money(GamePlayer player, int amount) {
        scenario.add(() -> logic.getRtsPlayer(player.getIndex()).deposit(amount));
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
     * {@code game.customModules(mf -> ScriptModule.registerScript(mf, "Guard", Guard::new))}.
     */
    public DukeGame customModules(Consumer<uz.duke.core.module.ModuleFactory> customizer) {
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
     * use for: a {@link uz.duke.core.thing.Layered} world lays every map at its storey height.
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

    public List<String> getChosenFactions() {
        return chosenFactions;
    }

    /** Swap in the chosen map's terrain during skirmish assembly. */
    public void applyMapTerrain(PathGrid grid) {
        this.terrain = grid;
        if (logic != null) {
            logic.setPathGrid(grid);
        }
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

    /** Whether this game is wired to a network session. */
    public boolean isMultiplayer() {
        return multiplayer != null;
    }

    // ---- replay ----

    /**
     * Write the game down as it is played, so it can be watched again — and so a
     * build can prove the simulation still produces the same world from the same
     * input. Call before starting.
     */
    public DukeGame recordReplay() {
        requireNotStarted();
        recorder = new uz.duke.core.replay.ReplayRecorder(CommandCodec.INSTANCE);
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
        replay = uz.duke.core.replay.Replay.parse(replayText, CommandCodec.INSTANCE);
        return this;
    }

    /** The replay driving this game, or {@code null} if it is being played live. */
    public uz.duke.core.replay.Replay getReplay() {
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
        setUp();
        var engineThread = new Thread(engine::execute, "duke-sim");
        engineThread.start();
        return engineThread;
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
            setUp();
        }
        for (int i = 0; i < frames; i++) {
            if (multiplayer == null || multiplayer.beforeStep(logic)) {
                logic.update();
            }
            client.update(); // keeps snapshots flowing for assertions
        }
    }

    /** Boot the engine and apply everything configured on this builder. */
    private void setUp() {
        if (started) {
            throw new IllegalStateException("game already started");
        }
        if (players.isEmpty()) {
            throw new IllegalStateException("add at least one player before starting");
        }

        logic = new RtsLogic();
        client = new RtsClient(logic);
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
            // A cut-off peer and a peer waiting on a slow one look identical from
            // the outside — both stopped — so say which this is.
            multiplayer.onConnectionLost(() -> setBanner("CONNECTION LOST"));
            // The simulation has already been stopped by the gate; all that is left
            // is to say why, so the screen does not simply freeze. The detail is
            // logged by the gate itself, which is where the two worlds were compared.
            multiplayer.onDesync(desync -> setBanner(DESYNC_MESSAGE));
        }
        engine.setMaxFps(maxFps);
        engine.init(); // note: engine init resets subsystems — apply scenario after

        // custom modules must exist before a unit's block names them
        for (var customizer : moduleCustomizers) {
            customizer.accept(logic.getThingFactory().getModuleFactory());
        }
        var loader = uz.duke.rts.RtsTemplate.register(new ThingTemplateLoader(logic.getThingFactory()));
        for (var customizer : templateCustomizers) {
            customizer.accept(loader);
        }
        for (var template : units) {
            logic.getThingFactory().addTemplate(template);
        }
        for (var text : unitTexts) {
            loader.load(text.text(), text.source());
        }
        logic.setWorld(world);
        if (terrain != null) {
            logic.setPathGrid(terrain);
        }

        for (var player : players) {
            player.bind(logic.getPlayerList().addPlayer(player.getName()).getIndex());
        }
        client.setViewerPlayer(localPlayer.getIndex());

        started = true; // spawn() from here on is immediate
        for (var action : scenario) {
            action.run();
        }

        if (commandHandler != null) {
            logic.setGameCommandHandler(commandHandler);
        }
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

        logic.setInGame(true);
        for (var callback : startCallbacks) {
            callback.accept(this);
        }
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
                            == uz.duke.core.player.Relationship.ENEMIES) {
                return; // an enemy still stands
            }
        }
        setBanner("VICTORY");
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

    /** Ask the engine loop to exit; {@link #start()} then returns. */
    public void stop() {
        if (engine != null) {
            engine.setQuitting(true);
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
        return localPlayer != null && localPlayer.isBound() ? localPlayer.getIndex() : -1;
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
            if (entry instanceof uz.duke.rts.module.ProductionUpdate.Data data) {
                var options = new ArrayList<BuildOption>();
                for (var name : data.builds()) {
                    var unit = logic.getThingFactory().findTemplate(name);
                    if (unit != null) {
                        options.add(new BuildOption(name,
                                Titled.of(unit),
                                uz.duke.rts.Buildable.costOf(unit)));
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
              Box
                MajorRadius = 18
                MinorRadius = 14
                Height = 16
              End
              BuildCost = 600
              BuildTime = 4.0
              VisionRange = 30
              ActiveBody
                MaxHealth = 400
              End
              PowerModule
                Produces = 10
              End
            End
            Object
              Name = Barracks
              DisplayName = Barracks
              KindOf = [STRUCTURE, SELECTABLE]
              Box
                MajorRadius = 20
                MinorRadius = 16
                Height = 14
              End
              BuildCost = 500
              BuildTime = 5.0
              VisionRange = 35
              ActiveBody
                MaxHealth = 600
              End
              ProductionUpdate
                Builds = [Rifleman, Tank]
              End
              PowerModule
                Consumes = 3
              End
              ; Stall the line when the base outgrows its plants. Asked for here
              ; rather than assumed by the engine — a game with no notion of
              ; capacity simply leaves this off.
              CapacityGate
              End
            End
            Object
              Name = Rifleman
              DisplayName = Rifleman
              KindOf = [INFANTRY, SELECTABLE, CAN_ATTACK]
              Cylinder
                Radius = 3
                Height = 9
              End
              BuildCost = 120
              BuildTime = 1.5
              VisionRange = 40
              ActiveBody
                MaxHealth = 80
              End
              MoveUpdate
                Speed = 14
              End
              WeaponUpdate
                Damage = 9
                AttackRange = 22
                ReloadFrames = 12
              End
              ExperienceModule
                ExperienceValue = 30
                ExperienceRequired = [60, 180, 360]
                LevelDamageBonus = [1.1, 1.2, 1.3]
                HealOnPromotion = Yes
              End
            End
            Object
              Name = Tank
              DisplayName = Battle Tank
              KindOf = [VEHICLE, SELECTABLE, CAN_ATTACK]
              Box
                MajorRadius = 8
                MinorRadius = 5
                Height = 6
              End
              BuildCost = 700
              BuildTime = 6.0
              VisionRange = 45
              ActiveBody
                MaxHealth = 300
              End
              MoveUpdate
                Speed = 20
                TurnRate = 120
              End
              WeaponUpdate
                Damage = 40
                AttackRange = 30
                ReloadFrames = 45
                SplashRadius = 6
                DamageType = EXPLOSION
              End
              ExperienceModule
                ExperienceValue = 100
                ExperienceRequired = [200, 500, 1000]
                LevelDamageBonus = [1.1, 1.2, 1.3]
                HealOnPromotion = Yes
              End
            End
            """;
}
