package uz.dukeengine.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
import uz.dukeengine.core.thing.Sighted;
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
 */
public abstract class GameLogic extends SubsystemInterface implements World {

    private final ThingFactory thingFactory;
    private final PlayerList playerList;
    private final MessageStream messageStream = new MessageStream();
    private final PartitionManager partition = new PartitionManager(this::getObjects);
    private final uz.dukeengine.core.script.ScriptEngine scriptEngine = new uz.dukeengine.core.script.ScriptEngine();
    private final List<GameObject> objects = new ArrayList<>();
    private PathGrid pathGrid; // null = open terrain (direct paths)
    private WorldTemplate world; // null = a game with no World block
    private boolean staticObstaclesDirty = true;
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
    public GameObject spawn(ThingTemplate template, Coord3D position, int playerIndex) {
        var object = createObject(template);
        object.setPosition(position);
        object.setPlayerIndex(playerIndex);
        return object;
    }

    /**
     * Whether {@code viewerPlayer} can see {@code target} — fog of war. A player
     * always sees its own units; otherwise the target must lie within the vision
     * range of one of the viewer's (or an ally's) living units.
     */
    public final boolean canSee(int viewerPlayer, GameObject target) {
        return target.getPlayerIndex() == viewerPlayer || canSee(viewerPlayer, target.getPosition());
    }

    /**
     * Whether {@code viewerPlayer} has eyes on a point of the map — the question
     * to ask about a place rather than a thing, such as where something just
     * happened after the thing itself has gone.
     */
    public final boolean canSee(int viewerPlayer, Coord3D position) {
        for (var watcher : objects) {
            if (watcher.isEffectivelyDead() || Sighted.of(watcher.getTemplate()) <= 0f) {
                continue;
            }
            boolean friendlyEye = watcher.getPlayerIndex() == viewerPlayer
                    || getRelationship(viewerPlayer, watcher.getPlayerIndex()) == Relationship.ALLIES;
            if (friendlyEye
                    && watcher.getPosition().distance(position) <= Sighted.of(watcher.getTemplate())) {
                return true;
            }
        }
        return false;
    }

    /** Every object {@code viewerPlayer} can currently see, in creation order. */
    public final List<GameObject> getVisibleObjects(int viewerPlayer) {
        var visible = new ArrayList<GameObject>();
        for (var object : objects) {
            if (canSee(viewerPlayer, object)) {
                visible.add(object);
            }
        }
        return visible;
    }

    public final PartitionManager getPartition() {
        return partition;
    }

    @Override
    public GameObject findClosest(Coord3D center, float range, Predicate<GameObject> filter) {
        return partition.closestObject(center, range, filter::test);
    }

    @Override
    public List<GameObject> objectsInRange(Coord3D center, float range, Predicate<GameObject> filter) {
        return partition.objectsInRange(center, range, filter::test);
    }

