package uz.dukeengine.core.module;

import static uz.dukeengine.core.module.Steps.HALF_TURN;
import static uz.dukeengine.core.module.Steps.across;
import static uz.dukeengine.core.module.Steps.angleBetween;
import static uz.dukeengine.core.module.Steps.headingVector;
import static uz.dukeengine.core.module.Steps.isBlocked;
import static uz.dukeengine.core.module.Steps.keepsCells;
import static uz.dukeengine.core.module.Steps.rotateToward;
import static uz.dukeengine.core.module.Steps.solidInTheWay;
import static uz.dukeengine.core.module.Steps.stepTo;

import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * A frame of a ground mover's walk along its {@link Route}: steered along it, held up by the movers in its way, turned
 * and sped by its gait — or as the engine always moved things — stepped round what is solid, onto each waypoint in turn,
 * and given up where it comes no nearer.
 */
final class RouteWalk {

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

    private final MoveUpdate legs;
    private final Route route;
    private final GivingWay giving;
    private final GaitDrive gaits;
    private final Progress progress;

    RouteWalk(MoveUpdate legs, Route route, GivingWay giving, GaitDrive gaits, Progress progress) {
        this.legs = legs;
        this.route = route;
        this.giving = giving;
        this.gaits = gaits;
        this.progress = progress;
    }

    /** A frame toward its route's next waypoint: {@code step} as far as it may go, {@code top} as fast. */
    void frame(GameObject owner, World world, float step, float top) {
        var data = legs.data();
        float turnPerFrame = legs.turnPerFrame();
        var position = owner.getPosition();
        // On a gait it steers along its route, not onto each waypoint in turn: those behind it are passed.
        var steer = data.gait() == MoveUpdate.Gait.OTHER ? null : steerAt(owner, position);
        var target = route.next();
        var delta = target.sub(position);
        // On the ground plane, because that is where walking happens: a step is
        // taken in x and y and the height is read off the floor afterwards. Once
        // the world had storeys in it, a waypoint one floor up stayed a storey
        // away however close the mover got — so it never arrived, shuffled about
        // on the spot, and was eventually stopped by the stuck check two seconds
        // later. That is what the shivering on the top step was.
        float distance = (float) Math.sqrt(delta.x() * delta.x() + delta.y() * delta.y());

        var heading = steer == null || across(steer, position) < 1e-4f ? delta : steer.sub(position);
        float desired = (float) StrictMath.atan2(heading.y(), heading.x());
        float rest = distance + route.legsAfterNext();
        float allowed = giving.allowed(owner, world, desired, top);
        if (!legs.isMoving()) {
            return; // it stepped aside, or planned again and has nowhere to go
        }
        if (data.gait() != MoveUpdate.Gait.OTHER) {
            byGait(owner, position, target, distance, desired, rest, allowed);
            return;
        }
        step = Math.min(step, allowed * GameConstants.SECONDS_PER_LOGICFRAME);
        if (data.acceleration() > 0f || data.braking() > 0f) {
            step = Math.min(step, gaits.gather(owner, top) * GameConstants.SECONDS_PER_LOGICFRAME);
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
            step = Math.min(step, tightestStep(turnPerFrame, distance, off));
        }

        if (progress.madeNoProgress(distance)) {
            noNearer(owner, position);
            return;
        }

        // A unit that spawned on top of something is already overlapping; let it
        // walk free rather than lock it in place forever.
        int toward = route.floorOfNext();
        boolean escaping = isBlocked(owner, position, toward);

        if (distance <= step || distance == 0f) {
            reach(owner, target, toward, escaping);
            return;
        }
        if (arrivedCloseEnough(rest)) {
            return;
        }

        giving.turn(owner, turnPerFrame <= 0f
                ? desired // instant turning: head straight for the waypoint
                : rotateToward(owner.getOrientation(), desired, turnPerFrame));
        stepAlong(owner, position, owner.getOrientation(), step, toward, escaping);
    }

    /** Where a mover on a gait steers along its route this frame; waypoints it passed by, its progress begun again. */
    private Coord3D steerAt(GameObject owner, Coord3D position) {
        int was = route.index();
        var steer = route.steerAt(owner, position);
        if (route.index() > was) {
            progress.reset();
        }
        return steer;
    }

