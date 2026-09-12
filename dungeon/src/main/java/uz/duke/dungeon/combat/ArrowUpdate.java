package uz.duke.dungeon.combat;

import uz.duke.core.GameConstants;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.World;
import uz.duke.dungeon.power.PowerBook;
import uz.duke.rts.module.ExperienceModule;

/**
 * An arrow in the air: it chases what it was loosed at, and hurts it on arrival.
 *
 * <p>Chases rather than flies at a point, which is a decision and not an
 * oversight. A shot that led its target would miss whenever the target turned,
 * and a dungeon full of arrows thudding into walls behind fleeing monsters is a
 * different game — one where the hero's range is a suggestion. It follows, so the
 * shot always arrives; what the flight costs is time.
 *
 * <p>It carries the damage rather than working it out on landing. The figure was
 * final when the weapon let go of it, and the archer may have levelled — or died —
 * in the third of a second since.
 *
 * <p>No body and no geometry, on purpose. Nothing can shoot at it, because
 * weapons only acquire things with a body; and it passes through the world rather
 * than shouldering monsters aside on its way past.
 */
public final class ArrowUpdate extends UpdateModule {

    /**
     * How long an arrow may stay in the air before it is given up on.
     *
     * <p>Only a backstop. Every ordinary end — arrival, or a target that dies
     * first — happens long before this; what it catches is the case nobody
     * thought of, so that a stray arrow cannot circle the dungeon forever.
     */
    private static final int LONGEST_FLIGHT = 5 * GameConstants.LOGICFRAMES_PER_SECOND;

    private ObjectId target;
    private ObjectId shooter;
    private float damage;
    private DamageType damageType = DamageType.NORMAL;
    private float stepPerFrame;
    private int flownFor;

    /** How far a free-flying shot has left to go; not a number a homing one uses. */
    private float travelLeft;

    /**
     * How far the burst reaches when this lands, or zero for a shot that only hurts
     * what it hit.
     *
     * <p>The difference between an arrow and a fireball, and it is one number: both
     * fly, both stop at the first body, and one of them takes the rest of the room
     * with it. Carried by the shot rather than looked up on landing for the reason
     * everything else it carries is -- by then the caster may have levelled, or
     * died.
     */
    private float blastRadius;

    /**
     * The run's powers, so that an arrow can pay its archer back.
     *
     * <p>Held by the arrow rather than looked up on landing because the arrow is
     * the only thing that knows how much it dealt, and the archer may be somebody
     * else's by then — a monster's shot must not heal the hero.
     */
    private final PowerBook powers;

    public ArrowUpdate(GameObject owner, ModuleData ignored, PowerBook powers) {
        super(owner);
        this.powers = powers;
    }

    /** The empty block that puts this on the arrow; a shot fills the rest in. */
    public static ModuleData parseData(uz.duke.core.ini.Ini ini) {
        ini.initFromIni(new Object(), NO_FIELDS);
        return null;
    }

    private static final uz.duke.core.ini.FieldParseTable<Object> NO_FIELDS =
            new uz.duke.core.ini.FieldParseTable<>();

    /** Send it after something, carrying what the weapon decided it was worth. */
    void loose(GameObject from, GameObject at, float carrying, DamageType type, float speed) {
        this.shooter = from.getId();
        this.target = at.getId();
        this.damage = carrying;
        this.damageType = type;
        this.stepPerFrame = speed * GameConstants.SECONDS_PER_LOGICFRAME;
        aimAt(at.getPosition());
    }

