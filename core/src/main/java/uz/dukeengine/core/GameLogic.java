package uz.dukeengine.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.message.MessageStream;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.partition.PartitionManager;
import uz.dukeengine.core.pathfind.ObstacleRules;
import uz.dukeengine.core.pathfind.Path;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.Pathfinder;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.replay.FrameLog;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Layered;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.core.thing.WorldTemplate;

/**
 * The deterministic simulation, ported from SAGE's {@code GameLogic}.
 *
 * <p>This is the single source of truth for game state. It owns every live
 * {@link GameObject}, hands out their {@link ObjectId}s in creation order, and
 * advances exactly one {@linkplain GameConstants#SECONDS_PER_LOGICFRAME logic
 * frame} per {@link #update()} call. The engine only calls it when the
 * simulation may step (not paused, and — in multiplayer — the next frame's
 * network data is ready).
 *
 * <p>Each frame it ticks every object's update modules in creation order, reaps
 * destroyed objects, then runs the game-specific {@link #simulate()} hook.
 * Everything here is deterministic: ordered iteration, monotonic ids, no
 * wall-clock reads.
 *
 * <p>What it keeps it keeps in collaborators, each with one part of the world: its live things ({@link Roster}), who
 * sleeps ({@link Sleepers}), what each player sees ({@link Sight}), the scenery ({@link Scenery}), what the still
 * things lay on the ground ({@link Obstacles}), routes and their budget ({@link Routes}), where a mover stands
 * ({@link StandingPlaces}), the decks ({@link Decks}) and what it shows without reading ({@link Spectacle}).
 */
public abstract class GameLogic extends SubsystemInterface implements World {

    private final ThingFactory thingFactory;
    private final PlayerList playerList;
    private final MessageStream messageStream = new MessageStream();
    private final uz.dukeengine.core.script.ScriptEngine scriptEngine = new uz.dukeengine.core.script.ScriptEngine();
    private final Roster roster = new Roster();
    private final Sleepers sleepers = new Sleepers();
    private final Scenery scenery = new Scenery(this::getPathGrid);
    private final Obstacles obstacles = new Obstacles(roster, scenery, this::getPathGrid);
    private final Routes routes = new Routes(this::getPathGrid, obstacles, this::groundCells);
    private final StandingPlaces standing = new StandingPlaces(this::getPathGrid, routes, this::groundCells);
    private final Sight sight = new Sight(roster, sleepers, this::getPathGrid, this::getRelationship);
    private final Decks decks = new Decks(this::getPathGrid, roster);
    private final Spectacle spectacle = new Spectacle(this::findObject);
    /** The things made this frame, in creation order, to be told they were made by its end. */
    private final List<GameObject> unannounced = new ArrayList<>();
    /** The things marked destroyed since the last reap, to be reaped by the next. */
    private List<GameObject> marked = new ArrayList<>();
    private PathGrid pathGrid; // null = open terrain (direct paths)
    private WorldTemplate world; // null = a game with no World block
    private FrameLog frameLog;

    /** Enough to hold a busy frame's worth; a headless run with no client drops the excess. */
    private static final int MAX_PENDING_EVENTS = 1024;

    private final ArrayDeque<WorldEvent> pendingEvents = new ArrayDeque<>();

    /**
     * Where every world starts its random numbers unless its game says otherwise: fixed, so two peers and a
     * replay draw the same sequence with nothing to agree on first.
     */
    public static final long DEFAULT_RANDOM_SEED = 0x5AFE5EEDL;

    private long randomSeed = DEFAULT_RANDOM_SEED;
    /** The damage a body's damage scale leaves as it is; see {@link #setUnresistableDamage}. */
    private uz.dukeengine.core.module.DamageType unresistableDamage;
    private final uz.dukeengine.core.math.LogicRandom random =
            new uz.dukeengine.core.math.LogicRandom(DEFAULT_RANDOM_SEED);

    private int frame;
    private int nextObjectId = 1;
    private boolean paused;
    private boolean inGame;

    protected GameLogic() {
        this(new ThingFactory(ModuleFactory.withDefaults()));
    }

    protected GameLogic(ThingFactory thingFactory) {
        this(thingFactory, new PlayerList());
    }

    /**
     * @param playerList the roster; pass one built with a {@code PlayerFactory}
     *     to give the game its own {@link uz.dukeengine.core.player.Player} subtype
     */
    @SuppressWarnings("this-escape") // the collaborators keep callbacks into this world, none called before it is made
    protected GameLogic(ThingFactory thingFactory, PlayerList playerList) {
        this.thingFactory = thingFactory;
        this.playerList = playerList;
    }

    public final ThingFactory getThingFactory() {
        return thingFactory;
    }

    public final PlayerList getPlayerList() {
        return playerList;
    }

    @Override
    public void init() {
        thingFactory.init();
        messageStream.init();
        playerList.init();
        scriptEngine.init();
        pendingEvents.clear();
        clearState();
    }

    @Override
    public void reset() {
        thingFactory.reset();
        messageStream.reset();
        playerList.reset();
        scriptEngine.reset();
        pendingEvents.clear();
        clearState();
    }

    @Override
    public Relationship getRelationship(int a, int b) {
        return playerList.getRelationship(a, b);
    }

    @Override
    public uz.dukeengine.core.player.Player getPlayer(int index) {
        return playerList.getPlayer(index);
    }

    @Override
    public ThingTemplate findTemplate(String name) {
        return thingFactory.findTemplate(name);
    }

    @Override
    public final GameObject spawn(ThingTemplate template, Coord3D position, int playerIndex) {
        return spawn(template, position, playerIndex, thing -> {
        });
    }

    @Override
    public final GameObject spawn(ThingTemplate template, Coord3D position, int playerIndex,
            java.util.function.Consumer<GameObject> setup) {
        var object = createObject(template);
        object.setPosition(position);
        object.setPlayerIndex(playerIndex);
        setup.accept(object);
        if (pathGrid != null && pathGrid.hasDecks()) {
            object.setFloor(pathGrid.floorAt(object.getPosition())); // on the deck it was put down on, if any
        }
        onSpawned(object);
        return object;
    }

    /**
     * Name the damage a body's damage scale leaves as it is — the reference's {@code DAMAGE_UNRESISTABLE}, what kills
     * outright whatever the thing's plan; null for none.
     */
    public final void setUnresistableDamage(uz.dukeengine.core.module.DamageType type) {
        this.unresistableDamage = type;
    }

    @Override
    public final uz.dukeengine.core.module.DamageType unresistableDamage() {
        return unresistableDamage;
    }

    /** A thing just made and set up, for a simulation to give what every thing it makes gets. Nothing here. */
    protected void onSpawned(GameObject thing) {
    }

    // ---- what each player sees ----

    /**
     * Whether {@code viewerPlayer} can see {@code target} — fog of war. A player
     * always sees its own units; otherwise the target must lie within the vision
     * range of one of the viewer's (or an ally's) living units. A thing hidden from
     * the viewer is not seen at all ({@link GameObject#isHiddenFrom}).
     */
    public final boolean canSee(int viewerPlayer, GameObject target) {
        if (target.isHiddenFrom(viewerPlayer)) {
            return false;
        }
        return target.getPlayerIndex() == viewerPlayer || canSee(viewerPlayer, target.getPosition());
    }

