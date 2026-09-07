package uz.duke.core.module;

import java.util.List;
import uz.duke.core.GameConstants;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.math.Coord3D;
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
public final class MoveUpdate extends UpdateModule {

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
        var world = getOwner().getWorld();
        if (world == null) {
            this.waypoints = List.of(destination);
        } else {
            var path = world.findPath(getOwner().getPosition(), destination);
            this.waypoints = path.isEmpty() ? List.of() : path.getWaypoints();
        }
        this.waypointIndex = 0;
        resetProgress();
    }

    /** Cancel any current move. */
    public void stop() {
        this.waypoints = List.of();
        this.waypointIndex = 0;
        resetProgress();
    }

    private void resetProgress() {
        this.closestApproach = Float.MAX_VALUE;
        this.framesWithoutProgress = 0;
    }

    public boolean isMoving() {
        return waypointIndex < waypoints.size();
    }

    /** The final destination of the current path, or {@code null} if not moving. */
    public Coord3D getGoal() {
        return waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1);
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
                owner.setPosition(target);
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

        for (var swerve : SWERVE_ANGLES) {
            var next = position.add(headingVector(facing + swerve).scale(step));
            if (escaping || isClear(owner, next)) {
                owner.setPosition(next);
                return;
            }
        }
        // Hemmed in on every side: hold position and let the progress check time it out.
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

    private static boolean isClear(GameObject mover, Coord3D position) {
        return !isBlocked(mover, position);
    }

    private static boolean isBlocked(GameObject mover, Coord3D position) {
        var world = mover.getWorld();
        return world != null && world.findBlocker(mover, position) != null;
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
