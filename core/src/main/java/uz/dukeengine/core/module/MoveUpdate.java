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
 * <p>Movement is solid: before every step the locomotor asks the world whether
 * the space it is about to occupy is free ({@link World#findBlocker}), and steers
 * around whatever is in the way. Objects with no {@link uz.dukeengine.core.thing.Geometry}
 * pass through each other exactly as before.
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
     */
    public record Data(float speed, float turnRate, float acceleration, float braking, float accelerationDamaged,
            float brakingDamaged, float damagedBelow, float minSpeed, float minTurnSpeed, float closeEnough,
            boolean canMoveBackwards, Gait gait) implements ModuleData {

        /** What a block leaves out: at once, arriving within 1, damaged under a tenth, moving as things always moved. */
        static final Data DEFAULTS = new Data(0f, 0f, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, Gait.OTHER);

        public Data {
            gait = gait == null ? Gait.OTHER : gait;
            closeEnough = closeEnough <= 0f ? 1f : closeEnough;
        }

        public Data(float speed) {
            this(speed, 0f);
        }

        /** A top speed and a turn rate; everything else as a block leaves it. */
        public Data(float speed, float turnRate) {
            this(speed, turnRate, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, Gait.OTHER);
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
        this.then = null;
        this.destination = destination;
        this.stoppedShort = false;
        this.lookedAgain = false;
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
        this.destination = way;
        this.then = destination;
        this.waypoints = List.of(way);
        this.route = null;
        this.goalReachable = true;
        this.lookedAgain = false;
        var world = getOwner().getWorld();
        this.navigationVersion = world == null ? 0 : world.getNavigationVersion();
    }

    /** On from the way it was given to where it was going, if it was given one — see {@link #leave}. */
    private boolean goOn() {
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
            // Asked for this owner, so the route allows for its width and comes
            // back straightened rather than as a walk of cell centres — and, where
            // there is no way there, as a route to the nearest place there is one.
            var path = world.findPath(getOwner(), destination);
            if (path == null) {
                waiting = true; // the frame's searching is spent: the old route, or standing, until its turn
                return;
            }
            waiting = false;
            this.waypoints = path.getWaypoints();
            this.route = path;
            this.goalReachable = path.reachesGoal();
            this.navigationVersion = world.getNavigationVersion();
        }
        this.waypointIndex = 0;
        resetProgress();
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
            return; // arrived
        }
        if (!lookedAgain) {
            lookedAgain = true;
            planRoute();
            if (isMoving()) {
                return;
            }
        }
        stoppedShort = true;
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

    /** Cancel any current move. */
    public void stop() {
        this.speedNow = 0f;
        this.brakingOnto = false;
        this.backing = false;
        this.then = null;
        this.waiting = false;
        this.waypoints = List.of();
        this.route = null;
        this.waypointIndex = 0;
        this.destination = null;
        this.stoppedShort = false;
        resetProgress();
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

    /** Where the unit was ordered to go, or {@code null} if it has no orders. */
    public Coord3D getGoal() {
        return destination;
    }

    @Override
    public void update() {
        if (!isMoving()) {
            speedNow = 0f;
            return;
        }
        var owner = getOwner();
        if (owner.isEffectivelyDead() || owner.isContained() || owner.hasStatus(ObjectStatus.DISABLED)
                || owner.hasStatus(ObjectStatus.HELD)) {
            speedNow = 0f;
            return; // dead, inside a transport, frozen or held — cannot move, and keeps its orders for when it can
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
        if (data.gait() != Gait.OTHER) {
            walkByGait(owner, position, target, distance, desired, rest, slowed ? topSpeed * 0.5f : topSpeed);
            return;
        }
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
        owner.setOrientation(facing);

        if (!escaping && standingOnTheDestination(owner, position.add(headingVector(facing).scale(step)))) {
            stop(); // pressed against the thing we were sent to — this is arrival
            return;
        }

        for (var swerve : SWERVE_ANGLES) {
            var next = position.add(headingVector(facing + swerve).scale(step));
            if (escaping || isClear(owner, next, toward)) {
                stepTo(owner, next, toward);
                return;
            }
        }
        // Hemmed in on every side: hold position and let the progress check time it out.
    }

    /** Onto the waypoint it is a step from, where the ground there takes it, and on to the next leg or the end. */
    private void reach(GameObject owner, Coord3D target, int toward, boolean escaping) {
        if (escaping || isClear(owner, target, toward)) {
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
            if (escaping || isClear(owner, slide, toward)) {
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
        for (var swerve : SWERVE_ANGLES) {
            var next = position.add(headingVector(heading + swerve).scale(step));
            if (escaping || isClear(owner, next, toward)) {
                stepTo(owner, next, toward);
                return;
            }
        }
    }

    /** Legs: turned toward its way, aiming at its speed less the share of 45 degrees it is still off. */
    private float legs(GameObject owner, float desired, float rest, float top) {
        var facing = turnPerFrame <= 0f ? desired : rotateToward(owner.getOrientation(), desired, turnPerFrame);
        owner.setOrientation(facing);
        float off = Math.abs(angleBetween(facing, desired));
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
        var facing = turnPerFrame <= 0f ? desired : rotateToward(owner.getOrientation(), desired, turnPerFrame);
        owner.setOrientation(facing);
        float share = Math.min(1f, Math.abs(angleBetween(facing, desired)) / QUARTER_OF_HALF);
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
        owner.setOrientation(rotateToward(facing, aim, speedNow == 0f ? 0f : turn));
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
