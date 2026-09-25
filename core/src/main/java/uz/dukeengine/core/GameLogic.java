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
        if (revealedTo.contains(viewerPlayer)) {
            return true; // the whole map, for good
        }
        for (var watcher : objects) {
            if (watcher.isEffectivelyDead() || watcher.hasStatus(uz.dukeengine.core.thing.ObjectStatus.HIDDEN)
                    || watcher.isContained() && !watcher.seesOut()) {
                continue; // a thing not there sees nothing either, nor one shut in a hold
            }
            boolean goingUp = watcher.hasStatus(uz.dukeengine.core.thing.ObjectStatus.UNDER_CONSTRUCTION);
            float fog = goingUp ? watcher.getGeometry().footprintRadius() : watcher.getFogRange();
            float byAll = goingUp ? 0f : uz.dukeengine.core.thing.Sighted.seenByAllOf(watcher.getTemplate());
            if (fog <= 0f && byAll <= 0f) {
                continue;
            }
            float away = watcher.getPosition().distance(position);
            if (byAll > 0f && away <= byAll && !watcher.isHiddenFrom(viewerPlayer)) {
                return true;
            }
            boolean eye = watcher.getPlayerIndex() == viewerPlayer
                    || getRelationship(viewerPlayer, watcher.getPlayerIndex()) == Relationship.ALLIES
                    || sharedSight != null && sharedSight.test(viewerPlayer, watcher);
            if (eye && away <= fog) {
                return true;
            }
        }
        return false;
    }

    /** Whose things lend a player their sight beyond its own and its allies' — see {@link #setSharedSight}. */
    private java.util.function.BiPredicate<Integer, GameObject> sharedSight;

    /**
     * Let the game say which things that are neither a player's nor an ally's lend it their sight — the reference's
     * CIA Intelligence and satellite hacks, seeing through the enemy's units for a time: asked of such a thing, on
     * the simulation thread, and where it says yes the thing's sight counts for that player as its own units' does.
     * A pure function of the simulation's state, or the peers see differently; null for none.
     */
    public final void setSharedSight(java.util.function.BiPredicate<Integer, GameObject> rule) {
        this.sharedSight = rule;
    }

    /** The players the whole map has been revealed to, for good — see {@link #revealMapTo}. Sorted. */
    private final java.util.TreeSet<Integer> revealedTo = new java.util.TreeSet<>();

    /**
     * Reveal the whole map to {@code player} for the rest of the match: no fog, no shroud, in what that player is
     * shown — the reference's {@code MAP_REVEAL_ALL_PERM}, and what a player beaten while the others fight on is given
     * to watch the end by. From code on the simulation thread, where every machine does it on the same frame: it is
     * part of the checksum. Nothing the simulation decides reads it.
     */
    public final void revealMapTo(int player) {
        revealedTo.add(player);
    }

    /** Whether the whole map has been revealed to {@code player}. */
    public final boolean isMapRevealedTo(int player) {
        return revealedTo.contains(player);
    }

    /** The players the whole map has been revealed to, in order — for a save. */
    public final java.util.SortedSet<Integer> getRevealedTo() {
        return java.util.Collections.unmodifiableSortedSet(revealedTo);
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
    public final boolean canStep(GameObject mover, Coord3D from, Coord3D to, int toward) {
        if (pathGrid == null || !pathGrid.hasDecks()) {
            return canStep(from, to);
        }
        int fromX = pathGrid.toCellX(from);
        int fromY = pathGrid.toCellY(from);
        int toX = pathGrid.toCellX(to);
        int toY = pathGrid.toCellY(to);
        int floor = mover.getFloor();
        return pathGrid.walks(floor, fromX, fromY, toX, toY)
                || toward >= 0 && pathGrid.enters(floor, toward, fromX, fromY, toX, toY);
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

    /**
     * Lay a deck over the map's ground — see {@link PathGrid#addDeck} — at run time: its floor comes back. The ground
     * under it closes where it stands lower than the clearance ({@link #setDeckClearance}); things placed on it after
     * are on it.
     */
    public final int addDeck(Coord3D first, Coord3D second, Coord3D third, Coord3D fourth) {
        return pathGrid.addDeck(first, second, third, fourth);
    }

    /** How high a deck must stand over the ground for the ground under it to stay open; the reference's 10. */
    public final void setDeckClearance(float clearance) {
        pathGrid.setDeckClearance(clearance);
    }

    /**
     * Open or close a deck. Closed — a bridge destroyed — every thing on it is handed back to the ground under it, and
     * the game is told who, on the simulation thread ({@link #onDeckClosed}): the reference kills them with falling
     * damage, and the game decides. Every route is planned again, round it or back over it.
     */
    public final void setDeckOpen(int floor, boolean open) {
        pathGrid.setDeckOpen(floor, open);
        if (open) {
            return;
        }
        var fell = new ArrayList<GameObject>();
        for (var object : objects) {
            if (object.getFloor() == floor) {
                fell.add(object);
                object.setFloor(0);
                var at = object.getPosition();
                object.setPosition(new Coord3D(at.x(), at.y(), pathGrid.groundHeight(at)));
            }
        }
        for (var listener : deckListeners) {
            listener.accept(floor, List.copyOf(fell));
        }
    }

    private final List<java.util.function.BiConsumer<Integer, List<GameObject>>> deckListeners = new ArrayList<>();

    /** Told, when a deck is closed, which things were on it — see {@link #setDeckOpen}. */
    public final void onDeckClosed(java.util.function.BiConsumer<Integer, List<GameObject>> listener) {
        deckListeners.add(listener);
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
        return findBlocker(mover, position, candidate -> false);
    }

    @Override
    public GameObject findBlocker(GameObject mover, Coord3D position, Predicate<GameObject> passing) {
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
                        && candidate.getFloor() == mover.getFloor() // over a deck and under it, nobody's way
                        && levelAt(candidate.getPosition()) == level
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
        refreshStaticObstacles();
        return groundCells().take(mover, place);
    }

    @Override
    public final Coord3D takePlace(GameObject mover, Coord3D place, Coord3D near) {
        refreshStaticObstacles();
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
        if (!groundCells().keepsCells(mover)) {
            return null;
        }
        float room = Solid.of(mover.getTemplate()).footprintRadius() + Solid.of(from.getTemplate()).footprintRadius();
        float cell = pathGrid.getCellSize();
        var block = groundCells().nearest(mover, mover.getPosition(), candidate -> {
            var middle = groundCells().pointOf(candidate);
            float reach = room + candidate.half() * cell;
            return clearOf(way, middle, reach);
        });
        if (block == null) {
            return null;
        }
        pathGrid.movers().claimGoal(mover.getId().value(), block);
        return groundCells().pointOf(block);
    }

    /** Whether a point is further than {@code reach} from every leg of {@code way}. */
    private static boolean clearOf(List<Coord3D> way, Coord3D point, float reach) {
        for (int i = 1; i < way.size(); i++) {
            if (distanceToLeg(point, way.get(i - 1), way.get(i)) < reach) {
                return false;
            }
        }
        return way.size() != 1 || squaredAcross(point, way.getFirst()) >= reach * reach;
    }

    private static float distanceToLeg(Coord3D p, Coord3D a, Coord3D b) {
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        float length = dx * dx + dy * dy;
        float t = length <= 0f ? 0f : Math.clamp(((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / length, 0f, 1f);
        float x = a.x() + dx * t - p.x();
        float y = a.y() + dy * t - p.y();
        return (float) Math.sqrt(x * x + y * y);
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
     *
     * <p>Only what the game's {@link ObstacleRules} put in the way is laid, and a fence along its line alone; a still
     * thing turned or moved has its footprint laid again ({@link #stillThingMoved}).
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
            if (object.isMobile() || shape.isPoint() || object.isEffectivelyDead() || object.isContained()
                    || !inTheWay(object)) {
                continue; // a building lying dead while its death plays out is in nobody's way
            }
            float fence = object.getTemplate() instanceof Solid solid ? solid.fenceWidth() : 0f;
            if (fence > 0f) {
                layFence(object, fence, ((Solid) object.getTemplate()).fenceOffset());
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

    /** What of the still things is in the way of a route — see {@link ObstacleRules}. */
    private ObstacleRules obstacleRules = ObstacleRules.EVERYTHING;

    /** The game's answer to what is in the way on the ground; null for every still thing with a shape, as before. */
    public final void setObstacleRules(ObstacleRules rules) {
        this.obstacleRules = rules == null ? ObstacleRules.EVERYTHING : rules;
        this.staticObstaclesDirty = true;
    }

    public final ObstacleRules getObstacleRules() {
        return obstacleRules;
    }

    private boolean inTheWay(GameObject object) {
        var rules = obstacleRules;
        for (var kind : rules.outOfTheWay()) {
            if (object.isKindOf(kind)) {
                return false;
            }
        }
        if (!rules.inTheWay().isEmpty() && rules.inTheWay().stream().noneMatch(object::isKindOf)) {
            return false;
        }
        return rules.aboveGround() <= 0f
                || object.getPosition().z() - groundHeight(object.getPosition()) <= rules.aboveGround();
    }

    /**
     * A fence in the way along its line alone — the reference's {@code Pathfinder::classifyFence}: points half a cell
     * apart along its facing, from its offset behind its position for its width, each closing the cell it falls in.
     */
    private void layFence(GameObject object, float width, float offset) {
        float cell = pathGrid.getCellSize();
        float step = cell * 0.5f;
        float halfThick = cell / 10f;
        double angle = object.getOrientation();
        float c = (float) StrictMath.cos(angle);
        float s = (float) StrictMath.sin(angle);
        int along = (int) Math.ceil(width / step);
        int across = (int) Math.ceil(2f * halfThick / step);
        var at = object.getPosition();
        float rowX = at.x() - offset * c - halfThick * s;
        float rowY = at.y() + halfThick * c - offset * s;
        for (int row = 0; row < across; row++, rowX += s * step, rowY -= c * step) {
            float x = rowX;
            float y = rowY;
            for (int point = 0; point < along; point++, x += c * step, y += s * step) {
                pathGrid.setObstacle(pathGrid.toCellX(new Coord3D(x, y, 0f)), pathGrid.toCellY(new Coord3D(x, y, 0f)));
            }
        }
    }

    @Override
    public final void stillThingMoved() {
        staticObstaclesDirty = true;
    }

    @Override
    public int getNavigationVersion() {
        return pathGrid == null ? 0 : pathGrid.getObstacleVersion();
    }

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
        return route(mover, to, java.util.Set.of());
    }

    @Override
    public Path findPath(GameObject mover, Coord3D to, java.util.Set<ObjectId> round) {
        if (pathGrid == null) {
            return new Path(List.of(to));
        }
        refreshStaticObstacles();
        if (cellsThisFrame >= pathfindBudget) {
            return null;
        }
        return route(mover, to, round);
    }

    private Path route(GameObject mover, Coord3D to, java.util.Set<ObjectId> round) {
        var tally = new Pathfinder.Tally();
        float clearance = Solid.of(mover.getTemplate()).footprintRadius();
        Pathfinder.Traffic traffic = null;
        if (groundCells().keepsCells(mover)) {
            var ids = new java.util.HashSet<Integer>();
            for (var id : round) {
                ids.add(id.value());
            }
            traffic = groundCells().trafficFor(mover, ids);
        }
        var path = pathGrid.hasDecks()
                ? Pathfinder.findPathOrNearest(pathGrid, mover.getPosition(), mover.getFloor(), to,
                        pathGrid.floorAt(to), clearance, null, tally)
                : Pathfinder.findPathOrNearest(pathGrid, mover.getPosition(), to, clearance, zones(), tally, traffic);
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
        objectsCopy = null;
        revealedTo.clear();
        beams.clear();
        ridingEffects.clear();
        nextEffect = 1;
        nextBeam = 1;
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
        // What was made this frame is told so by its end, so the frame's picture shows what making it set.
        for (int i = 0; i < objects.size(); i++) {
            objects.get(i).announceCreated();
        }
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
        List<GameObject> leaving = new ArrayList<>();
        List<GameObject> lying = new ArrayList<>();
        for (var object : objects) {
            if (object.isEffectivelyDead() && !object.hasDied() && !object.isDestroyed()) {
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
            staticObstaclesDirty = true; // dead, it is in nobody's way
        }
        if (leaving.isEmpty()) {
            return;
        }
        objects.removeAll(leaving);
        objectsCopy = null;
        staticObstaclesDirty = true; // a demolished building reopens its ground
        if (!ridingEffects.isEmpty()) {
            var gone = new java.util.HashSet<ObjectId>();
            leaving.forEach(object -> gone.add(object.getId()));
            ridingEffects.values().removeIf(effect -> gone.contains(effect.thing())); // a thing gone ends its effects
        }

        // Announce and react only once the corpses are gone, so a die module that
        // spawns wreckage builds it in a world that no longer holds the body.
        for (var object : leaving) {
            groundCells().forget(object.getId()); // off the ground's cells, where it stood and where it was going
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
        objects.add(object);
        objectsCopy = null;
        staticObstaclesDirty = true;
        return object;
    }

    /** Flag an object for removal; it is reaped at the start of the next frame. */
    public final void destroyObject(GameObject object) {
        object.markDestroyed();
    }

    // ---- snapshot restore hooks (used by save/load) ----

    // ---- beams ----

    /** The beams the simulation owns, by number; out of the checksum and the save, since nothing decided reads them. */
    private final java.util.TreeMap<Integer, uz.dukeengine.core.thing.Beam> beams = new java.util.TreeMap<>();
    private int nextBeam = 1;

    @Override
    public final int beam(String look, Coord3D from, Coord3D to, float width) {
        int id = nextBeam++;
        beams.put(id, new uz.dukeengine.core.thing.Beam(id, look, from, to, width));
        return id;
    }

    @Override
    public final void moveBeam(int beam, Coord3D from, Coord3D to, float width) {
        beams.computeIfPresent(beam, (id, was) -> new uz.dukeengine.core.thing.Beam(id, was.look(), from, to, width));
    }

    @Override
    public final void endBeam(int beam) {
        beams.remove(beam);
    }

    private final java.util.TreeMap<Integer, uz.dukeengine.core.thing.RidingEffect> ridingEffects =
            new java.util.TreeMap<>();
    private int nextEffect = 1;

    @Override
    public final int effect(String name, GameObject thing, String bone, Coord3D offset) {
        int id = nextEffect++;
        ridingEffects.put(id, new uz.dukeengine.core.thing.RidingEffect(id, name, thing.getId(), bone, offset));
        return id;
    }

    @Override
    public final void endEffect(int effect) {
        ridingEffects.remove(effect);
    }

    /** Every effect riding a thing now, in the order they were started. */
    public final List<uz.dukeengine.core.thing.RidingEffect> getRidingEffects() {
        return List.copyOf(ridingEffects.values());
    }

    /** Every beam the simulation owns now, in the order they were made. */
    public final List<uz.dukeengine.core.thing.Beam> getBeams() {
        return List.copyOf(beams.values());
    }

    /** Remove every object — used when loading a saved game over this world. */
    public final void clearWorld() {
        objects.clear();
        objectsCopy = null;
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
        objects.add(object);
        objectsCopy = null;
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

    /**
     * Live objects, in creation order. Unmodifiable snapshot: the world may change while it is walked. The same copy
     * until the objects next change, rather than a copy for every caller — the frame's own walks and every partition
     * query read it, and copying the list for each was a sixteenth of a busy frame.
     */
    @Override
    public final List<GameObject> getObjects() {
        var copy = objectsCopy;
        if (copy == null) {
            copy = List.copyOf(objects);
            objectsCopy = copy;
        }
        return copy;
    }

    /** The copy {@link #getObjects} hands out, or null once the objects have changed since it was made. */
    private List<GameObject> objectsCopy;

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
            if (object.getBody() != null && object.getBody().getDamageScale() != 1f) {
                hash = mix(hash, Float.floatToIntBits(object.getBody().getDamageScale())); // 1 sums as it always did
            }
            if (object.getOwnVisionRange() >= 0f) {
                hash = mix(hash, Float.floatToIntBits(object.getOwnVisionRange()));
            }
            if (object.getOwnFogRange() >= 0f) {
                hash = mix(hash, Float.floatToIntBits(object.getOwnFogRange())); // unset sums as it always did
            }
            if (object.getTargetableFrom() > 0) {
                hash = mix(hash, object.getTargetableFrom());
            }
            if (object.getProducer() != null) {
                hash = mix(hash, object.getProducer().value()); // made by nothing sums as it always did
            }
            if (object.getFloor() != 0) {
                hash = mix(hash, object.getFloor());
            }
            int statuses = object.statusBits();
            if (statuses != 0) {
                hash = mix(hash, statuses);
            }
        }
        for (int player : revealedTo) {
            hash = mix(hash, player); // nothing revealed sums as it always did
        }
        if (pathGrid != null) {
            for (var deck : pathGrid.decks()) {
                if (!deck.isOpen()) {
                    hash = mix(hash, -deck.floor()); // a closed deck: every deck open sums as none did
                }
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
