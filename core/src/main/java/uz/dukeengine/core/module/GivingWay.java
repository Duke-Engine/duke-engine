package uz.dukeengine.core.module;

import static uz.dukeengine.core.module.Steps.HALF_TURN;
import static uz.dukeengine.core.module.Steps.across;
import static uz.dukeengine.core.module.Steps.angleBetween;
import static uz.dukeengine.core.module.Steps.headingVector;
import static uz.dukeengine.core.module.Steps.isGroundMover;
import static uz.dukeengine.core.module.Steps.keepsCells;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.World;

/**
 * How a ground mover keeps out of other ground movers' way and they out of its: held up only by one it drives into,
 * going no faster than that one draws away, planning again round those it is stuck behind, stepping aside for one of
 * higher priority, and turning no box into another — the reference's {@code AIUpdateInterface::processCollision},
 * {@code blockedBy} and {@code Pathfinder::moveAllies}. Legs pass legs.
 */
final class GivingWay {

    /** The reference's {@code MIN_REPATH_TIME}-like rule: a route asked for within 3 frames of the last waits a second. */
    private static final int TOO_SOON_FRAMES = 3;
    /** Held this long, it plans again round what holds it: two seconds. */
    private static final int STUCK_FRAMES = 2 * GameConstants.LOGICFRAMES_PER_SECOND;
    /** How long a step aside may take, or passing through movers with nowhere to step aside to: 10 seconds. */
    private static final int ASIDE_FRAMES = 10 * GameConstants.LOGICFRAMES_PER_SECOND;
    /** How far ahead of where it stands a mover looks for the one it would drive into, at least. */
    private static final float PROBE = 1f;
    /** How far another mover's middle may be from its outline, at most, for the one looking for those it touches. */
    private static final float OTHERS_REACH = 60f;
    /** How far a mover may come into the footprint of one ahead of it: a touch. */
    private static final float TOUCH = 1f;

    private final MoveUpdate legs;
    /** How many frames running another mover has held it up: the reference's {@code m_blockedFrames}. */
    private int heldFrames;
    /** The speed it is kept under after being held, world units a second: the reference's {@code m_bumpSpeedLimit}. */
    private float bumpLimit = Float.MAX_VALUE;
    /** The frame it last asked for a route, and — asked too soon after it — the frame it asks again. */
    private int lastRouteFrame = Integer.MIN_VALUE / 2;
    private int routeAgainAt = -1;
    /** The movers it plans its next route round, as though they were stone: those it is stuck behind. */
    private Set<ObjectId> round = Set.of();
    /** Until which frame it passes through other movers: stepping aside with nowhere to step. */
    private int passThroughUntil = -1;
    /** Until which frame its step aside may last before it gives it up where it stands. */
    private int asideUntil = -1;
    /** Whom it is stepping aside for, while it is. */
    private ObjectId asideFor;
    /** Whether it is planning a route now: not asked aside by the allies its own route asks, round and round. */
    private boolean planning;
    /** How many frames running a box's turn has waited on another's footprint. */
    private int turnWaits;
    /** The movers it last planned a route round, and where it stood then. */
    private Set<ObjectId> roundLast = Set.of();
    private Coord3D roundFrom;
    /**
     * How far it stood from where it was going when it began planning round the movers it plans round now, and the frame
     * it began: what it has come no nearer than since, while they are added up.
     */
    private float roundAway;
    private int roundSince;
    /** Whether it has walked through the movers it plans round since it last came nearer. */
    private boolean passedRound;

    GivingWay(MoveUpdate legs) {
        this.legs = legs;
    }

    /** A new order: no step aside, and nobody planned round. */
    void newOrder() {
        asideUntil = -1;
        round = Set.of();
        roundLast = Set.of();
    }

    /** Stopped: not held, and stepping aside no longer. */
    void stopped() {
        heldFrames = 0;
        asideUntil = -1;
    }

    /** Not held now. */
    void notHeld() {
        heldFrames = 0;
    }

    /** Whether it is stepping aside. */
    boolean stepsAside() {
        return asideUntil >= 0;
    }

    /** Whether its step aside has lasted long enough: ten seconds, and it stays where it got to. */
    boolean asideOver(World world) {
        return asideUntil >= 0 && world != null && world.getFrame() > asideUntil;
    }

    /** Whether its wait for a route asked too soon is over — told once. */
    boolean routeAgainDue(World world) {
        if (routeAgainAt >= 0 && world != null && world.getFrame() >= routeAgainAt) {
            routeAgainAt = -1;
            return true;
        }
        return false;
    }

