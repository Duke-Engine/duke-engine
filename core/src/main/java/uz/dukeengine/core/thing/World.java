package uz.dukeengine.core.thing;

import java.util.function.Predicate;
import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Path;
import uz.dukeengine.core.player.Player;
import uz.dukeengine.core.player.Relationship;

/**
 * The simulation context a {@link GameObject} (and its modules) can query —
 * the role SAGE fills with the global {@code TheGameLogic}/{@code ThePlayerList}
 * singletons.
 *
 * <p>Exposed as an interface so modules can find other objects and check
 * diplomacy without the {@code thing} package depending on {@code GameLogic}
 * directly (which depends on {@code thing}) — breaking what would otherwise be a
 * package cycle. {@code GameLogic} implements this.
 */
public interface World {

    /** The live object with this id, or {@code null} if none. */
    GameObject findObject(ObjectId id);

    /** The current logic frame number. */
    int getFrame();

    /** The diplomatic stance player {@code a} holds toward player {@code b}. */
    Relationship getRelationship(int a, int b);

    /** The player at this index, or {@code null} if none. */
    Player getPlayer(int index);

    /** The unit template registered under this name, or {@code null}. */
    ThingTemplate findTemplate(String name);

    /** Create a new object from a template at a position, owned by a player. */
    GameObject spawn(ThingTemplate template, Coord3D position, int playerIndex);

    /**
     * The same, with {@code setup} run on the thing before anything is told it is made — its facing, a status it is
     * made with: a construction site is made under construction, so what reaches a new thing reaches it knowing so.
     */
    default GameObject spawn(ThingTemplate template, Coord3D position, int playerIndex,
            java.util.function.Consumer<GameObject> setup) {
        var thing = spawn(template, position, playerIndex);
        setup.accept(thing);
        return thing;
    }

    /**
     * Every live object, in creation order — the whole-world scan a module needs
     * when a range query will not do (tallying a player's assets, say). Prefer
     * {@link #findClosest} or {@link #objectsInRange} when they fit.
     */
    java.util.List<GameObject> getObjects();

    /**
     * The nearest object to {@code center} within {@code range} that satisfies
     * {@code filter}, or {@code null} if none. Lets modules acquire targets
     * without depending on the partition package (avoids a package cycle); the
     * filter is a plain {@link Predicate} so only {@code thing} types leak here.
     */
    GameObject findClosest(Coord3D center, float range, Predicate<GameObject> filter);

    /**
     * The nearest object {@code from} can reach within {@code reach}, measured
     * surface to surface rather than centre to centre — so a wide building counts
     * as soon as its wall is in range.
     *
     * <p>This is what anything with a working distance should use: weapons,
     * repair, capture, boarding. Objects with no {@link Geometry} measure centre
     * to centre, so data written before shapes existed behaves as it always did.
     */
    GameObject findClosestInReach(GameObject from, float reach, Predicate<GameObject> filter);

    /** How far {@code a} is from {@code b}, surface to surface; negative if they overlap. */
    static float reachBetween(GameObject a, GameObject b) {
        return Footprint.of(a).separation(Footprint.of(b));
    }

    /** A still thing's words changed: what the game has it lay over its footprint by a word is laid again. */
    default void stillThingsWordsChanged() {
    }

    /** {@code thing} stands somewhere else now — told each move, for the world to know where its things stand. */
    default void thingMoved(GameObject thing) {
    }

    /** A still thing turned or moved: its footprint is laid on the ground again before the next route. */
    default void stillThingMoved() {
    }

    /** How wide a cell of the world's ground is: the width of the band "beside" a thing is measured in. */
    default float cellSize() {
        return uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
    }

    /**
     * Whether {@code who} is standing near enough to {@code what} to work on it: within a cell of touching. A hair over
     * counts — {@link #standingNextTo} stops a mover exactly a cell short, and the sums that put it there end a few
     * millionths past it.
     */
    default boolean isBeside(GameObject who, GameObject what) {
        return reachBetween(who, what) <= cellSize() * (1f + 1e-4f);
    }

