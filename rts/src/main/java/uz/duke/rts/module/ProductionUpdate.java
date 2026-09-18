package uz.duke.rts.module;

import java.util.ArrayDeque;
import java.util.Deque;
import uz.duke.core.ini.Ini;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.Solid;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.core.thing.World;
import uz.duke.rts.Buildable;
import uz.duke.rts.player.RtsPlayer;

/**
 * Builds units over time, ported in spirit from SAGE's {@code ProductionUpdate}.
 *
 * <p>A factory/structure carries this module. {@link #queue} charges the owning
 * player up front (failing if they cannot afford it) and enqueues the unit; each
 * frame the head of the queue counts down its build time, and on completion the
 * unit is spawned next to the producer. Build cost and time come from the unit's
 * own {@link ThingTemplate}, mirroring SAGE's {@code BuildCost}/{@code BuildTime}.
 *
 * <p>One unit builds at a time, in queue order — deterministic by construction.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class ProductionUpdate extends UpdateModule {

    /**
     * Per-structure configuration: the unit template names this structure may
     * build — SAGE's command set. INI: {@code Builds = ElfArcher Rider}.
     * An empty list means "no build menu" (scripts can still queue anything).
     */
    public record Data(java.util.List<String> builds) implements ModuleData {
        public Data {
            builds = java.util.List.copyOf(builds);
        }

        public Data() {
            this(java.util.List.of());
        }
    }

    /** Which way finished units step out of the producer. */
    private static final Coord3D EXIT_DIRECTION = new Coord3D(0f, -1f, 0f);

    /** Breathing room between the producer's wall and the unit that just left it. */
    private static final float EXIT_CLEARANCE = 10f;

    /** How far to look for free ground when the doorway is crowded. */
    private static final float EXIT_SEARCH_RADIUS = 60f;

    private static final class DataBuilder {
        final java.util.List<String> builds = new java.util.ArrayList<>();
    }

    private static final uz.duke.core.ini.FieldParseTable<DataBuilder> DATA_TABLE =
            new uz.duke.core.ini.FieldParseTable<DataBuilder>()
                    .add("Builds", (ini, b) -> {
                        for (var token = ini.getNextTokenOrNull(); token != null; token = ini.getNextTokenOrNull()) {
                            b.builds.add(token);
                        }
                    });

    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return new Data(builder.builds);
    }

    private static final class Job {
        final ThingTemplate unit;
        int framesRemaining;

        Job(ThingTemplate unit, int framesRemaining) {
            this.unit = unit;
            this.framesRemaining = framesRemaining;
        }
    }

    private final Deque<Job> queue = new ArrayDeque<>();
    private final java.util.List<String> builds;
    private Coord3D rallyPoint;

    public ProductionUpdate(GameObject owner, Data data) {
        super(owner);
        this.builds = data.builds();
    }

    /** The unit template names this structure offers in its build menu. */
    public java.util.List<String> getBuilds() {
        return builds;
    }

    /** Whether this structure's build menu offers {@code templateName}. */
    public boolean canBuild(String templateName) {
        return builds.contains(templateName);
    }

    /** Set where finished units should move to once produced (null = stay put). */
    public void setRallyPoint(Coord3D rallyPoint) {
        this.rallyPoint = rallyPoint;
    }

    public Coord3D getRallyPoint() {
        return rallyPoint;
    }

    /**
     * Charge the owner and enqueue {@code unit} for production. Returns false if
     * the owner cannot afford it (nothing is queued in that case).
     */
    public boolean queue(ThingTemplate unit) {
        var world = getOwner().getWorld();
        if (world == null) {
            return false;
        }
        var player = RtsPlayer.of(world, getOwner().getPlayerIndex());
        if (player == null || !player.withdraw(Buildable.costOf(unit))) {
            return false;
        }
        queue.addLast(new Job(unit, Math.max(1, Buildable.framesOf(unit))));
        return true;
    }

    public boolean isProducing() {
        return !queue.isEmpty();
    }

    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Whether every gate attached to this factory agrees the line may move.
     *
     * <p>A factory with no gates builds without interruption, which is the plain
     * mechanism. What used to be here instead was one game's condition — a power
     * check — applied to every game built on the engine, including those with no
     * idea what power was.
     */
    private boolean gatesAllow() {
        for (var module : getOwner().getModules()) {
            if (module instanceof ProductionGate gate && !gate.canProduce()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void update() {
        var head = queue.peekFirst();
        if (head == null) {
            return;
        }
        var world = getOwner().getWorld();
        if (!gatesAllow()) {
            return; // something attached to this factory is holding the line
        }
        head.framesRemaining--;
        if (head.framesRemaining > 0) {
            return;
        }
        queue.removeFirst();

        var owner = getOwner();
        if (world != null) {
            var produced = world.spawn(head.unit, exitPosition(world, owner, head.unit), owner.getPlayerIndex());
            if (rallyPoint != null) {
                var ai = produced.findModule(MoveUpdate.class);
                if (ai != null) {
                    ai.moveTo(rallyPoint);
                }
            }
        }
    }

    /**
     * Where a finished unit appears: clear of the producer's own walls, and clear
     * of whatever else is already standing outside them.
     *
     * <p>Both objects are shapes now, so a fixed offset would bury a tank inside
     * the barracks that built it. The exit is pushed out by the two footprints
     * plus a margin, then nudged to genuinely free ground.
     */
    private static Coord3D exitPosition(World world, GameObject producer, ThingTemplate unit) {
        var unitShape = Solid.of(unit);
        float distance = producer.getGeometry().footprintRadius()
                + unitShape.footprintRadius() + EXIT_CLEARANCE;
        var doorway = producer.getPosition().add(EXIT_DIRECTION.scale(distance));
        return world.findClearPosition(unitShape, doorway, EXIT_SEARCH_RADIUS);
    }
}