    /**
     * Whether {@code viewerPlayer} has eyes on a point of the map — the question
     * to ask about a place rather than a thing, such as where something just
     * happened after the thing itself has gone.
     *
     * <p>A thing clears the fog by its fog range ({@link GameObject#getFogRange}), not its sight; one going up only
     * over itself, its footprint's radius, as the reference's {@code Object::getShroudClearingRange} has a building
     * under construction see itself alone; and one finished that everyone sees round ({@code seenByAllWithin}) clears
     * that much for every player, unless it is hidden from them.
     */
    public final boolean canSee(int viewerPlayer, Coord3D position) {
        return sight.canSee(viewerPlayer, position);
    }

    /**
     * Let the game say which things that are neither a player's nor an ally's lend it their sight — the reference's
     * CIA Intelligence and satellite hacks, seeing through the enemy's units for a time: asked of such a thing, on
     * the simulation thread, and where it says yes the thing's sight counts for that player as its own units' does.
     * A pure function of the simulation's state, or the peers see differently; null for none.
     */
    public final void setSharedSight(java.util.function.BiPredicate<Integer, GameObject> rule) {
        sight.setShared(rule);
    }

    /**
     * Reveal the whole map to {@code player} for the rest of the match: no fog, no shroud, in what that player is
     * shown — the reference's {@code MAP_REVEAL_ALL_PERM}, and what a player beaten while the others fight on is given
     * to watch the end by. Where the game keeps cells ({@link #setSightCells}) every one of his reads in sight, whatever
     * else was done to them. From code on the simulation thread, where every machine does it on the same frame: it is
     * part of the checksum.
     */
    public final void revealMapTo(int player) {
        sight.reveal(player);
    }

    /** Whether the whole map has been revealed to {@code player}. */
    public final boolean isMapRevealedTo(int player) {
        return sight.isRevealedTo(player);
    }

    /** The players the whole map has been revealed to, in order — for a save. */
    public final java.util.SortedSet<Integer> getRevealedTo() {
        return sight.revealedTo();
    }

    /**
     * Mark the whole map seen by {@code player}, none of it in sight — the reference's {@code
     * PartitionManager::revealMapForPlayer}, which {@code GameLogic::startNewGame} runs for each player of a skirmish
     * or network match whose own option leaves the shroud out ({@code UseShroud = No}): every cell of his cells ({@link
     * #setSightCells}) he has never seen reads seen from then, as though a looker had covered the whole map and left
     * long enough ago for its while after to have run out. A thing standing there is not shown for it, and his lookers
     * open cells to in sight the ordinary way. Not {@link #revealMapTo}, which puts the whole map in sight for good,
     * and which a player marked seen may be given as well. Called once, before the frame a game wants it from, and on
     * the simulation thread after the start, so every machine does it alike: it is part of the checksum and a save.
     * Where the game keeps no cells it marks nothing until it does.
     */
    public final void markMapSeen(int player) {
        sight.markSeen(player);
    }

    /** Whether the whole map was marked seen by {@code player}. */
    public final boolean isMapMarkedSeen(int player) {
        return sight.isMarkedSeen(player);
    }

    /** The players whose whole map was marked seen, in order — for a save. */
    public final java.util.SortedSet<Integer> getMarkedSeen() {
        return sight.markedSeen();
    }

    /** Every object {@code viewerPlayer} can currently see, in creation order — as {@link #canSee} says of each. */
    public final List<GameObject> getVisibleObjects(int viewerPlayer) {
        return sight.visibleTo(viewerPlayer);
    }

    /**
     * Keep each player's view of the map by cells of {@code cellSize} — never seen, seen, in sight — a cell no looker
     * covers any more staying in sight {@code linger} frames: the reference's partition cells of 40 and its 150
     * ({@code PartitionCellSize}, {@code UnlookPersistDuration}). From then {@link #canSee} and what a viewer is shown
     * count by the cells, the while after included — see {@link SightCells}. Looked at as each frame ends, the same on
     * every machine. Needs the ground's grid, whose size is the map's; a new grid starts every player's cells afresh.
     * A cell size of 0 or less, as before: sight worked out afresh at each question, by the lookers' reach alone.
     */
    public final void setSightCells(float cellSize, int linger) {
        sight.setCells(cellSize, linger);
    }

    /** Each player's cells, or null where the game keeps none. On the simulation thread. */
    public final SightCells getSightCells() {
        return sight.cells();
    }

    /**
     * Let stone and the floors above a looker hide the ground from it in each player's cells — see {@link
     * SightCells.Ground}: a line of sight stops at the map's stone, the stone itself seen, and a floor standing above
     * the looker's is seen and nothing on it in sight — a crawler's dark, kept by the simulation, saved with it and the
     * same on every machine. Off, as the reference's is: a looker covers its disc.
     */
    public final void setSightHiddenByStone(boolean hidden) {
        sight.setHiddenByStone(hidden);
    }

    /**
     * What {@code player} has seen, to keep with a saved game — see {@link SightCells#remember}; null where the game
     * keeps no cells.
     */
    public final SightCells.Memory getSightMemory(int player) {
        return sight.memory(player);
    }

    /** {@code player}'s cells as a saved game kept them — see {@link SightCells#recall}; nothing where none are kept. */
    public final void setSightMemory(int player, SightCells.Memory memory) {
        sight.recall(player, memory);
    }

    // ---- where things stand ----

    public final PartitionManager getPartition() {
        return roster.partition();
    }

    @Override
    public GameObject findClosest(Coord3D center, float range, Predicate<GameObject> filter) {
        return roster.partition().closestObject(center, range, filter::test);
    }

    @Override
    public List<GameObject> objectsInRange(Coord3D center, float range, Predicate<GameObject> filter) {
        return roster.partition().objectsInRange(center, range, filter::test);
    }

    @Override
    public final List<GameObject> thingsNear(Coord3D center, float reach) {
        return roster.partition().near(center, reach);
    }

    @Override
    public GameObject findClosestInReach(GameObject from, float reach, Predicate<GameObject> filter) {
        return roster.partition().closestWithinReach(Footprint.of(from), reach, filter::test);
    }

    /** Directions probed when looking for free ground, in this fixed order. */
    private static final int CLEAR_POSITION_SAMPLES = 8;

    @Override
    public Coord3D findClearPosition(Geometry shape, Coord3D near, float searchRadius) {
        if (shape.isPoint() || isGroundClear(shape, near)) {
            return onGround(near);
        }
        float ringStep = Math.max(shape.footprintRadius(), 1f);
        for (float radius = ringStep; radius <= searchRadius; radius += ringStep) {
            for (int sample = 0; sample < CLEAR_POSITION_SAMPLES; sample++) {
                double angle = 2 * Math.PI * sample / CLEAR_POSITION_SAMPLES;
                var candidate = new Coord3D(
                        near.x() + radius * (float) StrictMath.cos(angle),
                        near.y() + radius * (float) StrictMath.sin(angle),
                        0f);
                if (isGroundClear(shape, candidate)) {
                    return onGround(candidate);
                }
            }
        }
        return onGround(near);
    }

    /** The same point, standing on the floor that is under it. */
    private Coord3D onGround(Coord3D position) {
        return new Coord3D(position.x(), position.y(), groundHeight(position));
    }

    @Override
    public final boolean isGroundBlocked(Coord3D position) {
        // The terrain layer only: a building standing here is an obstacle a mover
        // may walk around, and findBlocker is what answers for those.
        return pathGrid != null
                && pathGrid.isTerrainBlocked(pathGrid.toCellX(position), pathGrid.toCellY(position));
    }