    /**
     * Where {@code who} should stand to be just short of touching {@code what}.
     *
     * <p>Sent at a thing's own position, a unit is sent at a square the thing is standing on — and anything
     * that does not move is baked into the obstacle layer, so that square cannot be walked to and the path
     * fails before a step is taken. So it stops a whole cell short — less than that and the square it is sent
     * to is still inside the thing's own footprint. x and y are the ground; z is height.
     *
     * <p>Measured as it will stand there — facing its way to it, as a mover arrives — not as it stands now: a truck
     * standing a little turned at its pile reaches less far toward the depot once it is square to it, and its move
     * ended a hair more than a cell off.
     *
     * <p>This is the straight-line answer, which is all a world with no ground to walk on can give. A world
     * that has one gives a spot that can be stood on and walked to as well — see {@code GameLogic}.
     */
    default Coord3D standingNextTo(GameObject who, GameObject what) {
        var here = who.getPosition();
        if (reachBetween(who, what) - cellSize() <= 0f) {
            return here;
        }
        // Toward the nearest point of its outline, not its middle: heading for the middle of a box from off a
        // corner stops short of touching it — a saboteur stood 13.28 off a supply centre's corner, needing 10.
        var target = Footprint.of(what);
        var there = target.nearestTo(here);
        float across = there.x() - here.x();
        float along = there.y() - here.y();
        float span = (float) Math.sqrt(across * across + along * along);
        if (span <= 0.0001f) {
            return here;
        }
        var arriving = new Footprint(Solid.of(who.getTemplate()), here, (float) StrictMath.atan2(along, across));
        float gap = Math.max(0f, arriving.separation(target) - cellSize());
        return new Coord3D(here.x() + across / span * gap, here.y() + along / span * gap, here.z());
    }

    /**
     * Where on the straight line through {@code who} it stands between {@code least} and {@code most} of {@code what},
     * measured as a reach is, outline to outline: nearer where it is further than {@code most}, further away where it
     * is nearer than {@code least}, where it stands where it is within both. The straight-line answer to
     * {@link #findPathWithin}, all a world with no ground to walk on can give.
     */
    default Coord3D withinOf(GameObject who, GameObject what, float least, float most) {
        var here = who.getPosition();
        float gap = reachBetween(who, what);
        float by = gap > most ? gap - most : gap < least ? gap - least : 0f;
        var there = what.getPosition();
        float across = there.x() - here.x();
        float along = there.y() - here.y();
        float span = (float) Math.sqrt(across * across + along * along);
        if (by == 0f || span <= 0.0001f) {
            return here;
        }
        return new Coord3D(here.x() + across / span * by, here.y() + along / span * by, here.z());
    }

    /**
     * A route for {@code mover} that ends at the nearest place between {@code least} and {@code most} of {@code what},
     * outline to outline — a route to fight, ending where its weapon first reaches (the reference's {@code
     * findAttackPath}) — or {@code null} where the frame's searching is spent, as {@link #findPath(GameObject,
     * Coord3D)}. A world with no ground to walk on goes straight to {@link #withinOf}.
     */
    default Path findPathWithin(GameObject mover, GameObject what, float least, float most) {
        return findPath(mover, withinOf(mover, what, least, most));
    }

    /** The name of the class of ground under {@code thing} — climbing, wading — or null for plain ground. */
    default String groundClassUnder(GameObject thing) {
        return null;
    }

    /** Every object within {@code range} of {@code center} that satisfies {@code filter}. */
    java.util.List<GameObject> objectsInRange(Coord3D center, float range, Predicate<GameObject> filter);

    /**
     * A navigable path from {@code from} to {@code to} around terrain obstacles,
     * or a direct single-waypoint path when the world has no navigation grid.
     * Empty if no route exists.
     */
    Path findPath(Coord3D from, Coord3D to);

    /**
     * A route for {@code mover} to {@code to}, wide enough for its body and
     * pulled straight wherever it can see ahead — or {@code null} where the frame's
     * searching is spent and it has to wait its turn: asked again next frame, it gets one.
     *
     * <p>The version to use for anything that actually walks. A path found for a
     * point grazes corners the mover then collides with, and comes out of cell
     * centres as a staircase across ground it could have crossed in a line.
     */
    Path findPath(GameObject mover, Coord3D to);

    /**
     * The same, going round the movers in {@code round} as though they were stone — those it is stuck behind — where
     * the world keeps its movers on the ground's cells; the plain route otherwise.
     */
    default Path findPath(GameObject mover, Coord3D to, java.util.Set<ObjectId> round) {
        return findPath(mover, to);
    }

