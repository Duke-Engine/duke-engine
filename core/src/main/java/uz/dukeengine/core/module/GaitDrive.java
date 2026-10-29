package uz.dukeengine.core.module;

import static uz.dukeengine.core.module.Steps.HALF_TURN;
import static uz.dukeengine.core.module.Steps.angleBetween;
import static uz.dukeengine.core.module.Steps.rotateToward;

import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Solid;

/**
 * How a ground mover gathers and sheds speed and turns: its {@link MoveUpdate.Gait}'s rules — legs, treads and wheels
 * each their own way ({@code Locomotor.cpp}'s {@code moveTowardsPosition*}) — with the speed it has now, and whether it
 * is braking onto the end of its way or backing.
 */
final class GaitDrive {

    /** A quarter of a half turn: the 45 degrees off its way at which legs and treads stand to turn. */
    private static final float QUARTER_OF_HALF = (float) (StrictMath.PI / 4.0);
    /** The 9 degrees off its way beyond which wheels slow to their turning speed: the reference's {@code PI / 20}. */
    private static final float SMALL_TURN = (float) (StrictMath.PI / 20.0);
    /**
     * The least a braking tread or wheel slides a frame: the reference's {@code MIN_VEL}, a cell over the frames of a
     * second.
     */
    static final float SLIDE_FRAMES = GameConstants.LOGICFRAMES_PER_SECOND;
    /** Wheels within this many cells of the end of their way... */
    private static final float DONUT_CELLS = 4f;
    /** ...for this many frames brake onto it: the reference's {@code DONUT_DISTANCE} and 2.5 s. */
    private static final int DONUT_FRAMES = (int) (2.5f * GameConstants.LOGICFRAMES_PER_SECOND);

    private final MoveUpdate legs;
    private final GivingWay way;
    /** How fast it is going now, world units a second: gathered and shed at its data's rates. */
    private float speedNow;
    /** Whether treads or wheels are braking onto the end of their way, which they then slide straight onto. */
    private boolean brakingOnto;
    /** Whether wheels are backing toward a point behind them, and whether in a three-point turn. */
    private boolean backing;
    private boolean threePoint;
    /** The frame wheels came, and have stayed, within four cells of the end of their way; -1 while they are not. */
    private int nearEndSince = -1;

    GaitDrive(MoveUpdate legs, GivingWay way) {
        this.legs = legs;
        this.way = way;
    }