    @Override
    public final boolean canStep(Coord3D from, Coord3D to) {
        return pathGrid == null || pathGrid.canStep(
                pathGrid.toCellX(from), pathGrid.toCellY(from),
                pathGrid.toCellX(to), pathGrid.toCellY(to));
    }

    @Override
    public final boolean canStep(GameObject mover, Coord3D from, Coord3D to, int toward) {
        if (pathGrid == null) {
            return true;
        }
        var grid = gridFor(mover); // the ground as it walks it: its classes open to it
        int fromX = grid.toCellX(from);
        int fromY = grid.toCellY(from);
        int toX = grid.toCellX(to);
        int toY = grid.toCellY(to);
        if (!grid.hasDecks()) {
            return grid.canStep(fromX, fromY, toX, toY);
        }
        int floor = mover.getFloor();
        return grid.walks(floor, fromX, fromY, toX, toY)
                || toward >= 0 && grid.enters(floor, toward, fromX, fromY, toX, toY);
    }

    // ---- classes of ground, and routes by sectors ----

    /** The ground as {@code mover} walks it: the grid, or its passage for the classes it may enter. Null for none. */
    final PathGrid gridFor(GameObject mover) {
        return routes.gridFor(mover);
    }

    /**
     * Look for routes by sectors of {@code mapCells} of the map's cells a side — see {@link
     * uz.dukeengine.core.pathfind.Sectors}: a route across a wide world is found through the sectors its way crosses,
     * examining the ground along it rather than every cell it could reach, and what reaches what is kept sector by
     * sector as the ground changes rather than worked out over every cell. The route is the best within those sectors,
     * not always the best in the world. 0 looks at the whole grid for each route, as a world always did; a grid with
     * decks is searched whole whatever this says.
     */
    public final void setRouteSectors(int mapCells) {
        routes.setSectors(mapCells);
    }

    public final int getRouteSectors() {
        return routes.sectors();
    }

    /**
     * Lay the routes of a body of {@code radius} now — while a map loads — rather than on the first order of a mover that
     * wide, which pays for it on a wide world: the sectors of its width (see {@link #setRouteSectors}). Nothing where
     * routes are not looked for by sectors.
     */
    public final void layRoutesFor(float radius) {
        routes.sectorsOf(pathGrid, radius);
    }

    /**
     * The zones of the ground as {@code grid} sees it — the reference's zones joined per set of surfaces
     * ({@code groundCliff}, {@code waterGround}, {@code groundRubble}): the grid's own for a mover of plain ground.
     */
    final uz.dukeengine.core.pathfind.Zones zonesOf(PathGrid grid) {
        return routes.zonesOf(grid);
    }

    /** The name of the class of ground under {@code thing} — climbing, wading — or null for plain ground. */
    @Override
    public final String groundClassUnder(GameObject thing) {
        if (pathGrid == null) {
            return null;
        }
        var at = thing.getPosition();
        return pathGrid.groundClassAt(pathGrid.toCellX(at), pathGrid.toCellY(at));
    }

    @Override
    public final boolean isGroundBlocked(GameObject mover, Coord3D position) {
        if (pathGrid == null || mover.getFloor() == 0) {
            return isGroundBlocked(position);
        }
        var deck = pathGrid.deck(mover.getFloor());
        return deck == null || !deck.isOpen() || !deck.walkable(pathGrid.toCellX(position), pathGrid.toCellY(position));
    }

    @Override
    public final float groundHeight(GameObject mover, Coord3D position) {
        return pathGrid == null ? 0f : pathGrid.heightOn(mover.getFloor(), position);
    }

    @Override
    public final int floorAfter(GameObject mover, Coord3D from, Coord3D to, int toward) {
        if (pathGrid == null || toward < 0 || !pathGrid.enters(mover.getFloor(), toward,
                pathGrid.toCellX(from), pathGrid.toCellY(from), pathGrid.toCellX(to), pathGrid.toCellY(to))) {
            return mover.getFloor();
        }
        return toward;
    }

    // ---- decks ----

    /**
     * Lay a deck over the map's ground — see {@link PathGrid#addDeck} — at run time: its floor comes back. The ground
     * under it closes where it stands lower than the clearance ({@link #setDeckClearance}); things placed on it after
     * are on it.
     */
    public final int addDeck(Coord3D first, Coord3D second, Coord3D third, Coord3D fourth) {
        return decks.add(first, second, third, fourth);
    }

    /** How high a deck must stand over the ground for the ground under it to stay open; the reference's 10. */
    public final void setDeckClearance(float clearance) {
        decks.setClearance(clearance);
    }

    /**
     * Open or close a deck. Closed — a bridge destroyed — every thing on it is handed back to the ground under it, and
     * the game is told who, on the simulation thread ({@link #onDeckClosed}): the reference kills them with falling
     * damage, and the game decides. Every route is planned again, round it or back over it.
     */
    public final void setDeckOpen(int floor, boolean open) {
        decks.setOpen(floor, open);
    }

    /** Told, when a deck is closed, which things were on it — see {@link #setDeckOpen}. */
    public final void onDeckClosed(java.util.function.BiConsumer<Integer, List<GameObject>> listener) {
        decks.onClosed(listener);
    }

    @Override
    public final float groundHeight(Coord3D position) {
        return pathGrid == null ? 0f : pathGrid.groundHeight(position);
    }

    @Override
    public final float mapWidth() {
        return pathGrid == null ? 0f : pathGrid.getWidth() * pathGrid.getCellSize();
    }

    @Override
    public final float mapHeight() {
        return pathGrid == null ? 0f : pathGrid.getHeight() * pathGrid.getCellSize();
    }

    /**
     * Which floor a point is on — the number two objects have to share before
     * either can be in the other's way.
     */
    @Override
    public int levelAt(Coord3D position) {
        return pathGrid == null
                ? 0
                : pathGrid.level(pathGrid.toCellX(position), pathGrid.toCellY(position));
    }

