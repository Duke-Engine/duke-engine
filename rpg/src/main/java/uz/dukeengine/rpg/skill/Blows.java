package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.BlowRoll;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.hero.LevelBonus;

/**
 * What every skill that hurts shares: what it is worth and whether it rolled critical, whom it may be found on --
 * never across the {@link Seal} -- and what it does to them: hurt, chilled, dazed, put somewhere.
 */
final class Blows {

    private final SkillBook book;
    private final GameObject owner;
    private final Seal seal;
    private final SkillBook.Rules rules;

    /**
     * Whether the last cast's damage was made critical by its roll — see {@link #damageOf}. The book's, not a
     * cast's: a lasting blast lands later with whatever the latest roll said.
     */
    private boolean rolledCritical;

    Blows(SkillBook book, Seal seal, SkillBook.Rules rules) {
        this.book = book;
        this.owner = book.getOwner();
        this.seal = seal;
        this.rules = rules;
    }

    /**
     * What a skill hits for: its own figure at this rank, the ultimate's window if
     * one is open, and how much harder its caster's level makes it. Only a monster
     * carries a level of its own, so a hero's figure is untouched by that last.
     */
    float damageOf(Skill skill, int rank) {
        // A critical strike rolls on a skill's damage as on a blow, once a cast.
        float rolled = BlowRoll.of(owner, owner.getWorld());
        rolledCritical = rolled > 1f;
        return skill.damageAt(rank) * book.damageMultiplier() * bonusOf(owner) * rolled;
    }

    boolean critical() {
        return rolledCritical;
    }

    /**
     * Its level's bonus, read here as well as by the weapon. A skill deals its own
     * damage rather than going through a weapon, so without asking it would hit as
     * hard at the thirtieth level as at the first.
     */
    static float bonusOf(GameObject owner) {
        var bonus = owner.findModule(LevelBonus.class);
        return bonus == null ? 1f : bonus.damageMultiplier();
    }

    /** How fast what a skill throws travels: its own figure, or the drawn arrow's. */
    float speedOf(Skill skill) {
        return skill.projectileSpeed() > 0f ? skill.projectileSpeed() : rules.shotSpeed();
    }

    /** A blow of his: it names him and his side, so a kill it makes is his to be credited with. */
    void hurt(GameObject victim, float amount) {
        hurt(owner, victim, amount, rolledCritical);
    }

    static void hurt(GameObject owner, GameObject victim, float amount, boolean critical) {
        victim.getBody().damage(amount, DamageType.NORMAL, new Death(DeathType.NORMAL, owner.getId(),
                owner.getPlayerIndex(), critical));
    }

    /** A blow of his that he drinks from — see {@link SkillBook#drink}. */
    void strike(GameObject victim, float amount) {
        hurt(victim, amount);
        SkillBook.drink(owner, amount);
    }

    /** Each of {@code victims} struck {@code each}, in their order. */
    void strikeAll(Iterable<GameObject> victims, float each) {
        for (var victim : victims) {
            strike(victim, each);
        }
    }

    List<GameObject> enemiesWithin(World world, float radius) {
        return enemiesWithin(world, owner.getPosition(), radius);
    }

    /**
     * The same, round a spot the player chose rather than round the caster -- and only on the caster's side of the
     * seal, wherever the spot is: dropped into the court from outside, it catches nobody in it.
     */
    List<GameObject> enemiesWithin(World world, Coord3D centre, float radius) {
        int player = owner.getPlayerIndex();
        var casterAt = owner.getPosition();
        return world.objectsInRange(centre, radius, candidate ->
                candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(player, candidate) == Relationship.ENEMIES
                        && !seal.parts(world, casterAt, candidate.getPosition()));
    }

    /**
     * The nearest living enemy within {@code range}, measured surface to surface
     * as weapons measure it. Ties go to the lower object id — not for fairness but
     * for reproducibility: two equidistant skeletons must not be chosen by
     * whichever the world happens to list first.
     */
    GameObject nearestEnemy(World world, float range) {
        GameObject best = null;
        float bestReach = Float.MAX_VALUE;
        for (var candidate : enemiesWithin(world, range)) {
            float reach = World.reachBetween(owner, candidate);
            if (reach < bestReach
                    || (reach == bestReach && candidate.getId().value() < best.getId().value())) {
                best = candidate;
                bestReach = reach;
            }
        }
        return best;
    }

    /**
     * The creature the player clicked, if it is still something he may hit from
     * where he stands. Everything the un-aimed path checks, asked of one named
     * thing instead of all of them.
     */
    GameObject aimedAt(World world, ObjectId at, float range) {
        var victim = world.findObject(at);
        if (victim == null || victim.getBody() == null || victim.isEffectivelyDead()) {
            return null;
        }
        if (world.getRelationship(owner, victim) != Relationship.ENEMIES
                || seal.parts(world, owner.getPosition(), victim.getPosition())) {
            return null;
        }
        return World.reachBetween(owner, victim) <= range ? victim : null;
    }