    /**
     * Whether {@code mover} walks straight from {@code from} to {@code to} past nothing that stops it — the reference's
     * {@code Pathfinder::isLinePassable}, which a mover steering along its route asks; anywhere, in a world with no
     * grid.
     */
    default boolean walksStraight(GameObject mover, Coord3D from, Coord3D to) {
        return true;
    }

    // ---- ground movers on the cells ----

    /**
     * Where a ground mover sent to {@code place} is to go: the nearest block of cells round it that it may have and can
     * walk to, held as its own from now on — the reference's {@code Pathfinder::adjustDestination}, see {@code
     * GameLogic}. Where a world keeps no cells, or for a mover that keeps none, the place itself.
     */
    default Coord3D takePlace(GameObject mover, Coord3D place) {
        return place;
    }

    /**
     * The same for one of a group sent to {@code near}: the block pulled toward it, and never one whose walk from it is
     * far longer than the way across — behind a wall ({@code adjustDestination} given the group's destination).
     */
    default Coord3D takePlace(GameObject mover, Coord3D place, Coord3D near) {
        return takePlace(mover, place);
    }

    /**
     * A ground mover holding the block it stands on as its own, as it does once it stops: false where it may not —
     * an ally going there, an enemy still on it — and holds nothing.
     */
    default boolean holdPlace(GameObject mover) {
        return false;
    }

    /** Whether {@code mover} keeps a block of the ground's cells: a body walking on the ground, alive and not carried. */
    default boolean keepsCells(GameObject mover) {
        return false;
    }

    /** The block a ground mover was going to, let go: a move into something holds none. */
    default void letPlaceGo(GameObject mover) {
    }

    /** Whether the block a ground mover is going to is still its own, no ally having claimed it since. */
    default boolean holdsPlace(GameObject mover) {
        return true;
    }

    /** Where a ground mover stands, marked on the cells it covers; off the ground, its cells let go. */
    default void markStanding(GameObject mover) {
    }

    /**
     * The allies standing still on the ground {@code mover} would cover walking {@code way}: those a route through them
     * asks aside ({@code Pathfinder::moveAllies}). None where a world keeps no cells.
     */
    default java.util.List<GameObject> stillAlliesOn(GameObject mover, java.util.List<Coord3D> way) {
        return java.util.List.of();
    }

    /**
     * A place for {@code mover} to step aside to, out of the way {@code from} is going: the nearest block it may have
     * whose ground, its width and {@code from}'s, stays clear of {@code way} — held as its own — or null for none.
     */
    default Coord3D placeAside(GameObject mover, GameObject from, java.util.List<Coord3D> way) {
        return null;
    }

    /**
     * Whether {@code mover} runs over {@code other} rather than being held up by it — a crusher its victim, which is
     * never an ally. Nothing is, where the world says nothing of crushing.
     */
    default boolean runsOver(GameObject mover, GameObject other) {
        return false;
    }

    /**
     * Increments whenever the navigable world changes shape — a building goes up
     * or comes down.
     *
     * <p>A path is a plan made against the world as it was. Rather than have the
     * simulation hunt down everything holding a stale plan, anything that keeps
     * one remembers this number and notices for itself that its route needs
     * rethinking.
     */
    int getNavigationVersion();

    /**
     * Announce that something happened, for the presentation layer to react to.
     *
     * <p>One way only: nothing in the simulation reads these back, so posting one
     * can never change what happens next. Modules use it to report moments a
     * snapshot cannot express — a shot fired, a wall breached.
     */
    void post(WorldEvent event);

    /**
     * Play the effect of that name at a point, turned to {@code facing} — see
     * {@link uz.dukeengine.core.event.EffectPlayed}: shown by every machine's
     * client from this frame, where its player sees the point. An event, so out
     * of the checksum.
     */
    default void effect(String name, uz.dukeengine.core.math.Coord3D where, float facing) {
        post(new uz.dukeengine.core.event.EffectPlayed(getFrame(), name, where, facing, null));
    }

    /**
     * Make a beam the simulation owns — a laser, the reference's orbital cannon — drawn between two points of the
     * world as the game's look {@code look} says ({@code Laser}), at {@code width} of its full width, 0 to 1, by every
     * client that sees either end, until {@link #endBeam}. Moved as often as the simulation likes with {@link
     * #moveBeam}. Drawing only: out of the checksum, not saved, and nothing the simulation decides reads it.
     *
     * @return its number, for {@link #moveBeam} and {@link #endBeam}; -1 where this world draws none
     */
    default int beam(String look, uz.dukeengine.core.math.Coord3D from, uz.dukeengine.core.math.Coord3D to,
            float width) {
        return -1;
    }

