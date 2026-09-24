package uz.dukeengine.rts.module;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rts.Buildable;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.player.RtsPlayer;
import uz.dukeengine.rts.player.Upgrade;

/**
 * Builds units over time, ported in spirit from SAGE's {@code ProductionUpdate}.
 *
 * <p>A factory/structure carries this module. {@link #queue} charges the owning
 * player up front (failing if they cannot afford it) and enqueues the unit; each
 * frame the head of the queue counts down its build time, and on completion the
 * unit is spawned next to the producer. Build cost and time come from the unit's
 * own {@link ThingTemplate}, mirroring SAGE's {@code BuildCost}/{@code BuildTime}.
 *
 * <p>Research waits in the same queue ({@link #queueResearch}): one thing at a time, in
 * order, charged when queued and refunded when called off ({@link #cancel}) — a unit
 * the same. An upgrade of the side's may be queued once for the whole side and never
 * again once it is done; one of the building's own, once for that building.
 *
 * <p>One thing builds at a time, in queue order — deterministic by construction.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class ProductionUpdate extends UpdateModule {

    /**
     * Per-structure configuration: the unit template names this structure may
     * build — SAGE's command set — and the upgrades it may research.
     * {@code Builds = [ElfArcher, Rider]}, {@code Researches = [Upgrade_Armour]}.
     * An empty list means "no build menu" (scripts can still queue anything).
     */
    public record Data(List<String> builds, List<String> researches) implements ModuleData {
        public Data {
            builds = builds == null ? List.of() : List.copyOf(builds);
            researches = researches == null ? List.of() : List.copyOf(researches);
        }

        public Data(List<String> builds) {
            this(builds, List.of());
        }

        public Data() {
            this(List.of(), List.of());
        }
    }

    /**
     * One thing waiting in the queue, as a reader sees it: a unit or research, and how far along it is — 0 for
     * all but the first, which counts from 0 to 1.
     */
    public record Queued(ThingTemplate unit, Upgrade research, float progress) {

        /** The unit's template name or the upgrade's name. */
        public String name() {
            return unit != null ? unit.name() : research.name();
        }

        public boolean isResearch() {
            return research != null;
        }
    }

    /** Which way finished units step out of the producer. */
    private static final Coord3D EXIT_DIRECTION = new Coord3D(0f, -1f, 0f);

    /** Breathing room between the producer's wall and the unit that just left it. */
    private static final float EXIT_CLEARANCE = 10f;

    /** How far to look for free ground when the doorway is crowded. */
    private static final float EXIT_SEARCH_RADIUS = 60f;

    private static final class Job {
        final ThingTemplate unit;
        final Upgrade research;
        final int frames;
        int framesRemaining;

        Job(ThingTemplate unit, Upgrade research, int frames) {
            this.unit = unit;
            this.research = research;
            this.frames = frames;
            this.framesRemaining = frames;
        }

        int cost() {
            return research != null ? research.cost() : Buildable.costOf(unit);
        }
    }

    private final List<Job> queue = new ArrayList<>();
    private final List<String> builds;
    private final List<String> researches;
    private Coord3D rallyPoint;

    public ProductionUpdate(GameObject owner, Data data) {
        super(owner);
        this.builds = data.builds();
        this.researches = data.researches();
    }

    /** The unit template names this structure offers in its build menu. */
    public List<String> getBuilds() {
        return builds;
    }

    /** Whether this structure's build menu offers {@code templateName}. */
    public boolean canBuild(String templateName) {
        return builds.contains(templateName);
    }

    /** The upgrades this structure may research. */
    public List<String> getResearches() {
        return researches;
    }

    /** Whether this structure may research {@code upgrade}. */
    public boolean canResearch(String upgrade) {
        return researches.contains(upgrade);
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
     * the side may not make it yet ({@link RtsSimulation#canBuild}) or cannot
     * afford it (nothing is queued or charged in either case).
     */
    public boolean queue(ThingTemplate unit) {
        var player = owner();
        if (player == null || getOwner().hasStatus(ObjectStatus.SOLD)
                || getOwner().getWorld() instanceof RtsSimulation rts && !rts.canBuild(player.getIndex(), unit)
                || !player.withdraw(Buildable.costOf(unit))) {
            return false;
        }
        queue.add(new Job(unit, null, Math.max(1, Buildable.framesOf(unit))));
        return true;
    }

    /**
     * Charge the owner and enqueue research of {@code upgrade}. Refused — nothing charged, nothing queued —
     * where it cannot be afforded, where the side already has an upgrade of the side's or has it queued at any
     * building, or where this building already has, or has queued, an upgrade of its own.
     */
    public boolean queueResearch(Upgrade upgrade) {
        var player = owner();
        if (player == null || upgrade == null || getOwner().hasStatus(ObjectStatus.SOLD)) {
            return false;
        }
        boolean taken = upgrade.scope() == Upgrade.Scope.PLAYER
                ? getOwner().getWorld() instanceof RtsSimulation rts
                        && (rts.hasUpgrade(player.getIndex(), upgrade.name())
                                || rts.isUpgradeQueued(player.getIndex(), upgrade.name()))
                : getOwner().hasCondition(upgrade.name()) || isResearching(upgrade.name());
        if (taken || !player.withdraw(upgrade.cost())) {
            return false;
        }
        queue.add(new Job(null, upgrade, Math.max(1, upgrade.frames())));
        return true;
    }

    /** Whether {@code upgrade} is waiting in, or at the head of, this queue. */
    public boolean isResearching(String upgrade) {
        return queue.stream().anyMatch(job -> job.research != null && job.research.name().equals(upgrade));
    }

    /**
     * Call off the {@code index}-th thing queued, the first 0: its cost comes back in full, as the reference
     * refunds, and what was behind it moves up — the next starting from nothing.
     *
     * @return whether there was such a thing
     */
    public boolean cancel(int index) {
        if (index < 0 || index >= queue.size()) {
            return false;
        }
        var job = queue.remove(index);
        var player = owner();
        if (player != null) {
            player.refund(job.cost());
        }
        return true;
    }

    /** Call off everything queued, each paid back in full, as a building being sold does. */
    public void cancelAll() {
        while (!queue.isEmpty()) {
            cancel(queue.size() - 1);
        }
    }

    private RtsPlayer owner() {
        var world = getOwner().getWorld();
        return world == null ? null : RtsPlayer.of(world, getOwner().getPlayerIndex());
    }

    public boolean isProducing() {
        return !queue.isEmpty();
    }

    public int getQueueSize() {
        return queue.size();
    }

    /** How many units queued here the test counts. */
    public int countQueued(java.util.function.Predicate<ThingTemplate> test) {
        int count = 0;
        for (var job : queue) {
            if (job.unit != null && test.test(job.unit)) {
                count++;
            }
        }
        return count;
    }

    /** The units queued, in the order they will be built — research left out; {@link #getEntries} has both. */
    public List<ThingTemplate> getQueue() {
        return queue.stream().filter(job -> job.unit != null).map(job -> job.unit).toList();
    }

    /** Everything queued, units and research, in order, with how far along each is. A copy, to read. */
    public List<Queued> getEntries() {
        var entries = new ArrayList<Queued>(queue.size());
        for (int at = 0; at < queue.size(); at++) {
            var job = queue.get(at);
            float progress = at == 0 ? 1f - job.framesRemaining / (float) job.frames : 0f;
            entries.add(new Queued(job.unit, job.research, progress));
        }
        return entries;
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
        if (queue.isEmpty()) {
            return;
        }
        var head = queue.getFirst();
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
        if (head.research != null) {
            if (world instanceof RtsSimulation rts) {
                rts.upgradeCompleted(owner, head.research);
            }
            return;
        }
        if (world != null) {
            var produced = world.spawn(head.unit, exitPosition(world, owner, head.unit), owner.getPlayerIndex());
            if (rallyPoint != null) {
                var ai = produced.getLocomotor();
                if (ai != null) {
                    ai.moveTo(rallyPoint);
                }
            }
            if (world instanceof RtsSimulation rts) {
                rts.produced(owner, produced);
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