    /** How many frames running a box's turn has waited. */
    int turnWaits() {
        return turnWaits;
    }

    /** The movers its next route goes round as though they were stone; none, the ground alone. */
    Set<ObjectId> round() {
        return round;
    }

    /** Nobody planned round. */
    void forgetRound() {
        round = Set.of();
    }

    /**
     * About to plan from {@code here}: round the same ones again and not a step further for the last route round them,
     * blocked and stuck, it walks through them a while, as the reference lets such a unit path through units.
     */
    void beforePlanning(World world, Coord3D here) {
        if (round.isEmpty()) {
            return;
        }
        if (round.equals(roundLast) && roundFrom != null && across(here, roundFrom) < PROBE) {
            passThroughUntil = world.getFrame() + STUCK_FRAMES;
        }
        roundLast = round;
        roundFrom = here;
    }

    /** Given a route this frame. */
    void planned(World world) {
        lastRouteFrame = world.getFrame();
    }

    /**
     * The idle allies standing still on the ground its route covers — {@code way}, from where it stands — not moving,
     * not busy — asked to step aside off it, as the reference's {@code Pathfinder::moveAllies} asks them once a route
     * through them is found.
     */
    void askAlliesAside(World world, GameObject owner, List<Coord3D> way) {
        planning = true;
        try {
            if (!world.keepsCells(owner) || way.size() < 2) {
                return;
            }
            for (var other : world.stillAlliesOn(owner, way)) {
                if (idle(other)) {
                    other.findModule(MoveUpdate.class).stepAsideFor(owner, way);
                }
            }
        } finally {
            planning = false;
        }
    }