    /**
     * A frame of legs, treads or wheels: turned and sped or slowed by the gait's rules, then a step along its facing —
     * or, braking onto the end, straight onto it — past whatever stands in the way as every step is.
     */
    private void byGait(GameObject owner, Coord3D position, Coord3D target, float distance, float desired,
            float rest, float top) {
        float was = Math.abs(angleBetween(owner.getOrientation(), desired));
        int waitsWere = giving.turnWaits();
        float travel = gaits.travel(owner, desired, distance, rest, top);
        float off = Math.abs(angleBetween(owner.getOrientation(), desired));
        if (progress.turnedToward(off, was) || giving.turnWaits() > waitsWere) {
            // Turning toward its way is progress, though it stands; and a turn waiting on another's footprint is held,
            // not stuck, until it goes through — the reference checks no turn against footprints at all.
            progress.counted();
        } else if (progress.madeNoProgress(distance)) {
            noNearer(owner, position);
            return;
        }
        int toward = route.floorOfNext();
        boolean escaping = isBlocked(owner, position, toward);
        float step = Math.abs(travel) * GameConstants.SECONDS_PER_LOGICFRAME;
        if (gaits.brakingOnto()) {
            // Braking, it does not go by its facing: it slides straight onto its point, as the reference's braking
            // objects do, at least a cell a second.
            var world = owner.getWorld();
            step = Math.max(step, world == null ? 1f / GaitDrive.SLIDE_FRAMES : world.cellSize() / GaitDrive.SLIDE_FRAMES);
            if (distance <= step || distance == 0f) {
                reach(owner, target, toward, escaping);
                return;
            }
            var slide = position.add(target.sub(position).scale(step / distance));
            if (giving.canStep(owner, position, slide, toward, escaping)) {
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
        stepAlong(owner, position, travel < 0f ? owner.getOrientation() + HALF_TURN : owner.getOrientation(), step,
                toward, escaping);
    }

    /**
     * No nearer for too long: on from here by a route where something stands in the doorway, round a mover standing
     * still in its way, or else stopped — as close as it is ever going to get, rather than circle forever.
     */
    private void noNearer(GameObject owner, Coord3D position) {
        if (!legs.goOn() && !giving.planRoundWhatStopsIt(owner, position)) {
            legs.stopShort();
        }
    }

    /**
     * A step along {@code facing}, or — where something solid is in the way — along the first of the swerves that is
     * clear. Another ground mover in the way is not swerved round: the step waits, and the giving way sorts it out.
     * Pressed against the thing it was sent to, it has arrived.
     */
    private void stepAlong(GameObject owner, Coord3D position, float facing, float step, int toward,
            boolean escaping) {
        var straight = position.add(headingVector(facing).scale(step));
        if (!escaping && standingOnTheDestination(owner, straight)) {
            legs.stop(); // pressed against the thing we were sent to — this is arrival
            return;
        }
        if (step <= 0f) {
            return;
        }
        if (giving.canStep(owner, position, straight, toward, escaping)) {
            stepTo(owner, straight, toward);
            return;
        }
        if (keepsCells(owner) && !solidInTheWay(owner, straight, toward)) {
            return; // a mover in the way: it waits, and is held, not steered round
        }
        for (var swerve : SWERVE_ANGLES) {
            var next = position.add(headingVector(facing + swerve).scale(step));
            if (giving.canStep(owner, position, next, toward, escaping)) {
                stepTo(owner, next, toward);
                return;
            }
        }
        // Hemmed in on every side: hold position and let the progress check time it out.
    }

    /** Onto the waypoint it is a step from, where the ground there takes it, and on to the next leg or the end. */
    private void reach(GameObject owner, Coord3D target, int toward, boolean escaping) {
        if (giving.canStep(owner, owner.getPosition(), target, toward, escaping)) {
            stepTo(owner, target, toward);
            route.pass(); // advance to the next leg (or finish the path)
            progress.reset();
            if (!legs.isMoving()) {
                gaits.arrive();
                legs.routeWalked();
            }
        }
    }

    /**
     * Arrived: on its last leg, the rest of its way shorter than its close-enough distance — the reference's {@code
     * onPathDistToGoal < getCloseEnoughDist()}. It stops where it stands; but for a move exactly to its point.
     */
    private boolean arrivedCloseEnough(float rest) {
        if (legs.goesExactly() || !route.onLastLeg() || rest >= legs.closeEnough()) {
            return false;
        }
        route.walkedOut();
        gaits.arrive();
        progress.reset();
        legs.routeWalked();
        return true;
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
        var destination = legs.getDestination();
        if (world == null || destination == null) {
            return false;
        }
        var blocker = world.findBlocker(mover, step);
        return blocker != null && Footprint.of(blocker).contains(destination);
    }

    /**
     * The longest step that still lets a turn at {@code turnPerFrame} reach a point {@code distance} away and
     * {@code off} radians off its heading.
     *
     * <p>A mover turning as hard as it can draws a circle of radius step / turn. A point is inside one of the
     * two circles tangent to the heading — and so out of reach going on at that speed — when it is nearer than
     * twice that radius times the sine of its bearing; slowing shrinks the circle until it is not.
     */
    private static float tightestStep(float turnPerFrame, float distance, float off) {
        float sine = (float) StrictMath.sin(off);
        return sine <= 0.0001f ? Float.MAX_VALUE : turnPerFrame * distance / (2f * sine);
    }
}