    /** Its speed now, its braking and backing, and since when wheels have been near the end of their way. */
    void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeFloat(speedNow).writeBoolean(brakingOnto).writeBoolean(backing).writeBoolean(threePoint)
                .writeInt(nearEndSince);
    }

    void load(uz.dukeengine.core.save.SaveIn in) {
        speedNow = in.readFloat();
        brakingOnto = in.readBoolean();
        backing = in.readBoolean();
        threePoint = in.readBoolean();
        nearEndSince = in.readInt();
    }

    float speedNow() {
        return speedNow;
    }

    boolean brakingOnto() {
        return brakingOnto;
    }

    /** Standing: no speed. */
    void stand() {
        speedNow = 0f;
    }

    /** Walked at {@code speed} this frame, by a steering rather than its gait. */
    void steered(float speed) {
        speedNow = speed;
    }

    /** At the end of its way: no speed, and no braking onto it. */
    void arrive() {
        speedNow = 0f;
        brakingOnto = false;
    }

    /** Stopped: no speed, no braking, no backing. */
    void stop() {
        arrive();
        backing = false;
    }

    /** A new way: not near its end. */
    void newWay() {
        nearEndSince = -1;
    }

    /** As the engine always moved things: up to {@code top} at its acceleration, at once down. */
    float gather(GameObject owner, float top) {
        speedNow = approach(speedNow, top, acceleration(owner), 0f);
        return speedNow;
    }

    /**
     * A frame of its gait toward {@code desired}, {@code distance} from its next waypoint and {@code rest} from the end:
     * turned, and sped or slowed.
     *
     * @return its speed along its facing, world units a second — less than 0 backing
     */
    float travel(GameObject owner, float desired, float distance, float rest, float top) {
        return switch (legs.data().gait()) {
            case LEGS -> legs(owner, desired, rest, top);
            case TREADS -> treads(owner, desired, distance, rest, top);
            case WHEELS -> wheels(owner, desired, rest, top);
            case OTHER -> throw new IllegalStateException("walked as it always was");
        };
    }

    /** Legs: turned toward its way, aiming at its speed less the share of 45 degrees it is still off. */
    private float legs(GameObject owner, float desired, float rest, float top) {
        turnToward(owner, desired);
        float off = Math.abs(angleBetween(owner.getOrientation(), desired));
        float goal = top * (1f - Math.min(1f, off / QUARTER_OF_HALF));
        float braking = braking(owner);
        if (rest < slowDownDistance(speedNow, legs.data().minSpeed(), braking)) {
            goal = legs.data().minSpeed();
        }
        speedNow = approach(speedNow, goal, acceleration(owner), braking);
        return speedNow;
    }

    /** Treads: turned and sped as legs are, and braking onto the end of the way within its stopping distance. */
    private float treads(GameObject owner, float desired, float distance, float rest, float top) {
        turnToward(owner, desired);
        float share = Math.min(1f, Math.abs(angleBetween(owner.getOrientation(), desired)) / QUARTER_OF_HALF);
        float goal = top * (1f - share);
        float cell = Steps.cellSize(owner);
        if (distance < 2f * cell && share > 0.05f) {
            goal = speedNow * 0.6f; // near its point and off it: slower, rather than round it
        }
        float braking = braking(owner);
        float slowDown = braking <= 0f ? 0f : speedNow / 1.5f * (speedNow / braking);
        goal = brakeOnto(owner, goal, slowDown, slowDown, rest, cell, braking, false);
        speedNow = approach(speedNow, goal, acceleration(owner), braking);
        return speedNow;
    }

    /** Turned toward {@code desired} at its turn rate, or at once. */
    private void turnToward(GameObject owner, float desired) {
        float turnPerFrame = legs.turnPerFrame();
        way.turn(owner, turnPerFrame <= 0f ? desired : rotateToward(owner.getOrientation(), desired, turnPerFrame));
    }

    /**
     * Wheels: turning only while rolling, at its speed's share of its turning speed; slowing to that speed off its way;
     * backing where it may, and braking onto the end.
     */
    private float wheels(GameObject owner, float desired, float rest, float top) {
        var data = legs.data();
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
        float cell = Steps.cellSize(owner);
        goal = brakeOnto(owner, goal, slowDown, Math.max(slowDown, cell), rest, cell, braking, true);
        // It turns only as it rolls, by its speed's share of its turning speed: none while it stands.
        float turnPerFrame = legs.turnPerFrame();
        float turn = turnPerFrame <= 0f ? HALF_TURN : Math.min(1f, speedNow / turnSpeed) * turnPerFrame;
        way.turn(owner, rotateToward(facing, aim, speedNow == 0f ? 0f : turn));
        speedNow = approach(speedNow, goal, acceleration(owner), braking);
        return backing ? -speedNow : speedNow;
    }

    /**
     * The speed to aim at while braking onto the end of the way: the reference's {@code IS_BRAKING}, begun within
     * {@code trigger} of the end, ended once the end is a cell and twice the stopping distance away — and, for wheels
     * ({@code donut}), begun once they have been within four cells of it for 2.5 s rather than circle it; braking, a
     * frame's braking off where it would not stop in time, half of it where it nearly would.
     */
    private float brakeOnto(GameObject owner, float goal, float slowDown, float trigger, float rest, float cell,
            float braking, boolean donut) {
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
        if (donut) {
            var world = owner.getWorld();
            int frame = world == null ? 0 : world.getFrame();
            if (rest > DONUT_CELLS * cell) {
                nearEndSince = -1;
            } else if (nearEndSince < 0) {
                nearEndSince = frame;
            } else if (frame - nearEndSince > DONUT_FRAMES) {
                brakingOnto = true;
            }
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
        var data = legs.data();
        return isDamaged(owner) && data.accelerationDamaged() > 0f ? data.accelerationDamaged() : data.acceleration();
    }

    private float braking(GameObject owner) {
        var data = legs.data();
        return isDamaged(owner) && data.brakingDamaged() > 0f ? data.brakingDamaged() : data.braking();
    }

    /** Whether its body is below the share of its health its data counts as damaged. */
    private boolean isDamaged(GameObject owner) {
        var body = owner.getBody();
        // At or below, as the reference's body is really damaged while health / most <= its threshold.
        return body != null && body.getMaxHealth() > 0f
                && body.getHealth() <= body.getMaxHealth() * legs.data().damagedBelow();
    }

    /** Half its length along its facing: a box's major radius, else its radius. */
    private static float halfLength(GameObject owner) {
        var shape = Solid.of(owner.getTemplate());
        return shape instanceof Geometry.Box box ? box.majorRadius() : shape.footprintRadius();
    }
}
