package uz.dukeengine.core.module;

import java.util.List;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;

/**
 * Drives an object toward a goal position each frame, ported in spirit from
 * SAGE's {@code AIUpdateInterface} + {@code Locomotor}.
 *
 * <p>This is the consumer end of a {@code MoveTo} command: set a goal with
 * {@link #moveTo}, and each logic frame the unit steps toward it at its
 * configured speed, snapping to the goal and stopping once within one step.
 * Speed is authored in world-units-per-second and converted to a per-frame step
 * using the fixed logic rate, so movement is identical regardless of render fps.
 *
 * <p>Buildings and whatever else cannot move are solid: before every step the
 * locomotor asks the world whether the space it is about to occupy is free
 * ({@link World#findBlocker}), and steers around what is in the way. Other ground
 * movers are not shoved or swerved round: a mover keeps a block of the ground's
 * cells of its own ({@link World#takePlace}), is held up only by one it drives
 * into, goes no faster than that one draws away, plans again round it when it is
 * stuck, and steps aside for one of higher priority — the reference's {@code
 * AIUpdateInterface::processCollision} and {@code blockedBy}. Legs pass legs.
 * Objects with no {@link uz.dukeengine.core.thing.Geometry} pass through each
 * other exactly as before.
 *
 * <p>How it gathers and sheds speed and turns is its {@link Gait}'s, as the
 * reference's locomotors move by their appearance ({@code Locomotor.cpp}): legs,
 * treads and wheels each turn and slow their own way, and a mover whose data
 * names none moves as the engine always moved things.
 *
 * <p>Determinism: trigonometry goes through {@link StrictMath}, not
 * {@link Math}. {@code Math.sin}/{@code cos}/{@code atan2} are only required to
 * land within 1 ulp and are free to use platform intrinsics, so two peers can
 * disagree in the last bit — which is a desync. {@code StrictMath} is defined to
 * produce the same bits everywhere.
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class MoveUpdate extends UpdateModule implements Locomotor {

    /**
     * How a ground mover moves — the reference's locomotor appearance ({@code LOCO_LEGS_TWO}, {@code LOCO_TREADS},
     * {@code LOCO_WHEELS_FOUR}, {@code LOCO_OTHER}) — which chooses how it turns and how it slows.
     */
    public enum Gait {
        /**
         * On foot ({@code moveTowardsPositionLegs}): turns toward its way as it goes, aiming at its speed less the
         * share of 45 degrees it is off — so on the spot 45 degrees or more off — and nearing the end eases to its least
         * speed within {@code (v - MinSpeed)^2 / (2 × Braking) × 1.05}.
         */
        LEGS,
        /**
         * Tracks ({@code moveTowardsPositionTreads}): turns and gathers speed as legs do, goes at 0.6 of its speed off
         * its way within two cells of a point, and brakes within {@code (v / 1.5) × (v / Braking)} of the end, then
         * slides straight onto it.
         */
        TREADS,
        /**
         * Wheels ({@code moveTowardsPositionWheels}): turns only while it rolls, by its turn rate times its speed over
         * its turning speed — {@code MinTurnSpeed} or a quarter of its speed, whichever is more — slowing to that speed
         * more than 9 degrees off; brakes within {@code (v / 1.5) × (v / Braking + 1 frame) + 1 frame's travel}, at
         * least a cell, of the end, then slides onto it; and, where it may move backwards, backs toward a point behind
         * it, or turns in three points toward one more than five half-lengths away.
         */
        WHEELS,
        /**
         * As the engine always moved things: straight at its way, curving at its turn rate, and turning on the spot only
         * toward a point more than 90 degrees off.
         */
        OTHER
    }

    /**
     * Where in a group walking a shared route a mover goes: the reference's locomotor {@code MovePriority}. A group's
     * infantry end their columns 10 further back for each step from the front.
     */
    public enum MovePriority {
        BACK, MIDDLE, FRONT
    }

    /**
     * How a ground mover moves: the reference's locomotor lines. Every rate is per second, a frame being a thirtieth of
     * one; 0 for an acceleration, a braking or a turn rate is at once.
     *
     * @param speed               its top speed, world units a second
     * @param turnRate            degrees a second it turns; 0 is at once
     * @param acceleration        how fast it gains speed, world units a second each second ({@code Acceleration})
     * @param braking             how fast it sheds speed, the same ({@code Braking})
     * @param accelerationDamaged its acceleration once damaged ({@code AccelerationDamaged}); 0 is its acceleration
     * @param brakingDamaged      its braking once damaged; 0 is its braking
     * @param damagedBelow        the share of its most health below which it is damaged — the reference's
     *                            {@code MovementPenaltyDamageState}, really damaged, under 0.1
     * @param minSpeed            the least speed legs ease to nearing the end ({@code MinSpeed})
     * @param minTurnSpeed        the speed wheels turn at, where more than a quarter of its speed ({@code MinTurnSpeed})
     * @param closeEnough         how near the end of its route counts as arrived ({@code CloseEnoughDist}): 1 unless set
     * @param canMoveBackwards    whether wheels may back toward a point behind them ({@code CanMoveBackwards})
     * @param gait                how it moves — see {@link Gait}
     * @param pathPriority        which of two movers stuck on each other steps aside: the lower — the reference's
     *                            dozers first, which a game marks with a higher number; of two alike, one on wheels or
     *                            treads before one on legs ({@code AIUpdateInterface::hasHigherPathPriority})
     * @param movePriority        where in a group's columns it goes — see {@link MovePriority}; the front unless set
     */
    public record Data(float speed, float turnRate, float acceleration, float braking, float accelerationDamaged,
            float brakingDamaged, float damagedBelow, float minSpeed, float minTurnSpeed, float closeEnough,
            boolean canMoveBackwards, Gait gait, int pathPriority, MovePriority movePriority) implements ModuleData {

        /** What a block leaves out: at once, arriving within 1, damaged under a tenth, moving as things always moved. */
        static final Data DEFAULTS = new Data(0f, 0f, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, Gait.OTHER, 0,
                MovePriority.FRONT);

        public Data {
            gait = gait == null ? Gait.OTHER : gait;
            closeEnough = closeEnough <= 0f ? 1f : closeEnough;
            movePriority = movePriority == null ? MovePriority.FRONT : movePriority;
        }

        public Data(float speed) {
            this(speed, 0f);
        }

        /** A top speed and a turn rate; everything else as a block leaves it. */
        public Data(float speed, float turnRate) {
            this(speed, turnRate, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, Gait.OTHER, 0, MovePriority.FRONT);
        }

        /** Everything but a path priority, which is then none. */
        public Data(float speed, float turnRate, float acceleration, float braking, float accelerationDamaged,
                float brakingDamaged, float damagedBelow, float minSpeed, float minTurnSpeed, float closeEnough,
                boolean canMoveBackwards, Gait gait) {
            this(speed, turnRate, acceleration, braking, accelerationDamaged, brakingDamaged, damagedBelow, minSpeed,
                    minTurnSpeed, closeEnough, canMoveBackwards, gait, 0);
        }

        /** Everything but a move priority, which is then the front. */
        public Data(float speed, float turnRate, float acceleration, float braking, float accelerationDamaged,
                float brakingDamaged, float damagedBelow, float minSpeed, float minTurnSpeed, float closeEnough,
                boolean canMoveBackwards, Gait gait, int pathPriority) {
            this(speed, turnRate, acceleration, braking, accelerationDamaged, brakingDamaged, damagedBelow, minSpeed,
                    minTurnSpeed, closeEnough, canMoveBackwards, gait, pathPriority, MovePriority.FRONT);
        }
    }

    /**
     * Directions tried when the way ahead is blocked: straight on first, then
     * progressively wider swerves to each side. The order is fixed so every peer
     * picks the same way round an obstacle.
     */
    private static final float[] SWERVE_ANGLES = {
        0f,
        (float) Math.toRadians(45), (float) Math.toRadians(-45),
        (float) Math.toRadians(90), (float) Math.toRadians(-90),
    };

    /** Give up on a leg after this long without getting any closer to it. */
    private static final int STUCK_FRAME_LIMIT = 2 * GameConstants.LOGICFRAMES_PER_SECOND;

    private final Data data;
    private float topSpeed;           // world units a second
    private float stepPerFrame;
    private float turnPerFrame; // radians/frame; 0 = instant turning
    private float progressEpsilon;
    /** How fast it is going now, world units a second: gathered and shed at its data's rates. */
    private float speedNow;
    /** Whether treads or wheels are braking onto the end of their way, which they then slide straight onto. */
    private boolean brakingOnto;
    /** Whether wheels are backing toward a point behind them, and whether in a three-point turn. */
    private boolean backing;
    private boolean threePoint;
    /** How far off its way it was last frame, in radians, to tell a turn toward it from standing still. */
    private float lastOff = Float.MAX_VALUE;
    private List<Coord3D> waypoints = List.of();
    /** The route the waypoints came from, for the floor each is on; null for a way given rather than planned. */
    private uz.dukeengine.core.pathfind.Path route;
    private int waypointIndex;
    private Coord3D destination;      // where it was told to go, as opposed to the next corner
    private int navigationVersion;    // the world's shape when this route was planned
    private float closestApproach;
    private int framesWithoutProgress;
    /** Whether the route it follows ends at {@link #destination}, rather than as near to it as it gets. */
    private boolean goalReachable = true;
    /** Whether its last move ended short of where it was sent — see {@link #stoppedShort}. */
    private boolean stoppedShort;
    /** Whether a route that stopped short has had its one fresh look from where it ended. */
    private boolean lookedAgain;
    /**
     * Whether it asked for a route while the frame's searching was spent, and waits its turn: it keeps walking the
     * route it had, or stands, and asks again each frame until it is given one.
     */
    private boolean waiting;
    /** Where it goes on to by a route once it has walked the way it was given — see {@link #leave}. */
    private Coord3D then;
    /** The points it has yet to go through, and the place it holds at their end — see {@link #moveThrough}. */
    private List<Coord3D> through = List.of();
    private Coord3D throughTo;

    // ---- the ground's cells ----
    /** Whether it is going to a place — a block of the ground's cells of its own — rather than into something. */
    private boolean toPlace;
    /** The place it was sent to, before the block round it it was given. */
    private Coord3D sentTo;
    /** Where it stood when this frame began, and how far it went the frame before. */
    private Coord3D lastPosition;
    private float lastStep;

    // ---- giving way ----
    /** How many frames running another mover has held it up: the reference's {@code m_blockedFrames}. */
    private int heldFrames;
    /** The speed it is kept under after being held, world units a second: the reference's {@code m_bumpSpeedLimit}. */
    private float bumpLimit = Float.MAX_VALUE;
    /** The frame it last asked for a route, and — asked too soon after it — the frame it asks again. */
    private int lastRouteFrame = Integer.MIN_VALUE / 2;
    private int routeAgainAt = -1;
    /** The movers it plans its next route round, as though they were stone: those it is stuck behind. */
    private java.util.Set<uz.dukeengine.core.thing.ObjectId> round = java.util.Set.of();
    /** Until which frame it passes through other movers: stepping aside with nowhere to step. */
    private int passThroughUntil = -1;
    /** Until which frame its step aside may last before it gives it up where it stands. */
    private int asideUntil = -1;
    /** Whether it is planning a route now: not asked aside by the allies its own route asks, round and round. */
    private boolean planning;
    /** How many frames running a box's turn has waited on another's footprint. */
    private int turnWaits;
    /** The movers it last planned a route round, and where it stood then. */
    private java.util.Set<uz.dukeengine.core.thing.ObjectId> roundLast = java.util.Set.of();
    private Coord3D roundFrom;

    public MoveUpdate(GameObject owner, Data data) {
        super(owner);
        this.data = data;
        setSpeed(data.speed(), data.turnRate());
    }

    @Override
    public void setSpeed(float speed, float turnRate) {
        topSpeed = speed;
        stepPerFrame = speed * GameConstants.SECONDS_PER_LOGICFRAME;
        turnPerFrame = (float) Math.toRadians(turnRate * GameConstants.SECONDS_PER_LOGICFRAME);
        progressEpsilon = stepPerFrame * 0.25f;
    }

    /**
     * Order the unit to move to {@code destination}, routing around terrain via
     * the world's pathfinder. Falls back to a straight line when there is no
     * world or no navigation grid.
     *
     * <p>Somewhere it cannot reach, it goes as near as it can instead of standing
     * where it is — the reference game's pathfinder does the same — and says so
     * once it gets there: {@link #stoppedShort}.
     */
    public void moveTo(Coord3D destination) {
        var world = getOwner().getWorld();
        sentTo = destination;
        head(world == null ? destination : world.takePlace(getOwner(), destination), true);
    }

    /**
     * Go exactly to {@code destination}, holding no block of the ground's cells there: a move into something —
     * entering it, docking at it, closing on it — which the reference leaves where it is ({@code
     * setAdjustsDestination(false)}).
     */
    @Override
    public void moveExactlyTo(Coord3D destination) {
        var world = getOwner().getWorld();
        if (world != null) {
            world.letPlaceGo(getOwner());
        }
        sentTo = destination;
        head(destination, false);
    }

    /**
     * Through the points of {@code way} in turn, exactly — those within a cell of where it stands passed over — then to
     * the block round {@code place} it takes now and holds as its own on the way ({@code AIFollowPathState}).
     */
    @Override
    public void moveThrough(List<Coord3D> way, Coord3D place) {
        var world = getOwner().getWorld();
        var goal = world == null ? place : world.takePlace(getOwner(), place);
        sentTo = place;
        goThrough(way, goal);
    }

    private void goThrough(List<Coord3D> way, Coord3D goal) {
        var world = getOwner().getWorld();
        float cell = world == null ? 0f : world.cellSize();
        int next = 0;
        while (next < way.size() && across(way.get(next), getOwner().getPosition()) < cell) {
            next++;
        }
        if (next == way.size()) {
            head(goal, true);
            return;
        }
        head(way.get(next), false);
        through = List.copyOf(way.subList(next + 1, way.size()));
        throughTo = goal;
    }

    private void head(Coord3D destination, boolean place) {
        this.then = null;
        this.through = List.of();
        this.throughTo = null;
        this.destination = destination;
        this.toPlace = place;
        this.stoppedShort = false;
        this.lookedAgain = false;
        this.asideUntil = -1;
        this.round = java.util.Set.of();
        this.roundLast = java.util.Set.of();
        planRoute();
        nowhereNearer();
    }

    /**
     * The reference's {@code aiFollowExitProductionPath}: straight to {@code way} — its maker's door, through its
     * maker's own walls, which no route could be planned out of and none is needed through, since a mover standing
     * in something is let walk out of it ({@link #isBlocked}) — then on to {@code destination} by a route. Where the
     * way makes no headway, it goes on from wherever it got to.
     */
    @Override
    public void leave(Coord3D way, Coord3D destination) {
        stop();
        var world = getOwner().getWorld();
        if (world != null) {
            world.letPlaceGo(getOwner()); // the first leg is through its maker's walls: nowhere of its own yet
        }
        this.toPlace = false;
        this.destination = way;
        this.then = destination;
        this.waypoints = List.of(way);
        this.route = null;
        this.goalReachable = true;
        this.lookedAgain = false;
        this.navigationVersion = world == null ? 0 : world.getNavigationVersion();
    }

    /** On from the way it was given to where it was going, if it was given one — see {@link #leave}. */
    private boolean goOn() {
        if (throughTo != null) {
            goThrough(through, throughTo);
            return true;
        }
        if (then == null) {
            return false;
        }
        moveTo(then);
        return true;
    }

    /** A route given that leads nowhere nearer than where it stands: that is already as near as it gets. */
    private void nowhereNearer() {
        if (!waiting && !isMoving() && !goalReachable) {
            lookedAgain = true;
            stoppedShort = true;
        }
    }

    /** Work out the way to {@link #destination} from wherever the owner stands now. */
    private void planRoute() {
        var world = getOwner().getWorld();
        if (world == null) {
            this.waypoints = List.of(destination);
            this.route = null;
            this.goalReachable = true;
        } else {
            if (toPlace && sentTo != null && then == null && asideUntil < 0 && !world.holdsPlace(getOwner())) {
                // An ally has claimed its block since: the nearest block it may have instead.
                destination = world.takePlace(getOwner(), sentTo);
            }
            if (!round.isEmpty()) {
                var here = getOwner().getPosition();
                if (round.equals(roundLast) && roundFrom != null && across(here, roundFrom) < PROBE) {
                    // Round the same ones again and not a step further for the last route round them: blocked and
                    // stuck, it walks through them a while, as the reference lets such a unit path through units.
                    passThroughUntil = world.getFrame() + STUCK_FRAMES;
                }
                roundLast = round;
                roundFrom = here;
            }
            // Asked for this owner, so the route allows for its width and comes
            // back straightened rather than as a walk of cell centres — and, where
            // there is no way there, as a route to the nearest place there is one.
            var path = round.isEmpty() ? world.findPath(getOwner(), destination)
                    : world.findPath(getOwner(), destination, round);
            if (path == null) {
                waiting = true; // the frame's searching is spent: the old route, or standing, until its turn
                return;
            }
            waiting = false;
            lastRouteFrame = world.getFrame();
            this.waypoints = path.getWaypoints();
            this.route = path;
            this.goalReachable = path.reachesGoal();
            this.navigationVersion = world.getNavigationVersion();
            this.waypointIndex = 0;
            planning = true;
            try {
                askAlliesAside(world);
            } finally {
                planning = false;
            }
        }
        this.waypointIndex = 0;
        heldFrames = 0;
        resetProgress();
    }

    /**
     * The idle allies standing still on the ground its route covers — not moving, not busy — asked to step aside off
     * it, as the reference's {@code Pathfinder::moveAllies} asks them once a route through them is found.
     */
    private void askAlliesAside(World world) {
        var owner = getOwner();
        if (!world.keepsCells(owner) || waypoints.isEmpty()) {
            return;
        }
        var way = new java.util.ArrayList<Coord3D>(waypoints.size() + 1);
        way.add(owner.getPosition());
        way.addAll(waypoints);
        for (var other : world.stillAlliesOn(owner, way)) {
            if (idle(other)) {
                other.findModule(MoveUpdate.class).stepAsideFor(owner, way);
            }
        }
    }

    /**
     * It has walked its route to the end. For a route that stops short of where it was sent, that is as near
     * as it gets — after one fresh look from where it now stands, which is what takes a mover the rest of the
     * way once it has stepped out of a wall it was standing in, or a gate has opened.
     */
    private void routeWalked() {
        if (goOn()) {
            return;
        }
        if (goalReachable || destination == null) {
            holdWhereItStands(); // arrived
            return;
        }
        if (!lookedAgain) {
            lookedAgain = true;
            planRoute();
            if (isMoving()) {
                return;
            }
        }
        stoppedShort = true;
        holdWhereItStands();
    }

    /**
     * Stopped: it keeps its block where it is within a cell of it, and otherwise holds the block it stands on instead —
     * as does one that went into something rather than to a place.
     */
    private void holdWhereItStands() {
        var world = getOwner().getWorld();
        if (world == null || !world.keepsCells(getOwner())) {
            return;
        }
        var at = getOwner().getPosition();
        if (!toPlace || destination == null || across(at, destination) > world.cellSize()) {
            world.holdPlace(getOwner());
        }
    }

    /**
     * Whether its last move ended somewhere other than where it was sent: there was no way there and it went
     * as near as it could, or it stopped getting any nearer. What tells an errand "got as close as it could"
     * from "arrived". False while it is moving, after it arrives, and after an order to stop.
     */
    public boolean stoppedShort() {
        return stoppedShort;
    }

    /**
     * Whether the route it is following ends where it was sent; false when there is no way there and it is
     * going as near as it can instead.
     */
    public boolean isGoalReachable() {
        return goalReachable;
    }

    /** Cancel any current move: it holds the block it stands on from now. */
    public void stop() {
        this.speedNow = 0f;
        this.brakingOnto = false;
        this.backing = false;
        this.then = null;
        this.through = List.of();
        this.throughTo = null;
        this.waiting = false;
        this.waypoints = List.of();
        this.route = null;
        this.waypointIndex = 0;
        this.destination = null;
        this.stoppedShort = false;
        this.heldFrames = 0;
        this.asideUntil = -1;
        resetProgress();
        this.toPlace = false;
        holdWhereItStands();
    }

    private void resetProgress() {
        this.closestApproach = Float.MAX_VALUE;
        this.framesWithoutProgress = 0;
        this.lastOff = Float.MAX_VALUE;
    }

    /** How fast it is going now, world units a second. */
    public float getSpeedNow() {
        return speedNow;
    }

    public boolean isMoving() {
        return waiting || waypointIndex < waypoints.size();
    }

    /** Whether it is waiting its turn for a route; see {@link #waiting}. */
    public boolean isWaitingForRoute() {
        return waiting;
    }

    /**
     * Where the unit was ordered to go, or {@code null} if it has no orders: the place it was sent to, as its order
     * named it — not the block round it that it stops on ({@link #getDestination}) — so an errand can tell its own
     * order from another given since.
     */
    public Coord3D getGoal() {
        if (destination == null) {
            return null;
        }
        return (toPlace || throughTo != null) && sentTo != null ? sentTo : destination;
    }

    /**
     * Where it is walking to now, or {@code null}: for a move to a place, the block round it that it holds and stops on;
     * a corner of the way it was given; the place it steps aside to; otherwise its goal itself.
     */
    public Coord3D getDestination() {
        return destination;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        var here = owner.getPosition();
        lastStep = lastPosition == null ? 0f : across(here, lastPosition);
        lastPosition = here;
        if (world != null) {
            world.markStanding(owner);
        }
        if (!isMoving()) {
            speedNow = 0f;
            heldFrames = 0;
            standStill(owner, world);
            return;
        }
        if (owner.isEffectivelyDead() || owner.isContained() || owner.hasStatus(ObjectStatus.DISABLED)
                || owner.hasStatus(ObjectStatus.HELD)) {
            speedNow = 0f;
            return; // dead, inside a transport, frozen or held — cannot move, and keeps its orders for when it can
        }
        if (asideUntil >= 0 && world != null && world.getFrame() > asideUntil) {
            stop(); // ten seconds stepping aside is enough: it stays where it got to
            return;
        }
        if (routeAgainAt >= 0 && world != null && world.getFrame() >= routeAgainAt) {
            routeAgainAt = -1;
            planRoute(); // its wait for a route asked too soon is over
        }
        boolean slowed = owner.hasStatus(ObjectStatus.SLOWED);
        float step = slowed ? stepPerFrame * 0.5f : stepPerFrame;

        if (waiting) {
            planRoute(); // its turn, if this frame's searching has room for it
            if (!waiting && !isMoving()) {
                nowhereNearer();
                routeWalked();
                return;
            }
        }
        if (waypointIndex >= waypoints.size()) {
            return; // still waiting, and no old route to walk meanwhile
        }

        if (then == null && routeIsStale(owner)) { // a way it was given is walked as given
            planRoute(); // something was built or destroyed across the way — think again
            if (!isMoving()) {
                routeWalked(); // no way nearer from here
                return;
            }
        }

        var position = owner.getPosition();
        var target = waypoints.get(waypointIndex);
        var delta = target.sub(position);
        // On the ground plane, because that is where walking happens: a step is
        // taken in x and y and the height is read off the floor afterwards. Once
        // the world had storeys in it, a waypoint one floor up stayed a storey
        // away however close the mover got — so it never arrived, shuffled about
        // on the spot, and was eventually stopped by the stuck check two seconds
        // later. That is what the shivering on the top step was.
        float distance = (float) Math.sqrt(delta.x() * delta.x() + delta.y() * delta.y());

        float desired = (float) StrictMath.atan2(delta.y(), delta.x());
        float rest = distance + legsAfter(waypointIndex);
        float allowed = giveWay(owner, world, desired, slowed ? topSpeed * 0.5f : topSpeed);
        if (!isMoving()) {
            return; // it stepped aside, or planned again and has nowhere to go
        }
        if (data.gait() != Gait.OTHER) {
            walkByGait(owner, position, target, distance, desired, rest, allowed);
            return;
        }
        step = Math.min(step, allowed * GameConstants.SECONDS_PER_LOGICFRAME);
        if (data.acceleration() > 0f || data.braking() > 0f) {
            speedNow = approach(speedNow, (slowed ? topSpeed * 0.5f : topSpeed), acceleration(owner), 0f);
            step = Math.min(step, speedNow * GameConstants.SECONDS_PER_LOGICFRAME);
        }
        if (turnPerFrame > 0f && distance > step) {
            float off = Math.abs(angleBetween(owner.getOrientation(), desired));
            if (off > HALF_TURN / 2f) {
                // A mover that only drives forward cannot reach a point inside its own turning circle, and a
                // point behind it always is. Driving on at full speed it circles the point, and the progress
                // check gives up on it: a dozer sent nine units behind itself drove 36 on, turned, came back,
                // and was stopped 27 short. So it turns where it stands until the point is ahead of it. Each
                // such frame turns it a full turn's worth toward the point, so it cannot last more than half a
                // circle — and it is not counted against its progress, because it is progress.
                owner.setOrientation(rotateToward(owner.getOrientation(), desired, turnPerFrame));
                return;
            }
            // Ahead, but perhaps inside the circle a full-speed turn draws: then slow until the turn it can
            // make reaches it, rather than orbiting it.
            step = Math.min(step, tightestStep(distance, off));
        }

        if (madeNoProgress(distance)) {
            if (goOn()) {
                return; // something stands in the doorway: on from here by a route
            }
            stop(); // as close as it is ever going to get — stop rather than circle forever
            stoppedShort = true;
            return;
        }

        // A unit that spawned on top of something is already overlapping; let it
        // walk free rather than lock it in place forever.
        int toward = route == null ? -1 : route.floorOf(waypointIndex); // the floor the next waypoint is on
        boolean escaping = isBlocked(owner, position, toward);

        if (distance <= step || distance == 0f) {
            reach(owner, target, toward, escaping);
            return;
        }
        if (arrivedCloseEnough(rest)) {
            return;
        }

        float facing = turnPerFrame <= 0f
                ? desired // instant turning: head straight for the waypoint
                : rotateToward(owner.getOrientation(), desired, turnPerFrame);
        turn(owner, facing);
        facing = owner.getOrientation();

        if (!escaping && standingOnTheDestination(owner, position.add(headingVector(facing).scale(step)))) {
            stop(); // pressed against the thing we were sent to — this is arrival
            return;
        }
        stepAlong(owner, position, facing, step, toward, escaping);
    }

    /**
     * A step along {@code facing}, or — where something solid is in the way — along the first of the swerves that is
     * clear. Another ground mover in the way is not swerved round: the step waits, and the giving way sorts it out.
     */
    private void stepAlong(GameObject owner, Coord3D position, float facing, float step, int toward,
            boolean escaping) {
        if (step <= 0f) {
            return;
        }
        var straight = position.add(headingVector(facing).scale(step));
        if (canStep(owner, position, straight, toward, escaping)) {
            stepTo(owner, straight, toward);
            return;
        }
        if (keepsCells(owner) && !solidInTheWay(owner, straight, toward)) {
            return; // a mover in the way: it waits, and is held, not steered round
        }
        for (var swerve : SWERVE_ANGLES) {
            var next = position.add(headingVector(facing + swerve).scale(step));
            if (canStep(owner, position, next, toward, escaping)) {
                stepTo(owner, next, toward);
                return;
            }
        }
        // Hemmed in on every side: hold position and let the progress check time it out.
    }

    /** Onto the waypoint it is a step from, where the ground there takes it, and on to the next leg or the end. */
    private void reach(GameObject owner, Coord3D target, int toward, boolean escaping) {
        if (canStep(owner, owner.getPosition(), target, toward, escaping)) {
            stepTo(owner, target, toward);
            waypointIndex++; // advance to the next leg (or finish the path)
            resetProgress();
            if (!isMoving()) {
                speedNow = 0f;
                brakingOnto = false;
                routeWalked();
            }
        }
    }

    /**
     * Arrived: on its last leg, the rest of its way shorter than its close-enough distance — the reference's {@code
     * onPathDistToGoal < getCloseEnoughDist()}. It stops where it stands.
     */
    private boolean arrivedCloseEnough(float rest) {
        if (waypointIndex != waypoints.size() - 1 || rest >= data.closeEnough()) {
            return false;
        }
        waypointIndex = waypoints.size();
        speedNow = 0f;
        brakingOnto = false;
        resetProgress();
        routeWalked();
        return true;
    }

    /** How far its route goes on past waypoint {@code index}: the legs between the waypoints after it. */
    private float legsAfter(int index) {
        float length = 0f;
        for (int i = index + 1; i < waypoints.size(); i++) {
            var a = waypoints.get(i - 1);
            var b = waypoints.get(i);
            length += (float) Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + (b.y() - a.y()) * (b.y() - a.y()));
        }
        return length;
    }

    // ---- standing still ----

    /** How often, in frames, a still mover looks whether it may keep the ground it stands on. */
    private static final int STILL_LOOK_FRAMES = 8;

    /**
     * Still: it holds the block it stands on, as a mover does once it stops; and where it may not — another still one
     * less than half a cell away holds it, an ally's claim, an enemy it cannot drive over — it goes to the nearest block
     * it may have, unless it is busy: the reference's {@code AIUpdateInterface::processCollision} for two units on top of
     * each other.
     */
    private void standStill(GameObject owner, World world) {
        if (world == null || !world.keepsCells(owner)
                || (world.getFrame() + owner.getId().value()) % STILL_LOOK_FRAMES != 0 || world.holdsPlace(owner)) {
            return;
        }
        if (!world.holdPlace(owner) && !owner.isBusy()) {
            moveTo(owner.getPosition());
        }
    }

    // ---- giving way ----

    /** The reference's {@code MIN_REPATH_TIME}-like rule: a route asked for within 3 frames of the last waits a second. */
    private static final int TOO_SOON_FRAMES = 3;
    /** Held this long, it plans again round what holds it: two seconds. */
    private static final int STUCK_FRAMES = 2 * GameConstants.LOGICFRAMES_PER_SECOND;
    /** Facing its way within this, it is not turning: the reference's {@code PI / 30}, 6 degrees. */
    private static final float FACING_ITS_WAY = (float) (StrictMath.PI / 30.0);
    /** How long a step aside may take, or passing through movers with nowhere to step aside to: 10 seconds. */
    private static final int ASIDE_FRAMES = 10 * GameConstants.LOGICFRAMES_PER_SECOND;
    /** How far ahead of where it stands a mover looks for the one it would drive into, at least. */
    private static final float PROBE = 1f;

    /**
     * The fastest it may go this frame for the ground movers it would drive into — {@code blockedBy}, {@code
     * calculateMaxBlockedSpeed} and {@code doLocomotor}'s bump limit: held, no faster than the one in front draws away,
     * the limit falling 5% a frame, and growing back 5% a frame from a fifth of its speed once it is not. Held two
     * seconds, or at once behind one standing still while facing its way, it plans again round them; stuck on each
     * other, the one of lower priority steps aside; held by one on legs, a mover on wheels or treads has it step aside.
     */
    private float giveWay(GameObject owner, World world, float desired, float top) {
        if (world == null || !world.keepsCells(owner) || world.getFrame() < passThroughUntil) {
            heldFrames = 0;
            return top;
        }
        float probe = Math.max(Math.max(PROBE, stepPerFrame), turningRoom(owner));
        var ahead = owner.getPosition().add(headingVector(owner.getOrientation()).scale(probe));
        var mine = uz.dukeengine.core.thing.Footprint.of(owner, ahead);
        float reach = uz.dukeengine.core.thing.Solid.of(owner.getTemplate()).footprintRadius() + probe + OTHERS_REACH;
        float limit = Float.MAX_VALUE;
        var holders = new java.util.ArrayList<GameObject>();
        for (var other : world.objectsInRange(owner.getPosition(), reach,
                candidate -> candidate != owner && isGroundMover(candidate))) {
            if (!mine.overlaps(uz.dukeengine.core.thing.Footprint.of(other)) || !heldBy(owner, world, other)) {
                continue;
            }
            holders.add(other);
            limit = Math.min(limit, blockedSpeed(owner, other));
        }
        boolean held = !holders.isEmpty();
        float speed = top;
        if (held && speed > limit) {
            speed = limit;
            bumpLimit = Math.min(bumpLimit, speed) * 0.95f;
            speed = bumpLimit;
            heldFrames++;
        } else {
            held = false;
            heldFrames = 0;
            if (bumpLimit < Float.MAX_VALUE) {
                bumpLimit = Math.max(bumpLimit, top * 0.2f) * 1.05f;
                if (bumpLimit >= top) {
                    bumpLimit = Float.MAX_VALUE;
                }
            }
            speed = Math.min(speed, bumpLimit);
        }
        if (held) {
            resetProgress(); // held, it is not lost: the giving way sorts it out, and plans again if it must
            sortOutTheHold(owner, world, holders, desired);
        }
        return speed;
    }

    /**
     * How far ahead a box looks for the one it would drive into: far enough to stop with room to turn where it stands —
     * its corners reach out to its bounding circle, a little past its front.
     */
    private static float turningRoom(GameObject owner) {
        var shape = uz.dukeengine.core.thing.Solid.of(owner.getTemplate());
        return shape instanceof uz.dukeengine.core.thing.Geometry.Box box
                ? shape.footprintRadius() - box.majorRadius() + PROBE : 0f;
    }

    /** How far another mover's middle may be from its outline, at most, for the one looking for those it touches. */
    private static final float OTHERS_REACH = 60f;

    /** Held: who steps aside, and whether it plans again round them. */
    private void sortOutTheHold(GameObject owner, World world, List<GameObject> holders, float desired) {
        boolean facingItsWay = Math.abs(angleBetween(owner.getOrientation(), desired)) <= FACING_ITS_WAY;
        boolean stuckBehindStill = false;
        for (var other : holders) {
            var theirs = other.findModule(MoveUpdate.class);
            boolean otherMoving = theirs.isMoving();
            if (!walksOnLegs(owner) && walksOnLegs(other) && !movingAwayFrom(other, owner) && !other.isBusy()) {
                theirs.stepAsideFor(owner, wayAheadOf(owner)); // a vehicle held by infantry: the infantry steps aside
                continue;
            }
            if (!otherMoving) {
                stuckBehindStill |= facingItsWay;
                continue;
            }
            if (facingItsWay && theirs.heldBy(other, world, owner) && !theirs.needsToTurn(other)
                    && !higherPriority(owner, other)) {
                stepAsideFor(other, wayAheadOf(other)); // stuck on each other: the lower priority steps aside
                return;
            }
        }
        if (heldFrames > STUCK_FRAMES || stuckBehindStill) {
            var ids = new java.util.HashSet<uz.dukeengine.core.thing.ObjectId>();
            for (var other : holders) {
                ids.add(other.getId());
            }
            planAgainRound(world, ids);
        }
    }

    /** A route round the movers it is stuck behind — or, asked for within 3 frames of the last, a second from now. */
    private void planAgainRound(World world, java.util.Set<uz.dukeengine.core.thing.ObjectId> stuckBehind) {
        round = stuckBehind;
        heldFrames = 0;
        if (world.getFrame() - lastRouteFrame < TOO_SOON_FRAMES) {
            if (routeAgainAt < 0) {
                routeAgainAt = world.getFrame() + GameConstants.LOGICFRAMES_PER_SECOND;
            }
            return;
        }
        planRoute();
    }

    /**
     * Whether {@code other} holds {@code mover} up: it drives into it — the other within 45 degrees of its heading, 34
     * if the other stands, the two not drawing apart — while more than a cell from its own goal; legs are never held by
     * legs, nor anything by what it runs over ({@code AIUpdateInterface::blockedBy}).
     */
    boolean heldBy(GameObject mover, World world, GameObject other) {
        if (walksOnLegs(mover) && walksOnLegs(other) || world.runsOver(mover, other)) {
            return false;
        }
        var at = mover.getPosition();
        if (destination != null && Math.abs(destination.x() - at.x()) < world.cellSize()
                && Math.abs(destination.y() - at.y()) < world.cellSize()) {
            return false; // nearly there: nothing holds it up now
        }
        var theirs = other.findModule(MoveUpdate.class);
        boolean otherMoving = theirs != null && theirs.isMoving();
        var there = other.getPosition();
        float dx = at.x() - there.x();
        float dy = at.y() - there.y();
        float apart = dx * dx + dy * dy;
        float cell = world.cellSize();
        if (apart < cell * cell * 0.0001f) {
            return higherPriority(mover, other); // on one point: the lower priority goes on
        }
        float mineX = (float) StrictMath.cos(mover.getOrientation());
        float mineY = (float) StrictMath.sin(mover.getOrientation());
        float theirX = (float) StrictMath.cos(other.getOrientation());
        float theirY = (float) StrictMath.sin(other.getOrientation());
        float sameWay = mineX * theirX + mineY * theirY;
        if (heldFrames > GameConstants.LOGICFRAMES_PER_SECOND && sameWay <= 0f) {
            return false; // held a second and crossing: through
        }
        float toThem = Math.abs(angleBetween(mover.getOrientation(), (float) StrictMath.atan2(-dy, -dx)));
        float toMe = Math.abs(angleBetween(other.getOrientation(), (float) StrictMath.atan2(dy, dx)));
        if (toThem > HALF_TURN / 2f) {
            return false; // going away from it
        }
        float limit = HALF_TURN / 4f * (otherMoving ? 1f : 0.75f);
        if (toThem > limit) {
            if (sameWay <= 0f || !otherMoving || toMe <= limit) {
                return false;
            }
            float nextX = dx + mineX - theirX;
            float nextY = dy + mineY - theirY;
            if (apart <= nextX * nextX + nextY * nextY) {
                return false; // drawing apart
            }
            return !higherPriority(mover, other);
        }
        return !other.isEffectivelyDead();
    }

    /**
     * How fast {@code mover} may go and not run into {@code other}: as fast as the other draws away along the line
     * between them, over how much of its own heading lies along it; none where the other comes at it ({@code
     * calculateMaxBlockedSpeed}).
     */
    private static float blockedSpeed(GameObject mover, GameObject other) {
        float dx = other.getPosition().x() - mover.getPosition().x();
        float dy = other.getPosition().y() - mover.getPosition().y();
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0f) {
            return 0f;
        }
        dx /= length;
        dy /= length;
        float away = dx * (float) StrictMath.cos(other.getOrientation()) + dy * (float) StrictMath.sin(other.getOrientation());
        if (away < 0f) {
            return 0f; // it comes at us
        }
        var theirs = other.findModule(MoveUpdate.class);
        float theirSpeed = theirs == null || !theirs.isMoving() ? 0f
                : theirs.lastStep / GameConstants.SECONDS_PER_LOGICFRAME;
        float toward = dx * (float) StrictMath.cos(mover.getOrientation())
                + dy * (float) StrictMath.sin(mover.getOrientation());
        if (toward <= 0f) {
            return Float.MAX_VALUE;
        }
        return theirSpeed * away / toward;
    }

    /**
     * Of two stuck on each other, whether {@code mover} goes on and {@code other} steps aside: the higher path priority
     * — the game's, then wheels or treads before legs — then, going the same way, the one ahead, else the lower id
     * ({@code hasHigherPathPriority}).
     */
    static boolean higherPriority(GameObject mover, GameObject other) {
        var mine = mover.findModule(MoveUpdate.class);
        var theirs = other.findModule(MoveUpdate.class);
        int ours = mine == null ? 0 : mine.data.pathPriority();
        int their = theirs == null ? 0 : theirs.data.pathPriority();
        if (ours != their) {
            return ours > their;
        }
        boolean vehicle = !walksOnLegs(mover);
        if (vehicle != !walksOnLegs(other)) {
            return vehicle;
        }
        float mineX = (float) StrictMath.cos(mover.getOrientation());
        float mineY = (float) StrictMath.sin(mover.getOrientation());
        float theirX = (float) StrictMath.cos(other.getOrientation());
        float theirY = (float) StrictMath.sin(other.getOrientation());
        if (mineX * theirX + mineY * theirY <= 0f) {
            return mover.getId().value() < other.getId().value();
        }
        float ahead = (mineX + theirX) * (other.getPosition().x() - mover.getPosition().x())
                + (mineY + theirY) * (other.getPosition().y() - mover.getPosition().y());
        if (ahead != 0f) {
            return ahead < 0f; // it is ahead of the other
        }
        return mover.getId().value() < other.getId().value();
    }

    /** Whether it has to turn to face the way on: more than 6 degrees off its next waypoint ({@code needToRotate}). */
    private boolean needsToTurn(GameObject owner) {
        if (waiting || waypointIndex >= waypoints.size()) {
            return waiting;
        }
        var next = waypoints.get(waypointIndex);
        var at = owner.getPosition();
        float way = (float) StrictMath.atan2(next.y() - at.y(), next.x() - at.x());
        return Math.abs(angleBetween(owner.getOrientation(), way)) > FACING_ITS_WAY;
    }

    /**
     * Out of the way of {@code from}, going along {@code way}: to the nearest block it may have whose ground stays clear
     * of it, for up to 10 seconds — going on to where it was sent afterwards if it was on its way somewhere — or, where
     * there is no such block, through the movers in its way meanwhile ({@code aiMoveAwayFromUnit}).
     */
    void stepAsideFor(GameObject from, List<Coord3D> way) {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null) {
            return;
        }
        var aside = world.placeAside(owner, from, way);
        if (aside == null) {
            passThroughUntil = world.getFrame() + ASIDE_FRAMES;
            return;
        }
        var goOnTo = isMoving() && toPlace && asideUntil < 0 ? sentTo : null;
        this.then = null;
        this.destination = aside;
        this.toPlace = true;
        this.stoppedShort = false;
        this.lookedAgain = false;
        this.round = java.util.Set.of();
        planRoute();
        this.then = goOnTo;
        this.asideUntil = world.getFrame() + ASIDE_FRAMES;
    }

    /** The way a mover is going from where it stands: its position and the waypoints left to it. */
    private static List<Coord3D> wayAheadOf(GameObject mover) {
        var way = new java.util.ArrayList<Coord3D>();
        way.add(mover.getPosition());
        var theirs = mover.findModule(MoveUpdate.class);
        if (theirs != null) {
            for (int i = theirs.waypointIndex; i < theirs.waypoints.size(); i++) {
                way.add(theirs.waypoints.get(i));
            }
        }
        return way;
    }

    /** Whether {@code other} is moving away from {@code from}. */
    private static boolean movingAwayFrom(GameObject other, GameObject from) {
        var theirs = other.findModule(MoveUpdate.class);
        if (theirs == null || !theirs.isMoving() || theirs.lastStep <= 0f) {
            return false;
        }
        float dx = other.getPosition().x() - from.getPosition().x();
        float dy = other.getPosition().y() - from.getPosition().y();
        return dx * (float) StrictMath.cos(other.getOrientation()) + dy * (float) StrictMath.sin(other.getOrientation())
                > 0f;
    }

    // ---- stepping among movers ----

    /** Whether a thing is a ground mover that keeps cells: walks, has a body, and is on the ground now. */
    static boolean isGroundMover(GameObject thing) {
        var world = thing.getWorld();
        return thing.getLocomotor() instanceof MoveUpdate && world != null && world.keepsCells(thing);
    }

    /** Whether {@code thing} walks on legs, as the reference's infantry do: legs pass legs. */
    public static boolean walksOnLegs(GameObject thing) {
        return thing.getLocomotor() instanceof MoveUpdate walking && walking.data.gait() == Gait.LEGS;
    }

    /** Where in a group's columns it goes. */
    public MovePriority movePriority() {
        return data.movePriority();
    }

    /** How it moves. */
    public Gait gait() {
        return data.gait();
    }

    private static boolean keepsCells(GameObject owner) {
        var world = owner.getWorld();
        return world != null && world.keepsCells(owner);
    }

    /** Whether a mover not moving and not busy may be asked to step aside. */
    private static boolean idle(GameObject thing) {
        var theirs = thing.findModule(MoveUpdate.class);
        return theirs != null && !theirs.isMoving() && !theirs.planning && !thing.isBusy()
                && !thing.hasStatus(ObjectStatus.HELD);
    }

    private static boolean allied(World world, GameObject a, GameObject b) {
        return a.getPlayerIndex() == b.getPlayerIndex()
                || world.getRelationship(a.getPlayerIndex(), b.getPlayerIndex())
                        == uz.dukeengine.core.player.Relationship.ALLIES;
    }

    /** How far a mover may come into the footprint of one ahead of it: a touch. */
    private static final float TOUCH = 1f;

    /**
     * Whether it may step from {@code from} to {@code to}: nothing solid there — or it is getting out of something
     * solid it stands in — and, among the movers it may not pass, none ahead of it that it comes more than a touch
     * into, nor deeper into one ahead that it is already further into. One it passes beside it may brush past, as a
     * diagonal step past an occupied cell takes it in the reference, where nothing stops a mover but the one it drives
     * into; where they stand, their cells keep them apart.
     */
    private boolean canStep(GameObject owner, Coord3D from, Coord3D to, int toward, boolean escaping) {
        if (!keepsCells(owner)) {
            return escaping || isClear(owner, to, toward);
        }
        if (!escaping && solidInTheWay(owner, to, toward)) {
            return false;
        }
        var world = owner.getWorld();
        if (world.getFrame() < passThroughUntil) {
            return true;
        }
        var here = uz.dukeengine.core.thing.Footprint.of(owner, from);
        var there = uz.dukeengine.core.thing.Footprint.of(owner, to);
        for (var other : moversOverlapping(owner, to)) {
            var them = uz.dukeengine.core.thing.Footprint.of(other);
            if (-there.separation(them) > Math.max(TOUCH, -here.separation(them)) + 1e-4f
                    && ahead(from, to, other.getPosition())) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code at} lies within 45 degrees of the step from {@code from} to {@code to}: driven into, not passed. */
    private static boolean ahead(Coord3D from, Coord3D to, Coord3D at) {
        float stepX = to.x() - from.x();
        float stepY = to.y() - from.y();
        float awayX = at.x() - from.x();
        float awayY = at.y() - from.y();
        float along = stepX * awayX + stepY * awayY;
        return along > 0f
                && along * along >= 0.5f * (stepX * stepX + stepY * stepY) * (awayX * awayX + awayY * awayY);
    }

    /** Whether something solid — anything but a ground mover — or the ground itself refuses a step there. */
    private static boolean solidInTheWay(GameObject owner, Coord3D to, int toward) {
        var world = owner.getWorld();
        if (world.findBlocker(owner, to, MoveUpdate::isGroundMover) != null) {
            return true;
        }
        return !world.canStep(owner, owner.getPosition(), to, toward)
                && !world.isGroundBlocked(owner, owner.getPosition());
    }

    /** The ground movers {@code owner} would overlap standing at {@code at}, that it may not pass through. */
    private static List<GameObject> moversOverlapping(GameObject owner, Coord3D at) {
        var world = owner.getWorld();
        var footprint = uz.dukeengine.core.thing.Footprint.of(owner, at);
        float reach = uz.dukeengine.core.thing.Solid.of(owner.getTemplate()).footprintRadius() + OTHERS_REACH;
        return world.objectsInRange(at, reach, other -> other != owner && isGroundMover(other)
                && !(walksOnLegs(owner) && walksOnLegs(other)) && !world.runsOver(owner, other)
                && footprint.overlaps(uz.dukeengine.core.thing.Footprint.of(other)));
    }

    /**
     * Turned to {@code facing} — unless it is a box and the turn would put it into another's footprint, when the turn
     * waits: the reference turns no box into another.
     */
    private void turn(GameObject owner, float facing) {
        float was = owner.getOrientation();
        if (facing == was) {
            return;
        }
        if (!(uz.dukeengine.core.thing.Solid.of(owner.getTemplate()) instanceof uz.dukeengine.core.thing.Geometry.Box)
                || !keepsCells(owner)) {
            owner.setOrientation(facing);
            return;
        }
        var world = owner.getWorld();
        if (world.getFrame() < passThroughUntil || turnWaits > STUCK_FRAMES) {
            owner.setOrientation(facing); // it has waited long enough: through, as a unit with nowhere to go does
            turnWaits = 0;
            return;
        }
        var before = moversOverlapping(owner, owner.getPosition());
        owner.setOrientation(facing);
        for (var other : moversOverlapping(owner, owner.getPosition())) {
            if (!before.contains(other)) {
                owner.setOrientation(was); // it would turn into another: it waits
                turnWaits++;
                return;
            }
        }
        turnWaits = 0;
    }

    private static float across(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ---- by its gait ----

    /** A quarter of a half turn: the 45 degrees off its way at which legs and treads stand to turn. */
    private static final float QUARTER_OF_HALF = (float) (StrictMath.PI / 4.0);
    /** The 9 degrees off its way beyond which wheels slow to their turning speed: the reference's {@code PI / 20}. */
    private static final float SMALL_TURN = (float) (StrictMath.PI / 20.0);
    /**
     * The least a braking tread or wheel slides a frame: the reference's {@code MIN_VEL}, a cell over the frames of a
     * second.
     */
    private static final float SLIDE_FRAMES = GameConstants.LOGICFRAMES_PER_SECOND;

    /**
     * A frame of legs, treads or wheels: turned and sped or slowed by the gait's rules, then a step along its facing —
     * or, braking onto the end, straight onto it — past whatever stands in the way as every step is.
     */
    private void walkByGait(GameObject owner, Coord3D position, Coord3D target, float distance, float desired,
            float rest, float top) {
        float was = Math.abs(angleBetween(owner.getOrientation(), desired));
        float travel = switch (data.gait()) {
            case LEGS -> legs(owner, desired, rest, top);
            case TREADS -> treads(owner, desired, distance, rest, top);
            case WHEELS -> wheels(owner, desired, rest, top);
            case OTHER -> throw new IllegalStateException("walked as it always was");
        };
        float off = Math.abs(angleBetween(owner.getOrientation(), desired));
        boolean turnedToward = off < was - 1e-6f && off < lastOff;
        lastOff = off;
        if (turnedToward) {
            framesWithoutProgress = 0; // turning toward its way is progress, though it stands
        } else if (madeNoProgress(distance)) {
            if (goOn()) {
                return;
            }
            stop();
            stoppedShort = true;
            return;
        }
        int toward = route == null ? -1 : route.floorOf(waypointIndex);
        boolean escaping = isBlocked(owner, position, toward);
        float step = Math.abs(travel) * GameConstants.SECONDS_PER_LOGICFRAME;
        if (brakingOnto) {
            // Braking, it does not go by its facing: it slides straight onto its point, as the reference's braking
            // objects do, at least a cell a second.
            step = Math.max(step, owner.getWorld() == null ? 1f / SLIDE_FRAMES : owner.getWorld().cellSize() / SLIDE_FRAMES);
            if (distance <= step || distance == 0f) {
                reach(owner, target, toward, escaping);
                return;
            }
            var slide = position.add(target.sub(position).scale(step / distance));
            if (canStep(owner, position, slide, toward, escaping)) {
                stepTo(owner, slide, toward);
            }
            return;
        }
        if (step <= 0f) {
            arrivedCloseEnough(rest);
            return; // turning where it stands
        }
        if (distance <= step || distance == 0f) {
            reach(owner, target, toward, escaping);
            return;
        }
        if (arrivedCloseEnough(rest)) {
            return;
        }
        float heading = travel < 0f ? owner.getOrientation() + HALF_TURN : owner.getOrientation();
        if (!escaping && standingOnTheDestination(owner, position.add(headingVector(heading).scale(step)))) {
            stop();
            return;
        }
        stepAlong(owner, position, heading, step, toward, escaping);
    }

    /** Legs: turned toward its way, aiming at its speed less the share of 45 degrees it is still off. */
    private float legs(GameObject owner, float desired, float rest, float top) {
        turn(owner, turnPerFrame <= 0f ? desired : rotateToward(owner.getOrientation(), desired, turnPerFrame));
        float off = Math.abs(angleBetween(owner.getOrientation(), desired));
        float goal = top * (1f - Math.min(1f, off / QUARTER_OF_HALF));
        float braking = braking(owner);
        if (rest < slowDownDistance(speedNow, data.minSpeed(), braking)) {
            goal = data.minSpeed();
        }
        speedNow = approach(speedNow, goal, acceleration(owner), braking);
        return speedNow;
    }

    /** Treads: turned and sped as legs are, and braking onto the end of the way within its stopping distance. */
    private float treads(GameObject owner, float desired, float distance, float rest, float top) {
        turn(owner, turnPerFrame <= 0f ? desired : rotateToward(owner.getOrientation(), desired, turnPerFrame));
        float share = Math.min(1f, Math.abs(angleBetween(owner.getOrientation(), desired)) / QUARTER_OF_HALF);
        float goal = top * (1f - share);
        float cell = cellSize(owner);
        if (distance < 2f * cell && share > 0.05f) {
            goal = speedNow * 0.6f; // near its point and off it: slower, rather than round it
        }
        float braking = braking(owner);
        float slowDown = braking <= 0f ? 0f : speedNow / 1.5f * (speedNow / braking);
        goal = brakeOnto(goal, slowDown, slowDown, rest, cell, braking);
        speedNow = approach(speedNow, goal, acceleration(owner), braking);
        return speedNow;
    }

    /**
     * Wheels: turning only while rolling, at its speed's share of its turning speed; slowing to that speed off its way;
     * backing where it may, and braking onto the end.
     */
    private float wheels(GameObject owner, float desired, float rest, float top) {
        float facing = owner.getOrientation();
        float turnSpeed = Math.max(data.minTurnSpeed(), top / 4f);
        float off = angleBetween(facing, desired);
        if (speedNow == 0f) {
            backing = data.canMoveBackwards() && Math.abs(off) > HALF_TURN / 2f;
            threePoint = backing && rest > 5f * halfLength(owner);
        }
        float aim = desired;
        if (backing) {
            if (Math.abs(off) < HALF_TURN / 2f) {
                backing = false;
            } else {
                threePoint = rest > 5f * halfLength(owner);
                if (!threePoint) {
                    aim = desired + HALF_TURN; // its back toward the point
                    off = angleBetween(facing, aim);
                }
            }
        }
        float goal = top;
        if (Math.abs(off) > SMALL_TURN) {
            goal = Math.min(goal, turnSpeed);
        }
        float braking = braking(owner);
        float slowDown = braking <= 0f ? 0f
                : speedNow / 1.5f * (speedNow / braking + GameConstants.SECONDS_PER_LOGICFRAME)
                        + speedNow * GameConstants.SECONDS_PER_LOGICFRAME;
        float cell = cellSize(owner);
        goal = brakeOnto(goal, slowDown, Math.max(slowDown, cell), rest, cell, braking);
        // It turns only as it rolls, by its speed's share of its turning speed: none while it stands.
        float turn = turnPerFrame <= 0f ? HALF_TURN : Math.min(1f, speedNow / turnSpeed) * turnPerFrame;
        turn(owner, rotateToward(facing, aim, speedNow == 0f ? 0f : turn));
        speedNow = approach(speedNow, goal, acceleration(owner), braking);
        return backing ? -speedNow : speedNow;
    }

    /**
     * The speed to aim at while braking onto the end of the way: the reference's {@code IS_BRAKING}, begun within
     * {@code trigger} of the end, ended once the end is a cell and twice the stopping distance away; braking, a frame's
     * braking off where it would not stop in time, half of it where it nearly would.
     */
    private float brakeOnto(float goal, float slowDown, float trigger, float rest, float cell, float braking) {
        if (braking <= 0f) {
            brakingOnto = false;
            return goal;
        }
        if (rest < trigger) {
            brakingOnto = true;
        }
        if (rest > cell && rest > 2f * slowDown) {
            brakingOnto = false;
        }
        if (!brakingOnto) {
            return goal;
        }
        float frame = braking * GameConstants.SECONDS_PER_LOGICFRAME;
        if (slowDown > rest) {
            return Math.max(0f, speedNow - frame);
        }
        return slowDown > rest * 0.75f ? Math.max(0f, speedNow - frame / 2f) : speedNow;
    }

    /** {@code speed} moved toward {@code goal} by at most a frame of its acceleration up or its braking down. */
    private static float approach(float speed, float goal, float acceleration, float braking) {
        float frame = GameConstants.SECONDS_PER_LOGICFRAME;
        if (goal > speed) {
            return acceleration <= 0f ? goal : Math.min(goal, speed + acceleration * frame);
        }
        return braking <= 0f ? goal : Math.max(goal, speed - braking * frame);
    }

    /** How far legs need to ease from {@code speed} to {@code least}: the reference's {@code calcSlowDownDist}. */
    private static float slowDownDistance(float speed, float least, float braking) {
        float over = speed - least;
        if (over <= 0f || braking <= 0f) {
            return 0f;
        }
        return over * over / braking * 0.5f * 1.05f; // its FUDGE, so a walker stops on a dime
    }

    private float acceleration(GameObject owner) {
        return isDamaged(owner) && data.accelerationDamaged() > 0f ? data.accelerationDamaged() : data.acceleration();
    }

    private float braking(GameObject owner) {
        return isDamaged(owner) && data.brakingDamaged() > 0f ? data.brakingDamaged() : data.braking();
    }

    /** Whether its body is below the share of its health its data counts as damaged. */
    private boolean isDamaged(GameObject owner) {
        var body = owner.getBody();
        return body != null && body.getMaxHealth() > 0f && body.getHealth() < body.getMaxHealth() * data.damagedBelow();
    }

    private static float cellSize(GameObject owner) {
        var world = owner.getWorld();
        return world == null ? uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE : world.cellSize();
    }

    /** Half its length along its facing: a box's major radius, else its radius. */
    private static float halfLength(GameObject owner) {
        var shape = uz.dukeengine.core.thing.Solid.of(owner.getTemplate());
        return shape instanceof uz.dukeengine.core.thing.Geometry.Box box ? box.majorRadius() : shape.footprintRadius();
    }

    /**
     * True when the world has changed shape since this route was planned, so the
     * remaining waypoints may lead through something that is now solid.
     */
    private boolean routeIsStale(GameObject owner) {
        var world = owner.getWorld();
        return world != null && world.getNavigationVersion() != navigationVersion;
    }

    /**
     * True once the unit has spent {@link #STUCK_FRAME_LIMIT} frames without
     * closing meaningfully on its waypoint.
     *
     * <p>Counting blocked frames is not enough: a unit circling an obstacle finds
     * a free step every frame and would orbit forever. What actually matters is
     * whether it is getting closer, so that is what is measured.
     */
    private boolean madeNoProgress(float distance) {
        if (distance < closestApproach - progressEpsilon) {
            closestApproach = distance;
            framesWithoutProgress = 0;
            return false;
        }
        return ++framesWithoutProgress >= STUCK_FRAME_LIMIT;
    }

    /**
     * Whether the way forward is blocked by the very thing the unit was sent to
     * stand on.
     *
     * <p>A destination inside something's body can never be reached, and a unit
     * that keeps trying does not stand still — it steps aside, finds that clear,
     * steps aside again, and walks a slow circle around its target until the
     * progress check gives up on it. Which is exactly what it looks like.
     *
     * <p>The test is narrow on purpose: only a blocker that <em>covers the
     * destination</em> counts. Anything else in the way is an obstacle to get
     * round, and getting round things means moving away from the goal for a
     * while.
     */
    private boolean standingOnTheDestination(GameObject mover, Coord3D step) {
        var world = mover.getWorld();
        if (world == null || destination == null) {
            return false;
        }
        var blocker = world.findBlocker(mover, step);
        return blocker != null && Footprint.of(blocker).contains(destination);
    }

    private static boolean isClear(GameObject mover, Coord3D position, int toward) {
        return !isBlocked(mover, position, toward);
    }

    /**
     * Whether a mover may not stand at {@code position} — someone in the way, or
     * the ground itself.
     *
     * <p>Terrain used to be left entirely to pathfinding, on the reasoning that a
     * route never crosses a wall. That holds only while the mover follows its
     * route: swerving around a neighbour is a step the path never planned, and in
     * a corridor barely wider than the mover the only way past is sideways into
     * stone. Nothing refused it, and once a mover's centre was inside a wall it
     * could never leave — a search that starts on blocked ground has nowhere to
     * begin, so it returned no path at all, for ever.
     *
     * <p>A mover already standing in stone is not held there. Refusing its steps
     * too would make the wall it should be escaping into a cage.
     */
    private static boolean isBlocked(GameObject mover, Coord3D position, int toward) {
        var world = mover.getWorld();
        if (world == null) {
            return false;
        }
        if (world.findBlocker(mover, position) != null) {
            return true;
        }
        // Stone, or a floor this one is not joined to. A step onto a higher floor
        // is refused for the same reason a wall is: from here, it is not ground.
        return !world.canStep(mover, mover.getPosition(), position, toward)
                && !world.isGroundBlocked(mover, mover.getPosition());
    }

    /** A step to {@code position}: onto the floor the step leads to — a deck's, at its entry — and at its height. */
    private static void stepTo(GameObject mover, Coord3D position, int toward) {
        var world = mover.getWorld();
        if (world != null) {
            mover.setFloor(world.floorAfter(mover, mover.getPosition(), position, toward));
        }
        mover.setPosition(onGround(mover, position));
    }

    /**
     * The same point, standing on the floor under it.
     *
     * <p>Where a mover is walking has always been decided in two dimensions and
     * still is; how high it stands while doing so is read off the ground beneath
     * it, every step. Read rather than carried, because the ground is what
     * decides — a mover that kept its own height would climb a stair and arrive
     * at the top still standing at the bottom's.
     *
     * <p>On flat ground this returns z = 0, which is where everything already was.
     */
    private static Coord3D onGround(GameObject mover, Coord3D position) {
        var world = mover.getWorld();
        return world == null
                ? position
                : new Coord3D(position.x(), position.y(), world.groundHeight(mover, position));
    }

    private static Coord3D headingVector(float angle) {
        return new Coord3D((float) StrictMath.cos(angle), (float) StrictMath.sin(angle), 0f);
    }

    /** Step {@code current} toward {@code target} by at most {@code maxStep} radians. */
    private static float rotateToward(float current, float target, float maxStep) {
        float diff = angleBetween(current, target);
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return current + Math.signum(diff) * maxStep;
    }

    /** Half a full turn, in radians. */
    private static final float HALF_TURN = (float) StrictMath.PI;

    /** The signed turn from {@code from} to {@code to}, the short way round, in radians. */
    private static float angleBetween(float from, float to) {
        return (float) StrictMath.atan2(StrictMath.sin(to - from), StrictMath.cos(to - from));
    }

    /**
     * The longest step that still lets a turn at this mover's rate reach a point {@code distance} away and
     * {@code off} radians off its heading.
     *
     * <p>A mover turning as hard as it can draws a circle of radius step / turn. A point is inside one of the
     * two circles tangent to the heading — and so out of reach going on at that speed — when it is nearer than
     * twice that radius times the sine of its bearing; slowing shrinks the circle until it is not.
     */
    private float tightestStep(float distance, float off) {
        float sine = (float) StrictMath.sin(off);
        return sine <= 0.0001f ? Float.MAX_VALUE : turnPerFrame * distance / (2f * sine);
    }
}
