package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ScriptedMove;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Facing;
import uz.dukeengine.rpg.Seal;

/**
 * What a skill moves: the caster -- a {@code DASH}, a {@code BLINK}, a {@code SKEWER} that carries him -- or those it
 * catches -- a {@code PULL} to his front, a {@code PUSH} away from a spot. Every landing is walked with
 * {@link StrictMath}.
 */
final class Moves {

    /** How far apart a dash checks the ground it is crossing. */
    static final float DASH_STEP = 5f;

    /** How far apart a blink checks for floor while it walks its landing back. */
    private static final float BLINK_STEP = 5f;

    /** How finely a push's line is walked: a unit at a time, so it ends exactly where it is sent. */
    private static final float PUSH_STEP = 1f;

    private final GameObject owner;
    private final Blows blows;
    private final Seal seal;
    private final CastMarks marks;

    /** The {@code SKEWER} carrying him, or null. */
    private Charge charge;

    Moves(GameObject owner, Blows blows, Seal seal, CastMarks marks) {
        this.owner = owner;
        this.blows = blows;
        this.seal = seal;
        this.marks = marks;
    }

    /** Whether a charge is carrying him: his legs are not his own until it ends. */
    boolean isCharging() {
        return charge != null;
    }

    /** A frame of the charge carrying him, if one is. */
    void driveTheCharge() {
        if (charge != null && !charge.drive()) {
            var ended = charge;
            charge = null; // his legs his own again before what it caught is dropped
            ended.end();
        }
    }

    boolean dash(Skill skill, int level, World world, Coord3D towards) {
        float facing = owner.getOrientation();
        if (towards != null) {
            // Face where he was sent before he goes, so the model and the
            // travel agree — and so the next thing he does looks that way.
            Facing.turnToward(owner, towards);
        }
        var from = owner.getPosition();
        var end = dashEnd(owner, world, reachOf(skill, owner, towards));
        if (seal.parts(world, from, end)) {
            // It comes down over a seal (see {@link uz.dukeengine.rpg.Seal}): refused, as a blink into
            // a pillar is -- nothing spent, and not so much as turned. No charge
            // crossing it, the trample below only ever finds his own side.
            owner.setOrientation(facing);
            return false;
        }
        owner.setPosition(end);
        // A charge hurts what it goes through; a sprint does not. Which of
        // the two it is, is a number in the file rather than a second
        // effect here — so the archer's sprint is untouched by having said
        // nothing about damage.
        if (skill.damage() > 0f) {
            trample(world, from, blows.damageOf(skill, level), skill.radius());
        }
        return true;
    }

    boolean blink(Skill skill, World world, Coord3D towards) {
        if (towards == null) {
            return false;
        }
        var landing = somewhereHeCanStand(owner, world, Blows.withinReach(owner, towards, skill.distance()));
        if (landing == null || seal.parts(world, owner.getPosition(), landing)) {
            // Nowhere along that line is floor, or where it comes down is over
            // a seal (see {@link uz.dukeengine.rpg.Seal}); the cast is not spent.
            return false;
        }
        var leaving = owner.getPosition();
        Facing.turnToward(owner, landing);
        owner.setPosition(landing);
        // Both ends, and the first of them read before he moves -- the
        // client flashes the place he left as well as the place he
        // arrived, and half a blink is a teleport with a bug.
        Blows.fired(owner, null, leaving, landing);
        return true;
    }

    /** A {@code SKEWER}: he drives on along his facing; one of no speed is over in the frame it is cast, as a dash is. */
    boolean skewer(Skill skill, int level, Coord3D towards) {
        if (towards != null) {
            Facing.turnToward(owner, towards);
        }
        var legs = owner.getLocomotor();
        if (legs != null) {
            legs.stop();
        }
        float each = blows.damageOf(skill, level);
        charge = new Charge(owner, blows, seal, skill, each, blows.critical(), reachOf(skill, owner, towards));
        if (skill.projectileSpeed() <= 0f) {
            while (charge != null) {
                driveTheCharge();
            }
        }
        return true;
    }

    boolean pull(Skill skill, int level, World world) {
        var point = pullPoint(owner, world, skill.range());
        float each = blows.damageOf(skill, level);
        for (var victim : blows.enemiesWithin(world, skill.radius())) {
            blows.placeNear(victim, world, point, owner.getPosition(), Float.NaN);
            blows.strike(victim, each);
            Blows.daze(victim, skill.stunFrames());
        }
        return true;
    }