    @Override
    public GameObject findClosestInReach(GameObject from, float reach, Predicate<GameObject> filter) {
        return partition.closestWithinReach(Footprint.of(from), reach, filter::test);
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
        return partition.firstOverlapping(footprint,
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
        if (Solid.of(mover.getTemplate()).isPoint()) {
            return null; // no body, nothing to bump into
        }
        int level = levelAt(position);
        // Something in the air is in nobody's way on the ground, as the reference's aircraft are not.
        return partition.firstOverlapping(Footprint.of(mover, position),
                candidate -> candidate != mover
                        && !candidate.isDestroyed()
                        && !candidate.isEffectivelyDead()
                        && !candidate.isContained()
                        && !candidate.hasStatus(uz.dukeengine.core.thing.ObjectStatus.AIRBORNE)
                        && levelAt(candidate.getPosition()) == level);
    }

    /** Install a navigation grid so movement routes around terrain obstacles. */
    @Override
    public final float cellSize() {
        return pathGrid == null ? World.super.cellSize() : pathGrid.getCellSize();
    }

    public final void setPathGrid(PathGrid pathGrid) {
        this.pathGrid = pathGrid;
        // A world built in storeys says how tall one is, and every map laid in it is laid at
        // that height: a floor swapped in mid-game included. A map that says its own keeps it —
        // the world's height is what a map is laid at when it has not said.
        if (pathGrid != null && pathGrid.getLevelHeight() <= 0f && world instanceof Layered layered) {
            pathGrid.setLevelHeight(layered.levelHeight());
        }
        this.staticObstaclesDirty = true;
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

    /**
     * Bake every immobile object into the navigation grid's obstacle layer, so
     * paths route around buildings instead of into them.
     *
     * <p>Rebuilt wholesale rather than tracked incrementally: the set is small
     * (only things that cannot move), and a full rebuild cannot drift out of sync
     * with the world the way a running tally can. Runs only when something has
     * actually changed.
     *
     * <p>A cell counts as occupied when the object's outline comes within half a
     * cell of the cell's centre. Erring toward under-blocking is deliberate: a
     * cell wrongly left open is caught by {@code MoveUpdate}'s per-step collision
     * check, whereas a cell wrongly closed can seal a building's own doorway.
     */
    private void refreshStaticObstacles() {
        if (!staticObstaclesDirty || pathGrid == null) {
            return;
        }
        staticObstaclesDirty = false;
        pathGrid.beginObstacles();
        float cellSize = pathGrid.getCellSize();
        float halfCell = cellSize * 0.5f;
        for (var object : objects) {
            var shape = Solid.of(object.getTemplate());
            if (object.isMobile() || shape.isPoint()) {
                continue;
            }
            var footprint = Footprint.of(object);
            var position = object.getPosition();
            float reach = shape.footprintRadius() + halfCell;
            int minX = (int) Math.floor((position.x() - reach) / cellSize);
            int maxX = (int) Math.floor((position.x() + reach) / cellSize);
            int minY = (int) Math.floor((position.y() - reach) / cellSize);
            int maxY = (int) Math.floor((position.y() + reach) / cellSize);
            for (int cy = minY; cy <= maxY; cy++) {
                for (int cx = minX; cx <= maxX; cx++) {
                    if (footprint.distanceTo(pathGrid.cellCenter(cx, cy)) <= halfCell) {
                        pathGrid.setObstacle(cx, cy);
                    }
                }
            }
        }
        pathGrid.commitObstacles();
    }

    @Override
    public int getNavigationVersion() {
        return pathGrid == null ? 0 : pathGrid.getObstacleVersion();
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

    public final PathGrid getPathGrid() {
        return pathGrid;
    }

    @Override
    public Path findPath(Coord3D from, Coord3D to) {
        if (pathGrid == null) {
            return new Path(List.of(to)); // open terrain: go straight there
        }
        refreshStaticObstacles();
        return Pathfinder.findPath(pathGrid, from, to);
    }

    /**
     * A route for {@code mover} to {@code to} — or, where there is none, to the nearest place there is one to,
     * {@link Path#reachesGoal} saying which: see {@link Pathfinder#findPathOrNearest}.
     */
    @Override
    public Path findPath(GameObject mover, Coord3D to) {
        if (pathGrid == null) {
            return new Path(List.of(to));
        }
        refreshStaticObstacles();
        if (cellsThisFrame >= pathfindBudget) {
            return null; // the frame's searching is spent: it waits for the next
        }
        // A search once started runs to its end, as the reference's do (processPathfindQueue starts one only while
        // the frame's total is under PATHFIND_CELLS_PER_FRAME): a frame goes over by one search at most, and no
        // search is thrown away half done to be started again.
        var tally = new Pathfinder.Tally();
        var path = Pathfinder.findPathOrNearest(pathGrid, mover.getPosition(), to,
                Solid.of(mover.getTemplate()).footprintRadius(), zones(), tally);
        cellsThisFrame += tally.cells();
        return path;
    }

    // ---- what searching costs ----

    /** SAGE's {@code PATHFIND_CELLS_PER_FRAME} (AIPathfind.cpp): the cells a frame's path searches may examine. */
    public static final int DEFAULT_PATHFIND_BUDGET = 5000;

    private int pathfindBudget = DEFAULT_PATHFIND_BUDGET;
    private int cellsThisFrame;
    private int cellsLastFrame;
    private uz.dukeengine.core.pathfind.Zones zones;

    /**
     * How many cells a frame's path searches may examine before the rest wait for the next frame — a mover
     * waiting keeps the route it had, or stands. A search is started only while the frame is under it, and runs to
     * its end once started. The same on every machine; 5000 unless the game says otherwise.
     */
    public final void setPathfindBudget(int cells) {
        this.pathfindBudget = Math.max(1, cells);
    }

    public final int getPathfindBudget() {
        return pathfindBudget;
    }

    /** How many cells the last whole frame's path searches examined. */
    public final int getCellsExaminedLastFrame() {
        return cellsLastFrame;
    }

    /**
     * The grid's connected zones as it stands — recomputed when anything that decides where can be walked has
     * changed ({@link PathGrid#getShapeVersion}). Null for a world with no grid.
     */
    public final uz.dukeengine.core.pathfind.Zones zones() {
        if (pathGrid == null) {
            return null;
        }
        refreshStaticObstacles();
        if (zones == null || !zones.isCurrent(pathGrid)) {
            zones = uz.dukeengine.core.pathfind.Zones.of(pathGrid);
        }
        return zones;
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
        if (pathGrid == null || isBeside(who, what)) {
            return straight;
        }
        var zones = zones();
        var from = who.getPosition();
        int fromX = pathGrid.toCellX(from);
        int fromY = pathGrid.toCellY(from);
        if (pathGrid.isBlocked(fromX, fromY)) {
            // Standing in stone, it steps out first: what it can reach is what that cell can.
            var way = nearestOpenCentre(from);
            if (way == null) {
                return straight;
            }
            fromX = pathGrid.toCellX(way);
            fromY = pathGrid.toCellY(way);
        }
        int zone = zones.zoneOf(fromX, fromY);
        // The spot will do if it can be stood on and walked to — which its zone says at once, where it used to take a
        // search each time, and every frame for a unit closing on something.
        if (zones.zoneOf(pathGrid.toCellX(straight), pathGrid.toCellY(straight)) == zone && zone >= 0) {
            return straight;
        }
        var beside = besideCellNearest(who, what, straight, zones, zone);
        return beside != null ? beside : reachableNearest(straight, zones, zone, fromX, fromY);
    }

    /** The centre of the open cell nearest a point in stone, in rings outward, or null for none near. */
    private Coord3D nearestOpenCentre(Coord3D from) {
        int cx = pathGrid.toCellX(from);
        int cy = pathGrid.toCellY(from);
        for (int ring = 1; ring <= 8; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == ring && !pathGrid.isBlocked(cx + dx, cy + dy)) {
                        return pathGrid.cellCenter(cx + dx, cy + dy);
                    }
                }
            }
        }
        return null;
    }

