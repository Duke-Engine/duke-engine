package uz.duke.core.module;

import java.util.List;
import uz.duke.core.GameConstants;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectStatus;

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
 * <p>SAGE's real locomotor models acceleration, turn rates and movement
 * surfaces; this is the straight-line core those build on.
 */
public final class AIUpdate extends UpdateModule {

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

    private final float stepPerFrame;
    private final float turnPerFrame; // radians/frame; 0 = instant turning
    private List<Coord3D> waypoints = List.of();
    private int waypointIndex;

    public AIUpdate(GameObject owner, Data data) {
        super(owner);
        this.stepPerFrame = data.speedPerSecond() * GameConstants.SECONDS_PER_LOGICFRAME;
        this.turnPerFrame = (float) Math.toRadians(
                data.turnRateDegreesPerSecond() * GameConstants.SECONDS_PER_LOGICFRAME);
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
    }

    /** Cancel any current move. */
    public void stop() {
        this.waypoints = List.of();
        this.waypointIndex = 0;
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

        if (distance <= step || distance == 0f) {
            owner.setPosition(target);
            waypointIndex++; // advance to the next leg (or finish the path)
            return;
        }

        float desired = (float) Math.atan2(delta.y(), delta.x());
        if (turnPerFrame <= 0f) {
            // Instant turning: head straight for the waypoint.
            owner.setOrientation(desired);
            owner.setPosition(position.add(delta.normalize().scale(step)));
            return;
        }
        // Rotate toward the goal and advance along the new facing (curving in).
        float facing = rotateToward(owner.getOrientation(), desired, turnPerFrame);
        owner.setOrientation(facing);
        var heading = new Coord3D((float) Math.cos(facing), (float) Math.sin(facing), 0f);
        owner.setPosition(position.add(heading.scale(step)));
    }

    /** Step {@code current} toward {@code target} by at most {@code maxStep} radians. */
    private static float rotateToward(float current, float target, float maxStep) {
        float diff = (float) Math.atan2(Math.sin(target - current), Math.cos(target - current));
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return current + Math.signum(diff) * maxStep;
    }
}
