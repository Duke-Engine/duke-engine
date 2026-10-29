package uz.dukeengine.game;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import uz.dukeengine.core.Flavour;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord2D;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.replay.Replay;
import uz.dukeengine.core.replay.ReplayRecorder;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.core.thing.WorldTemplate;
import uz.dukeengine.core.view.CameraView;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.swing.GameWindow;

/**
 * The Unity-style entry point of duke-engine: build a playable RTS in a few
 * lines, batteries included — window, camera, unit selection, right-click
 * orders, fog of war, economy and combat all built in.
 *
 * <pre>{@code
 * var game = DukeGame.create("My RTS")
 *         .loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS)
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
 *
 * <p>The class is the facade; each part of what it does lives in a collaborator of its own that it delegates to:
 * {@code Roster} (the players), {@code Content} (templates and weapons), {@code TerrainSetup} (the map),
 * {@code Skirmish} (the catalogue and its choice), {@code Callbacks} (the game's code the simulation calls),
 * {@code Netplay} (the session and the talk beside it), {@code Selection} (the window's selection and bar),
 * {@code Pointer} (the pointer, the aim and the click's word), {@code ClientLink} (the client and how it looks) and
 * {@code Presentation} (what is shown and told back).
 */
public final class DukeGame {

    /**
     * Shown when the worlds diverge. Plain ASCII on purpose: the 3D client draws
     * banners with a bitmap font that has no glyph for a dash it has never seen.
     */
    static final String DESYNC_MESSAGE = "Synchronization lost - game stopped";

    private final String title;
    private String subtitle = "";
    private final List<Runnable> scenario = new ArrayList<>();
    private int windowWidth = 1120;
    private int windowHeight = 720;
    private int maxFps = GameConstants.DEFAULT_MAX_FPS;
    private Long randomSeed;
    private uz.dukeengine.core.Sleep sleep;
    private ReplayRecorder recorder;
    private Replay replay;

    /** The kind of game this is, which makes its world: see {@link #flavour()}. */
    private final Flavour flavour;
    private GameLogic logic;
    private LogicDriver driver;
    private MatchEngine engine;
    private volatile boolean started;
    /** Whether the simulation's thread was ever started. */
    private volatile boolean running;

    private final Roster roster = new Roster();
    private final Content content = new Content();
    private final TerrainSetup terrain = new TerrainSetup();
    private final Skirmish skirmish = new Skirmish();
    private final Callbacks callbacks = new Callbacks();
    private final Netplay netplay = new Netplay(roster);
    private final Selection selection = new Selection();
    private final Pointer pointer = new Pointer(this, roster, selection);
    private final ClientLink link = new ClientLink();
    private final Presentation presentation = new Presentation();

    private DukeGame(String title, Flavour flavour) {
        this.title = title;
        this.flavour = java.util.Objects.requireNonNull(flavour, "flavour");
    }

    /** Begin building a game of the kind on the classpath — see {@code Flavour.found}. */
    public static DukeGame create(String title) {
        return new DukeGame(title, Flavour.found());
    }

    /** Begin building a game of {@code flavour}'s kind: one made for this game, whose own API it keeps. */
    public static DukeGame create(String title, Flavour flavour) {
        return new DukeGame(title, flavour);
    }

    /** The kind of game this is: the flavour that makes its world. */
    public Flavour flavour() {
        return flavour;
    }

    /**
     * The kind of game this is, as {@code kind} — {@code game.flavour(RtsFlavour.class).onProduced(…)} — or an error
     * where it is of another kind.
     */
    public <F extends Flavour> F flavour(Class<F> kind) {
        if (!kind.isInstance(flavour)) {
            throw new IllegalStateException("this game is of " + flavour.getClass().getName() + ", not "
                    + kind.getName());
        }
        return kind.cast(flavour);
    }

    public String getTitle() {
        return title;
    }