    boolean push(Skill skill, int level, World world, Coord3D towards) {
        var middle = towards != null ? towards : owner.getPosition();
        float facing = owner.getOrientation();
        float each = blows.damageOf(skill, level);
        int frames = Math.max(1, skill.durationFrames());
        // The farthest first, so the outer ones clear the way the inner are thrown along; a victim in the way
        // flies with the rest, and only where one already went stops the next (E95).
        var victims = new ArrayList<>(blows.enemiesWithin(world, middle, skill.radius()));
        victims.sort(Comparator.comparingDouble((GameObject v) -> -flat(v.getPosition(), middle))
                .thenComparingInt(v -> v.getId().value()));
        var thrown = new HashSet<ObjectId>();
        victims.forEach(v -> thrown.add(v.getId()));
        var landed = new ArrayList<GameObject>();
        var ends = new ArrayList<Coord3D>();
        for (var victim : victims) {
            var stands = victim.getPosition();
            float dx = stands.x() - middle.x();
            float dy = stands.y() - middle.y();
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1e-3f) {
                dx = (float) StrictMath.cos(facing);
                dy = (float) StrictMath.sin(facing);
            } else {
                dx /= length;
                dy /= length;
            }
            var end = pushEnd(victim, world, dx, dy, skill.distance(), thrown, landed, ends);
            landed.add(victim);
            ends.add(end);
            if (skill.damage() > 0f) {
                blows.hurt(victim, each);
            }
            if (!skill.look().isEmpty()) {
                marks.markOn(skill, victim, 0f);
            }
            int stun = skill.stunFrames();
            ScriptedMove.along(victim, end, frames, skill.hitWidth()).onLanded(() -> Blows.daze(victim, stun));
        }
        return true;
    }

    /**
     * How far a dash actually carries: the skill's distance, or the spot he was
     * pointed at if that is nearer. Sent two steps away he takes two steps —
     * being flung the full distance past a click is not what the click said.
     */
    private static float reachOf(Skill skill, GameObject owner, Coord3D towards) {
        return towards == null ? skill.distance()
                : Math.min(skill.distance(), owner.getPosition().distance(towards));
    }

    /** Where a push of {@code distance} along ({@code dx}, {@code dy}) leaves {@code victim}: short of stone and seals. */
    private Coord3D pushEnd(GameObject victim, World world, float dx, float dy, float distance,
            Set<ObjectId> thrown, List<GameObject> landed, List<Coord3D> ends) {
        var from = victim.getPosition();
        var end = new Coord3D(from.x(), from.y(), from.z());
        for (float gone = PUSH_STEP; gone <= distance + 1e-3f; gone += PUSH_STEP) {
            var step = new Coord3D(from.x() + dx * gone, from.y() + dy * gone, from.z());
            if (world.isGroundBlocked(step) || world.findBlocker(victim, step, thing -> thrown.contains(thing.getId()))
                    != null || seal.parts(world, from, step) || onALanding(victim, step, landed, ends)) {
                break;
            }
            end = step;
        }
        return new Coord3D(end.x(), end.y(), world.groundHeight(end));
    }

    /** Whether {@code victim} at {@code step} would stand in one thrown before it, where that one comes down. */
    private static boolean onALanding(GameObject victim, Coord3D step, List<GameObject> landed, List<Coord3D> ends) {
        for (int i = 0; i < landed.size(); i++) {
            float room = radiusOf(victim) + radiusOf(landed.get(i));
            if (flat(step, ends.get(i)) < room) {
                return true;
            }
        }
        return false;
    }

    private static float radiusOf(GameObject thing) {
        return thing.getGeometry() == null ? 0f : thing.getGeometry().footprintRadius();
    }

    private static float flat(Coord3D a, Coord3D b) {
        float x = a.x() - b.x();
        float y = a.y() - b.y();
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Where the caster comes down.
     *
     * <p>He goes <em>over</em> what is between: a wall in the way is a wall he
     * clears, and a skeleton in the way is one he is past before it can turn
     * round. That is the whole point of the skill — an escape that any corridor
     * could cancel is not an escape, and a dash that stops at the first thing it
     * meets stops at the thing it was meant to get away from.
     *
     * <p>What he may not do is land inside something. So the arc is walked from
     * the far end back, and he comes down on the first clear spot at or before
     * where he was pointed: aimed into rock he falls short of it rather than into
     * it, and a unit whose centre is in stone stays the bug it was the day it took
     * an afternoon to find.
     */
    private static Coord3D dashEnd(GameObject owner, World world, float distance) {
        float facing = owner.getOrientation();
        float dx = (float) StrictMath.cos(facing);
        float dy = (float) StrictMath.sin(facing);
        var from = owner.getPosition();
        for (float gone = distance; gone >= DASH_STEP; gone -= DASH_STEP) {
            var overThere = new Coord3D(from.x() + dx * gone, from.y() + dy * gone, from.z());
            // On the floor he lands on, not the one he left. He clears whatever is
            // between, and that can include a step up: coming down on the storey
            // he took off from would put him inside its floor.
            var step = new Coord3D(overThere.x(), overThere.y(), world.groundHeight(overThere));
            if (!world.isGroundBlocked(step) && world.findBlocker(owner, step) == null) {
                return step;
            }
        }
        return from; // nowhere to come down: he stays where he is
    }

    /**
     * Where a blink actually puts him: the spot he chose, or the nearest place
     * short of it he could have stood.
     *
     * <p>Walked back from the far end the way a dash's landing is, and for the
     * same reason -- somewhere he could not stand is not somewhere he may appear.
     * What it does not do is stop at the first thing in between: a dash is
     * stopped by the wall, and this is bought precisely to ignore it.
     *
     * @return the landing, or null if nowhere along that line is floor -- in which
     *     case the cast is refused rather than quietly cancelled, so the cooldown
     *     survives a blink into a pillar
     */
    private static Coord3D somewhereHeCanStand(GameObject owner, World world, Coord3D wanted) {
        var from = owner.getPosition();
        float dx = wanted.x() - from.x();
        float dy = wanted.y() - from.y();
        float away = (float) StrictMath.sqrt(dx * dx + dy * dy);
        if (away <= 0.0001f) {
            return null; // he pointed at his own feet; that is not somewhere else
        }
        for (float gone = away; gone >= BLINK_STEP; gone -= BLINK_STEP) {
            float share = gone / away;
            var overThere = new Coord3D(from.x() + dx * share, from.y() + dy * share, from.z());
            // The floor he arrives on, not the one he left -- he crossed a storey
            // as easily as a wall, and coming down on the old height would bury him.
            var step = new Coord3D(overThere.x(), overThere.y(), world.groundHeight(overThere));
            if (!world.isGroundBlocked(step) && world.findBlocker(owner, step) == null) {
                return step;
            }
        }
        return null;
    }

    /** {@code reach} in front of him, or as near it as there is floor, walked back toward him from the far end. */
    private static Coord3D pullPoint(GameObject owner, World world, float reach) {
        float facing = owner.getOrientation();
        var from = owner.getPosition();
        for (float gone = Math.max(0f, reach); gone > 0f; gone -= DASH_STEP) {
            var at = new Coord3D(from.x() + (float) StrictMath.cos(facing) * gone,
                    from.y() + (float) StrictMath.sin(facing) * gone, 0f);
            var step = new Coord3D(at.x(), at.y(), world.groundHeight(at));
            if (!world.isGroundBlocked(step)) {
                return step;
            }
        }
        return from;
    }

    /**
     * Hurt whoever he went through, rather than only whoever he landed among.
     *
     * <p>Sampled along the line he travelled instead of taken at either end,
     * because the whole of a charge is the monsters between here and there. Three
     * samples rather than a swept shape: a charge is short, a monster is wide, and
     * an exact sweep would be a lot of arithmetic for a difference nobody could
     * see. Each victim is hurt once however many samples find it.
     *
     * <p>It does not <em>shove</em> anything. Pushing a body out of the way is a
     * question about collision and the navigation grid rather than about a skill,
     * and it is not one this answers.
     */
    private void trample(World world, Coord3D from, float each, float radius) {
        var to = owner.getPosition();
        // Ordered, so the same charge hurts the same monsters in the same order on
        // every machine -- enemiesWithin is already ordered, and this keeps it.
        var struck = new LinkedHashSet<GameObject>();
        for (int sample = 0; sample <= 2; sample++) {
            float along = sample / 2f;
            var at = new Coord3D(
                    from.x() + (to.x() - from.x()) * along,
                    from.y() + (to.y() - from.y()) * along,
                    from.z() + (to.z() - from.z()) * along);
            struck.addAll(blows.enemiesWithin(world, at, radius));
        }
        blows.strikeAll(struck, each);
    }
}
