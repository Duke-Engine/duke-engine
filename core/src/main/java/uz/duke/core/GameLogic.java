package uz.duke.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;
import uz.duke.core.message.MessageStream;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.partition.PartitionManager;
import uz.duke.core.pathfind.Path;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.core.pathfind.Pathfinder;
import uz.duke.core.player.PlayerList;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.core.thing.World;

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
    private final uz.duke.core.script.ScriptEngine scriptEngine = new uz.duke.core.script.ScriptEngine();
    private final List<GameObject> objects = new ArrayList<>();
    private PathGrid pathGrid; // null = open terrain (direct paths)

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
     *     to give the game its own {@link uz.duke.core.player.Player} subtype
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
        clearState();
    }

    @Override
    public void reset() {
        thingFactory.reset();
        messageStream.reset();
        playerList.reset();
        scriptEngine.reset();
        clearState();
    }

    @Override
    public Relationship getRelationship(int a, int b) {
        return playerList.getRelationship(a, b);
    }

    @Override
    public uz.duke.core.player.Player getPlayer(int index) {
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
        if (target.getPlayerIndex() == viewerPlayer) {
            return true;
        }
        for (var watcher : objects) {
            if (watcher.isEffectivelyDead() || watcher.getTemplate().getVisionRange() <= 0f) {
                continue;
            }
            boolean friendlyEye = watcher.getPlayerIndex() == viewerPlayer
                    || getRelationship(viewerPlayer, watcher.getPlayerIndex()) == Relationship.ALLIES;
            if (friendlyEye
                    && watcher.getPosition().distance(target.getPosition()) <= watcher.getTemplate().getVisionRange()) {
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

    /** Install a navigation grid so movement routes around terrain obstacles. */
    public final void setPathGrid(PathGrid pathGrid) {
        this.pathGrid = pathGrid;
    }

    public final PathGrid getPathGrid() {
        return pathGrid;
    }

    @Override
    public Path findPath(Coord3D from, Coord3D to) {
        if (pathGrid == null) {
            return new Path(List.of(to)); // open terrain: go straight there
        }
        return Pathfinder.findPath(pathGrid, from, to);
    }

    private void clearState() {
        objects.clear();
        frame = 0;
        nextObjectId = 1;
        paused = false;
        inGame = false;
    }

    @Override
    public final void update() {
        messageStream.propagate(this::onCommand);
        updateObjects();
        reapDestroyed();
        simulate();
        scriptEngine.evaluate(this);
        frame++;
    }

    /** Register a map/mission {@link uz.duke.core.script.Trigger}. */
    public final void addTrigger(uz.duke.core.script.Trigger trigger) {
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
        // Dead objects leave the world (the engine's default DieModule behaviour).
        for (var object : objects) {
            if (object.isEffectivelyDead()) {
                object.markDestroyed();
            }
        }
        objects.removeIf(GameObject::isDestroyed);
    }

    /** Advance game-specific state by one logic frame. */
    protected abstract void simulate();

    /** Create, register and return a new object built from {@code template}. */
    public final GameObject createObject(ThingTemplate template) {
        var object = thingFactory.newObject(template, new ObjectId(nextObjectId++));
        object.setWorld(this);
        objects.add(object);
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
     * {@link Float#floatToIntBits} so float state hashes identically everywhere.
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
        }
        return hash;
    }

    private static long mix(long hash, int value) {
        return hash * 31 + value;
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
