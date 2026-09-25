package uz.dukeengine.core.module;

import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;

/**
 * Moves a thing through the air: straight to where it is sent — a place, or a thing it follows — over cliffs,
 * water and buildings, never off the map, at a height it keeps above whatever is under it; gaining and shedding
 * speed at limited rates, and turning at a limited rate. The reference's air locomotors, the ones a template
 * chooses instead of walking.
 *
 * <p>Measured in the RTS this was taken from: 124 aircraft on 65 air locomotors — hovering ones, which may stop in
 * the air, turn where they are and settle 25 to 100 above the ground, and winged ones, which cannot stop: a least
 * speed, and with nothing to do they circle, as tight as their speed and turning allow ({@link Kind}). A winged one
 * sent to a place flies over it, as the reference's jets do — an attack run passes over its target — going wide
 * first where the place lies inside the circle it would turn on, and circles it from there.
 *
 * <p>It keeps its thing {@link ObjectStatus#AIRBORNE}, so nothing on the ground is blocked by it, placed around
 * it, or runs it over, and a weapon that cannot hit the air leaves it be. It sets the thing's height, and its
 * pitch and roll for the look — nose down as it gains speed, up as it sheds it, banked into its turns. It answers
 * as {@link MoveUpdate} does ({@link Locomotor}), so an order, a weapon and a computer player give it the same
 * orders. Deterministic: whole frames, and {@link StrictMath} for every angle.
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class FlyUpdate extends UpdateModule implements Locomotor {

    /** How a thing flies. */
    public enum Kind {
        /** It may stop in the air, and turn where it hangs. */
        HOVERING,
        /** Never slower than its least speed; with nothing to do, it circles its last goal, or where it first had none. */
        WINGED
    }

    /**
     * @param kind            how it flies
     * @param speed           its top speed, in world units a second
     * @param minSpeed        a winged thing's least speed; a hovering one may always stop
     * @param acceleration    how fast it gains speed, units a second each second; 0 is at once
     * @param braking         how fast it sheds speed, the same; 0 is at once
     * @param turnRate        degrees a second it turns; 0 is at once
     * @param preferredHeight how high above the ground under it it flies
     * @param climbRate       how fast it rises or sinks toward that height, units a second; 0 is at once
     */
    public record Data(Kind kind, float speed, float minSpeed, float acceleration, float braking, float turnRate,
            float preferredHeight, float climbRate) implements ModuleData {

        /** What a block leaves out: hovering, turning at a half turn a second, 50 up, climbing 20 a second. */
        static final Data DEFAULTS = new Data(Kind.HOVERING, 0f, 0f, 0f, 0f, 180f, 50f, 20f);

        public Data {
            kind = kind == null ? Kind.HOVERING : kind;
        }
    }

    /** How far its nose goes down as it gains speed flat out, or up as it brakes hard, in radians. */
    private static final float MOST_PITCH = 0.25f;
    /** How far it banks turning as hard as it can, in radians. */
    private static final float MOST_BANK = 0.5f;
    /** What share of the way from its old pitch and roll to the new it goes each frame, so neither snaps. */
    private static final float EASE = 0.2f;
    /**
     * How far past its turning circle a place inside it must lie, in turning radii, before a winged thing going wide
     * turns back onto it. At the circle's edge the turn itself is the whole way, and a frame's error leaves the place
     * inside again — it circled round it for good; from half a radius out, the turn ends pointing at it and the last
     * stretch is straight.
     */
    private static final float WIDE = 1.5f;

    private static final float DT = GameConstants.SECONDS_PER_LOGICFRAME;
    private static final float PI = (float) Math.PI;

    private final Data data;
    private float speed;
    private float turnRate;
    private float turnPerFrame;
    private float velocity;
    private Coord3D goal;
    private ObjectId following;
    private Coord3D circling;
    private boolean arrived = true;
    /** A winged thing flying straight on, out wide of a place inside its turning circle, to come back over it. */
    private boolean goingWide;

    public FlyUpdate(GameObject owner, Data data) {
        super(owner);
        this.data = data;
        setSpeed(data.speed(), data.turnRate());
        // Nothing here takes off: a winged thing is made in the air, already at the least speed it may fly at.
        this.velocity = data.kind() == Kind.WINGED ? data.minSpeed() : 0f;
    }

    @Override
    public void moveTo(Coord3D destination) {
        this.goal = destination;
        this.following = null;
        this.arrived = false;
        this.goingWide = false;
    }

    /** Follow {@code target} wherever it goes, until told otherwise or it is gone. */
    public void follow(ObjectId target) {
        this.following = target;
        this.goal = null;
        this.arrived = false;
    }

    /** Stop where it is — a winged thing cannot, and circles there. */
    @Override
    public void stop() {
        goal = null;
        following = null;
        arrived = true;
        circling = getOwner().getPosition();
    }

    @Override
    public boolean isMoving() {
        return !arrived;
    }

    @Override
    public void setSpeed(float speed, float turnRate) {
        this.speed = speed;
        this.turnRate = turnRate;
        this.turnPerFrame = (float) Math.toRadians(turnRate * DT);
    }

    @Override
    public boolean flies() {
        return true;
    }

    /** How fast it is going, in world units a second. */
    public float getVelocity() {
        return velocity;
    }

    /** How tight it can circle at the speed it is going: its speed over its turning. */
    public float circleRadius() {
        float turning = (float) Math.toRadians(turnRate);
        return turning <= 0f ? 0f : Math.max(velocity, data.minSpeed()) / turning;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead()) {
            return;
        }
        owner.setStatus(ObjectStatus.AIRBORNE);
        if (owner.hasStatus(ObjectStatus.DISABLED) || owner.hasStatus(ObjectStatus.HELD)) {
            return; // it hangs where it is, keeping its speed and its orders for when it may go
        }
        var here = owner.getPosition();
        var target = targetNow(world, here);
        float heading = owner.getOrientation();
        float wantedHeading = heading;
        float wantedSpeed = 0f;

        if (target != null) {
            float dx = target.x() - here.x();
            float dy = target.y() - here.y();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            wantedHeading = distance > 0f ? (float) StrictMath.atan2(dy, dx) : heading;
            wantedSpeed = speed;
            if (data.kind() == Kind.WINGED && following == null) {
                // Inside the circle it would turn on, no turn brings it over the place: out wide, then back over it.
                goingWide = insideTurn(here, heading, wantedHeading, target, goingWide ? WIDE : 1f);
                if (goingWide) {
                    wantedHeading = heading;
                }
            }
            if (data.kind() == Kind.HOVERING) {
                // Shed speed so as to stop on the goal: v^2 = 2 b d. And turn where it hangs before setting off
                // somewhere behind it.
                if (data.braking() > 0f) {
                    wantedSpeed = Math.min(wantedSpeed, (float) Math.sqrt(2f * data.braking() * distance));
                }
                if (Math.abs(angleBetween(heading, wantedHeading)) > PI / 2f) {
                    wantedSpeed = 0f;
                }
            }
        } else if (data.kind() == Kind.WINGED) {
            if (circling == null) {
                circling = here; // never sent anywhere: it circles where it first had nothing to do
            }
            // The reference's maintainCurrentPositionWings: aim for the spot on the circle round the centre a little
            // short of the side opposite, at its least speed; on the centre itself, as though it lay ahead.
            float dx = circling.x() - here.x();
            float dy = circling.y() - here.y();
            float toward = Math.abs(dx) < 1e-3f && Math.abs(dy) < 1e-3f ? heading : (float) StrictMath.atan2(dy, dx);
            float aim = toward + (PI - PI / 8f);
            float radius = circleRadius();
            wantedHeading = (float) StrictMath.atan2(circling.y() + (float) StrictMath.sin(aim) * radius - here.y(),
                    circling.x() + (float) StrictMath.cos(aim) * radius - here.x());
            wantedSpeed = data.minSpeed();
        }
        if (data.kind() == Kind.WINGED) {
            wantedSpeed = Math.max(wantedSpeed, data.minSpeed());
        }

        float turn = angleBetween(heading, wantedHeading);
        if (turnPerFrame > 0f) {
            turn = Math.clamp(turn, -turnPerFrame, turnPerFrame);
        }
        heading = normalised(heading + turn);

        float change = wantedSpeed - velocity;
        float mostGain = data.acceleration() > 0f ? data.acceleration() * DT : Float.MAX_VALUE;
        float mostLoss = data.braking() > 0f ? data.braking() * DT : Float.MAX_VALUE;
        change = Math.clamp(change, -mostLoss, mostGain);
        velocity = Math.max(0f, velocity + change);

        float step = velocity * DT;
        float x = here.x() + (float) StrictMath.cos(heading) * step;
        float y = here.y() + (float) StrictMath.sin(heading) * step;
        if (target != null) {
            float dx = target.x() - here.x();
            float dy = target.y() - here.y();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (data.kind() == Kind.HOVERING && step >= distance && following == null) {
                x = target.x(); // there: it stops on the spot
                y = target.y();
                velocity = 0f;
                arrive(target);
            } else if (data.kind() == Kind.WINGED && following == null && distance <= step) {
                arrive(target); // over it: from here it circles it
            }
        }
        float wide = world.mapWidth();
        float high = world.mapHeight();
        if (wide > 0f && high > 0f) {
            x = Math.clamp(x, 0f, wide);
            y = Math.clamp(y, 0f, high);
        }

        float ground = world.groundHeight(new Coord3D(x, y, here.z()));
        float climb = ground + data.preferredHeight() - here.z();
        if (data.climbRate() > 0f) {
            climb = Math.clamp(climb, -data.climbRate() * DT, data.climbRate() * DT);
        }
        owner.setPosition(new Coord3D(x, y, here.z() + climb));
        owner.setOrientation(heading);
        look(owner, change, mostGain, mostLoss, turn);
    }

    /**
     * Whether {@code target} lies within {@code radii} turning radii of the middle of the circle it would turn on to
     * face it, turning its hardest toward {@code wanted} at the speed it goes.
     */
    private boolean insideTurn(Coord3D here, float heading, float wanted, Coord3D target, float radii) {
        float radius = circleRadius();
        float side = angleBetween(heading, wanted) >= 0f ? PI / 2f : -PI / 2f;
        float dx = target.x() - (here.x() + (float) StrictMath.cos(heading + side) * radius);
        float dy = target.y() - (here.y() + (float) StrictMath.sin(heading + side) * radius);
        return dx * dx + dy * dy < radii * radii * radius * radius;
    }

    /** Where it is going this frame: its goal, the thing it follows, or nothing. */
    private Coord3D targetNow(uz.dukeengine.core.thing.World world, Coord3D here) {
        if (following != null) {
            var thing = world.findObject(following);
            if (thing == null || thing.isEffectivelyDead()) {
                following = null;
                arrived = true;
                circling = here;
                return null;
            }
            return thing.getPosition();
        }
        return arrived ? null : goal;
    }

    private void arrive(Coord3D at) {
        arrived = true;
        circling = at;
        goal = null;
    }

    /** Nose down as it gains speed and up as it sheds it; banked into its turn — eased, so neither snaps. */
    private void look(GameObject owner, float change, float mostGain, float mostLoss, float turn) {
        float pitch = 0f;
        if (change > 0f && mostGain < Float.MAX_VALUE) {
            pitch = -MOST_PITCH * change / mostGain;
        } else if (change < 0f && mostLoss < Float.MAX_VALUE) {
            pitch = MOST_PITCH * -change / mostLoss;
        }
        float roll = turnPerFrame > 0f ? MOST_BANK * turn / turnPerFrame : 0f;
        owner.setPitch(owner.getPitch() + (pitch - owner.getPitch()) * EASE);
        owner.setRoll(owner.getRoll() + (roll - owner.getRoll()) * EASE);
    }

    /** The turn from {@code from} to {@code to}, the short way round: between -pi and pi. */
    private static float angleBetween(float from, float to) {
        return normalised(to - from);
    }

    private static float normalised(float angle) {
        float twoPi = 2f * PI;
        float turned = angle % twoPi;
        if (turned > PI) {
            turned -= twoPi;
        } else if (turned <= -PI) {
            turned += twoPi;
        }
        return turned;
    }
}
