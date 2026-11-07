package uz.dukeengine.core.module;

import static uz.dukeengine.core.module.Steps.across;
import static uz.dukeengine.core.module.Steps.isBlocked;
import static uz.dukeengine.core.module.Steps.stepTo;

import java.util.List;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.save.UnsavedModuleException;
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
public final class MoveUpdate extends UpdateModule implements Locomotor, Saveable {

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
     * @param damagedBelow        the share of its most health at or below which it is damaged — the reference's
     *                            {@code MovementPenaltyDamageState}, really damaged, at 0.1 or less
     * @param minSpeed            the least speed legs ease to nearing the end ({@code MinSpeed})
     * @param minTurnSpeed        the speed wheels turn at, where more than a quarter of its speed ({@code MinTurnSpeed})
     * @param closeEnough         how near the end of its route counts as arrived ({@code CloseEnoughDist}): 1 unless set
     * @param canMoveBackwards    whether wheels may back toward a point behind them ({@code CanMoveBackwards})
     * @param gait                how it moves — see {@link Gait}
     * @param pathPriority        which of two movers stuck on each other steps aside: the lower — the reference's
     *                            dozers first, which a game marks with a higher number; of two alike, one on wheels or
     *                            treads before one on legs ({@code AIUpdateInterface::hasHigherPathPriority})
     * @param movePriority        where in a group's columns it goes — see {@link MovePriority}; the front unless set
     * @param surfaces            the classes of ground it may enter besides plain ground — the reference's locomotor
     *                            {@code Surfaces} — {@code [RUBBLE]} for infantry; none, plain ground only
     * @param classLocomotors     its locomotor on a class of ground, by the class — a commando's cliff locomotor,
     *                            climbing at 20 where he walks at 30 ({@code
     *                            chooseGoodLocomotorFromCurrentSet}): moved by while the cell under it is of that
     *                            class, and a class it may enter besides
     */
    public record Data(float speed, float turnRate, float acceleration, float braking, float accelerationDamaged,
            float brakingDamaged, float damagedBelow, float minSpeed, float minTurnSpeed, float closeEnough,
            boolean canMoveBackwards, Gait gait, int pathPriority, MovePriority movePriority, List<String> surfaces,
            java.util.Map<String, Data> classLocomotors) implements ModuleData {

        /** What a block leaves out: at once, arriving within 1, damaged under a tenth, moving as things always moved. */
        static final Data DEFAULTS = new Data(0f, 0f, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, Gait.OTHER, 0,
                MovePriority.FRONT);

        public Data {
            gait = gait == null ? Gait.OTHER : gait;
            closeEnough = closeEnough <= 0f ? 1f : closeEnough;
            movePriority = movePriority == null ? MovePriority.FRONT : movePriority;
            surfaces = surfaces == null ? List.of() : List.copyOf(surfaces);
            classLocomotors = classLocomotors == null ? java.util.Map.of()
                    : java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(classLocomotors));
        }