    private boolean isGroundClear(Geometry shape, Coord3D position) {
        if (pathGrid != null
                && pathGrid.isTerrainBlocked(pathGrid.toCellX(position), pathGrid.toCellY(position))) {
            return false; // the map itself forbids it — nothing may be placed in a cliff
        }
        var footprint = new Footprint(shape, position, 0f);
        int level = levelAt(position);
        return roster.partition().firstOverlapping(footprint,
                candidate -> !candidate.isDestroyed()
                        && !candidate.isEffectivelyDead()
                        && !candidate.isContained()
                        && !candidate.hasStatus(uz.dukeengine.core.thing.ObjectStatus.AIRBORNE)
                        && levelAt(candidate.getPosition()) == level) == null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only what is standing on the same floor can be in the way. A corridor
     * that runs under a raised room shares its ground with nothing: the two are
     * the same square of map and different places, and a body in one has never
     * been anywhere near a body in the other.
     */
    @Override
    public GameObject findBlocker(GameObject mover, Coord3D position) {
        return findBlocker(mover, position, candidate -> false);
    }

    @Override
    public GameObject findBlocker(GameObject mover, Coord3D position, Predicate<GameObject> passing) {
        if (Solid.of(mover.getTemplate()).isPoint()) {
            return null; // no body, nothing to bump into
        }
        int level = levelAt(position);
        // Something in the air is in nobody's way on the ground, as the reference's aircraft are not.
        return roster.partition().firstOverlapping(Footprint.of(mover, position),
                candidate -> candidate != mover
                        && !candidate.isDestroyed()
                        && !candidate.isEffectivelyDead()
                        && !candidate.isContained()
                        && !candidate.hasStatus(uz.dukeengine.core.thing.ObjectStatus.AIRBORNE)
                        && candidate.getFloor() == mover.getFloor() // over a deck and under it, nobody's way
                        && levelAt(candidate.getPosition()) == level
                        && obstacles.holdsAStep(candidate)
                        && !passing.test(candidate));
    }

    // ---- ground movers on the cells ----

    private GroundCells groundCells;

    /** The movers on the ground's cells, made the first time they are asked for. */
    private GroundCells groundCells() {
        if (groundCells == null) {
            groundCells = new GroundCells(this);
        }
        return groundCells;
    }

    @Override
    public final Coord3D takePlace(GameObject mover, Coord3D place) {
        obstacles.refresh();
        return groundCells().take(mover, place);
    }

    @Override
    public final Coord3D takePlace(GameObject mover, Coord3D place, Coord3D near) {
        obstacles.refresh();
        return groundCells().take(mover, place, near);
    }

    @Override
    public final boolean holdPlace(GameObject mover) {
        return groundCells().hold(mover);
    }

    @Override
    public final boolean keepsCells(GameObject mover) {
        return groundCells().keepsCells(mover);
    }

    @Override
    public final void letPlaceGo(GameObject mover) {
        groundCells().letGo(mover);
    }

    @Override
    public final boolean holdsPlace(GameObject mover) {
        return groundCells().holds(mover);
    }

    @Override
    public final void markStanding(GameObject mover) {
        groundCells().mark(mover);
    }

    @Override
    public final List<GameObject> stillAlliesOn(GameObject mover, List<Coord3D> way) {
        return groundCells().keepsCells(mover) ? groundCells().stillAlliesOn(mover, way) : List.of();
    }

    @Override
    public final Coord3D placeAside(GameObject mover, GameObject from, List<Coord3D> way) {
        return standing.placeAside(mover, from, way);
    }

    /** Install a navigation grid so movement routes around terrain obstacles. */
    @Override
    public final float cellSize() {
        return pathGrid == null ? World.super.cellSize() : pathGrid.mapCellSize();
    }

    public final void setPathGrid(PathGrid pathGrid) {
        setPathGrid(pathGrid, 1);
    }

    /**
     * The same, walked at {@code cellsPerCell} cells a side for each of the map's ({@link PathGrid#subdivided}):
     * routes, what stands in the way and the movers' own cells on the finer grid, so a body goes between two trees
     * where their trunks leave it room, while every rule counted in cells — beside, near enough, a cell a second — is
     * still counted in the map's ({@link #cellSize}) and a frame's searching is held to as much ground as before. 1 is
     * the map's own grid, as it always was.
     */
    public final void setPathGrid(PathGrid pathGrid, int cellsPerCell) {
        pathGrid = pathGrid == null ? null : pathGrid.subdivided(cellsPerCell);
        this.pathGrid = pathGrid;
        // A world built in storeys says how tall one is, and every map laid in it is laid at
        // that height: a floor swapped in mid-game included. A map that says its own keeps it —
        // the world's height is what a map is laid at when it has not said.
        if (pathGrid != null && pathGrid.getLevelHeight() <= 0f && world instanceof Layered layered) {
            pathGrid.setLevelHeight(layered.levelHeight());
        }
        obstacles.layAfresh(); // a new grid holds nothing laid
        routes.forgetSectors();
        scenery.lay();
        sight.lay();
    }

    // ---- scenery ----

    /**
     * The scenery that stands in the way on the ground: pieces a map placed for their look — trees, boulders — that are
     * no things of the simulation's, each a circle in world units. None closes a cell: a route keeps a body clear of
     * each by its true distance ({@link PathGrid#clearOfCircles}), and a ground mover's step goes no deeper into one
     * than it already stands, more than a touch — so a body goes between two trunks where it fits between them. Set
     * with the map it belongs to, which every machine lays alike.
     */
    public final void setSceneryFootprints(List<uz.dukeengine.core.pathfind.SceneryFootprint> footprints) {
        scenery.setFootprints(footprints);
    }

    /** The zones a point lies in, as the game names them — see {@link #setZones}. */
    private java.util.function.Function<Coord3D, java.util.Set<String>> mapZones = at -> java.util.Set.of();

    /**
     * The zones of the map a point lies in — a town, a dungeon — by the game's names, for whatever asks where a thing
     * is: a skill refused in a town. The game's rule, read the same on every machine.
     */
    public final void setZones(java.util.function.Function<Coord3D, java.util.Set<String>> rule) {
        this.mapZones = rule == null ? at -> java.util.Set.of() : rule;
    }

    @Override
    public final java.util.Set<String> zonesAt(Coord3D at) {
        var found = mapZones.apply(at);
        return found == null ? java.util.Set.of() : found;
    }

    /** The ways across what no walk crosses — see {@link #setTraversalLinks}. */
    private List<uz.dukeengine.core.pathfind.TraversalLink> traversalLinks = List.of();

    /**
     * The ways across what no walk crosses, in world units: a walker whose goal no walk reaches walks to the nearer end
     * of one that brings it nearer, is carried across ({@code ScriptedMove}, busy at the link's clip) and walks on from
     * the other end. Set with the map they belong to, as every machine lays them.
     */
    public final void setTraversalLinks(List<uz.dukeengine.core.pathfind.TraversalLink> links) {
        this.traversalLinks = links == null ? List.of() : List.copyOf(links);
    }

    @Override
    public final List<uz.dukeengine.core.pathfind.TraversalLink> traversalLinks() {
        return traversalLinks;
    }

    @Override
    public final boolean joined(Coord3D a, Coord3D b) {
        var connected = zones();
        return connected == null || connected.connected(pathGrid.toCellX(a), pathGrid.toCellY(a), pathGrid.toCellX(b),
                pathGrid.toCellY(b));
    }

    /**
     * Take a piece of the map's scenery away — a tree felled — by its index in the map's scenery: its footprint leaves
     * the ground at once, the same on every machine, and the picture is told ({@link
     * uz.dukeengine.core.event.SceneryChanged}). In the checksum; a piece already gone, or none, changes nothing.
     */
    public final void removeScenery(int index) {
        if (scenery.remove(index)) {
            post(new uz.dukeengine.core.event.SceneryChanged(frame, index, true));
        }
    }

    /** Put a piece taken away back — a tree grown again: its footprint rejoins the ground, and the picture is told. */
    public final void restoreScenery(int index) {
        if (scenery.restore(index)) {
            post(new uz.dukeengine.core.event.SceneryChanged(frame, index, false));
        }
    }

    /** Whether the piece of scenery at {@code index} has been taken away. */
    public final boolean isSceneryRemoved(int index) {
        return scenery.isRemoved(index);
    }

    /** The pieces taken away, in order, for a save to carry. */
    public final List<Integer> getRemovedScenery() {
        return List.copyOf(scenery.removed());
    }

    /** The pieces taken away, as a save carried them: laid at once, nothing told — the picture reads them as it loads. */
    public final void setRemovedScenery(java.util.Collection<Integer> removed) {
        scenery.setRemoved(removed);
    }

    /**
     * The standing piece of scenery nearest {@code place} whose footprint comes within {@code reach} of it — the tree a
     * click on the ground under its crown means — by its index, the lowest on a tie; -1 for none.
     */
    public final int sceneryNear(Coord3D place, float reach) {
        return scenery.near(place, reach);
    }

    /** The standing pieces of scenery whose middles lie within {@code reach} of {@code place}, by index. */
    public final List<uz.dukeengine.core.pathfind.SceneryFootprint> sceneryWithin(Coord3D place, float reach) {
        return scenery.within(place, reach);
    }

    /** The piece of scenery at {@code index}, standing or not, or null where the map has no such piece in the way. */
    public final uz.dukeengine.core.pathfind.SceneryFootprint sceneryPiece(int index) {
        return scenery.piece(index);
    }

    /**
     * The scenery that stands in the way along its length — a castle's walls, each a box turned with its facing — in
     * world units. Unlike a round piece's, a box closes the cells of the ground whose middles it covers, on the layer
     * still things lay (not the map's stone, so nothing draws rock there). Set with the map it belongs to, which every
     * machine lays alike.
     */
    public final void setSceneryBoxes(List<uz.dukeengine.core.pathfind.SceneryBox> boxes) {
        scenery.setBoxes(boxes);
        obstacles.layAfresh(); // laid again, whatever the still things are
    }

    @Override
    public final boolean sceneryInTheWay(GameObject mover, Coord3D from, Coord3D to) {
        return scenery.inTheWay(mover, from, to);
    }

    /** What the game's World block says of its world, as far as the engine reads it. */
    public final void setWorld(WorldTemplate world) {
        this.world = world;
        if (pathGrid != null) {
            setPathGrid(pathGrid);
        }
    }

    public final WorldTemplate getWorld() {
        return world;
    }

    // ---- what the still things lay on the ground ----

    /** Lay the ground whole at its next laying, as though nothing had been laid: for a test to hold the two alike. */
    final void forgetWhatWasLaid() {
        obstacles.forget();
    }

    /** The game's answer to what is in the way on the ground; null for every still thing with a shape, as before. */
    public final void setObstacleRules(ObstacleRules rules) {
        obstacles.setRules(rules);
    }

    public final ObstacleRules getObstacleRules() {
        return obstacles.rules();
    }

    @Override
    public final void stillThingsWordsChanged() {
        obstacles.wordsChanged();
    }

    @Override
    public final void stillThingMoved() {
        obstacles.changed();
    }

    @Override
    public int getNavigationVersion() {
        return pathGrid == null ? 0 : pathGrid.getObstacleVersion();
    }

    // ---- events ----

    /** Told every death as it is reaped — see {@link #onDied}. */
    private final List<Consumer<ObjectDied>> deathWatchers = new ArrayList<>();

    /**
     * Told every death as it is reaped, on the simulation thread: the same {@link ObjectDied} the client's event
     * carries — whose it was, who dealt the blow and whose side that was — beside the event rather than instead of it.
     * A watcher here may change the world, as a kill counted or a bounty paid does, which an event never may. A thing
     * removed without dying — sold, cleared away — is no death, and is not heard.
     */
    public final void onDied(Consumer<ObjectDied> watcher) {
        deathWatchers.add(watcher);
    }

    /** Told every thing brought back from a death it lay in — see {@link #onRevived}. */
    private final List<Consumer<GameObject>> revivalWatchers = new ArrayList<>();

    /**
     * Told every thing brought back from a death it was kept in the world through ({@link GameObject#revive}), on the
     * simulation thread, as it is brought back: what a game undid at its death it does again here — the deck a bridge
     * took with it when it fell opened again as it is raised.
     */
    public final void onRevived(Consumer<GameObject> watcher) {
        revivalWatchers.add(watcher);
    }

    @Override
    public final void post(WorldEvent event) {
        if (pendingEvents.size() >= MAX_PENDING_EVENTS) {
            // Nobody is draining (a headless run, say). Drop the oldest rather than
            // grow without bound; events are presentation only, so nothing breaks.
            pendingEvents.pollFirst();
        }
        pendingEvents.addLast(event);
    }

    /**
     * Take everything announced since the last call, in the order it happened.
     *
     * <p>Drained rather than cleared per frame because the engine may run several
     * logic frames for one client frame while catching up; anything else would
     * silently lose the moments in between. Call from the engine thread — the
     * client runs there too.
     */
    public final List<WorldEvent> drainEvents() {
        if (pendingEvents.isEmpty()) {
            return List.of();
        }
        var drained = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return drained;
    }

    // ---- routes ----

    public final PathGrid getPathGrid() {
        return pathGrid;
    }

    @Override
    public Path findPath(Coord3D from, Coord3D to) {
        return routes.findPath(from, to);
    }

    /**
     * A route for {@code mover} to {@code to} — or, where there is none, to the nearest place there is one to,
     * {@link Path#reachesGoal} saying which: see {@link Pathfinder#findPathOrNearest}.
     */
    @Override
    public Path findPath(GameObject mover, Coord3D to) {
        return routes.findPath(mover, to, java.util.Set.of());
    }

    /**
     * A route into the band round {@code what} — see {@link Pathfinder#findPathWithin}, the cells it examines counted
     * against the frame's searching as any route's are — or, where no cell of the band is found within its limit, a
     * route to the straight-line answer ({@link #withinOf}), as near as there is one to.
     */
    @Override
    public Path findPathWithin(GameObject mover, GameObject what, float least, float most) {
        return routes.findPathWithin(mover, what, least, most, () -> withinOf(mover, what, least, most));
    }

    /** Straight past stone and past the still allies its routes go round, as its route was straightened. */
    @Override
    public boolean walksStraight(GameObject mover, Coord3D from, Coord3D to) {
        return routes.walksStraight(mover, from, to);
    }

    @Override
    public Path findPath(GameObject mover, Coord3D to, java.util.Set<ObjectId> round) {
        return routes.findPath(mover, to, round);
    }

    // ---- what searching costs ----

    /** SAGE's {@code PATHFIND_CELLS_PER_FRAME} (AIPathfind.cpp): the cells a frame's path searches may examine. */
    public static final int DEFAULT_PATHFIND_BUDGET = 5000;

    /**
     * How many cells a frame's path searches may examine before the rest wait for the next frame — a mover
     * waiting keeps the route it had, or stands. A search is started only while the frame is under it, and runs to
     * its end once started. The same on every machine; 5000 unless the game says otherwise, counted in the map's
     * cells where it is walked finer.
     */
    public final void setPathfindBudget(int cells) {
        routes.setBudget(cells);
    }

    public final int getPathfindBudget() {
        return routes.budget();
    }

    /** How many cells the last whole frame's path searches examined. */
    public final int getCellsExaminedLastFrame() {
        return routes.cellsLastFrame();
    }

    /**
     * The grid's connected zones as it stands — recomputed when anything that decides where can be walked has
     * changed ({@link PathGrid#getShapeVersion}). Null for a world with no grid.
     */
    public final uz.dukeengine.core.pathfind.Zones zones() {
        return routes.zones();
    }

    /**
     * Beside it as the world measures it — a cell off its outline — or, sent beside it ({@link #standingNextTo}) and
     * arrived: stopped within its close-enough distance of the point, whichever way it faces, as the reference takes a
     * unit within its {@code CloseEnoughDist} of its dock position as there ({@code AIInternalMoveToState}). A box that
     * turned on its way — round a wall, say — stands on its point side on, farther off than the point was worked out
     * for.
     */
    @Override
    public boolean isBeside(GameObject who, GameObject what) {
        return World.super.isBeside(who, what) || standing.arrivedBeside(who, what);
    }

    /**
     * Where {@code who} should stand to work on {@code what}: beside it, on ground it can stand on, and somewhere
     * it can walk to from where it is.
     *
     * <p>The straight line toward the thing, stopped a cell short, is that spot whenever it will do — and on
     * open ground it always does. It would not always: measured in an RTS on the engine, a supply truck leaving
     * a pile for its depot on a map with cliffs was given a spot on a cliff face; no route led there, the truck
     * never set off, and the load it carried was never banked. So where the straight-line spot cannot be stood
     * on or reached, the answer is the cell beside the thing nearest it that can be reached — found by walking
     * outward from the mover with the pathfinder's own steps, so that "reached" is the search's own answer
     * rather than a guess about it.
     *
     * <p>Its own position only when it is beside the thing already. Where no cell beside it can be reached at
     * all — the thing is walled off from it — the reachable place nearest the straight-line spot: sent there, a
     * mover gets as close as it can and says so ({@link Path#reachesGoal}).
     */
    @Override
    public Coord3D standingNextTo(GameObject who, GameObject what) {
        var straight = World.super.standingNextTo(who, what);
        var point = pathGrid == null || World.super.isBeside(who, what) ? straight
                : standing.spotBeside(who, what, straight);
        standing.sentBeside(who, what, point);
        return point;
    }

    // ---- the frame ----

    private void clearState() {
        forgetEveryThing();
        sight.forget(); // a new match is a map nobody has seen
        spectacle.clear();
        obstacles.changed();
        random.restore(randomSeed);
        frame = 0;
        nextObjectId = 1;
        paused = false;
        inGame = false;
    }

    @Override
    public final void update() {
        obstacles.refresh(); // before commands: a MoveTo issued now must see the world as it is
        recordFrame();
        messageStream.propagate(this::onCommand);
        sleepers.update(frame, roster, cellSize());
        reapDestroyed();
        eachFrame.run();
        simulate();
        scriptEngine.evaluate(this);
        // What was made this frame is told so by its end, so the frame's picture shows what making it set: what that
        // makes in turn with it.
        for (int i = 0; i < unannounced.size(); i++) {
            unannounced.get(i).announceCreated();
        }
        unannounced.clear();
        routes.frameEnded();
        sight.look(frame, getPlayerList().getPlayerCount());
        frame++;
    }

    /**
     * Throw away commands queued but not yet applied.
     *
     * <p>For a replay, which is the sole authority on what a frame's input was.
     * A simulation can generate commands of its own — a scripted attack, a
     * timed reinforcement — and on playback it would generate them again; without
     * this they would be applied twice, once from the game and once from the
     * recording.
     */
    public final void discardPendingCommands() {
        messageStream.clear();
    }

    /**
     * Watch every frame's input, so the game can be written down and replayed.
     * Pass {@code null} to stop recording.
     */
    public final void setFrameLog(FrameLog frameLog) {
        this.frameLog = frameLog;
    }

    /**
     * Hand this frame to the recorder before anything is applied.
     *
     * <p>The checkpoint is taken first, and deliberately: it is the world as the
     * frame <em>begins</em>, which is the state a replay can be checked against
     * before it, too, applies the frame's commands.
     */
    private void recordFrame() {
        if (frameLog == null) {
            return;
        }
        if (frameLog.wantsCheckpoint(frame)) {
            frameLog.checkpoint(frame, checksum());
        }
        if (!messageStream.isEmpty()) {
            frameLog.commands(frame, messageStream.peekAll());
        }
    }

    /** Register a map/mission {@link uz.dukeengine.core.script.Trigger}. */
    public final void addTrigger(uz.dukeengine.core.script.Trigger trigger) {
        scriptEngine.addTrigger(trigger);
    }

    /** Queue a command for deterministic processing at the start of next frame. */
    public final void issueCommand(Command command) {
        messageStream.appendMessage(command);
    }

    /**
     * Apply {@code command} now, in the frame it is given, exactly as one drained at the start of a frame is applied:
     * the same checks, the same listeners — a game's own order heard by whatever hears the game's orders — its result
     * in the world before this returns. For an order the game's own code decides during a frame — a computer player
     * choosing its builder's work, an order a module queued settled in a tick — as the reference's AI calls its order
     * paths directly.
     *
     * <p>On the simulation thread only, and not from inside a thing's own update, where nothing may change what modules
     * it has. It goes neither to the wire nor to a recording: every machine runs the same code and applies it alike on
     * the same frame, and a replay runs that code again. An order a player gives still goes by {@link #issueCommand}.
     */
    public final void applyNow(Command command) {
        onCommand(command);
    }

    /**
     * Handle one command drained from the stream. Default does nothing; concrete
     * simulations override it, narrowing {@link Command} to their own game's
     * sealed command hierarchy and pattern-matching over it exhaustively.
     */
    protected void onCommand(Command command) {
    }

    /** What runs this world does each frame — see {@link #eachFrame}. */
    private Runnable eachFrame = () -> {
    };

    /**
     * Do {@code work} every frame on the simulation thread, just before the world's own {@link #simulate}: what the
     * runtime running this world does there — the orders posted from other threads, the game's per-frame code.
     */
    public final void eachFrame(Runnable work) {
        this.eachFrame = work == null ? () -> {
        } : work;
    }

    /** Where a command outside this world's own set goes — see {@link #onOtherCommand}. */
    private java.util.function.Consumer<Command> otherCommands;

    /**
     * Hand the commands this world's own set does not know to {@code handler} — what runs the world, which knows the
     * game's own: on the simulation thread, as each is applied.
     */
    public final void onOtherCommands(java.util.function.Consumer<Command> handler) {
        this.otherCommands = handler;
    }

    /**
     * A command this world's own set does not know, which a simulation's {@link #onCommand} passes on: the handler
     * given {@link #onOtherCommands} hears it, and with none it is a mistake, logged and dropped.
     */
    protected void onOtherCommand(Command command) {
        if (otherCommands == null) {
            java.util.logging.Logger.getLogger(GameLogic.class.getName())
                    .warning(() -> "ignoring a command of no set this world knows: " + command.getClass().getName());
            return;
        }
        otherCommands.accept(command);
    }

    /**
     * Let things far from every waker sleep, by {@code rule} — see {@link Sleep}; null wakes every thing for good, as a
     * world with no rule has it. Who sleeps is decided on the next frame, and then as the rule says.
     */
    public final void setSleep(Sleep rule) {
        sleepers.set(rule);
    }

    /** Whether {@code thing} sleeps now: the world has a rule, and it put the thing to sleep at its last look. */
    public final boolean isAsleep(GameObject thing) {
        return sleepers.isAsleep(thing);
    }

    /**
     * What the world keeps of its own beside its things, its players and its lines a save writes elsewhere — the things
     * awake since who sleeps was last decided — for a save, between frames.
     */
    public final void saveWorld(uz.dukeengine.core.save.SaveOut out) {
        var awake = sleepers.awakeOr(null);
        out.writeBoolean(awake != null);
        if (awake != null) {
            out.writeIds(awake.stream().map(GameObject::getId).toList());
        }
    }

    /**
     * Read back what {@link #saveWorld} wrote, every thing of the save already in the world: those awake as the save
     * had them, every other asleep until who sleeps is next decided on the rule's own frame, as in the saved world.
     */
    public final void loadWorld(uz.dukeengine.core.save.SaveIn in) {
        if (!in.readBoolean()) {
            sleepers.restore(null);
        } else {
            sleepers.restore(in.readIds().stream().map(id -> {
                var thing = findObject(id);
                if (thing == null) {
                    throw new IllegalArgumentException("thing " + id.value() + " is awake in the save but not here");
                }
                return thing;
            }).toList());
        }
    }

    /**
     * What the world keeps of its ground movers outside their modules, for a save between frames: the frame's searching,
     * where each was sent beside a thing, the obstacles' version as their next laying leaves it, and the movers' cells.
     */
    public final void saveMovers(uz.dukeengine.core.save.SaveOut out) {
        routes.save(out);
        standing.save(out);
        out.writeBoolean(pathGrid != null);
        if (pathGrid != null) {
            out.writeInt(pathGrid.getObstacleVersion() + (obstacles.behind() ? 1 : 0));
            pathGrid.movers().save(out);
        }
    }

    /**
     * Read back what {@link #saveMovers} wrote, last, the save's things, decks and scenery all back: the obstacles laid
     * afresh from them are the ground the saved world's next laying gives, a function of them, at that laying's version.
     */
    public final void loadMovers(uz.dukeengine.core.save.SaveIn in) {
        routes.load(in);
        standing.load(in);
        if (in.readBoolean() != (pathGrid != null)) {
            throw new IllegalArgumentException("the save's world " + (pathGrid == null ? "had" : "had no")
                    + " ground grid, and this one " + (pathGrid == null ? "has none" : "has one"));
        }
        if (pathGrid != null) {
            int version = in.readInt();
            obstacles.layAfresh();
            obstacles.refresh();
            pathGrid.restoreObstacleVersion(version);
            pathGrid.movers().load(in);
        }
    }

    /** The commands queued for the start of the next frame, in order — for a save to carry. */
    public final List<Command> getPendingCommands() {
        return messageStream.peekAll();
    }

    // ---- reaping ----

    private void reapDestroyed() {
        reap(true);
        while (marked.stream().anyMatch(roster::isHere)) {
            reap(false); // what went took things with it — a hold its passengers — and they go the same frame
        }
    }

    /**
     * Take out what is destroyed, and — with {@code deaths} — what has died since the last frame: looked for among every
     * thing, or, where things sleep and who sleeps was not decided this frame, among the things awake and those marked
     * destroyed — a sleeper that died of no blow is found on the rule's next frame.
     */
    private void reap(boolean deaths) {
        var suspects = marked;
        marked = new ArrayList<>();
        List<GameObject> leaving = new ArrayList<>();
        List<GameObject> lying = new ArrayList<>();
        for (var object : sleepers.lookedAtByTheReap(roster, suspects)) {
            if (deaths && object.isEffectivelyDead() && !object.hasDied() && !object.isDestroyed()) {
                if (keptDead(object)) {
                    lying.add(object); // dies now, and stays while its death plays out
                    continue;
                }
                object.markDestroyed();
            }
            if (object.isDestroyed()) {
                leaving.add(object);
            }
        }
        // Told with the thing still in the world, which it stays in: findObject finds it, and it is gone only when a
        // module of its destroys it.
        for (var object : lying) {
            object.markDied();
            die(object, leaving);
            obstacles.changed(); // dead, it is in nobody's way
        }
        if (leaving.isEmpty()) {
            return;
        }
        for (var object : leaving) {
            if (roster.leave(object)) {
                CreationOrder.takeOut(unannounced, object);
                sleepers.left(object);
            }
        }
        obstacles.changed(); // a demolished building reopens its ground
        spectacle.left(leaving); // a thing gone ends its effects

        // Announce and react only once the corpses are gone, so a die module that
        // spawns wreckage builds it in a world that no longer holds the body.
        for (var object : leaving) {
            groundCells().forget(object.getId()); // off the ground's cells, where it stood and where it was going
            standing.forget(object.getId());
            if (!object.hasDied() && !object.hasVanished()) {
                die(object, leaving); // one kept dead was told when it died, and one vanished leaves without a word
            }
        }
    }

    /** Whether a module of {@code object} keeps it in the world, dead — see {@link uz.dukeengine.core.module.KeepsDead}. */
    private static boolean keptDead(GameObject object) {
        for (var module : object.getModules()) {
            if (module instanceof uz.dukeengine.core.module.KeepsDead keeper && keeper.keepsDead()) {
                return true;
            }
        }
        return false;
    }

    /** Tell a thing's death: the {@code ObjectDied}, if it died rather than was taken away, and its die modules. */
    private void die(GameObject object, List<GameObject> leaving) {
        var death = object.isEffectivelyDead() ? object.getBody().getDeath() : Death.NORMAL;
        if (object.isEffectivelyDead()) {
            var died = new ObjectDied(frame, object.getId(), object.getTemplate().name(),
                    object.getPlayerIndex(), object.getPosition(), death.type(), death.killer(),
                    object.getOrientation(), sideOf(death, leaving));
            post(died);
            for (var watcher : deathWatchers) {
                watcher.accept(died);
            }
        }
        for (var module : object.getModules()) {
            if (module instanceof DieModule dieModule) {
                dieModule.onDie(death);
            }
        }
    }

    /**
     * Whose side dealt a death: what the blow says, or — for a blow that did not say — the killer's, looked for among
     * what stands and what leaves this frame with it. -1 for nobody, or a killer long gone.
     */
    private int sideOf(Death death, List<GameObject> leaving) {
        if (death.killer() == null || death.killerPlayerIndex() >= 0) {
            return death.killerPlayerIndex();
        }
        var killer = findObject(death.killer());
        if (killer == null) {
            killer = leaving.stream().filter(gone -> gone.getId().equals(death.killer())).findFirst().orElse(null);
        }
        return killer == null ? -1 : killer.getPlayerIndex();
    }

    /** Advance game-specific state by one logic frame. */
    protected abstract void simulate();

    /** Create, register and return a new object built from {@code template}. */
    public final GameObject createObject(ThingTemplate template) {
        var object = thingFactory.newObject(template, new ObjectId(nextObjectId++));
        object.setWorld(this);
        enter(object);
        obstacles.changed();
        return object;
    }

    /** Flag an object for removal; it is reaped at the start of the next frame. */
    public final void destroyObject(GameObject object) {
        object.markDestroyed();
    }

    // ---- beams, effects riding things, streams ----

    @Override
    public final int beam(String look, Coord3D from, Coord3D to, float width) {
        return spectacle.beam(look, from, to, width);
    }

    @Override
    public final void moveBeam(int beam, Coord3D from, Coord3D to, float width) {
        spectacle.moveBeam(beam, from, to, width);
    }

    @Override
    public final void endBeam(int beam) {
        spectacle.endBeam(beam);
    }

    @Override
    public final int effect(String name, GameObject thing, String bone, Coord3D offset) {
        return spectacle.effect(name, thing, bone, offset);
    }

    @Override
    public final void endEffect(int effect) {
        spectacle.endEffect(effect);
    }

    /** Every effect riding a thing now, in the order they were started. */
    public final List<uz.dukeengine.core.thing.RidingEffect> getRidingEffects() {
        return spectacle.effects();
    }

    @Override
    public final void rideStream(String stream, GameObject thing, int most) {
        spectacle.rideStream(stream, thing, most);
    }

    @Override
    public final void breakStream(String stream) {
        spectacle.breakStream(stream);
    }

    /** Every stream things ride now, by name. */
    public final List<uz.dukeengine.core.thing.Stream> getStreams() {
        return spectacle.streams();
    }

    /** Every beam the simulation owns now, in the order they were made. */
    public final List<uz.dukeengine.core.thing.Beam> getBeams() {
        return spectacle.beams();
    }

    // ---- snapshot restore hooks (used by save/load) ----

    /** Remove every object — used when loading a saved game over this world. */
    public final void clearWorld() {
        forgetEveryThing();
    }

    private void forgetEveryThing() {
        roster.clear();
        sleepers.clear();
        unannounced.clear();
        marked = new ArrayList<>();
    }

    public final void setFrame(int frame) {
        this.frame = frame;
    }

    public final void setNextObjectId(int nextObjectId) {
        this.nextObjectId = nextObjectId;
    }

    /** Re-create an object with a specific id (not the auto-allocated one). */
    public final GameObject restoreObject(ThingTemplate template, ObjectId id) {
        var object = thingFactory.newObject(template, id);
        object.restored();
        object.setWorld(this);
        enter(object);
        return object;
    }

    /** {@code object} comes into the world: last in its order, found by its id and where it stands. */
    private void enter(GameObject object) {
        roster.enter(object);
        sleepers.entered(object);
        unannounced.add(object);
        if (object.isDestroyed()) {
            marked.add(object); // destroyed before it came in, and gone by the next reap
        }
    }

    // ---- the live things ----

    @Override
    public final void thingMoved(GameObject thing) {
        roster.partition().moved(thing);
    }

    @Override
    public final void ownerChanged(GameObject thing, int was) {
        roster.ownerChanged(thing, was);
    }

    @Override
    public final void spanChanged(GameObject thing) {
        roster.spanChanged(thing);
    }

    @Override
    public final void mobilityChanged(GameObject thing) {
        roster.mobilityChanged(thing);
    }

    @Override
    public final void revived(GameObject thing) {
        obstacles.changed(); // alive, it stands in the way again
        post(new uz.dukeengine.core.event.ObjectRevived(frame, thing.getId()));
        for (var watcher : revivalWatchers) {
            watcher.accept(thing);
        }
    }

    @Override
    public final void destroyMarked(GameObject thing) {
        marked.add(thing);
    }

    @Override
    public final void blowLanded(GameObject thing) {
        sleepers.blowLanded(thing, roster);
    }

    /**
     * The live things {@code player} owns, in the order they came into the world: the world's own list, to be walked
     * without changing the world while it is.
     */
    @Override
    public final List<GameObject> getObjectsOf(int player) {
        return roster.of(player);
    }

    /** The players that own a live thing now, lowest first. */
    public final java.util.SortedSet<Integer> getOwners() {
        return roster.owners();
    }

    /** The live things drawn along a line ({@link GameObject#setSpan}), in the order they came into the world. */
    public final List<GameObject> getSpanned() {
        return roster.spanned();
    }

    /** The live object with this id, or {@code null} if none (or it was reaped). */
    public final GameObject findObject(ObjectId id) {
        return roster.find(id);
    }

    /**
     * Live objects, in creation order. Unmodifiable snapshot: the world may change while it is walked. The same copy
     * until the objects next change, rather than a copy for every caller — the frame's own walks and every partition
     * query read it, and copying the list for each was a sixteenth of a busy frame.
     */
    @Override
    public final List<GameObject> getObjects() {
        return roster.copy();
    }

    public final int getObjectCount() {
        return roster.list().size();
    }

    public final int getNextObjectId() {
        return nextObjectId;
    }

    // ---- the checksum ----

    /**
     * A deterministic checksum of the whole world's state, ported in spirit from
     * SAGE's {@code VERIFY_CRC}. In lock-step every peer must compute the same
     * value each frame; a mismatch is a desync. Mixes each object's identity,
     * ownership, transform and health in creation order, using
     * {@link Float#floatToIntBits} so float state hashes identically everywhere —
     * and its statuses, for an object that carries any, which leaves the checksum of
     * a world with none, and every recording of one, as it was.
     */
    public final long checksum() {
        var sum = new Checksum();
        for (var object : roster.list()) {
            sum.mixThing(object);
        }
        for (int player : sight.revealedTo()) {
            sum.mix(player); // nothing revealed sums as it always did
        }
        for (int player : sight.markedSeen()) {
            sum.mix(~player); // told apart from the same player revealed; nothing marked sums as ever
        }
        for (int piece : scenery.removed()) {
            sum.mix(0x5CE0 ^ piece); // scenery taken away; all of it standing sums as it always did
        }
        if (pathGrid != null) {
            for (var deck : pathGrid.decks()) {
                if (!deck.isOpen()) {
                    sum.mix(-deck.floor()); // a closed deck: every deck open sums as none did
                }
            }
        }
        for (int index = 0; index < playerList.getPlayerCount(); index++) {
            sum.mixWith(playerList.getPlayer(index)::checksum); // a plain player sums nothing
        }
        long drawn = random.state();
        if (drawn != randomSeed) {
            // Where its random numbers stand, as the reference sums its seed: nothing drawn sums as it always did.
            sum.mixLong(drawn);
        }
        for (var part : checksumParts) {
            sum.mixLong(part.getAsLong());
        }
        return sum.value();
    }

    private final java.util.List<java.util.function.LongSupplier> checksumParts = new java.util.ArrayList<>();

    /**
     * A number of the game's own mixed into every checksum — state it keeps outside the engine that every machine
     * must agree on, its computer players' plans say — asked on the simulation's thread each time the world is summed.
     */
    public final void checksumAlso(java.util.function.LongSupplier part) {
        checksumParts.add(part);
    }

    private final java.util.Map<String, uz.dukeengine.core.save.Saveable> saveParts = new java.util.LinkedHashMap<>();

    /**
     * State of the game's own kept outside the engine — its computer players' plans, its scripts, its shared ledgers —
     * written into a snapshot under {@code name} after everything the engine writes and read back once every thing is
     * in the world again, in the order the parts were given. A name is one word, given once; kept across {@link #reset},
     * as the rules are. A load refuses a part the world does not keep and leaves none of the world's unread.
     */
    public final void saveAlso(String name, uz.dukeengine.core.save.Saveable part) {
        if (name.isEmpty() || name.chars().anyMatch(c -> c == ' ' || c == '|' || c == '\n' || c == '\r')) {
            throw new IllegalArgumentException("a save part is named by one word, not '" + name + "'");
        }
        if (saveParts.putIfAbsent(name, java.util.Objects.requireNonNull(part)) != null) {
            throw new IllegalArgumentException("a save part called " + name + " is kept already");
        }
    }

    /** The game's own save parts by name, in the order they were given — see {@link #saveAlso}. */
    public final java.util.Map<String, uz.dukeengine.core.save.Saveable> saveParts() {
        return java.util.Collections.unmodifiableMap(saveParts);
    }

    @Override
    public final uz.dukeengine.core.math.LogicRandom random() {
        return random;
    }

    /**
     * Start this world's random numbers from {@code seed} — before the first frame, and the same on every peer
     * and in every replay of the match, or the worlds part company at the first thing decided by chance. A
     * game that never calls this draws from {@link #DEFAULT_RANDOM_SEED}.
     */
    public final void setRandomSeed(long seed) {
        this.randomSeed = seed;
        random.restore(seed);
    }

    /** The current logic frame number — the simulation's clock. */
    public final int getFrame() {
        return frame;
    }

    /** Game time elapsed, in seconds, derived from the frame count. */
    public final float getGameTimeSeconds() {
        return frame * GameConstants.SECONDS_PER_LOGICFRAME;
    }

    public final boolean isGamePaused() {
        return paused;
    }

    public final void setGamePaused(boolean paused) {
        this.paused = paused;
    }

    public final boolean isInGame() {
        return inGame;
    }

    public final void setInGame(boolean inGame) {
        this.inGame = inGame;
    }
}
