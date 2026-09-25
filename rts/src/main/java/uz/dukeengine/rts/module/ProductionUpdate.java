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
 *
 * <p>A factory with an {@link Exit} lets what it made out as the reference's do: made inside, walking out through
 * its door, one at a time; one with a {@link Door} makes nothing until the door is open. A finished unit that may
 * not leave yet waits at the head of the queue, complete, and the queue waits with it.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class ProductionUpdate extends UpdateModule {

    /**
     * Per-structure configuration: the unit template names this structure may
     * build — SAGE's command set — and the upgrades it may research.
     * {@code Builds = [ElfArcher, Rider]}, {@code Researches = [Upgrade_Armour]}.
     * An empty list means "no build menu" (scripts can still queue anything).
     * Its {@link Exit} and {@link Door}s, where it has them — {@code Doors = [Door … End, Door … End]}, a job going out
     * by the one its reservation names ({@link ProductionReservation#door}), else the first, as the reference's
     * airfield keeps a door for each parking space; without an exit its units step out of its side. The {@link Words}
     * it holds while it works, where the game names them. {@code RefundsPriceNow}: a job called off gives back the
     * side's price for it at the moment of the cancel, as the reference's does ({@code
     * ProductionUpdate::cancelUnitCreate}, {@code calcCostToBuild}), rather than what was paid for it.
     */
    public record Data(List<String> builds, List<String> researches, Exit exit, List<Door> doors, Words words,
            boolean refundsPriceNow) implements ModuleData {
        public Data {
            builds = builds == null ? List.of() : List.copyOf(builds);
            researches = researches == null ? List.of() : List.copyOf(researches);
            doors = doors == null ? List.of() : List.copyOf(doors);
            words = words == null ? Words.NONE : words;
        }

        /** A factory of one door or none, whose cancel gives back what was paid, as every one did before. */
        public Data(List<String> builds, List<String> researches, Exit exit, Door door, Words words) {
            this(builds, researches, exit, door == null ? List.of() : List.of(door), words, false);
        }

        /** A factory that holds no words while it works, as every one did before it could. */
        public Data(List<String> builds, List<String> researches, Exit exit, Door door) {
            this(builds, researches, exit, door, null);
        }

        /** A factory that lets its units out of its side at once, as every one did before it had an exit. */
        public Data(List<String> builds, List<String> researches) {
            this(builds, researches, null, null);
        }

        public Data(List<String> builds) {
            this(builds, List.of());
        }

        public Data() {
            this(List.of(), List.of());
        }
    }

    /**
     * Where what a factory makes comes out: the reference's {@code UnitCreatePoint}, {@code NaturalRallyPoint},
     * {@code ExitDelay} and {@code InitialBurst} ({@code QueueProductionExitUpdate}). A unit is made at {@code
     * createPoint} and walks first to {@code rallyPoint} — both in the factory's own frame, turned and placed with
     * it, so a factory built facing east lets its units out on its east side — through the factory's own walls, and
     * then on to the rally point the player set, if there is one. It is made facing the way its factory faces.
     * After one leaves, the next waits {@code delay} frames, but for the first {@code burst}, which leave together.
     *
     * <p>{@code CreatePoint = [-10, -30, 0]}, {@code RallyPoint = [53, -30, 0]}. The reference pushes its natural
     * rally point two cells further out from the factory's centre; here the unit walks to the point as written.
     */
    public record Exit(Coord3D createPoint, Coord3D rallyPoint, int delay, int burst) {
        public Exit {
            createPoint = createPoint == null ? new Coord3D(0f, 0f, 0f) : createPoint;
            rallyPoint = rallyPoint == null ? createPoint : rallyPoint;
        }
    }

    /**
     * A factory's door: {@code ProductionUpdate}'s {@code DoorOpeningTime}, {@code DoorWaitOpenTime} and {@code
     * DoorCloseTime}, in frames, and the words the factory holds meanwhile, which the game names and its look is
     * chosen by. A finished unit opens it — {@code opening} for {@code openingFrames} — and is made the frame it is
     * open; it stays {@code open} for {@code openFrames} after the last one left, a unit finishing meanwhile
     * leaving at once, then {@code closing} for {@code closingFrames}, then none of the three. A unit finishing
     * while it closes has it open again at once. A word left out is not held.
     */
    public record Door(int openingFrames, int openFrames, int closingFrames, String opening, String open,
            String closing) {
    }

    /**
     * The words a factory holds while it works, the game's to name and its look's to be chosen by: {@code busy} while
     * anything is in its queue — the reference's {@code ACTIVELY_CONSTRUCTING}, which its cranes move under — and
     * {@code made} for {@code madeFrames} from a unit finished and on its way out ({@code CONSTRUCTION_COMPLETE} for
     * {@code ConstructionCompleteDuration}), not started again while it holds, as the reference's is not. A word left
     * out is not held.
     */
    public record Words(String busy, String made, int madeFrames) {

        /** For a factory that names none. */
        public static final Words NONE = new Words(null, null, 0);
    }

    /**
     * One thing waiting in the queue, as a reader sees it: a unit or research, and how far along it is — 0 for
     * all but the first, which counts from 0 to 1.
     */
    public record Queued(ThingTemplate unit, Upgrade research, float progress, int id) {

        /** An entry that names no job, as every entry did before a job had an id. */
        public Queued(ThingTemplate unit, Upgrade research, float progress) {
            this(unit, research, progress, 0);
        }

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
        final int paid;
        /** Which job it is, at this factory: a cancel of one of several alike names it by this. */
        final int id;
        /** What its factory's reservations gave it as it was queued, module by module. */
        final java.util.Map<ProductionReservation, Object> tokens;
        /** The door it goes out by, the first 0. */
        final int door;
        int framesRemaining;

        Job(ThingTemplate unit, Upgrade research, int frames, int paid, int id,
                java.util.Map<ProductionReservation, Object> tokens, int door) {
            this.unit = unit;
            this.research = research;
            this.frames = frames;
            this.framesRemaining = frames;
            this.paid = paid;
            this.id = id;
            this.tokens = tokens;
            this.door = door;
        }

        /** What a cancel gives back: what was paid, or the side's price for it now where the factory says so. */
        int refund(RtsPlayer player, boolean priceNow) {
            return priceNow && unit != null ? player.priceOf(unit) : paid;
        }
    }

    private final List<Job> queue = new ArrayList<>();
    /** The id the next job queued here is given. */
    private int nextJob = 1;
    private final List<String> builds;
    private final List<String> researches;
    private final Exit exit;
    private final List<Doorway> doorways;
    private final Words words;
    private final boolean refundsPriceNow;
    /** Frames the made word is held for yet; 0 while it is not. */
    private int madeLeft;
    private Coord3D rallyPoint;
    /** Frames before the next unit may leave by the exit. */
    private int exitWait;
    /** How many more may leave with no wait at all. */
    private int burstLeft;

    public ProductionUpdate(GameObject owner, Data data) {
        super(owner);
        this.builds = data.builds();
        this.researches = data.researches();
        this.exit = data.exit();
        this.doorways = data.doors().stream().map(Doorway::new).toList();
        this.words = data.words();
        this.refundsPriceNow = data.refundsPriceNow();
        this.burstLeft = exit == null ? 0 : Math.max(0, exit.burst());
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
     * How its rally point is shown while it is selected: the line through {@code points} and the nodes on it.
     *
     * @param rallyPoint the rally point, where the flag stands
     * @param points     from its door's create point — its own position where it has no exit — through its natural
     *                   rally point and any corners of its footprint to the rally point
     * @param nodes      the natural rally point and the corners, where the reference sets a node
     */
    public record RallyLine(Coord3D rallyPoint, List<Coord3D> points, List<Coord3D> nodes) {
        public RallyLine {
            points = List.copyOf(points);
            nodes = List.copyOf(nodes);
        }
    }

    /**
     * Its rally point as the player sees it while it is selected ({@code W3DWaypointBuffer::drawWaypoints}), or null
     * while it has none. Where the rally point lies behind the door — back toward the factory from its natural rally
     * point — the line first goes round its footprint: by the corner on the door's side nearest the rally point, and by
     * the far side's nearest too when the rally point lies past the first.
     */
    public RallyLine rallyLine() {
        if (rallyPoint == null) {
            return null;
        }
        var owner = getOwner();
        var door = exit == null ? owner.getPosition() : inFrameOf(owner, exit.createPoint());
        var natural = exit == null ? door : inFrameOf(owner, exit.rallyPoint());
        var points = new ArrayList<Coord3D>();
        var nodes = new ArrayList<Coord3D>();
        points.add(door);
        if (!natural.equals(door)) {
            points.add(natural);
            roundTheCorners(owner, door, natural, points, nodes);
        }
        nodes.add(natural);
        points.add(rallyPoint);
        return new RallyLine(rallyPoint, points, nodes);
    }

    private void roundTheCorners(GameObject owner, Coord3D door, Coord3D natural, List<Coord3D> points,
            List<Coord3D> nodes) {
        float outX = natural.x() - door.x();
        float outY = natural.y() - door.y();
        float out = (float) Math.sqrt(outX * outX + outY * outY);
        outX /= out;
        outY /= out;
        if ((natural.x() - rallyPoint.x()) * outX + (natural.y() - rallyPoint.y()) * outY <= 0f) {
            return; // out beyond the door: straight there
        }
        var shape = Solid.of(owner.getTemplate());
        float major = shape instanceof uz.dukeengine.core.thing.Geometry.Box box ? box.majorRadius()
                : shape.footprintRadius();
        float minor = shape instanceof uz.dukeengine.core.thing.Geometry.Box box ? box.minorRadius()
                : shape.footprintRadius();
        float cos = (float) StrictMath.cos(owner.getOrientation());
        float sin = (float) StrictMath.sin(owner.getOrientation());
        var at = owner.getPosition();
        float[][] corners = {
            {at.x() - major * cos - minor * sin, at.y() + minor * cos - major * sin},
            {at.x() + major * cos - minor * sin, at.y() + minor * cos + major * sin},
            {at.x() + major * cos + minor * sin, at.y() - minor * cos + major * sin},
            {at.x() - major * cos + minor * sin, at.y() - minor * cos - major * sin},
        };
        float[] near = null;
        float[] far = null;
        float nearAway = Float.MAX_VALUE;
        float farAway = Float.MAX_VALUE;
        for (var corner : corners) {
            boolean doorSide = (door.x() - corner[0]) * outX + (door.y() - corner[1]) * outY < 0f;
            float awayX = rallyPoint.x() - corner[0];
            float awayY = rallyPoint.y() - corner[1];
            float away = awayX * awayX + awayY * awayY;
            if (doorSide && away < nearAway) {
                near = corner;
                nearAway = away;
            } else if (!doorSide && away < farAway) {
                far = corner;
                farAway = away;
            }
        }
        if (near == null) {
            return;
        }
        var first = new Coord3D(near[0], near[1], at.z());
        points.add(first);
        nodes.add(first);
        // Past the first corner too: the rally point on the far side of it from the natural rally point.
        if (far != null && (near[0] - rallyPoint.x()) * (natural.x() - near[0])
                + (near[1] - rallyPoint.y()) * (natural.y() - near[1]) < 0f) {
            var second = new Coord3D(far[0], far[1], at.z());
            points.add(second);
            nodes.add(second);
        }
    }

    /**
     * Charge the owner and enqueue {@code unit} for production. Returns false if
     * the side may not make it yet ({@link RtsSimulation#canBuild}) or cannot
     * afford it (nothing is queued or charged in either case).
     */
    public boolean queue(ThingTemplate unit) {
        var player = owner();
        if (player == null || getOwner().hasStatus(ObjectStatus.SOLD)
                || getOwner().getWorld() instanceof RtsSimulation rts && !rts.canBuild(player.getIndex(), unit)) {
            return false;
        }
        var tokens = reserve(unit);
        if (tokens == null) {
            return false; // no room for it here: refused before anything is charged
        }
        if (!player.withdraw(player.priceOf(unit))) {
            tokens.forEach(ProductionReservation::release);
            return false;
        }
        queue.add(new Job(unit, null, Math.max(1, Buildable.framesOf(unit)), player.priceOf(unit), nextJob++, tokens,
                doorFor(tokens)));
        return true;
    }

    /** The door the first reservation naming one of this factory's doors names for a job, else the first. */
    private int doorFor(java.util.Map<ProductionReservation, Object> tokens) {
        for (var entry : tokens.entrySet()) {
            int door = entry.getKey().door(entry.getValue());
            if (door >= 0 && door < doorways.size()) {
                return door;
            }
        }
        return 0;
    }

    /**
     * Hold door {@code door} open, the first 0, or let it go — the reference's {@code setHoldDoorOpen}, a parking space
     * keeping its hangar open while it holds a jet: a closed door held starts opening; a door held stays open past its
     * time, and closes once let go.
     */
    public void holdDoorOpen(int door, boolean hold) {
        if (door >= 0 && door < doorways.size()) {
            doorways.get(door).hold(getOwner(), hold);
        }
    }

    /** Every reservation of this factory's asked for {@code unit}: their tokens, or null where one refused. */
    private java.util.Map<ProductionReservation, Object> reserve(ThingTemplate unit) {
        var tokens = new java.util.LinkedHashMap<ProductionReservation, Object>();
        for (var module : getOwner().getModules()) {
            if (module instanceof ProductionReservation reservation) {
                var token = reservation.reserve(unit);
                if (token == null) {
                    tokens.forEach(ProductionReservation::release);
                    return null;
                }
                tokens.put(reservation, token);
            }
        }
        return tokens;
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
        queue.add(new Job(null, upgrade, Math.max(1, upgrade.frames()), upgrade.cost(), nextJob++,
                java.util.Map.of(), 0));
        return true;
    }

    /** Whether {@code upgrade} is waiting in, or at the head of, this queue. */
    public boolean isResearching(String upgrade) {
        return queue.stream().anyMatch(job -> job.research != null && job.research.name().equals(upgrade));
    }

    /**
     * Call off the {@code index}-th thing queued, the first 0: its cost comes back in full, as the reference
     * refunds — what was paid, or the side's price now ({@link Data#refundsPriceNow}) — and what was behind it moves
     * up, the next starting from nothing.
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
            player.refund(job.refund(player, refundsPriceNow));
        }
        job.tokens.forEach(ProductionReservation::release);
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
            entries.add(new Queued(job.unit, job.research, progress, job.id));
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
        // The doors and the exit keep time whether or not anything is being made, as the reference's own modules do.
        for (var doorway : doorways) {
            doorway.step(getOwner());
        }
        if (burstLeft > 0) {
            exitWait = 0;
        } else if (exitWait > 0) {
            exitWait--;
        }
        if (madeLeft > 0 && --madeLeft == 0) {
            getOwner().clearCondition(words.made());
        }
        if (queue.isEmpty()) {
            getOwner().clearCondition(words.busy());
            return;
        }
        getOwner().setCondition(words.busy());
        var head = queue.getFirst();
        var world = getOwner().getWorld();
        if (!gatesAllow()) {
            return; // something attached to this factory is holding the line
        }
        if (head.framesRemaining > 0) {
            head.framesRemaining--;
        }
        if (head.framesRemaining > 0) {
            return;
        }

        var owner = getOwner();
        if (head.research != null) {
            queue.removeFirst();
            if (world instanceof RtsSimulation rts) {
                rts.upgradeCompleted(owner, head.research);
            }
            return;
        }
        if (burstLeft == 0 && exitWait > 0) {
            return; // complete, and waiting at the head of the queue for the way out
        }
        if (words.made() != null && madeLeft == 0) {
            owner.setCondition(words.made()); // finished and on its way out: from the door's first opening
            madeLeft = Math.max(1, words.madeFrames());
        }
        if (!doorways.isEmpty() && !doorways.get(head.door).letOut(owner)) {
            return; // its own door, and no other, opens for it
        }
        queue.removeFirst();
        if (world != null) {
            var produced = exit == null ? outOfTheSide(world, owner, head.unit) : outOfTheDoor(world, owner, head.unit);
            head.tokens.forEach((reservation, token) -> reservation.place(produced, token));
            if (world instanceof RtsSimulation rts) {
                rts.produced(owner, produced);
            }
        }
    }

    /** What a factory with no exit does: the unit beside it, sent to the rally point if there is one. */
    private GameObject outOfTheSide(World world, GameObject owner, ThingTemplate unit) {
        var produced = world.spawn(unit, exitPosition(world, owner, unit), owner.getPlayerIndex(),
                made -> made.setProducer(owner.getId()));
        var ai = produced.getLocomotor();
        if (rallyPoint != null && ai != null) {
            ai.moveTo(rallyPoint);
        }
        return produced;
    }

    /**
     * The reference's {@code exitObjectViaDoor}: made at the create point on the ground, facing its factory's way,
     * and on its way out through the door — on to the rally point, or else to the door's own point once more, as the
     * reference sends it so that two made together do not stand on one another.
     */
    private GameObject outOfTheDoor(World world, GameObject owner, ThingTemplate unit) {
        var made = inFrameOf(owner, exit.createPoint());
        var produced = world.spawn(unit, new Coord3D(made.x(), made.y(), world.groundHeight(made)),
                owner.getPlayerIndex(), thing -> thing.setProducer(owner.getId()));
        produced.setOrientation(owner.getOrientation());
        var door = inFrameOf(owner, exit.rallyPoint());
        var ai = produced.getLocomotor();
        if (ai != null) {
            ai.leave(door, rallyPoint != null ? rallyPoint : door);
        }
        exitWait = exit.delay();
        if (burstLeft > 0) {
            burstLeft--;
        }
        return produced;
    }

    /** A point of the factory's own frame where it stands: turned by its facing about its centre, and moved there. */
    private static Coord3D inFrameOf(GameObject owner, Coord3D point) {
        float cos = (float) StrictMath.cos(owner.getOrientation());
        float sin = (float) StrictMath.sin(owner.getOrientation());
        var at = owner.getPosition();
        return new Coord3D(at.x() + point.x() * cos - point.y() * sin, at.y() + point.x() * sin + point.y() * cos,
                at.z() + point.z());
    }

    /**
     * A factory's door as it stands — {@code ProductionUpdate::updateDoors} — counting down its time in each state
     * and holding that state's word on the factory while it is in it.
     */
    private static final class Doorway {
        private enum State { CLOSED, OPENING, OPEN, CLOSING }

        private final Door door;
        private State state = State.CLOSED;
        private int left;
        /** Whether a module of the factory holds it open: it neither closes nor finishes closing meanwhile. */
        private boolean held;

        Doorway(Door door) {
            this.door = door;
        }

        /** A frame of its time: into the next state once this one's is up — but for one held open. */
        void step(GameObject owner) {
            if (state == State.CLOSED) {
                return;
            }
            if (left > 0) {
                left--;
            }
            if (left > 0) {
                return;
            }
            switch (state) {
                case OPENING -> become(owner, State.OPEN, door.openFrames());
                case OPEN -> {
                    if (!held) {
                        become(owner, State.CLOSING, door.closingFrames());
                    }
                }
                case CLOSING -> {
                    if (!held) {
                        become(owner, State.CLOSED, 0);
                    }
                }
                case CLOSED -> { }
            }
        }

        /** Held open, or let go: a closed door held starts opening, as the reference's does. */
        void hold(GameObject owner, boolean hold) {
            held = hold;
            if (hold && state == State.CLOSED) {
                become(owner, State.OPENING, door.openingFrames());
            }
        }

        /**
         * A finished unit wants out: whether it may go now. A closed door starts opening and it waits; an open one
         * stays open its whole time again; a closing one is open again at once.
         */
        boolean letOut(GameObject owner) {
            switch (state) {
                case CLOSED -> {
                    if (door.openingFrames() > 0) {
                        become(owner, State.OPENING, door.openingFrames());
                        return false;
                    }
                    become(owner, State.OPEN, door.openFrames());
                }
                case OPENING -> {
                    return false;
                }
                case OPEN, CLOSING -> become(owner, State.OPEN, door.openFrames());
            }
            return true;
        }

        private void become(GameObject owner, State next, int frames) {
            hold(owner, wordOf(state), false);
            state = next;
            left = frames;
            hold(owner, wordOf(state), true);
        }

        private static void hold(GameObject owner, String word, boolean held) {
            if (word == null) {
                return;
            }
            if (held) {
                owner.setCondition(word);
            } else {
                owner.clearCondition(word);
            }
        }

        private String wordOf(State of) {
            return switch (of) {
                case CLOSED -> null;
                case OPENING -> door.opening();
                case OPEN -> door.open();
                case CLOSING -> door.closing();
            };
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
