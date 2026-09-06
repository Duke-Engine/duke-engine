package uz.duke.rts.module;

import java.util.ArrayDeque;
import java.util.Deque;
import uz.duke.core.ini.Ini;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingTemplate;

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

    /** Where finished units appear relative to the producer. */
    private static final Coord3D SPAWN_OFFSET = new Coord3D(0f, -10f, 0f);

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
        var player = world.getPlayer(getOwner().getPlayerIndex());
        if (player == null || !player.withdraw(unit.getBuildCost())) {
            return false;
        }
        queue.addLast(new Job(unit, Math.max(1, unit.getBuildTimeFrames())));
        return true;
    }

    public boolean isProducing() {
        return !queue.isEmpty();
    }

    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void update() {
        var head = queue.peekFirst();
        if (head == null) {
            return;
        }
        var world = getOwner().getWorld();
        if (world != null && !PowerGrid.isPowered(world, getOwner().getPlayerIndex())) {
            return; // base is under-powered — production stalls
        }
        head.framesRemaining--;
        if (head.framesRemaining > 0) {
            return;
        }
        queue.removeFirst();

        var owner = getOwner();
        if (world != null) {
            var produced = world.spawn(head.unit, owner.getPosition().add(SPAWN_OFFSET), owner.getPlayerIndex());
            if (rallyPoint != null) {
                var ai = produced.findModule(MoveUpdate.class);
                if (ai != null) {
                    ai.moveTo(rallyPoint);
                }
            }
        }
    }
}