        /** Plain ground only: every locomotor from before ground could have classes. */
        public Data(float speed, float turnRate, float acceleration, float braking, float accelerationDamaged,
                float brakingDamaged, float damagedBelow, float minSpeed, float minTurnSpeed, float closeEnough,
                boolean canMoveBackwards, Gait gait, int pathPriority, MovePriority movePriority) {
            this(speed, turnRate, acceleration, braking, accelerationDamaged, brakingDamaged, damagedBelow, minSpeed,
                    minTurnSpeed, closeEnough, canMoveBackwards, gait, pathPriority, movePriority, List.of(),
                    java.util.Map.of());
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

    /** Within this of a heading, in radians, it already faces it: a facing a turn of 2 pi from it is the same one. */
    private static final float FACING = 1e-5f;

    /** How often, in frames, a still mover looks whether it may keep the ground it stands on. */
    private static final int STILL_LOOK_FRAMES = 8;

    private final Progress progress = new Progress();
    private final Route route = new Route();
    private final LinkCrossing links = new LinkCrossing();
    private final GivingWay giving = new GivingWay(this);
    private final GaitDrive gaits = new GaitDrive(this, giving);
    private final RouteWalk walk = new RouteWalk(this, route, giving, gaits, progress);

    /** Its template's locomotor: a save writes the one given only where the game has given another since. */
    private final Data template;
    private Data data;
    /** Its locomotor as given, the template's or the game's since: what it moves by off the classes it has one for. */
    private Data given;
    /** The class of ground under it it last moved by, or null for plain ground. */
    private String movingOn;
    private float topSpeed;           // world units a second
    private float stepPerFrame;
    private float turnPerFrame; // radians/frame; 0 = instant turning
    private Coord3D destination;      // where it was told to go, as opposed to the next corner
    /** Whether its last move ended short of where it was sent — see {@link #stoppedShort}. */
    private boolean stoppedShort;
    /** Whether a route that stopped short has had its one fresh look from where it ended. */
    private boolean lookedAgain;
    /** What it goes on to once it has walked the way it was given — see {@link #leave}, {@link #stepAsideFor}. */
    private Walk then;
    /** The points it has yet to go through, and the place it holds at their end — see {@link #moveThrough}. */
    private List<Coord3D> through = List.of();
    private Coord3D throughTo;
    /** What it walks by in place of a route, or null — see {@link #steerBy}. */
    private Steering steering;
    /** The point it turns where it stands to face, or null — see {@link #faceToward}. */
    private Coord3D facing;

    // ---- the ground's cells ----
    /** Whether it is going to a place — a block of the ground's cells of its own — rather than into something. */
    private boolean toPlace;
    /** The place it was sent to, before the block round it it was given. */
    private Coord3D sentTo;
    /** Whether it is going exactly to its point — {@link #moveExactlyTo} — and so onto it, not a close-enough short. */
    private boolean exactly;
    /** The band round a thing its next route ends in — see {@link #moveWithin} — until that route is planned. */
    private Band band;

    /**
     * Between {@code least} and {@code most} of {@code what}, outline to outline. {@code what} is null for a thing killed
     * and gone from the world before a save, {@code fellAt} where it lay: no band is planned round a dead thing, and a
     * walk on into it goes where it fell.
     */
    private record Band(GameObject what, float least, float most, Coord3D fellAt) {

        Coord3D at() {
            return what == null ? fellAt : what.getPosition();
        }
    }

    /** A walk it goes on to: to the place round {@code to}, exactly onto it, or into a band round a thing. */
    private record Walk(Coord3D to, boolean exactly, Band within) {
    }

    /** Where it stood when this frame began, and how far it went the frame before. */
    private Coord3D lastPosition;
    private float lastStep;

    public MoveUpdate(GameObject owner, Data data) {
        super(owner);
        this.template = data;
        this.data = data;
        this.given = data;
        setSpeed(data.speed(), data.turnRate());
    }

    /**
     * The classes of ground it may enter: its locomotor's surfaces and every class it has a locomotor for — the
     * reference's locomotor set, whose surfaces are all its locomotors'.
     */
    public java.util.Set<String> surfaces() {
        var names = new java.util.TreeSet<>(given.surfaces());
        names.addAll(given.classLocomotors().keySet());
        return names;
    }

    /** Moved by its locomotor for the class of ground under it, where it has one, and by its own off it. */
    private void takeTheGroundsLocomotor(GameObject owner, World world) {
        if (world == null || given.classLocomotors().isEmpty()) {
            return;
        }
        var under = world.groundClassUnder(owner);
        if (java.util.Objects.equals(under, movingOn)) {
            return;
        }
        movingOn = under;
        var onIt = under == null ? null : given.classLocomotors().get(under);
        var by = onIt == null ? given : onIt;
        this.data = by;
        setSpeed(by.speed(), by.turnRate());
    }

    /**
     * Its locomotor given whole from now on — the reference's locomotor set changed ({@code
     * AIUpdateInterface::chooseLocomotorSet}): a heavy tank's upgrade doubling its acceleration, a worker's shoes. It
     * keeps where it is going, its route, its block and its way out of its maker, and the speed it has, which it
     * gains and sheds at the new rates from the next frame. Callable from any module's update, its own thing's
     * included.
     */
    public void setLocomotor(Data data) {
        this.data = java.util.Objects.requireNonNull(data);
        this.given = data;
        this.movingOn = null;
        setSpeed(data.speed(), data.turnRate());
    }

    @Override
    public void setSpeed(float speed, float turnRate) {
        topSpeed = speed;
        stepPerFrame = speed * GameConstants.SECONDS_PER_LOGICFRAME;
        turnPerFrame = (float) Math.toRadians(turnRate * GameConstants.SECONDS_PER_LOGICFRAME);
        progress.setStep(stepPerFrame);
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
     * setAdjustsDestination(false)}). It ends on the point, not its close-enough distance short: the reference's move
     * that does not adjust its destination keeps its locomotor's goal once it is close enough ({@code
     * AIInternalMoveToState::update}), and the mover goes on onto it — a truck onto its dock.
     */
    @Override
    public void moveExactlyTo(Coord3D destination) {
        letPlaceGo();
        sentTo = destination;
        head(destination, false);
        exactly = true;
    }

    /**
     * To the nearest place between {@code least} and {@code most} of {@code what}, by a route that ends there — the
     * reference's attack path ({@code Pathfinder::findAttackPath}) — holding no block of the ground's cells: once
     * planned, the route's end is where it goes, as a move exactly there goes.
     */
    @Override
    public void moveWithin(GameObject what, float least, float most) {
        within(new Band(what, least, most, null));
    }

    private void within(Band band) {
        letPlaceGo();
        var at = band.at();
        sentTo = at;
        head(at, false, band);
        exactly = true;
    }

    private void letPlaceGo() {
        var world = getOwner().getWorld();
        if (world != null) {
            world.letPlaceGo(getOwner());
        }
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
        head(destination, place, null);
    }

    private void head(Coord3D destination, boolean place, Band band) {
        this.steering = null;
        this.facing = null;
        links.forgetUnlessTo(destination);
        this.band = band;
        this.exactly = false;
        this.then = null;
        this.through = List.of();
        this.throughTo = null;
        this.destination = destination;
        this.toPlace = place;
        this.stoppedShort = false;
        this.lookedAgain = false;
        giving.newOrder();
        gaits.newWay();
        planRoute();
        nowhereNearer();
    }

    /**
     * The reference's {@code aiFollowExitProductionPath}: straight to {@code way} — its maker's door, through its
     * maker's own walls, which no route could be planned out of and none is needed through, since a mover standing
     * in something is let walk out of it ({@link Steps#isBlocked}) — then on to {@code destination} by a route. Where
     * the way makes no headway, it goes on from wherever it got to.
     */
    @Override
    public void leave(Coord3D way, Coord3D destination) {
        stop();
        var world = getOwner().getWorld();
        letPlaceGo(); // the first leg is through its maker's walls: nowhere of its own yet
        this.toPlace = false;
        this.exactly = false;
        this.destination = way;
        this.then = new Walk(destination, false, null);
        route.given(way, getOwner().getPosition(), world == null ? 0 : world.getNavigationVersion());
        gaits.newWay();
        this.lookedAgain = false;
    }

    /** On from the way it was given to where it was going, if it was given one — see {@link #leave}. */
    boolean goOn() {
        if (throughTo != null) {
            goThrough(through, throughTo);
            return true;
        }
        if (then == null) {
            return false;
        }
        var walk = then;
        if (walk.within() != null) {
            within(walk.within());
        } else if (walk.exactly()) {
            moveExactlyTo(walk.to());
        } else {
            moveTo(walk.to());
        }
        return true;
    }

    /** A route given that leads nowhere nearer than where it stands: that is already as near as it gets. */
    private void nowhereNearer() {
        if (!route.waiting() && !isMoving() && !route.goalReachable()) {
            lookedAgain = true;
            stoppedShort = true;
        }
    }

    /** Work out the way to {@link #destination} from wherever the owner stands now. */
    private void planRoute() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null) {
            route.straight(destination);
        } else {
            if (toPlace && sentTo != null && then == null && !giving.stepsAside() && !world.holdsPlace(owner)) {
                // An ally has claimed its block since: the nearest block it may have instead.
                destination = world.takePlace(owner, sentTo);
            }
            giving.beforePlanning(world, owner.getPosition());
            // Asked for this owner, so the route allows for its width and comes
            // back straightened rather than as a walk of cell centres — and, where
            // there is no way there, as a route to the nearest place there is one.
            var bandTo = band == null || band.what() == null || band.what().isEffectivelyDead()
                    || band.what().getWorld() != world ? null : band;
            var round = giving.round();
            var path = bandTo != null ? world.findPathWithin(owner, bandTo.what(), bandTo.least(), bandTo.most())
                    : round.isEmpty() ? world.findPath(owner, destination)
                    : world.findPath(owner, destination, round);
            if (path == null) {
                route.setWaiting(true); // the frame's searching is spent: the old route, or standing, until its turn
                return;
            }
            route.setWaiting(false);
            if (bandTo != null) {
                band = null; // planned: its end is where it goes from now on
                destination = path.getWaypoints().isEmpty() ? owner.getPosition() : path.getWaypoints().getLast();
            }
            giving.planned(world);
            route.follow(links.routeVia(world, owner, path, destination, stepPerFrame),
                    world.getNavigationVersion());
            giving.askAlliesAside(world, owner, route.ahead(owner.getPosition()));
        }
        route.setOff(owner.getPosition());
        giving.notHeld();
        resetProgress();
    }

    /**
     * A route again from where it stands; one that leaves it nowhere nearer to go has it stopped short, as a route given
     * that leads nowhere nearer has.
     *
     * @return whether it left it nowhere nearer to go
     */
    boolean planAgain() {
        planRoute();
        if (!route.waiting() && !isMoving()) {
            nowhereNearer();
            routeWalked();
            return true;
        }
        return false;
    }

    /**
     * It has walked its route to the end. For a route that stops short of where it was sent, that is as near
     * as it gets — after one fresh look from where it now stands, which is what takes a mover the rest of the
     * way once it has stepped out of a wall it was standing in, or a gate has opened.
     */
    void routeWalked() {
        if (goOn()) {
            return;
        }
        if (route.goalReachable() || destination == null) {
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
        return route.goalReachable();
    }

    /** Cancel any current move: it holds the block it stands on from now. */
    public void stop() {
        this.steering = null;
        this.facing = null;
        this.band = null;
        gaits.stop();
        this.then = null;
        this.through = List.of();
        this.throughTo = null;
        route.clear();
        this.destination = null;
        this.stoppedShort = false;
        giving.stopped();
        resetProgress();
        this.toPlace = false;
        this.exactly = false;
        holdWhereItStands();
    }

    /**
     * Turned where it stands toward {@code point} at its turn rate, a frame at a time, or at once where it turns at once
     * — through {@link GivingWay#turn}, so a box waits rather than turn into another.
     */
    @Override
    public boolean faceToward(Coord3D point) {
        var owner = getOwner();
        var here = owner.getPosition();
        if (point.x() == here.x() && point.y() == here.y()) {
            return false;
        }
        if (Math.abs(Steps.angleBetween(owner.getOrientation(), headingTo(here, point))) < FACING) {
            facing = null;
            return false;
        }
        if (!point.equals(facing)) {
            stop();
            facing = point;
            if (turnPerFrame <= 0f) {
                turnToFace(owner);
            }
        }
        return facing != null;
    }

    /** A frame of its turn toward the point it faces, if it is turning and free to: done once it faces it. */
    private void turnToFace(GameObject owner) {
        if (facing == null || owner.isEffectivelyDead() || owner.isContained()
                || owner.hasStatus(ObjectStatus.DISABLED) || owner.hasStatus(ObjectStatus.HELD)) {
            return; // keeps the turn for when it may
        }
        float wanted = headingTo(owner.getPosition(), facing);
        giving.turn(owner, turnPerFrame <= 0f ? wanted
                : Steps.rotateToward(owner.getOrientation(), wanted, turnPerFrame));
        if (owner.getOrientation() == wanted) {
            facing = null;
        }
    }

    private static float headingTo(Coord3D from, Coord3D to) {
        return (float) StrictMath.atan2(to.y() - from.y(), to.x() - from.x());
    }

    /** Stopped where it is, short of where it was sent: as close as it is ever going to get. */
    void stopShort() {
        stop();
        stoppedShort = true;
    }

    void resetProgress() {
        progress.reset();
    }

    /** How fast it is going now, world units a second. */
    @Override
    public float speedMoved() {
        return lastStep * GameConstants.LOGICFRAMES_PER_SECOND;
    }

    public float getSpeedNow() {
        return gaits.speedNow();
    }

    @Override
    public float closeEnough() {
        return data.closeEnough();
    }

    @Override
    public boolean isMoving() {
        return route.waiting() || route.walking() || steering != null;
    }

    /** Whether a held direction steers it now (see {@link #steerBy}), rather than a route or nothing. */
    public boolean isSteered() {
        return steering != null;
    }

    /**
     * Whether it stands still at work while a steering is kept: busy with a clip ({@link GameObject#busy}), where a
     * steered walk waits and goes on after. It is drawn standing then, not walking on the spot. Work it was busied
     * at on the move holds no one.
     */
    public boolean standsAtWork() {
        return steering != null && getOwner().getBusyClip() != null && !getOwner().busyOnTheMove();
    }

    /** What its {@link SpeedModifier}s make of its speed now, all of them multiplied; 1 for none. */
    private static float speedShare(GameObject owner) {
        float share = 1f;
        for (var module : owner.modulesView()) {
            if (module instanceof SpeedModifier modifier) {
                share *= Math.max(0f, modifier.speedShare());
            }
        }
        return share;
    }

    /**
     * Walk by {@code way} until stopped or sent somewhere, no route planned: asked for each frame's step. Null stops it
     * where it stands. Either way a link it meant to cross, or was crossing, is given up.
     */
    public void steerBy(Steering way) {
        links.forget();
        stop();
        steering = way;
    }

    /**
     * A step straight to {@code to}, no route, unless something refuses it: stone or anything solid — where it does not
     * stand in them already — or a ground mover it may not pass through and does not overlap where it stands. Taken,
     * it stands on the floor the step leads to, at its height.
     *
     * @return whether it took the step
     */
    public boolean stepStraightTo(Coord3D to) {
        var owner = getOwner();
        var here = owner.getPosition();
        if (!isBlocked(owner, here, -1) && isBlocked(owner, to, -1)
                || !GivingWay.moversOverlapping(owner, here).containsAll(GivingWay.moversOverlapping(owner, to))) {
            return false;
        }
        stepTo(owner, to, -1);
        return true;
    }

    /** Whether it is waiting its turn for a route: it asked while the frame's searching was spent. */
    public boolean isWaitingForRoute() {
        return route.waiting();
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
        takeTheGroundsLocomotor(owner, world);
        var here = owner.getPosition();
        lastStep = lastPosition == null ? 0f : across(here, lastPosition);
        lastPosition = here;
        if (world != null) {
            world.markStanding(owner);
        }
        if (links.isCrossing()) {
            var goal = links.carry(owner);
            if (goal != null) {
                moveTo(goal); // over: on to the goal from the far end
            }
            return;
        }
        if (!isMoving() && links.isPlanned() && world != null) {
            links.cross(owner, data.closeEnough()); // at its near end: carried across, and on from the far end
            return;
        }
        if (!isMoving()) {
            gaits.stand();
            giving.notHeld();
            turnToFace(owner);
            standStill(owner, world);
            return;
        }
        if (owner.isEffectivelyDead() || owner.isContained() || owner.hasStatus(ObjectStatus.DISABLED)
                || owner.hasStatus(ObjectStatus.HELD)) {
            gaits.stand();
            return; // dead, inside a transport, frozen or held — cannot move, and keeps its orders for when it can
        }
        if (standsAtWork()) {
            gaits.stand(); // at work or staggered: held, and walks on after
            return;
        }
        boolean slowed = owner.hasStatus(ObjectStatus.SLOWED);
        float share = speedShare(owner);
        float step = (slowed ? stepPerFrame * 0.5f : stepPerFrame) * share;
        if (steering != null) {
            gaits.steered(steering.step(this, step) ? step * GameConstants.LOGICFRAMES_PER_SECOND : 0f);
            return;
        }
        if (giving.asideOver(world)) {
            stop(); // ten seconds stepping aside is enough: it stays where it got to
            return;
        }
        if (giving.routeAgainDue(world)) {
            planAgain(); // its wait for a route asked too soon is over
            if (!isMoving()) {
                return;
            }
        }
        // Its quickeners lift the cap a way is walked at, as they lift its step: a rider on a route rides (E89).
        float top = (slowed ? topSpeed * 0.5f : topSpeed) * share;

        if (route.waiting() && planAgain()) {
            return; // its turn came, and its route leads nowhere nearer
        }
        if (!route.walking()) {
            return; // still waiting, and no old route to walk meanwhile
        }

        if (then == null && route.isStale(world)) { // a way it was given is walked as given
            planRoute(); // something was built or destroyed across the way — think again
            if (!isMoving()) {
                routeWalked(); // no way nearer from here
                return;
            }
        }

        walk.frame(owner, world, step, top);
    }

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

    /**
     * Out of the way of {@code from}, going along {@code way}: to the nearest block it may have whose ground stays clear
     * of it, for up to 10 seconds — going on afterwards with the walk it was on, if it was on its way somewhere: to a
     * place, exactly onto a point — a thing to take or use — or into a band round a thing, and through a second step
     * aside as through the first — or, where there is no such block, through the movers in its way meanwhile ({@code
     * aiMoveAwayFromUnit}).
     */
    void stepAsideFor(GameObject from, List<Coord3D> way) {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null) {
            return;
        }
        var aside = giving.placeAside(owner, world, from, way);
        if (aside == null) {
            return;
        }
        var goOnTo = giving.stepsAside() ? then : isMoving() ? walkingNow() : null;
        this.band = null;
        this.then = null;
        this.destination = aside;
        this.toPlace = true;
        this.exactly = false;
        this.stoppedShort = false;
        this.lookedAgain = false;
        giving.forgetRound();
        planRoute();
        this.then = goOnTo;
        giving.asideFrom(world, from);
    }

    /** The walk it is on, to go on with once it has stepped aside; null for one it has no way back into. */
    private Walk walkingNow() {
        if (band != null) {
            return new Walk(destination, true, band); // not yet planned: into the band once it is
        }
        if (toPlace) {
            return sentTo == null ? null : new Walk(sentTo, false, null);
        }
        return exactly ? new Walk(destination, true, null) : null;
    }

    /** How many routes it has been given since it was made. */
    int plans() {
        return route.plans();
    }

    Data data() {
        return data;
    }

    float stepPerFrame() {
        return stepPerFrame;
    }

    float turnPerFrame() {
        return turnPerFrame;
    }

    /** How far it went the frame before. */
    float lastStep() {
        return lastStep;
    }

    Route route() {
        return route;
    }

    GivingWay givingWay() {
        return giving;
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

    /** Whether it is going exactly onto its point, not its close-enough distance short — see {@link #moveExactlyTo}. */
    boolean goesExactly() {
        return exactly;
    }

    /** Its walk as it stands and a locomotor the game gave it; refused while it walks by a steering, which is code. */
    @Override
    public void save(SaveOut out) {
        if (steering != null) {
            throw new UnsavedModuleException(MoveUpdate.class.getName(), "thing " + getOwner().getId().value() + " ("
                    + getOwner().getTemplate().name() + ") walks by a steering (steerBy, "
                    + steering.getClass().getName() + "): that is code, which a save cannot hold");
        }
        boolean ownLocomotor = given.equals(template);
        out.writeBoolean(ownLocomotor);
        if (!ownLocomotor) {
            writeData(out, given);
        }
        out.writeString(movingOn).writeFloat(topSpeed).writeFloat(stepPerFrame).writeFloat(turnPerFrame);
        out.writePlace(destination).writeBoolean(stoppedShort).writeBoolean(lookedAgain);
        writeWalk(out, then);
        out.writePlaces(through).writePlace(throughTo).writePlace(facing);
        out.writeBoolean(toPlace).writePlace(sentTo).writeBoolean(exactly);
        writeBand(out, band);
        out.writePlace(lastPosition).writeFloat(lastStep);
        progress.save(out);
        route.save(out);
        links.save(out, getOwner().getWorld());
        giving.save(out);
        gaits.save(out);
    }

    @Override
    public void load(SaveIn in) {
        given = in.readBoolean() ? template : readData(in);
        movingOn = in.readString();
        var onIt = movingOn == null ? null : given.classLocomotors().get(movingOn);
        data = onIt == null ? given : onIt; // what takeTheGroundsLocomotor chose: a class's locomotor, or its own
        topSpeed = in.readFloat();
        stepPerFrame = in.readFloat();
        turnPerFrame = in.readFloat();
        progress.setStep(stepPerFrame);
        destination = in.readPlace();
        stoppedShort = in.readBoolean();
        lookedAgain = in.readBoolean();
        then = readWalk(in);
        through = List.copyOf(in.readPlaces());
        throughTo = in.readPlace();
        facing = in.readPlace();
        toPlace = in.readBoolean();
        sentTo = in.readPlace();
        exactly = in.readBoolean();
        band = readBand(in);
        lastPosition = in.readPlace();
        lastStep = in.readFloat();
        progress.load(in);
        route.load(in);
        links.load(in);
        giving.load(in);
        gaits.load(in);
    }

    private void writeWalk(SaveOut out, Walk walk) {
        out.writeBoolean(walk != null);
        if (walk != null) {
            out.writePlace(walk.to()).writeBoolean(walk.exactly());
            writeBand(out, walk.within());
        }
    }

    private Walk readWalk(SaveIn in) {
        return in.readBoolean() ? new Walk(in.readPlace(), in.readBoolean(), readBand(in)) : null;
    }

    /** A band by its thing's id, or where it lay once killed and gone; gone without dying, it is still planned round. */
    private void writeBand(SaveOut out, Band band) {
        out.writeBoolean(band != null);
        if (band == null) {
            return;
        }
        var what = band.what();
        boolean here = what != null && getOwner().getWorld().findObject(what.getId()) == what;
        if (what != null && !here && !what.isEffectivelyDead()) {
            throw new UnsavedModuleException(MoveUpdate.class.getName(), "thing " + getOwner().getId().value() + " ("
                    + getOwner().getTemplate().name() + ") is going within reach of thing " + what.getId().value()
                    + ", which has left the world without dying: a load would have nothing to plan its way round");
        }
        out.writeThing(here ? what : null);
        if (!here) {
            out.writePlace(band.at());
        }
        out.writeFloat(band.least()).writeFloat(band.most());
    }

    private Band readBand(SaveIn in) {
        if (!in.readBoolean()) {
            return null;
        }
        var id = in.readId();
        GameObject what = null;
        if (id != null) {
            what = in.world().findObject(id);
            if (what == null) {
                throw new UnsavedModuleException(MoveUpdate.class.getName(), "thing " + getOwner().getId().value()
                        + " was saved going within reach of thing " + id.value() + ", which is not in the world");
            }
        }
        var fellAt = id == null ? in.readPlace() : null;
        return new Band(what, in.readFloat(), in.readFloat(), fellAt);
    }

    private static void writeData(SaveOut out, Data data) {
        out.writeFloat(data.speed()).writeFloat(data.turnRate()).writeFloat(data.acceleration())
                .writeFloat(data.braking()).writeFloat(data.accelerationDamaged()).writeFloat(data.brakingDamaged())
                .writeFloat(data.damagedBelow()).writeFloat(data.minSpeed()).writeFloat(data.minTurnSpeed())
                .writeFloat(data.closeEnough()).writeBoolean(data.canMoveBackwards()).writeEnum(data.gait())
                .writeInt(data.pathPriority()).writeEnum(data.movePriority()).writeStrings(data.surfaces())
                .writeInt(data.classLocomotors().size());
        for (var onClass : data.classLocomotors().entrySet()) {
            out.writeString(onClass.getKey());
            writeData(out, onClass.getValue());
        }
    }

    private static Data readData(SaveIn in) {
        float speed = in.readFloat();
        float turnRate = in.readFloat();
        float acceleration = in.readFloat();
        float braking = in.readFloat();
        float accelerationDamaged = in.readFloat();
        float brakingDamaged = in.readFloat();
        float damagedBelow = in.readFloat();
        float minSpeed = in.readFloat();
        float minTurnSpeed = in.readFloat();
        float closeEnough = in.readFloat();
        boolean canMoveBackwards = in.readBoolean();
        var gait = in.readEnum(Gait.class);
        int pathPriority = in.readInt();
        var movePriority = in.readEnum(MovePriority.class);
        var surfaces = in.readStrings();
        var classLocomotors = new java.util.TreeMap<String, Data>();
        for (int count = in.readInt(); count > 0; count--) {
            var onClass = in.readString();
            classLocomotors.put(onClass, readData(in));
        }
        return new Data(speed, turnRate, acceleration, braking, accelerationDamaged, brakingDamaged, damagedBelow,
                minSpeed, minTurnSpeed, closeEnough, canMoveBackwards, gait, pathPriority, movePriority, surfaces,
                classLocomotors);
    }
}