    /** Of the cells beside {@code what} in the zone {@code zone}, the one nearest {@code wanted}; null for none. */
    private Coord3D besideCellNearest(GameObject who, GameObject what, Coord3D wanted,
            uz.dukeengine.core.pathfind.Zones zones, int zone) {
        var target = Footprint.of(what);
        float reach = target.shape().footprintRadius() + Solid.of(who.getTemplate()).footprintRadius()
                + 2f * pathGrid.getCellSize();
        var middle = what.getPosition();
        int fromX = pathGrid.toCellX(new Coord3D(middle.x() - reach, middle.y() - reach, 0f));
        int toX = pathGrid.toCellX(new Coord3D(middle.x() + reach, middle.y() + reach, 0f));
        int fromY = pathGrid.toCellY(new Coord3D(middle.x() - reach, middle.y() - reach, 0f));
        int toY = pathGrid.toCellY(new Coord3D(middle.x() + reach, middle.y() + reach, 0f));
        Coord3D best = null;
        float nearest = Float.MAX_VALUE;
        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                if (zone < 0 || zones.zoneOf(x, y) != zone) {
                    continue;
                }
                var centre = pathGrid.cellCenter(x, y);
                float gap = Footprint.of(who, centre).separation(target);
                if (gap < 0f || gap > cellSize()) {
                    continue; // on top of it, or not near enough to work on it
                }
                float away = squaredAcross(centre, wanted);
                if (away < nearest) {
                    nearest = away;
                    best = centre;
                }
            }
        }
        return best;
    }

    /** The cell of the zone nearest {@code wanted}, or {@code wanted} where the zone is no zone at all. */
    private Coord3D reachableNearest(Coord3D wanted, uz.dukeengine.core.pathfind.Zones zones, int zone, int fromX,
            int fromY) {
        int nearest = zone < 0 ? -1
                : zones.nearestIn(zone, pathGrid.toCellX(wanted), pathGrid.toCellY(wanted), fromX, fromY);
        return nearest < 0 ? wanted : pathGrid.cellCenter(nearest % pathGrid.getWidth(), nearest / pathGrid.getWidth());
    }

    private static float squaredAcross(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    private void clearState() {
        objects.clear();
        staticObstaclesDirty = true;
        random.restore(randomSeed);
        frame = 0;
        nextObjectId = 1;
        paused = false;
        inGame = false;
    }

    @Override
    public final void update() {
        refreshStaticObstacles(); // before commands: a MoveTo issued now must see the world as it is
        recordFrame();
        messageStream.propagate(this::onCommand);
        updateObjects();
        reapDestroyed();
        simulate();
        scriptEngine.evaluate(this);
        cellsLastFrame = cellsThisFrame;
        cellsThisFrame = 0;
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
     * Handle one command drained from the stream. Default does nothing; concrete
     * simulations override it, narrowing {@link Command} to their own game's
     * sealed command hierarchy and pattern-matching over it exhaustively.
     */
    protected void onCommand(Command command) {
    }

    /**
     * Tick every object's update modules once, in creation order. Objects
     * created during this pass are not ticked until next frame, so the frame's
     * object set is well-defined.
     */
    private void updateObjects() {
        int count = objects.size();
        for (int i = 0; i < count; i++) {
            objects.get(i).updateModules();
        }
    }

    private void reapDestroyed() {
        List<GameObject> leaving = null;
        for (var object : objects) {
            if (object.isEffectivelyDead()) {
                object.markDestroyed();
            }
            if (object.isDestroyed()) {
                if (leaving == null) {
                    leaving = new ArrayList<>();
                }
                leaving.add(object);
            }
        }
        if (leaving == null) {
            return;
        }
        objects.removeAll(leaving);
        staticObstaclesDirty = true; // a demolished building reopens its ground

        // Announce and react only once the corpses are gone, so a die module that
        // spawns wreckage builds it in a world that no longer holds the body.
        for (var object : leaving) {
            var death = object.isEffectivelyDead() ? object.getBody().getDeath() : Death.NORMAL;
            if (object.isEffectivelyDead()) {
                post(new ObjectDied(frame, object.getId(), object.getTemplate().name(),
                        object.getPlayerIndex(), object.getPosition(), death.type(), death.killer(),
                        object.getOrientation()));
            }
            for (var module : object.getModules()) {
                if (module instanceof DieModule die) {
                    die.onDie(death);
                }
            }
        }
    }

    /** Advance game-specific state by one logic frame. */
    protected abstract void simulate();

    /** Create, register and return a new object built from {@code template}. */
    public final GameObject createObject(ThingTemplate template) {
        var object = thingFactory.newObject(template, new ObjectId(nextObjectId++));
        object.setWorld(this);
        objects.add(object);
        staticObstaclesDirty = true;
        return object;
    }

    /** Flag an object for removal; it is reaped at the start of the next frame. */
    public final void destroyObject(GameObject object) {
        object.markDestroyed();
    }

    // ---- snapshot restore hooks (used by save/load) ----

    /** Remove every object — used when loading a saved game over this world. */
    public final void clearWorld() {
        objects.clear();
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
        object.setWorld(this);
        objects.add(object);
        return object;
    }

    /** The live object with this id, or {@code null} if none (or it was reaped). */
    public final GameObject findObject(ObjectId id) {
        for (var object : objects) {
            if (object.getId().equals(id)) {
                return object;
            }
        }
        return null;
    }

    /** Live objects, in creation order. Unmodifiable snapshot. */
    @Override
    public final List<GameObject> getObjects() {
        return List.copyOf(objects);
    }

    public final int getObjectCount() {
        return objects.size();
    }

    public final int getNextObjectId() {
        return nextObjectId;
    }

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
        long hash = 1125899906842597L; // a large prime seed
        for (var object : objects) {
            hash = mix(hash, object.getId().value());
            hash = mix(hash, object.getPlayerIndex());
            var p = object.getPosition();
            hash = mix(hash, Float.floatToIntBits(p.x()));
            hash = mix(hash, Float.floatToIntBits(p.y()));
            hash = mix(hash, Float.floatToIntBits(p.z()));
            hash = mix(hash, Float.floatToIntBits(object.getOrientation()));
            hash = mix(hash, object.getBody() == null ? -1 : Float.floatToIntBits(object.getBody().getHealth()));
            int statuses = object.statusBits();
            if (statuses != 0) {
                hash = mix(hash, statuses);
            }
        }
        return hash;
    }

    private static long mix(long hash, int value) {
        return hash * 31 + value;
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