    /**
     * The fastest it may go this frame for the ground movers it would drive into — {@code blockedBy}, {@code
     * calculateMaxBlockedSpeed} and {@code doLocomotor}'s bump limit: held, no faster than the one in front draws away,
     * the limit falling 5% a frame, and growing back 5% a frame from a fifth of its speed once it is not. Held two
     * seconds, or at once behind one standing still while facing its way, it plans again round them; stuck on each
     * other, the one of lower priority steps aside; held by one on legs, a mover on wheels or treads has it step aside.
     */
    float allowed(GameObject owner, World world, float desired, float top) {
        if (world == null || !world.keepsCells(owner) || world.getFrame() < passThroughUntil) {
            heldFrames = 0;
            return top;
        }
        float probe = Math.max(Math.max(PROBE, legs.stepPerFrame()), turningRoom(owner));
        var ahead = owner.getPosition().add(headingVector(owner.getOrientation()).scale(probe));
        var mine = Footprint.of(owner, ahead);
        float reach = Solid.of(owner.getTemplate()).footprintRadius() + probe + OTHERS_REACH;
        float limit = Float.MAX_VALUE;
        var holders = new ArrayList<GameObject>();
        for (var other : world.objectsInRange(owner.getPosition(), reach,
                candidate -> candidate != owner && isGroundMover(candidate))) {
            if (!mine.overlaps(Footprint.of(other)) || !heldBy(owner, world, other) || waitsForItsRoute(other)) {
                continue; // one waiting for its route holds nobody up, nor is asked aside: let it get its route
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
            legs.resetProgress(); // held, it is not lost: the giving way sorts it out, and plans again if it must
            sortOutTheHold(owner, world, holders, desired);
        }
        return speed;
    }

    /**
     * How far ahead a box looks for the one it would drive into: far enough to stop with room to turn where it stands —
     * its corners reach out to its bounding circle, a little past its front.
     */
    private static float turningRoom(GameObject owner) {
        var shape = Solid.of(owner.getTemplate());
        return shape instanceof Geometry.Box box ? shape.footprintRadius() - box.majorRadius() + PROBE : 0f;
    }

    /** Held: who steps aside, and whether it plans again round them. */
    private void sortOutTheHold(GameObject owner, World world, List<GameObject> holders, float desired) {
        boolean facingItsWay = Math.abs(angleBetween(owner.getOrientation(), desired)) <= Route.FACING_ITS_WAY;
        boolean stuckBehindStill = false;
        for (var other : holders) {
            var theirs = other.findModule(MoveUpdate.class);
            boolean otherMoving = theirs.isMoving();
            if (!MoveUpdate.walksOnLegs(owner) && MoveUpdate.walksOnLegs(other) && !movingAwayFrom(other, owner)
                    && !other.isBusy()) {
                theirs.stepAsideFor(owner, wayAheadOf(owner)); // a vehicle held by infantry: the infantry steps aside
                continue;
            }
            if (!otherMoving) {
                stuckBehindStill |= facingItsWay;
                continue;
            }
            if (facingItsWay && theirs.givingWay().heldBy(other, world, owner) && !theirs.route().needsToTurn(other)
                    && !higherPriority(owner, other)) {
                legs.stepAsideFor(other, wayAheadOf(other)); // stuck on each other: the lower priority steps aside
                return;
            }
        }
        if (heldFrames > STUCK_FRAMES || stuckBehindStill) {
            var ids = new HashSet<ObjectId>();
            for (var other : holders) {
                ids.add(other.getId());
            }
            planAgainRound(world, ids);
        }
    }

    /**
     * Stuck behind a mover standing still that no giving way moves — one at work, which is not asked aside — it plans
     * once round it before it gives up, as the reference's blocked and stuck unit plans with the units about it as
     * obstacles ({@code AIUpdateInterface::m_isBlockedAndStuck}).
     *
     * @return whether it planned round one
     */
    boolean planRoundWhatStopsIt(GameObject owner, Coord3D position) {
        var world = owner.getWorld();
        if (world == null || !world.keepsCells(owner)) {
            return false;
        }
        var ahead = position.add(headingVector(owner.getOrientation()).scale(Math.max(PROBE, legs.stepPerFrame())));
        var blocker = world.findBlocker(owner, ahead);
        if (blocker == null || !isGroundMover(blocker) || blocker.getLocomotor().isMoving()
                || round.contains(blocker.getId())) {
            return false;
        }
        planAgainRound(world, Set.of(blocker.getId()));
        legs.resetProgress();
        return true;
    }

    /**
     * A route round the movers it is stuck behind — or, asked for within 3 frames of the last, a second from now.
     *
     * <p>While it comes no nearer where it is going, those it plans round are added up, not replaced: held by one and
     * then by another, it plans round both, where planning round each in turn sent it from one into the other for ever.
     * Held again only by movers it plans round already, and no nearer for {@link Progress#STUCK_FRAME_LIMIT}, it walks
     * through them a while — as the reference lets a blocked and stuck unit go through units ({@code
     * setIgnoreCollisionTime}) — and, through them already, it is as near as it gets.
     */
    private void planAgainRound(World world, Set<ObjectId> stuckBehind) {
        var destination = legs.getDestination();
        float away = destination == null ? 0f : across(legs.getOwner().getPosition(), destination);
        if (round.isEmpty() || away < roundAway - world.cellSize()) {
            round = stuckBehind;
            roundAway = away;
            roundSince = world.getFrame();
            passedRound = false;
        } else if (!round.containsAll(stuckBehind)) {
            var all = new HashSet<>(round);
            all.addAll(stuckBehind);
            round = all;
        } else if (world.getFrame() - roundSince >= Progress.STUCK_FRAME_LIMIT) {
            if (passedRound) {
                legs.stopShort(); // through them, and no nearer: as close as it is ever going to get
                return;
            }
            passedRound = true;
            passThroughUntil = world.getFrame() + STUCK_FRAMES;
        }
        heldFrames = 0;
        if (world.getFrame() - lastRouteFrame < TOO_SOON_FRAMES) {
            if (routeAgainAt < 0) {
                routeAgainAt = world.getFrame() + GameConstants.LOGICFRAMES_PER_SECOND;
            }
            return;
        }
        legs.planAgain();
    }

    /**
     * Whether {@code other} holds {@code mover} up: it drives into it — the other within 45 degrees of its heading, 34
     * if the other stands, the two not drawing apart — while more than a cell from its own goal; legs are never held by
     * legs, nor anything by what it runs over ({@code AIUpdateInterface::blockedBy}).
     */
    boolean heldBy(GameObject mover, World world, GameObject other) {
        if (MoveUpdate.walksOnLegs(mover) && MoveUpdate.walksOnLegs(other) || world.runsOver(mover, other)) {
            return false;
        }
        var at = mover.getPosition();
        var destination = legs.getDestination();
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
                : theirs.lastStep() / GameConstants.SECONDS_PER_LOGICFRAME;
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
        int ours = mine == null ? 0 : mine.data().pathPriority();
        int their = theirs == null ? 0 : theirs.data().pathPriority();
        if (ours != their) {
            return ours > their;
        }
        boolean vehicle = !MoveUpdate.walksOnLegs(mover);
        if (vehicle != !MoveUpdate.walksOnLegs(other)) {
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

    /**
     * Asked out of the way of {@code from}, going along {@code way}: the place to step aside to — or none, out of its
     * way already (held itself meanwhile, it passes through movers for two seconds — the reference's {@code
     * privateMoveAwayFromUnit}, {@code setIgnoreCollisionTime}) or with nowhere to step, when it passes through the
     * movers in its way for ten.
     */
    Coord3D placeAside(GameObject owner, World world, GameObject from, List<Coord3D> way) {
        if (asideUntil >= world.getFrame() && from.getId().equals(asideFor)) {
            if (heldFrames > 0) {
                passThroughUntil = world.getFrame() + STUCK_FRAMES;
            }
            return null;
        }
        var aside = world.placeAside(owner, from, way);
        if (aside == null) {
            passThroughUntil = world.getFrame() + ASIDE_FRAMES;
        }
        return aside;
    }

    /** Stepping aside for {@code from}, for up to ten seconds from now. */
    void asideFrom(World world, GameObject from) {
        asideUntil = world.getFrame() + ASIDE_FRAMES;
        asideFor = from.getId();
    }

    /** The way a mover is going from where it stands: its position and the waypoints left to it. */
    private static List<Coord3D> wayAheadOf(GameObject mover) {
        return mover.findModule(MoveUpdate.class).route().ahead(mover.getPosition());
    }

    /** Whether {@code other} is on its way and waiting its turn for a route: the reference's isWaitingForPath. */
    private static boolean waitsForItsRoute(GameObject other) {
        var theirs = other.findModule(MoveUpdate.class);
        return theirs != null && theirs.isMoving() && theirs.route().waiting();
    }

    /** Whether {@code other} is moving away from {@code from}. */
    private static boolean movingAwayFrom(GameObject other, GameObject from) {
        var theirs = other.findModule(MoveUpdate.class);
        if (theirs == null || !theirs.isMoving() || theirs.lastStep() <= 0f) {
            return false;
        }
        float dx = other.getPosition().x() - from.getPosition().x();
        float dy = other.getPosition().y() - from.getPosition().y();
        return dx * (float) StrictMath.cos(other.getOrientation()) + dy * (float) StrictMath.sin(other.getOrientation())
                > 0f;
    }

    /** Whether a mover not moving and not busy may be asked to step aside. */
    private static boolean idle(GameObject thing) {
        var theirs = thing.findModule(MoveUpdate.class);
        return theirs != null && !theirs.isMoving() && !theirs.givingWay().planning && !thing.isBusy()
                && !thing.hasStatus(ObjectStatus.HELD);
    }

    /**
     * Whether it may step from {@code from} to {@code to}: nothing solid there — or it is getting out of something
     * solid it stands in — and, among the movers it may not pass, none ahead of it that it comes more than a touch
     * into, nor deeper into one ahead that it is already further into. One it passes beside it may brush past, as a
     * diagonal step past an occupied cell takes it in the reference, where nothing stops a mover but the one it drives
     * into; where they stand, their cells keep them apart.
     */
    boolean canStep(GameObject owner, Coord3D from, Coord3D to, int toward, boolean escaping) {
        if (!keepsCells(owner)) {
            return escaping || !Steps.isBlocked(owner, to, toward);
        }
        if (!escaping && Steps.solidInTheWay(owner, to, toward)) {
            return false;
        }
        var world = owner.getWorld();
        if (world.getFrame() < passThroughUntil) {
            return true;
        }
        var here = Footprint.of(owner, from);
        var there = Footprint.of(owner, to);
        for (var other : moversOverlapping(owner, to)) {
            var them = Footprint.of(other);
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

    /** The ground movers {@code owner} would overlap standing at {@code at}, that it may not pass through. */
    static List<GameObject> moversOverlapping(GameObject owner, Coord3D at) {
        var world = owner.getWorld();
        var footprint = Footprint.of(owner, at);
        float reach = Solid.of(owner.getTemplate()).footprintRadius() + OTHERS_REACH;
        return world.objectsInRange(at, reach, other -> other != owner && isGroundMover(other)
                && !(MoveUpdate.walksOnLegs(owner) && MoveUpdate.walksOnLegs(other)) && !world.runsOver(owner, other)
                && footprint.overlaps(Footprint.of(other)));
    }

    /**
     * Turned to {@code facing} — unless it is a box and the turn would put it into another's footprint, when the turn
     * waits: the reference turns no box into another.
     */
    void turn(GameObject owner, float facing) {
        float was = owner.getOrientation();
        if (facing == was) {
            return;
        }
        if (!(Solid.of(owner.getTemplate()) instanceof Geometry.Box) || !keepsCells(owner)) {
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
}