    /** A beam moved, and its width set: drawn so from this frame. */
    default void moveBeam(int beam, uz.dukeengine.core.math.Coord3D from, uz.dukeengine.core.math.Coord3D to,
            float width) {
    }

    /** A beam ended: gone from every client from this frame. */
    default void endBeam(int beam) {
    }

    /**
     * Keep {@code thing} riding the stream {@code stream} — a flame tank's jet, the reference's {@code
     * ProjectileStreamUpdate}: the places of up to {@code most} things riding it, in the order they started, each
     * dropped as it ends, drawn by a client as one ribbon of the game's look through them in order ({@code
     * W3DProjectileStreamDraw}). Drawing only: out of the checksum, not saved, and nothing decided reads it.
     */
    default void rideStream(String stream, GameObject thing, int most) {
    }

    /** A gap in {@code stream} from here — its aim changed: its ribbon is broken there, not drawn across. */
    default void breakStream(String stream) {
    }

    /**
     * Play the game's picture strip of that name at a point, for {@code seconds}, rising {@code rise} over them and
     * fading as they end — see {@link uz.dukeengine.core.event.StripPlayed}: shown where its player sees the point. An
     * event, so out of the checksum.
     */
    default void strip(String name, uz.dukeengine.core.math.Coord3D where, float seconds, float rise) {
        post(new uz.dukeengine.core.event.StripPlayed(getFrame(), name, where, seconds, rise));
    }

    /**
     * Play the sound cue of that name at {@code thing}, following it — see {@link
     * uz.dukeengine.core.event.SoundPlayed}: heard by every machine's client from this frame where its player sees the
     * thing; asked again within {@code hold} frames it goes on, not asked it stops; 0 plays it once. An event, so out
     * of the checksum.
     */
    default void sound(String cue, GameObject thing, int hold) {
        post(new uz.dukeengine.core.event.SoundPlayed(getFrame(), cue, thing.getPosition(), thing.getId(),
                Math.max(0, hold)));
    }

    /** The same at a point of the world. */
    default void sound(String cue, uz.dukeengine.core.math.Coord3D where, int hold) {
        post(new uz.dukeengine.core.event.SoundPlayed(getFrame(), cue, where, null, Math.max(0, hold)));
    }

    /** Play the effect of that name riding {@code thing}, from where it stands and turned the way it faces. */
    default void effect(String name, GameObject thing) {
        post(new uz.dukeengine.core.event.EffectPlayed(getFrame(), name, thing.getPosition(), thing.getOrientation(),
                thing.getId()));
    }

    /**
     * Play the particle system of that name riding {@code thing} until {@link #endEffect} — at {@code bone} of its
     * model, or, where it names none or the model has no such bone, at {@code offset} in the thing's own frame (x its
     * forward, z up), or its place: the reference's damage smoke at a numbered SMOKE bone, stopped by a repair, a
     * laser's flare, an orbital cannon's glow at its antenna. Drawn by every client that sees the thing; a thing gone
     * ends its effects. Drawing only: out of the checksum, not saved, and nothing the simulation decides reads it.
     *
     * @return its number, for {@link #endEffect}; -1 where this world draws none
     */
    default int effect(String name, GameObject thing, String bone, uz.dukeengine.core.math.Coord3D offset) {
        return -1;
    }

    /** An effect ended: it lets out nothing more from this frame, and what it let out is left to finish. */
    default void endEffect(int effect) {
    }

    /**
     * The object that would be in the way if {@code mover} stood at
     * {@code position}, or {@code null} if the space is free — the question a
     * locomotor asks before every step.
     *
     * <p>{@code mover} never blocks itself, and neither do objects that are not
     * physically present: the dead (already leaving the world) and the contained
     * (riding inside something else). An object with no {@link Geometry} blocks
     * nothing and is blocked by nothing.
     */
    GameObject findBlocker(GameObject mover, Coord3D position);

    /** The same, passing over the things {@code passing} takes: the movers a step does not stop for. */
    default GameObject findBlocker(GameObject mover, Coord3D position, Predicate<GameObject> passing) {
        var blocker = findBlocker(mover, position);
        return blocker == null || passing.test(blocker) ? null : blocker;
    }