    /** A short line shown under the title on the game's main menu. */
    public DukeGame subtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
        return this;
    }

    public String getSubtitle() {
        return subtitle;
    }

    // ---- builder configuration (before start) ----

    /** Unit and structure templates from the text of a {@code .duke} file: {@code Object} blocks, or the game's own. */
    public DukeGame loadUnits(String text) {
        requireNotStarted();
        content.addText(text, "units");
        return this;
    }

    /** Templates a game has already read, as a game that reads its files once for every world does. */
    public DukeGame addUnits(Collection<? extends ThingTemplate> templates) {
        requireNotStarted();
        content.addUnits(templates);
        return this;
    }

    /**
     * The weapons the game's units link by name from their weapon sets — one block a weapon, however many
     * units carry it. Handed to the world when it boots.
     */
    public DukeGame addWeapons(Collection<uz.dukeengine.combat.module.Weapon> more) {
        requireNotStarted();
        content.addWeapons(more);
        return this;
    }

    /** The game's weapon bonus table — see {@link uz.dukeengine.combat.module.WeaponBonus}. */
    public DukeGame addWeaponBonuses(Collection<uz.dukeengine.combat.module.WeaponBonus> more) {
        requireNotStarted();
        content.addWeaponBonuses(more);
        return this;
    }

    /** Unit definitions from a {@code .duke} file. */
    public DukeGame loadUnitsFile(Path file) {
        try {
            requireNotStarted();
            content.addText(Files.readString(file), file.toString());
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException("could not read units file: " + file, e);
        }
    }

    /** An open map of {@code cellsWide × cellsHigh} pathfinding cells (10 world units each). */
    public DukeGame map(int cellsWide, int cellsHigh) {
        requireNotStarted();
        terrain.setGrid(new PathGrid(cellsWide, cellsHigh));
        return this;
    }

    /** A map from ASCII art: {@code #} = impassable, anything else = open. */
    public DukeGame mapFromText(String asciiMap) {
        requireNotStarted();
        terrain.setGrid(MapLoader.fromText(asciiMap));
        return this;
    }

    /** Add a player. The first player added is the local (viewing) player by default. */
    public GamePlayer addPlayer(String name, Color color) {
        requireNotStarted();
        return roster.add(name, color);
    }

    /**
     * The camera as the game drives it, from its own code on the simulation thread — see {@link GameCamera}. Stepped
     * once a logic frame, before the game's own per-frame code.
     */
    public GameCamera camera() {
        return presentation.camera();
    }

    /**
     * Where the player is looking, told by the client from its own thread — what the game's camera follows while the
     * game is not driving it.
     */
    public void setCameraSeen(float x, float y, float angle) {
        setCameraSeen(x, y, angle, Float.NaN, Float.NaN);
    }

    /** The same with its pitch and how far back it stands, so a shot that tilts or draws back starts from them. */
    public void setCameraSeen(float x, float y, float angle, float pitch, float distance) {
        presentation.camera().seen(new CameraView(x, y, angle, pitch, distance));
    }

    /**
     * Roll the credits — {@code lines}, headings and names in order — up a darkened screen over {@code seconds}, over
     * everything; drawn only, out of the checksum. Esc or a click skips them; {@code ended} is told on the window's
     * thread when they have rolled out or been skipped. From any thread; a roll already going is replaced.
     */
    public void creditsRoll(List<CreditsRoll.Line> lines, float seconds, Runnable ended) {
        presentation.creditsRoll(new CreditsRoll(lines, seconds, ended));
    }

    /** The credits rolling, or null once the client has said they are gone — from any thread. */
    public CreditsRoll creditsShowing() {
        return presentation.creditsShowing();
    }

    /** Told by the client when {@code rolled} is over; a roll put up since stays. */
    public void creditsGone(CreditsRoll rolled) {
        presentation.creditsGone(rolled);
    }

    /**
     * Show a title card — {@code text} large in the middle of a darkened screen, {@code subtext} under it — faded in
     * over {@code fadeIn} seconds, held {@code hold}, faded out over {@code fadeOut}: an act's end, a chapter's name.
     * Drawn only, out of the checksum; the player's keys and clicks go to nothing while it shows. From any thread; a
     * card already up is replaced.
     */
    public void titleCard(String text, String subtext, float fadeIn, float hold, float fadeOut) {
        titleCard(text, subtext, fadeIn, hold, fadeOut, true);
    }

    /** The same, saying whether it takes the player's input while it shows. */
    public void titleCard(String text, String subtext, float fadeIn, float hold, float fadeOut, boolean takesInput) {
        presentation.titleCard(new TitleCard(text, subtext, fadeIn, hold, fadeOut, takesInput));
    }

    /** The card showing, or null once the client has said it is gone — from any thread. */
    public TitleCard titleCardShowing() {
        return presentation.titleCardShowing();
    }

    /** Told by the client when {@code shown} has played out; a card put up since stays. */
    public void titleCardGone(TitleCard shown) {
        presentation.titleCardGone(shown);
    }

    /**
     * Put the player's view over {@code (x, y)} — the reference's {@code TheTacticalView->lookAt} from its interface: a
     * press on the radar, the key that looks at the last event. From any thread: the client centres its own camera
     * there at its next frame, turned, pitched and as far back as it was, and the camera stays the player's, his
     * scrolling going on from there at once. Two before a frame land on the second; one while the game drives the
     * camera waits until it is let go.
     */
    public void moveView(float x, float y) {
        presentation.moveView(new Coord2D(x, y));
    }

    /** The view move asked for since the client last took one, or null: the client's, once a frame. */
    public Coord2D takeViewMove() {
        return presentation.takeViewMove();
    }

    /**
     * Where the key that looks at the latest alert looks — the reference's last radar event ({@code
     * Radar::getLastEventLoc}): an attack at most once in ten seconds, a building finished, a unit lost — and from now
     * on the client moves it no more on its own when a thing of the player's is hurt: the game says what an alert is.
     * Null for none yet. From any thread.
     */
    public void setLatestAlert(Coord2D place) {
        presentation.setLatestAlert(place);
    }

    /** Whether the game keeps the latest alert itself ({@link #setLatestAlert}). */
    public boolean alertsAreTheGames() {
        return presentation.alertsAreTheGames();
    }

    /** The latest alert the game set, or null. */
    public Coord2D latestAlert() {
        return presentation.latestAlert();
    }

    /**
     * What the player's view covers, as the client last drew it — the eye and the rays through the corners of the
     * world's part of the window, see {@link uz.dukeengine.core.view.ViewRays} — for a radar of the game's own to
     * outline; null before the client has drawn a frame. From any thread.
     */
    public uz.dukeengine.core.view.ViewRays viewRays() {
        return presentation.viewRays();
    }

    /** What the player's view covers, told by the client from its own thread each frame it draws the world. */
    public void setViewRays(uz.dukeengine.core.view.ViewRays rays) {
        presentation.setViewRays(rays);
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
     * Let this match's things far from every waker sleep from its first frame — see {@link uz.dukeengine.core.Sleep}:
     * a world larger than any view costs what is near its heroes. Mid-match the game's own code changes it on the
     * simulation thread, with {@code getLogic().setSleep}.
     */
    public DukeGame sleep(uz.dukeengine.core.Sleep rule) {
        requireNotStarted();
        this.sleep = rule;
        return this;
    }

    /**
     * Watch rather than play: no player is this machine's, so everything is seen — through no player's fog — and
     * nothing is ordered. An observer's seat in a skirmish, and a match run behind a front end.
     */
    public DukeGame observe() {
        requireNotStarted();
        roster.sit(null);
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

    /**
     * Take a seat in a match already running — a backdrop adopted as the played match ({@code Duke3D.adoptBackdrop}):
     * {@code player}'s point of view and orders from the next snapshot on, through its fog; null to watch again.
     */
    public void takeSeat(GamePlayer player) {
        roster.sit(player);
        link.viewer(roster.viewer());
    }

    /** Choose whose point of view (and input) the window uses. */
    public DukeGame localPlayer(GamePlayer player) {
        roster.sit(player);
        return this;
    }

    /**
     * A side a computer plays: what only a computer may make, it may
     * ({@code Prerequisites.Buildability.ONLY_BY_COMPUTER}).
     */
    public DukeGame computer(GamePlayer player) {
        scenario.add(() -> logic.getPlayerList().getPlayer(player.getIndex()).setComputer(true));
        return this;
    }

    /** Run {@code action} now once the game has started, or at its boot before the first frame. */
    private void atBoot(Runnable action) {
        if (started) {
            action.run();
        } else {
            scenario.add(action);
        }
    }

    /**
     * Place a unit or structure. Before {@link #start()} this schedules the spawn
     * for game boot; after start (from a callback, on the simulation thread) it
     * spawns immediately.
     */
    public DukeGame spawn(String templateName, GamePlayer owner, float x, float y) {
        atBoot(() -> {
            var template = logic.getThingFactory().findTemplate(templateName);
            if (template == null) {
                throw new IllegalArgumentException("unknown unit template '" + templateName
                        + "' — did you loadUnits() its block?");
            }
            logic.spawn(template, new Coord3D(x, y, 0f), owner.getIndex());
        });
        return this;
    }

    /** Place a neutral object (resource pile, critter) — owner is nobody. */
    public DukeGame spawnNeutral(String templateName, float x, float y) {
        atBoot(() -> {
            var template = logic.getThingFactory().findTemplate(templateName);
            if (template != null) {
                logic.spawn(template, new Coord3D(x, y, 0f), 0);
            }
        });
        return this;
    }

    /**
     * Register custom engine modules (e.g. compiled {@code UnitScript}s) before
     * units are read — the extension point for user code:
     * {@code game.customModules(mf -> ScriptModule.registerScript(mf, Guard.Data.class, Guard::new))}.
     */
    public DukeGame customModules(Consumer<uz.dukeengine.core.module.ModuleFactory> customizer) {
        requireNotStarted();
        content.customModules(customizer);
        return this;
    }

    /**
     * Register the game's own template block types before its units are read:
     * {@code game.templates(loader -> loader.type(Monster.class))}. An
     * {@code Object} block is already an RTS template, with a build cost and time.
     */
    public DukeGame templates(Consumer<ThingTemplateLoader> customizer) {
        requireNotStarted();
        content.templates(customizer);
        return this;
    }

    /**
     * What the game's {@code World} block says of its world. The engine reads what it has a
     * use for: a {@link uz.dukeengine.core.thing.Layered} world lays every map at its storey height.
     */
    public DukeGame world(WorldTemplate world) {
        requireNotStarted();
        content.world(world);
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
        callbacks.onStart(callback);
        return this;
    }

    /** Runs every logic frame (30×/second) on the simulation thread. */
    public DukeGame onTick(Consumer<DukeGame> callback) {
        callbacks.onTick(callback);
        return this;
    }

    /** Runs every {@code seconds} of game time on the simulation thread. */
    public DukeGame everySeconds(double seconds, Consumer<DukeGame> callback) {
        callbacks.every(seconds, callback);
        return this;
    }

    /**
     * A number of the game's own mixed into the world's checksum on every checked frame — state it keeps outside the
     * engine, which every machine must agree on — asked on the simulation thread. Two machines whose numbers part are
     * told they desynced, as for any other difference. See {@code GameLogic.checksumAlso}.
     */
    public DukeGame checksumAlso(LongSupplier part) {
        callbacks.checksumAlso(part);
        return this;
    }

    /**
     * State of the game's own kept outside the engine — a computer player's plans, a script's flags, a ledger its
     * modules share — written into a snapshot under {@code name} (one word, given once) and read back into a world
     * built by the same rules, once every thing of the save is in it, on the simulation thread. See {@code
     * GameLogic.saveAlso}; before the match is built.
     */
    public DukeGame saveAlso(String name, uz.dukeengine.core.save.Saveable part) {
        requireNotStarted();
        callbacks.saveAlso(name, part);
        return this;
    }

    /**
     * Told every death as it is reaped, on the simulation thread: what died, whose it was, what killed it and whose
     * side that was — known even when the killer is gone too — beside the client's event rather than instead of it.
     * Where a game counts what each side destroyed. A building sold, or anything removed without dying, is not heard.
     */
    public DukeGame onDied(Consumer<uz.dukeengine.core.event.ObjectDied> callback) {
        callbacks.onDied(callback);
        return this;
    }

    /**
     * Told every order of the game's own — a {@link uz.dukeengine.combat.message.GameOrder}, posted with
     * {@link #postCommand} from any thread — as it is applied: on the simulation thread, on the same frame and in the
     * same order on every machine, and again when a replay plays it. The game does what the order means there — grants
     * a science, fires a power — deterministically. The engine never reads its word or its number.
     */
    public DukeGame onOrder(Consumer<uz.dukeengine.combat.message.GameOrder> listener) {
        callbacks.onOrder(listener);
        return this;
    }

    /** Runs when a player who had units loses all of them (annihilation). */
    public DukeGame onPlayerDefeated(BiConsumer<DukeGame, GamePlayer> callback) {
        callbacks.onDefeated(callback);
        return this;
    }

    /**
     * Runs when a player drops out of a network game — their machine is gone, as
     * opposed to their army being destroyed.
     */
    public DukeGame onPlayerLeft(BiConsumer<DukeGame, GamePlayer> callback) {
        callbacks.onLeft(callback);
        return this;
    }

    /**
     * Handle commands outside the kind of game's set — the ones this game invented.
     *
     * <p>The engine's contract has always been that a game declares its own
     * command set ({@link Command}); a kind's set — the RTS library's {@code GameMessage} —
     * is not the only one a game on it might want. A roguelike's "cast the third
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
     * replay speak the kind's set. An order that must reach every machine and every
     * replay is a {@link uz.dukeengine.combat.message.GameOrder}, heard through {@link #onOrder}.
     */
    public DukeGame onCommand(Consumer<Command> handler) {
        callbacks.onCommand(handler);
        return this;
    }

    // ---- skirmish (map + faction selection, the real-RTS flow) ----

    /** Builds the chosen scenario (terrain, bases, neutrals) at boot time. */
    public interface SkirmishAssembler {
        void assemble(DukeGame game, String mapName, List<String> playerFactions);
    }

    /**
     * Register the skirmish catalogue: the maps and factions the player can
     * choose from, and the assembler that builds the match from a choice.
     * With a catalogue set, author-time spawns are usually unnecessary.
     */
    public DukeGame skirmish(List<String> maps, List<String> factions, SkirmishAssembler assembler) {
        requireNotStarted();
        skirmish.catalogue(maps, factions, assembler);
        return this;
    }

    public List<String> getMapChoices() {
        return skirmish.mapChoices();
    }

    /**
     * A picture for each map the player may choose, by the map's name — what a menu draws beside the row
     * while it is the lit one.
     *
     * <p>The game's to say, because the game knows where its maps are: {@code MapPackage.previewResource()}
     * is the name of the one found beside the map's own file. A map named here with no picture, or no
     * picture at all, is a row of words, which is what every map was.
     */
    public DukeGame mapPictures(Map<String, String> byMapName) {
        skirmish.pictures(byMapName);
        return this;
    }

    /** The picture for a map, or null. */
    public String getMapPicture(String mapName) {
        return skirmish.picture(mapName);
    }

    public List<String> getFactionChoices() {
        return skirmish.factionChoices();
    }

    /** Pick the map and per-player factions before starting (menus call this). */
    public void selectSkirmish(String mapName, List<String> playerFactions) {
        requireNotStarted();
        skirmish.choose(mapName, playerFactions);
    }

    public String getChosenMap() {
        return skirmish.chosenMap();
    }

    /**
     * Say that this match places everything it will ever draw while it is set up — a front end's shell map, a mission
     * laid out whole — so {@link #templatesThisMatchCanDraw} plans from what stands in it, as it does for a map chosen
     * from the skirmish list, rather than the client reading every look the game registered.
     */
    public DukeGame placesEverythingAtSetup() {
        requireNotStarted();
        skirmish.placesEverythingAtSetup();
        return this;
    }

    public List<String> getChosenFactions() {
        return skirmish.chosenFactions();
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
        terrain.set(grid, map);
        if (logic != null) {
            terrain.applyTo(logic);
        }
    }

    /** The record the current terrain was laid from, or null where the game never handed one over. */
    public Object getMapRecord() {
        return terrain.mapRecord();
    }

    // ---- multiplayer (before start) ----

    /**
     * Host a LAN game for every player in the project, binding this machine to
     * the <b>first</b> of them. Blocks until all the others have joined.
     */
    public MultiplayerSession hostMultiplayer(int port) throws IOException {
        return hostMultiplayer(port, roster.size(), null);
    }

    /**
     * Host a LAN game for {@code playerCount} players and wait for the other
     * {@code playerCount - 1} to join. Call before starting the engine; every
     * machine must run the same game definition.
     *
     * @param onGuestJoined told how many guests are in so far, for a lobby screen
     */
    public MultiplayerSession hostMultiplayer(int port, int playerCount, IntConsumer onGuestJoined)
            throws IOException {
        requireNotStarted();
        return netplay.host(port, playerCount, skirmish.encode(), onGuestJoined);
    }

    /** Abort a pending {@link #hostMultiplayer} that is waiting for a guest. */
    public void cancelHosting() {
        netplay.cancelHosting();
    }

    /**
     * Join a hosted LAN game. The host says which player this machine is; the
     * call blocks until the host has everyone and starts the game.
     */
    public MultiplayerSession joinMultiplayer(String host, int port) throws IOException {
        requireNotStarted();
        var session = netplay.join(host, port);
        skirmish.apply(session.getScenarioSpec()); // play the host's chosen match
        return session;
    }

    /**
     * Play this match over a session made before it — for a game that runs its own lobby, hosts or joins with
     * {@link MultiplayerSession#host} and {@link MultiplayerSession#join}, reads the host's settings off
     * {@link MultiplayerSession#getScenarioSpec}, and builds the match from them. This machine plays the player the
     * session seated it as. Before starting.
     */
    public DukeGame multiplayer(MultiplayerSession session) {
        requireNotStarted();
        netplay.adopt(session);
        return this;
    }

    /** Whether this game is wired to a network session. */
    public boolean isMultiplayer() {
        return netplay.active();
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
        recorder = new ReplayRecorder(flavour.codec());
        return this;
    }

    /**
     * The recording so far, whole up to the last frame written; safe from any thread while the match runs. Empty if
     * {@link #recordReplay()} was never called.
     */
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
        replay = Replay.parse(replayText, flavour.codec());
        return this;
    }

    /** The replay driving this game, or {@code null} if it is being played live. */
    public Replay getReplay() {
        return replay;
    }

    /** Whether the project has enough players for a network game. */
    public boolean supportsMultiplayer() {
        return roster.size() >= 2;
    }

    /** The most players this game definition can seat over the network. */
    public int getMaxNetworkPlayers() {
        return roster.size();
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
            netplay.close(); // the match is over for this machine; the others are told it has gone
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
    public void boot(IntConsumer progress) {
        if (!started) {
            setUp(progress);
        }
        progress.accept(100);
    }

    // ---- what the players say ----

    /**
     * Say a line from this machine's player to these players — everyone, allies, a chosen few — from any thread. It
     * goes beside the match, never in it: no frame waits for it, and neither the replay nor the checksum sees it. In a
     * network game every addressed machine hears it; alone, the sender hears its own.
     */
    public void say(String text, Collection<Integer> to) {
        netplay.say(text, to);
    }

    /**
     * Told every line said to this machine's player — its own included — with who said it and to whom, in the order
     * each sender said them, on the simulation's thread. The 3D client's {@code Duke3D.onChat} hands them to the
     * window's.
     */
    public DukeGame onChat(Consumer<uz.dukeengine.core.network.ChatLine> listener) {
        netplay.onChat(listener);
        return this;
    }

    /**
     * How far this machine has got loading the match, 0 to 100, told by whatever does the loading — the 3D client
     * does — on its own thread, once the match is built and until it starts. In a network game it is said to every
     * other machine, and what they have said is taken in: see {@link #onPeerLoadProgress}.
     */
    public void loadProgress(int percent) {
        netplay.loadProgress(percent);
    }

    /**
     * Told how far another machine of a network game has got loading — which player, what percent — for a load
     * screen that shows a bar for everybody. On the thread doing this machine's load until the match starts, and on
     * the simulation's thread after, for a slower machine still loading.
     */
    public DukeGame onPeerLoadProgress(ObjIntConsumer<GamePlayer> listener) {
        netplay.onPeerLoadProgress(listener);
        return this;
    }

    /**
     * Every template this match can ever draw, or {@code null} where there is no match to plan from.
     *
     * <p>What stands in the world once it is assembled, everything those can produce, and what that can
     * produce in turn — followed through the modules whose data names what they bring
     * ({@link uz.dukeengine.core.module.Brings}, a factory's build list), so the engine needs no help from
     * the game to follow it. A barracks that trains riflemen brings the rifleman in; a factory that builds a
     * war factory brings in everything the war factory builds.
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
    public Set<String> templatesThisMatchCanDraw() {
        return !started || !skirmish.knowsItsArt() ? null : content.reachableFrom(logic);
    }

    /**
     * Name, per template, the other templates it may bring into the world — what its death leaves (a hulk, rubble,
     * a burning soldier), a crate it drops — followed as a factory's build list is, so the art of what only a death
     * makes is read with the match rather than the first time one dies in the middle of a battle. Before
     * {@link #start}; an empty list for none.
     */
    public DukeGame brings(Function<String, ? extends Collection<String>> rule) {
        requireNotStarted();
        content.brings(rule);
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
            netplay.talk();
            if (engine.isLogicFrameReady()) {
                logic.update();
            }
            link.client().update(); // keeps snapshots flowing for assertions
        }
    }

    /** Boot the engine and apply everything configured on this builder, saying how far it has got. */
    private void setUp(IntConsumer progress) {
        if (started) {
            throw new IllegalStateException("game already started");
        }
        if (roster.size() == 0) {
            throw new IllegalStateException("add at least one player before starting");
        }

        logic = flavour.newWorld();
        driver = new LogicDriver(logic, flavour);
        logic.eachFrame(driver);
        // Whose side has been beaten and the camera's own moves: the engine game's state outside the world, saved first.
        logic.saveAlso("duke.driver", driver);
        logic.saveAlso("duke.camera", presentation.camera());
        callbacks.attach(logic);
        var client = link.create(logic, flavour.views());
        client.setCommands(selection::buttonsNow);
        client.setAimAnswer(pointer::aimAnswerNow);
        client.setAttackable(pointer::attackableNow);
        client.setContextOrder(pointer::snapshotWord);
        engine = new MatchEngine(logic, client);
        if (recorder != null) {
            logic.setFrameLog(recorder);
        }
        if (replay != null) {
            engine.setReplay(replay);
        }
        if (netplay.active()) {
            netplay.wire(driver, engine, this::announcePlayerLeft, this::setBanner);
        }
        engine.setMaxFps(maxFps);
        engine.everyTurn(netplay::talk);
        engine.init(); // note: engine init resets subsystems — apply scenario after
        if (randomSeed != null) {
            logic.setRandomSeed(randomSeed);
        }
        logic.setSleep(sleep);
        client.setCamera(presentation.camera()::shown);
        progress.accept(5);

        content.load(logic, flavour);
        progress.accept(15);
        content.setWorld(logic);
        if (terrain.grid() != null) {
            terrain.applyTo(logic);
        }
        progress.accept(20);

        roster.bindAll(logic.getPlayerList());
        link.viewer(roster.viewer());
        flavour.began(logic);
        progress.accept(30);

        started = true; // spawn() from here on is immediate
        for (var action : scenario) {
            action.run();
        }
        progress.accept(40);

        logic.onOtherCommands(callbacks::otherCommand);
        // The camera first: a move the game's code orders in a frame takes its first step in the next.
        driver.addTickCallback(() -> presentation.camera().step(id -> {
            var thing = logic.findObject(id);
            return thing == null || thing.isEffectivelyDead() ? null : thing.getPosition();
        }));
        callbacks.schedule(this, driver);
        driver.setDefeatListener(playerIndex -> {
            var player = roster.bound(playerIndex);
            if (player != null) {
                callbacks.playerDefeated(this, player);
            }
            updateOutcomeBanner(playerIndex);
        });

        // the skirmish assembler builds the chosen match (terrain, bases, neutrals)
        skirmish.assemble(this);
        progress.accept(70);
        // What reaches what, worked out now rather than by the first order given: a big map's zones are a pass over
        // every cell, and the load is where a pass over every cell belongs.
        logic.zones();
        progress.accept(80);

        logic.setInGame(true);
        callbacks.started(this);
        progress.accept(90);
    }

    private void announcePlayerLeft(int playerIndex) {
        var who = roster.playerAt(playerIndex);
        if (who != null) {
            callbacks.playerLeft(this, who);
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
                    && logic.getRelationship(local, object.getPlayerIndex()) == Relationship.ENEMIES) {
                return; // an enemy still stands
            }
        }
        setBanner("VICTORY");
    }

    // ---- how the client looks ----

    /**
     * Words for its things' moments — moving, attacking, each weapon slot firing, between shots and reloading, a turret
     * turning — added to the words each holds while they last, so its looks choose their model, clip and pieces by them
     * too; the reference's MOVING, FIRING_A and the rest. Null says none, as before.
     */
    public DukeGame momentWords(uz.dukeengine.core.view.MomentWords words) {
        link.momentWords(words);
        return this;
    }

    /**
     * Which things a viewer goes on seeing once seen: the view he last had of each while it was in sight, marked
     * remembered, for as long as the ground under it is seen but not in sight — drawn through the fog as it was, not
     * picked and with no bar; dropped once that ground is in sight again, and never shown on ground never seen. The
     * reference's ghosts of still things ({@code W3DGhostObject}): a tree, a house, an enemy's base that no longer
     * blink out as its looker leaves. Asked on the simulation thread; null, the default, for none.
     */
    public DukeGame remember(Predicate<GameObject> which) {
        link.remember(which);
        return this;
    }

    /**
     * How many frames a thing that goes out of a viewer's sight stays in his view as it is, after it was last in sight,
     * and a dead one — the reference's 60 and 150 ({@code GameClient::update}: "planes that pop out of the shroud,
     * fire, then head back"). 0, the default, for not at all.
     */
    public DukeGame keepOutOfSight(int frames, int deadFrames) {
        link.keepOutOfSight(frames, deadFrames);
        return this;
    }

    /**
     * The weather falling over the view in this match — see {@link uz.dukeengine.core.view.FallingWeather}; null
     * clears it. Drawing only: nothing in the simulation or the checksum, and a game run without a window keeps it
     * and draws nothing. From any thread.
     */
    public DukeGame weather(uz.dukeengine.core.view.FallingWeather weather) {
        presentation.weather(weather);
        return this;
    }

    /** The weather falling over the view in this match, or null for none. */
    public uz.dukeengine.core.view.FallingWeather getWeather() {
        return presentation.weather();
    }

    /** Show (or clear with "") a big centered message in the game window. */
    public void setBanner(String banner) {
        link.setBanner(banner);
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
        link.setStatus(status);
    }

    /**
     * Whether each frame carries what happens where the local player does not see, and the things there — for a sound
     * fog does not hide, heard and not shown ({@code WorldSnapshot.unseenEvents}, {@code hiddenUnits}). Off unless the
     * client asks, as its game's sounds say.
     */
    public DukeGame hearThroughFog(boolean hears) {
        link.hearThroughFog(hears);
        return this;
    }

    /**
     * Ask the engine loop to exit; {@link #start()} then returns. A network match that was never run lets go of its
     * session here, as a run one does when its loop ends.
     */
    public void stop() {
        if (engine != null) {
            engine.setQuitting(true);
        }
        if (!running) {
            netplay.close();
        }
    }

    /**
     * Run the world {@code multiplier} times as fast as the wall clock: 0.25 to 8, 0 or less a pause; each step still
     * a thirtieth of a second of it, so the checksums and the order of orders do not change
     * ({@link uz.dukeengine.core.GameEngine#setLogicRate}). In a match of several machines the game agrees on it as an
     * order. Once booted, from any thread.
     */
    public void setLogicRate(float multiplier) {
        booted().setLogicRate(multiplier);
    }

    /** How many times real time the world runs at; 1 until {@link #setLogicRate} says otherwise. Once booted. */
    public float getLogicRate() {
        return booted().getLogicRate();
    }

    /**
     * One frame of the world on the engine's next turn, while it is paused or at a rate of 0: a replay's Next Frame.
     * Once booted, from any thread. {@link #runHeadless} steps a frame each attempt, paused or not, and ignores it.
     */
    public void stepOnce() {
        booted().stepOnce();
    }

    private MatchEngine booted() {
        if (engine == null) {
            throw new IllegalStateException("the game has no engine yet: boot it first");
        }
        return engine;
    }

    // ---- runtime access (used by the window, callbacks, and tests) ----

    /** The most recent frame of the world; safe from any thread. */
    public WorldSnapshot getSnapshot() {
        return link.snapshot();
    }

    /** The navigation grid, or {@code null} for open terrain. */
    public PathGrid getTerrain() {
        return terrain.grid();
    }

    /** The display colour for a player index (neutral is gray). */
    public Color getColor(int playerIndex) {
        return roster.colorOf(playerIndex);
    }

    /** The index of the viewing player (whose units the window commands). */
    public int getLocalPlayerIndex() {
        return roster.localIndex();
    }

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
     * Mark the whole map seen by {@code player}, none of it in sight — the reference's {@code revealMapForPlayer},
     * what a skirmish or network match whose own option leaves the shroud out gives each player at its start; see
     * {@code GameLogic.markMapSeen}. The game reads its own option and decides, as it decides whether to {@link
     * #revealMapTo}. Before {@link #start()} it is done at boot, before the first frame; after, from the simulation
     * thread, at once. His ground is drawn seen from then, and what stands on it is shown only once his own see it.
     */
    public DukeGame markMapSeen(GamePlayer player) {
        atBoot(() -> logic.markMapSeen(player.getIndex()));
        return this;
    }

    /**
     * Make this machine a watcher from now on, mid-match: no player of its own, so nothing selected and no order sent
     * — the whole map seen, through nobody's fog. The machine goes on taking part in the match's lock-step, so the
     * others play on. For the machine whose player was beaten ({@code getLocalPlayerIndex()} before the call); what
     * {@link #observe} is before a match starts.
     */
    public DukeGame watch() {
        roster.watch();
        link.viewer(MatchClient.EVERYONE);
        return this;
    }

    /** Whether this machine is a watcher: from the start ({@link #observe}), or since {@link #watch}. */
    public boolean isWatching() {
        return roster.isWatching();
    }

    /**
     * Thread-safe: queue a command into the simulation, what the UI uses — one of the kind of game's set, an order every
     * side gives (a {@link uz.dukeengine.combat.message.CombatOrder}: a move, an attack, a stop; a word order,
     * {@link uz.dukeengine.combat.message.GameOrder}), or a command the game declared itself — see {@link #onCommand}.
     * Each is applied on the next frame boundary and lands in the same replay log.
     */
    public void postCommand(Command command) {
        driver.post(command);
    }

    // ---- the command bar ----

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
    public DukeGame commandBar(Function<List<Integer>, List<uz.dukeengine.core.view.CommandButton>> buttons) {
        selection.commandBar(buttons);
        return this;
    }

    /**
     * What the window has selected, so the next snapshot's buttons are about it.
     *
     * <p>Thread-safe and one-way: the window writes, the simulation reads. Selection is the window's own
     * idea — the simulation has never had one and does not gain one here, because two players selecting
     * different things must still reach the same frame.
     */
    public void setSelection(List<Integer> unitIds) {
        selection.set(unitIds);
    }

    public List<Integer> getSelection() {
        return selection.get();
    }

    /**
     * The things the local player's orders go to, where they are not what is selected — a game of one hero, whose
     * orders go to the hero whatever is picked out to be looked at. A click is judged for them: whether an attack on
     * what the pointer is on would be taken, and the word of the order it gives. {@code null} puts it back to the
     * selection, which is what it is until a window says otherwise.
     *
     * <p>Thread-safe and one-way, as the selection is: the window writes, the simulation reads.
     */
    public void setOrdered(List<Integer> unitIds) {
        selection.setOrdered(unitIds);
    }

    /** Who the player's orders go to now — see {@link #setOrdered} — from any thread. */
    public List<Integer> getOrderedTo() {
        return selection.orderedTo();
    }

    /**
     * Select these things in the client, from the game's own code on any thread: a portrait of a multi-selection
     * clicked, a double-click choosing all of a kind on the game's own bar. The client takes it up at its next frame,
     * in this order, under its own rule of what may be selected together — the player's own things, or one of
     * someone else's alone; what is gone or hidden is left out — and tells the selection back as it tells any other
     * ({@link #getSelection}).
     */
    public void select(List<ObjectId> ids) {
        select(ids, false);
    }

    /**
     * The same, {@code answered} asking the client to answer it as a click's is, with the select voice of a thing it
     * took in — as the reference's own every-unit keys, which a game that selects on the screen first takes itself.
     */
    public void select(List<ObjectId> ids, boolean answered) {
        selection.request(ids, answered);
    }

    /**
     * Thread-safe: the player's control groups as the client keeps them — each group that holds anyone by its number,
     * the ids of its members — told whenever they change, for a game that draws a group's number by a thing as the
     * reference does ({@code Drawable::drawUIText}).
     */
    public void setControlGroups(Map<Integer, List<Integer>> groups) {
        selection.setControlGroups(groups);
    }

    /** The player's control groups, as the client last told them: by number, the ids of each one's members. */
    public Map<Integer, List<Integer>> getControlGroups() {
        return selection.controlGroups();
    }

    /** A selection the game asked for: what, and whether it is answered with a voice. */
    public record Selecting(List<ObjectId> ids, boolean answered) {
    }

    /** The selection the game last asked for, taken once by the client that shows it; null when none waits. */
    public List<ObjectId> takeSelectionRequest() {
        var taken = takeSelecting();
        return taken == null ? null : taken.ids();
    }

    /** The same, with whether it is answered; null when none waits. */
    public Selecting takeSelecting() {
        return selection.takeRequest();
    }

    /**
     * What to do when one of the bar's buttons is pressed: which, what was selected, and where or at what
     * for one that aims — see {@link uz.dukeengine.core.view.CommandPress}.
     *
     * <p>The handler's job is to turn that into one of the game's own {@link Command}s and
     * {@link #postCommand} it, which is the road every order already travels — queued from the input
     * thread, applied at the start of a frame, written to the replay log. Doing anything to the world
     * here instead would be doing it off the simulation thread and out of the log.
     */
    public DukeGame onCommandPressed(Consumer<uz.dukeengine.core.view.CommandPress> pressed) {
        selection.onPressed(pressed);
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
        selection.press(id, place, facing, target);
    }

    /**
     * What to do when an armed button is pressed where the simulation will not take it — its ghost red ({@link
     * #aimFits}): which button, what was selected, where and facing which way. Nothing is sent and the button stays
     * armed; the game may say why in its own words and voice, as the reference's {@code
     * InGameUI::displayCantBuildMessage} does. A press taken is heard by {@link #onCommandPressed} as ever, and not
     * here.
     */
    public DukeGame onPressRefused(Consumer<uz.dukeengine.core.view.CommandPress> refused) {
        selection.onRefused(refused);
        return this;
    }

    /** Thread-safe: an armed button pressed at {@code place}, where the simulation would not take it. */
    public void refusePress(String id, Coord3D place, float facing) {
        selection.refuse(id, place, facing);
    }

    // ---- aiming ----

    /** Whether an armed button's place, faced one way, would do — see {@link #aimFits}. */
    @FunctionalInterface
    public interface AimFits {
        boolean test(String button, Coord3D place, float facing);
    }

    /** The game's answer about an armed button's place, faced one way — see {@link #aimAnswer}. */
    @FunctionalInterface
    public interface AimAnswering {
        uz.dukeengine.core.view.AimAnswer answer(String button, Coord3D place, float facing);
    }

    /**
     * Whether an armed button's place would do, asked as the cursor moves — for a ghost to be drawn green or
     * red. The game answers from the simulation's side: for a building, the RTS's {@code RtsSimulation.fits}.
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
                : (button, place, facing) -> new uz.dukeengine.core.view.AimAnswer(fits.test(button, place, facing),
                        List.of()));
    }

    /**
     * The same, the answer carrying besides yes or no the rectangles of ground to mark — what stands in the way, as the
     * reference lays a red bib under it ({@code BuildAssistant::isLocationLegalToBuild}) — each laid until the next
     * answer ({@code WorldSnapshot.aimMarks}). Asked where {@link #aimFits} is.
     */
    public DukeGame aimAnswer(AimAnswering answering) {
        pointer.aimAnswering(answering);
        return this;
    }

    /**
     * Whether a thing would do for an armed button that aims at one, asked while the cursor is over it: the button's id
     * and the thing's. False shows the pointer's refusal ({@code Deny}) and a click there does nothing; the button stays
     * armed. Absent, every thing is accepted. Asked on the simulation thread as the snapshot is built, as {@link
     * #aimAnswer} is, and answered through {@code WorldSnapshot.aimFits}.
     */
    public DukeGame aimThing(java.util.function.BiPredicate<String, Integer> takes) {
        pointer.aimThing(takes);
        return this;
    }

    /**
     * Thread-safe: the button the window has armed, where its cursor stands and which way the ghost faces —
     * or a null button when none is armed. One write of one value, so the simulation never reads a place
     * from one moment with a facing from another. A button armed for a thing has no place: a null place is asked
     * of {@link #aimThing} with the thing the pointer is on ({@link #setPointedAt}), not of {@link #aimAnswer}.
     */
    public void setAim(String buttonId, Coord3D place, float facing) {
        pointer.setAim(buttonId, place, facing);
    }

    // ---- what the pointer is on ----

    /**
     * Thread-safe: the thing under the window's pointer, or -1 for none — so the next snapshot can say whether
     * an attack on it by what is selected would be taken ({@link uz.dukeengine.core.view.WorldSnapshot#attackable}).
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
        pointer.setPointedAt(unitId, ground, seen);
    }

    /**
     * The thing the window last said its pointer is on ({@link #setPointedAt}), or -1 for none, whatever is selected —
     * for a game's own canvas to show what is under the pointer. Presentation only: nothing in the simulation reads
     * it. From any thread.
     */
    public int getPointedAt() {
        return pointer.pointedAt();
    }

    /**
     * The point of the ground the window last said its pointer is on ({@link #setPointedAt}), or null where it is on a
     * thing, on no ground, or has said nothing yet — for a game's own canvas, as {@link #getPointedAt} is. From any
     * thread.
     */
    public Coord3D getPointedGround() {
        return pointer.pointedGround();
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

    /**
     * What a click on the ground means where it is not a move: the reference's
     * {@code MSG_DO_SPECIAL_POWER_OVERRIDE_DESTINATION}, a particle beam or a gunship steered by any click on the
     * ground. Asked as {@link #contextOrder} is, with the point under the pointer where the pointer is on no thing; the
     * snapshot carries the word, the pointer shows its picture, and the click sends {@code GameOrder(player,
     * word, selection, place, null, 0)} instead of a move. A click on a thing still selects it or gives what
     * {@link #contextOrder} says.
     */
    public DukeGame groundOrder(GroundOrder rule) {
        pointer.groundOrder(rule == null ? null : (selected, place, seen) -> rule.orderAt(selected, place));
        return this;
    }

    /**
     * The same, the rule told whether the local player has ever seen the ground it is asked about: a beam steered only
     * over ground its player has seen, never into the black of the map. Over ground never seen a rule answering no word
     * leaves the click what it is with none.
     */
    public DukeGame groundOrder(SeenGroundOrder rule) {
        pointer.groundOrder(rule);
        return this;
    }

    /**
     * Whether this game names what a click on the ground orders — {@link #groundOrder} — and so decides rally points
     * itself: a click its rule answers with no word is a move, never a rally point of the client's own.
     */
    public boolean namesGroundOrders() {
        return pointer.namesGroundOrders();
    }

    /**
     * What a click on a thing means beyond selecting it and attacking it: the reference's context commands, the
     * game's to name. Asked on the simulation thread as the snapshot is built, for the thing under the pointer and
     * what the local player has selected — a transport its infantry may board is {@code Enter}, a supply dock its
     * truck works at {@code Dock}. The snapshot carries the word ({@code WorldSnapshot.contextOrder}), the pointer
     * shows the word's picture, and the click sends {@code GameOrder(player, word, selection, place,
     * target, 0)} for the game's {@link #onOrder} to carry out. The engine never reads the word.
     */
    public DukeGame contextOrder(ContextOrder rule) {
        pointer.contextOrder(rule);
        return this;
    }

    /**
     * A rule asked beside the ones already given, after them: the first to name a word is the word. So a library's rule
     * — the bag's pickup — and the game's own — a word for talking to a person — both answer on one match without
     * either repeating the other. {@link #contextOrder} still sets the one rule, putting away all that were added.
     */
    public DukeGame addContextOrder(ContextOrder rule) {
        pointer.addContextOrder(rule);
        return this;
    }

    /**
     * Run {@code work} on the simulation thread as each snapshot is made, where the world may be read: what a control of
     * the window's looks for — the nearest thing worth an order — found as the pointer's word is. From any thread.
     */
    public void eachSnapshot(Runnable work) {
        pointer.eachSnapshot(work);
    }

    /** The local player's own things his orders go to, selected, in order. On the simulation thread. */
    public List<GameObject> orderedOwn() {
        return pointer.orderedOwn();
    }

    /** The word a click on {@code thing} gives his selection — the game's context rules — or null. On the simulation thread. */
    public String contextWordOn(GameObject thing) {
        return pointer.contextWordOn(thing);
    }

    /** The word a click on the ground at {@code place} gives his selection — the ground rule — or null. Simulation thread. */
    public String groundWordAt(Coord3D place) {
        return pointer.groundWordAt(place);
    }

    // ---- told to the client ----

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
        driver.postTask(() -> world.post(new uz.dukeengine.core.event.TextFloated(world.getFrame(),
                new Coord3D(x, y, z), text, argb)));
    }

    /**
     * The same, rising from a thing and shown only to its owner and to whoever may see it — a hidden building's income
     * told to no enemy who has not found it. From any thread; a thing gone by then floats nothing.
     */
    public void floatTextAbout(ObjectId thing, String text, int argb) {
        var world = logic;
        driver.postTask(() -> {
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
        driver.postTask(() -> world.effect(name, new Coord3D(x, y, z), facing));
    }

    /** A mark the map burns into its ground — see {@link #markGround}. */
    public record GroundMark(String picture, float x, float y, float radius) {
    }

    /**
     * A mark of {@code picture} burnt into the ground at a place, {@code radius} each way — as the reference lays a
     * map's own scorches as it loads ({@code W3DTerrainVisual::load}, {@code addScorch}): drawn with the ground
     * whatever any player sees, as an effect's scorch is drawn only where its player could see it played, and in the
     * budget the effects' scorches share, the oldest let go. At load or at any time, from any thread; drawing only.
     */
    public DukeGame markGround(String picture, float x, float y, float radius) {
        presentation.markGround(new GroundMark(picture, x, y, radius));
        return this;
    }

    /** Every mark the game has burnt into the ground, in the order it laid them. */
    public List<GroundMark> groundMarks() {
        return presentation.groundMarks();
    }

    /**
     * A piece of a picture laid along the ground — a road's straight, its curve, its crossing — its corners going round
     * it, {@code a} to {@code b} along the picture from {@code u0} to {@code u1} and {@code a} to {@code d} across it
     * from {@code v0} to {@code v1}, a corner's height nothing; drawn in its {@code layer}, over an earlier one.
     * Or, with a {@link Mapping}, its picture laid by one straight mapping of the ground, read at every point laid, the
     * rectangle's numbers left unread — the reference's roads ({@code W3DRoadBuffer}), whose mitred ends, curves and
     * crossings keep their picture's size and are cut, not stretched.
     */
    public record StripPiece(String picture, float u0, float v0, float u1, float v1, Coord3D a, Coord3D b, Coord3D c,
            Coord3D d, int layer, Mapping mapping) {

        /** A piece whose picture is spread over its corners. */
        public StripPiece(String picture, float u0, float v0, float u1, float v1, Coord3D a, Coord3D b, Coord3D c,
                Coord3D d, int layer) {
            this(picture, u0, v0, u1, v1, a, b, c, d, layer, null);
        }

        /**
         * A straight mapping of the ground onto a picture: at the ground's {@code x} and {@code y}, {@code u = u0 + a*x
         * + b*y} and {@code v = v0 + c*x + d*y}.
         */
        public record Mapping(float u0, float a, float b, float v0, float c, float d) {

            public float u(float x, float y) {
                return u0 + a * x + b * y;
            }

            public float v(float x, float y) {
                return v0 + c * x + d * y;
            }
        }
    }

    /**
     * Pieces of pictures laid along the ground — the reference's roads, pavements, tracks and painted marks ({@code
     * W3DRoadBuffer}) — each laid over the ground's rise and fall as the reference lays its roads, lit and shrouded as
     * the ground, a later layer over an earlier one. How the pieces of a road meet — its curves, its crossings, the
     * picture each is cut from — is the game's to work out. At load or at any time, from any thread; drawing only.
     */
    public DukeGame layStrips(List<StripPiece> pieces) {
        presentation.layStrips(pieces);
        return this;
    }

    /** Every piece the game has laid along the ground, in the order it laid them. */
    public List<StripPiece> stripPieces() {
        return presentation.stripPieces();
    }

    /** Thread-safe: run work on the simulation thread next frame. */
    public void runOnSimThread(Runnable task) {
        driver.postTask(task);
    }

    /** Thread-safe: toggle the simulation pause state. */
    public void togglePause() {
        runOnSimThread(() -> logic.setGamePaused(!logic.isGamePaused()));
    }

    /**
     * The full simulation — the escape hatch to everything the engine can do. The kind of game's own world is its
     * flavour's to give: {@code game.flavour(RtsFlavour.class).logic()}.
     */
    public GameLogic getLogic() {
        return logic;
    }

    private void requireNotStarted() {
        if (started) {
            throw new IllegalStateException("configure the game before start()");
        }
    }
}