    /**
     * Send it down a line instead, to hit whoever is standing in the way.
     *
     * <p>The same arrow with the target left out. Everything after the flight —
     * the damage, the experience, the archer's share — is the same, which is why
     * this is a second way of being loosed rather than a second module: a shot
     * that misses and a shot that homes differ only in how they choose what to
     * hit.
     */
    void looseAlong(GameObject from, Coord3D towards, float carrying, DamageType type,
            float speed, float distance, float blast) {
        this.blastRadius = blast;
        this.shooter = from.getId();
        this.target = null;
        this.damage = carrying;
        this.damageType = type;
        this.stepPerFrame = speed * GameConstants.SECONDS_PER_LOGICFRAME;
        this.travelLeft = distance;
        aimAt(towards);
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || ++flownFor > LONGEST_FLIGHT) {
            owner.markDestroyed();
            return;
        }
        if (target == null) {
            flyOn(owner, world);
            return;
        }
        var victim = world.findObject(target);
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            owner.markDestroyed(); // it died on the way; the arrow has nothing to reach
            return;
        }

        // Measured wall to wall, the way a weapon measures range — so an arrow
        // arrives at a boss's flank rather than pressing on toward its middle.
        if (World.reachBetween(owner, victim) <= stepPerFrame) {
            strike(world, victim);
            return;
        }
        aimAt(victim.getPosition());
        var here = owner.getPosition();
        var toward = victim.getPosition();
        float dx = toward.x() - here.x();
        float dy = toward.y() - here.y();
        float distance = (float) StrictMath.sqrt(dx * dx + dy * dy);
        if (distance <= 0.0001f) {
            strike(world, victim);
            return;
        }
        owner.setPosition(new Coord3D(here.x() + dx / distance * stepPerFrame,
                here.y() + dy / distance * stepPerFrame, here.z()));
    }

    /**
     * One step of a shot that was never given anything to chase.
     *
     * <p>Three ways it ends and all three are the player's to read: it runs out of
     * travel, it meets stone, or it meets somebody. The stone matters as much as
     * the body — the lane the client drew stops at a wall, and a shot that carried
     * on through one would make that picture a lie.
     */
    private void flyOn(GameObject owner, World world) {
        if (travelLeft <= 0f) {
            owner.markDestroyed();
            return;
        }
        var hit = world.findClosestInReach(owner, stepPerFrame, candidate ->
                candidate != owner
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && !candidate.isContained()
                        && world.getRelationship(owner.getPlayerIndex(),
                                candidate.getPlayerIndex()) == Relationship.ENEMIES);
        if (hit != null) {
            strike(world, hit);
            return;
        }
        float facing = owner.getOrientation();
        var here = owner.getPosition();
        var next = new Coord3D(
                here.x() + (float) StrictMath.cos(facing) * stepPerFrame,
                here.y() + (float) StrictMath.sin(facing) * stepPerFrame,
                here.z());
        if (world.isGroundBlocked(next)) {
            owner.markDestroyed(); // spent against a wall
            return;
        }
        travelLeft -= stepPerFrame;
        owner.setPosition(next);
    }

    /** Point along the flight, so it is drawn as an arrow rather than a splinter. */
    private void aimAt(Coord3D toward) {
        var here = getOwner().getPosition();
        getOwner().setOrientation((float) StrictMath.atan2(
                toward.y() - here.y(), toward.x() - here.x()));
    }

    /**
     * Land, and take responsibility for the kill.
     *
     * <p>The weapon credited nobody when it let this go — its victim was alive at
     * the time — so if the arrow finishes something off, the archer's experience
     * is the arrow's to award.
     */
    private void strike(World world, GameObject victim) {
        victim.getBody().damage(damage, damageType);
        drinkFor(world.findObject(shooter));
        splash(world, victim);
        if (victim.isEffectivelyDead()) {
            var archer = world.findObject(shooter);
            var earned = victim.findModule(ExperienceModule.class);
            var his = archer == null ? null : archer.findModule(ExperienceModule.class);
            if (his != null && earned != null) {
                his.addExperience(earned.getExperienceValue());
            }
        }
        getOwner().markDestroyed();
    }

    /**
     * What a bursting shot does to everyone standing near what it hit.
     *
     * <p>The one it struck has already taken the full blow and is left alone here:
     * a fireball that hit you is not also a fireball that went off beside you.
     * Everyone else within the burst takes the same figure, which is the simplest
     * rule a player can hold in his head -- a falloff would be a second number to
     * explain and nothing on screen could show it.
     */
    private void splash(World world, GameObject struck) {
        if (blastRadius <= 0f) {
            return;
        }
        var owner = getOwner();
        int side = owner.getPlayerIndex();
        for (var caught : world.objectsInRange(struck.getPosition(), blastRadius, candidate ->
                candidate != struck
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(side, candidate.getPlayerIndex())
                                == Relationship.ENEMIES)) {
            caught.getBody().damage(damage, damageType);
            drinkFor(world.findObject(shooter));
        }
    }

    /**
     * A share of what this arrow carried, back to whoever loosed it.
     *
     * <p>Only for a shooter who has skills — which in this dungeon means the hero,
     * and says so without the arrow having to be told who he is. Powers belong to
     * the run, and the run has one hero.
     */
    private void drinkFor(GameObject archer) {
        float share = powers == null ? 0f : powers.lifestealFraction();
        if (share <= 0f || archer == null || archer.getBody() == null
                || archer.findModule(uz.duke.dungeon.skill.SkillBook.class) == null) {
            return;
        }
        archer.getBody().heal(damage * share);
    }
}