    /**
     * Leave whoever the blast round {@code centre} caught dragging his feet.
     *
     * <p>Through the engine's own timed-status module rather than a list kept
     * here, and that is why it is four lines: {@code StatusUpdate} already counts
     * a status down and clears it, {@code MoveUpdate} already halves the step of
     * anything wearing {@code SLOWED}, and a monster that carries its own timer
     * thaws by itself -- including after the hero who chilled it is dead, which a
     * list kept in his skill book would have got wrong.
     *
     * <p>A creature whose file never asked for a {@code StatusUpdate} simply does
     * not slow. Better that than the game deciding what a creature is made of
     * behind its own file's back.
     */
    void chill(World world, Coord3D centre, float radius, int frames) {
        if (frames <= 0) {
            return;
        }
        for (var victim : enemiesWithin(world, centre, radius)) {
            var timers = victim.findModule(StatusUpdate.class);
            if (timers != null) {
                timers.apply(ObjectStatus.SLOWED, frames);
            }
        }
    }

    /** Stunned {@code frames}: the engine's {@code DISABLED} on its own timers, as a stunning shot leaves it. */
    static void daze(GameObject victim, int frames) {
        var timers = frames <= 0 || victim.isEffectivelyDead() ? null : victim.findModule(StatusUpdate.class);
        if (timers != null) {
            timers.apply(ObjectStatus.DISABLED, frames);
        }
    }

    /**
     * Move {@code victim} to the first spot round {@code centre} — the centre, then rings a body wide, only the half of
     * each facing {@code ahead} where that is a number — where it can stand: not stone, no one in it, on the caster's
     * side of a seal. Left where it is where there is none.
     */
    void placeNear(GameObject victim, World world, Coord3D centre, Coord3D casterAt, float ahead) {
        float spacing = Math.max(1f, victim.getGeometry().footprintRadius() * 2f);
        boolean onlyAhead = !Float.isNaN(ahead);
        for (int ring = 0; ring <= 6; ring++) {
            int around = ring == 0 ? 1 : ring * 8;
            // Ahead only, for what is carried at a front: straight on first, then either side in turn out to a
            // quarter turn -- never back toward him, where a body would stand in his way.
            var angles = new ArrayList<Double>();
            if (onlyAhead) {
                double step = 2 * StrictMath.PI / around;
                angles.add((double) ahead);
                for (int k = 1; ring > 0 && k <= around / 4; k++) {
                    angles.add(ahead + k * step);
                    angles.add(ahead - k * step);
                }
            } else {
                for (int i = 0; i < around; i++) {
                    angles.add(2 * StrictMath.PI * i / around);
                }
            }
            for (double angle : angles) {
                var at = new Coord3D(centre.x() + (float) (StrictMath.cos(angle) * ring * spacing),
                        centre.y() + (float) (StrictMath.sin(angle) * ring * spacing), 0f);
                var step = new Coord3D(at.x(), at.y(), world.groundHeight(at));
                // Still things by the grid's rule, and bodies -- the ones already placed among them -- by distance.
                if (!world.isGroundBlocked(step) && world.findBlocker(victim, step) == null
                        && !seal.parts(world, casterAt, step)
                        && world.objectsInRange(step, spacing * 0.9f, other -> other != victim
                                && other.getBody() != null && !other.isEffectivelyDead()).isEmpty()) {
                    victim.setPosition(step);
                    var legs = victim.getLocomotor();
                    if (legs != null) {
                        legs.stop();
                    }
                    return;
                }
            }
        }
    }

    /**
     * A chosen spot, pulled back to the edge of what the skill can reach.
     *
     * <p>The same rule the client's ring draws, written once more here because the
     * simulation cannot take the client's word for anything: a command arrives
     * from a machine that may be running a different version of the game, or none.
     * What it must not do is <em>refuse</em> — the picture on screen says "as far
     * that way as you can", and the two have to say the same thing.
     */
    static Coord3D withinReach(GameObject owner, Coord3D wanted, float reach) {
        var from = owner.getPosition();
        float away = from.distance(wanted);
        if (away <= reach || away <= 0.0001f) {
            return wanted;
        }
        float share = reach / away;
        return new Coord3D(from.x() + (wanted.x() - from.x()) * share,
                from.y() + (wanted.y() - from.y()) * share, wanted.z());
    }

    /** That he loosed something from where he stands toward {@code to}: what the client flashes and sounds. */
    static void fired(GameObject owner, Coord3D to) {
        fired(owner, null, owner.getPosition(), to);
    }

    static void fired(GameObject owner, ObjectId target, Coord3D from, Coord3D to) {
        var world = owner.getWorld();
        world.post(new WeaponFired(world.getFrame(), owner.getId(), target, from, to));
    }

    /**
     * A thing of {@code template} spawned on the floor at {@code spot} for {@code owner}'s side, and its {@code module};
     * null where there is no such template, or it has no such module -- then it is taken away again.
     */
    static <M extends Module> M spawnOnFloor(GameObject owner, World world, String template, Coord3D spot,
            Class<M> module) {
        var thing = world.findTemplate(template);
        if (thing == null) {
            return null;
        }
        var spawned = world.spawn(thing, new Coord3D(spot.x(), spot.y(), world.groundHeight(spot)),
                owner.getPlayerIndex());
        var found = spawned.findModule(module);
        if (found == null) {
            spawned.markDestroyed();
        }
        return found;
    }
}