    /**
     * Whether the ground at {@code position} cannot be walked on.
     *
     * <p>The question a locomotor has to ask before every step, and could not.
     * {@link #findBlocker} answers "who is in the way", which is only half of what
     * is in the way: walls were left entirely to pathfinding, so anything that
     * moved for a reason the path did not anticipate — swerving around a
     * neighbour, most of all — could walk into stone unopposed. Once there it was
     * beyond help, because a search that starts on blocked ground has nowhere to
     * begin.
     *
     * <p>False where a world has no navigation grid at all: open ground everywhere.
     */
    boolean isGroundBlocked(Coord3D position);

    /**
     * Whether something may move between two points that are a step apart.
     *
     * <p>Stone is one reason it may not, and the one that has always been here.
     * The other is height: a floor that stands above the one you are on is not
     * somewhere you can walk, however open the ground looks from below. Where the
     * two are joined — a stair, a slope, a ladder — this says yes.
     *
     * <p>True everywhere in a world with no navigation grid, and true for any two
     * points on flat ground, which is every world that never sets a level.
     */
    boolean canStep(Coord3D from, Coord3D to);

    /**
     * How high the ground stands under a position — what an object's {@code z}
     * should be while it is standing there.
     *
     * <p>Zero on flat ground, which is to say almost everywhere: a world only has
     * a height under it once a map has said so.
     */
    float groundHeight(Coord3D position);

    /** How far the map reaches across x, in world units — 0 for a world with no edge to keep inside. */
    default float mapWidth() {
        return 0f;
    }

    /**
     * Whether {@code mover} may step between two points: along its own floor ({@link #canStep(Coord3D, Coord3D)} on
     * the ground), or onto floor {@code toward} where the step is a deck's entry or end; -1 for no other floor.
     */
    default boolean canStep(GameObject mover, Coord3D from, Coord3D to, int toward) {
        return canStep(from, to);
    }

    /**
     * Whether scenery standing in the way refuses {@code mover} a step from {@code from} to {@code to}: one that
     * takes it deeper into a piece's footprint than it already stands, more than a touch. None, in a world with none.
     */
    default boolean sceneryInTheWay(GameObject mover, Coord3D from, Coord3D to) {
        return false;
    }

    /** Whether the ground {@code mover} stands on is stone under a point: {@link #isGroundBlocked}, on its floor. */
    default boolean isGroundBlocked(GameObject mover, Coord3D position) {
        return isGroundBlocked(position);
    }

    /** How high the surface {@code mover} stands on is under a point: the ground's, or its deck's. */
    default float groundHeight(GameObject mover, Coord3D position) {
        return groundHeight(position);
    }

    /** The floor {@code mover} is on after a step: {@code toward} where the step enters it, its own otherwise. */
    default int floorAfter(GameObject mover, Coord3D from, Coord3D to, int toward) {
        return mover.getFloor();
    }

    /**
     * The damage the game names unresistable — the reference's {@code DAMAGE_UNRESISTABLE} — which a body's damage
     * scale does not change; null where it names none.
     */
    default uz.dukeengine.core.module.DamageType unresistableDamage() {
        return null;
    }

    /** How far the map reaches across y, the same. */
    default float mapHeight() {
        return 0f;
    }

    /**
     * Which floor a position stands on: the level of the cell under it. Zero on a world with one floor — and on a
     * world whose floor rises and falls, still the floor it is, however high the ground there stands.
     */
    default int levelAt(Coord3D position) {
        return 0;
    }

    /**
     * The simulation's own random numbers: the one stream everything the world decides by chance draws from,
     * seeded the same on every machine — see {@link uz.dukeengine.core.math.LogicRandom}.
     */
    uz.dukeengine.core.math.LogicRandom random();

    /**
     * Somewhere at or near {@code near} where {@code shape} fits without
     * overlapping anything — where to put a newly produced unit, a dropped
     * passenger, or anything else that must appear beside something solid.
     *
     * <p>Searches {@code near} first, then outward in rings, and gives up at
     * {@code searchRadius} by returning {@code near} itself: better a unit that
     * has to walk out of a crowd than a unit that never appears.
     */
    Coord3D findClearPosition(Geometry shape, Coord3D near, float searchRadius);
}
