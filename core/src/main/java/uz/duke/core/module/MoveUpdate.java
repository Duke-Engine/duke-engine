package uz.duke.core.module;

import java.util.List;
import uz.duke.core.GameConstants;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.Footprint;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectStatus;
import uz.duke.core.thing.World;

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
 * around whatever is in the way. Objects with no {@link uz.duke.core.thing.Geometry}
 * pass through each other exactly as before.
 *
 * <p>SAGE's real locomotor models acceleration, turn rates and movement
 * surfaces; this is the straight-line core those build on.
 *
 * <p>Determinism: trigonometry goes through {@link StrictMath}, not
 * {@link Math}. {@code Math.sin}/{@code cos}/{@code atan2} are only required to
 * land within 1 ulp and are free to use platform intrinsics, so two peers can
 * disagree in the last bit — which is a desync. {@code StrictMath} is defined to
 * produce the same bits everywhere.
 */
public final class MoveUpdate extends UpdateModule implements Locomotor {

    /**
     * INI configuration: {@code Speed} (world units/sec) and an optional
     * {@code TurnRate} (degrees/sec). A turn rate of 0 means the unit can change
     * heading instantly (the original straight-line behaviour); a positive rate
     * makes it rotate toward its goal and curve along its facing.
     */
    public record Data(float speedPerSecond, float turnRateDegreesPerSecond) implements ModuleData {
        public Data(float speedPerSecond) {
            this(speedPerSecond, 0f);
        }
    }

    private static final class DataBuilder {
        float speedPerSecond;
        float turnRateDegreesPerSecond;

        Data build() {
            return new Data(speedPerSecond, turnRateDegreesPerSecond);
        }
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("Speed", Ini.real((b, v) -> b.speedPerSecond = v))
            .add("TurnRate", Ini.real((b, v) -> b.turnRateDegreesPerSecond = v));

    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return builder.build();
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

    private final float stepPerFrame;
    private final float turnPerFrame; // radians/frame; 0 = instant turning
    private final float progressEpsilon;
    private List<Coord3D> waypoints = List.of();
    private int waypointIndex;
    private Coord3D destination;      // where it was told to go, as opposed to the next corner
    private int navigationVersion;    // the world's shape when this route was planned
    private float closestApproach;
    private int framesWithoutProgress;

    public MoveUpdate(GameObject owner, Data data) {
        super(owner);
        this.stepPerFrame = data.speedPerSecond() * GameConstants.SECONDS_PER_LOGICFRAME;
        this.turnPerFrame = (float) Math.toRadians(
                data.turnRateDegreesPerSecond() * GameConstants.SECONDS_PER_LOGICFRAME);
        this.progressEpsilon = stepPerFrame * 0.25f;
    }

    /**
     * Order the unit to move to {@code destination}, routing around terrain via
     * the world's pathfinder. Falls back to a straight line when there is no
     * world or no navigation grid.
     */
    public void moveTo(Coord3D destination) {
        this.destination = destination;
        planRoute();
    }

    /** Work out the way to {@link #destination} from wherever the owner stands now. */
    private void planRoute() {
        var world = getOwner().getWorld();
        if (world == null) {
            this.waypoints = List.of(destination);
        } else {
            // Asked for this owner, so the route allows for its width and comes
            // back straightened rather than as a walk of cell centres.
            var path = world.findPath(getOwner(), destination);
            this.waypoints = path.isEmpty() ? List.of() : path.getWaypoints();
            this.navigationVersion = world.getNavigationVersion();
        }
        this.waypointIndex = 0;
        resetProgress();
    }

    /** Cancel any current move. */
    public void stop() {
        this.waypoints = List.of();
        this.waypointIndex = 0;
        this.destination = null;
        resetProgress();
    }

    private void resetProgress() {
        this.closestApproach = Float.MAX_VALUE;
        this.framesWithoutProgress = 0;
    }

    public boolean isMoving() {
        return waypointIndex < waypoints.size();
    }

    /** Where the unit was ordered to go, or {@code null} if it has no orders. */
    public Coord3D getGoal() {
        return destination;
    }

    @Override
    public void update() {
        if (!isMoving()) {
            return;
        }
        var owner = getOwner();
        if (owner.isEffectivelyDead() || owner.isContained() || owner.hasStatus(ObjectStatus.DISABLED)) {
            return; // dead, inside a transport, or frozen — cannot move
        }
        float step = owner.hasStatus(ObjectStatus.SLOWED) ? stepPerFrame * 0.5f : stepPerFrame;

        if (routeIsStale(owner)) {
            planRoute(); // something was built or destroyed across the way — think again
            if (!isMoving()) {
                return; // no way through any more
            }
        }

        var position = owner.getPosition();
        var target = waypoints.get(waypointIndex);
        var delta = target.sub(position);
        float distance = delta.length();

        if (madeNoProgress(distance)) {
            stop(); // as close as it is ever going to get — stop rather than circle forever
            return;
        }

        // A unit that spawned on top of something is already overlapping; let it
        // walk free rather than lock it in place forever.
        boolean escaping = isBlocked(owner, position);

        if (distance <= step || distance == 0f) {
            if (escaping || isClear(owner, target)) {
                owner.setPosition(onGround(owner, target));
                waypointIndex++; // advance to the next leg (or finish the path)
                resetProgress();
            }
            return;
        }

        float desired = (float) StrictMath.atan2(delta.y(), delta.x());
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
            if (escaping || isClear(owner, next)) {
                owner.setPosition(onGround(owner, next));
                return;
            }
        }
        // Hemmed in on every side: hold position and let the progress check time it out.
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

    private static boolean isClear(GameObject mover, Coord3D position) {
        return !isBlocked(mover, position);
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
    private static boolean isBlocked(GameObject mover, Coord3D position) {
        var world = mover.getWorld();
        if (world == null) {
            return false;
        }
        if (world.findBlocker(mover, position) != null) {
            return true;
        }
        // Stone, or a floor this one is not joined to. A step onto a higher floor
        // is refused for the same reason a wall is: from here, it is not ground.
        return !world.canStep(mover.getPosition(), position)
                && !world.isGroundBlocked(mover.getPosition());
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
                : new Coord3D(position.x(), position.y(), world.groundHeight(position));
    }

    private static Coord3D headingVector(float angle) {
        return new Coord3D((float) StrictMath.cos(angle), (float) StrictMath.sin(angle), 0f);
    }

    /** Step {@code current} toward {@code target} by at most {@code maxStep} radians. */
    private static float rotateToward(float current, float target, float maxStep) {
        float diff = (float) StrictMath.atan2(
                StrictMath.sin(target - current), StrictMath.cos(target - current));
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return current + Math.signum(diff) * maxStep;
    }
}
