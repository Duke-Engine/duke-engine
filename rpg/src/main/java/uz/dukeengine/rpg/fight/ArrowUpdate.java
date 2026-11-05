package uz.dukeengine.rpg.fight;

import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.combat.module.ExperienceModule;
import uz.dukeengine.combat.module.StatusUpdate;

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
@ModuleGroup({ModuleGroups.MOVEMENT, ModuleGroups.COMBAT})
public final class ArrowUpdate extends UpdateModule implements Saveable {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

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
     * How long whoever this hurts stands dazed after, in frames, or zero for a shot that only hurts: the one it
     * struck and everyone its burst caught alike. Carried from the skill that threw it, as the damage is -- the fire
     * mage's fireball stuns, and its ordinary fire does not.
     */
    private int stunFrames;

    /**
     * What whoever this stuns wears while dazed: the Combat block's {@code StunLook}; blank for nothing.
     */
    private final String stunLook;

    /**
     * How high over the ground it flies: as high as it was when it left the bow, and over whatever ground it crosses —
     * so a shot rises over a hill and dips into a hollow rather than going straight through the one and over the
     * other. The ground's height is the simulation's own, so every machine flies it alike, and it lands where it is
     * drawn.
     */
    private float flight;

    /**
     * Where it was loosed from: its side of a seal (see {@link uz.dukeengine.rpg.Seal}), which it does not leave while
     * the gate stands. A shot that would is spent on the gate — see {@link Seal}.
     */
    private Coord3D loosedFrom;
    private final Seal seal;

    /** How wide a wave is, which passes through what it meets; 0 for a shot that stops at the first. */
    private float pierceWidth;
    /** Who a wave has hurt already, each once. */
    private final java.util.Set<ObjectId> struck = new java.util.HashSet<>();

    public ArrowUpdate(GameObject owner, String stunLook, Seal seal) {
        super(owner);
        this.stunLook = stunLook == null ? "" : stunLook;
        this.seal = seal;
    }

    /** Send it after something, carrying what the weapon decided it was worth. */
    void loose(GameObject from, GameObject at, float carrying, DamageType type, float speed) {
        this.loosedFrom = from.getPosition();
        this.shooter = from.getId();
        this.target = at.getId();
        this.damage = carrying;
        this.damageType = type;
        this.stepPerFrame = speed * GameConstants.SECONDS_PER_LOGICFRAME;
        this.flight = heightOverTheGround();
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
            float speed, float distance, float blast, int stun) {
        this.blastRadius = blast;
        this.stunFrames = stun;
        this.loosedFrom = from.getPosition();
        this.shooter = from.getId();
        this.target = null;
        this.damage = carrying;
        this.damageType = type;
        this.stepPerFrame = speed * GameConstants.SECONDS_PER_LOGICFRAME;
        this.travelLeft = distance;
        this.flight = heightOverTheGround();
        aimAt(towards);
    }

    /**
     * The same down a line, as a wave that passes through: every enemy within {@code width} of it is hurt once as it
     * goes, and only stone, a seal or its distance end it.
     */
    void waveAlong(GameObject from, Coord3D towards, float carrying, DamageType type, float speed, float distance,
            float width, int stun) {
        looseAlong(from, towards, carrying, type, speed, distance, 0f, stun);
        this.pierceWidth = Math.max(0.01f, width);
    }

    /** How far above the ground under it the shot is now. */
    private float heightOverTheGround() {
        var world = getOwner().getWorld();
        var at = getOwner().getPosition();
        return world == null ? 0f : at.z() - world.groundHeight(at);
    }

    /** A point of the flight: that place, at the shot's own height over the ground there. */
    private Coord3D overTheGround(World world, float x, float y) {
        return new Coord3D(x, y, world.groundHeight(new Coord3D(x, y, 0f)) + flight);
    }

    @Override
    public void save(SaveOut out) {
        out.writeId(target).writeId(shooter).writeFloat(damage).writeString(damageType.name())
                .writeFloat(stepPerFrame).writeInt(flownFor).writeFloat(travelLeft).writeFloat(blastRadius)
                .writeInt(stunFrames).writeFloat(flight).writePlace(loosedFrom).writeFloat(pierceWidth)
                .writeBoolean(critical);
        out.writeIds(struck.stream().sorted(java.util.Comparator.comparingInt(ObjectId::value)).toList());
    }

    @Override
    public void load(SaveIn in) {
        target = in.readId();
        shooter = in.readId();
        damage = in.readFloat();
        damageType = DamageType.of(in.readString());
        stepPerFrame = in.readFloat();
        flownFor = in.readInt();
        travelLeft = in.readFloat();
        blastRadius = in.readFloat();
        stunFrames = in.readInt();
        flight = in.readFloat();
        loosedFrom = in.readPlace();
        pierceWidth = in.readFloat();
        critical = in.readBoolean();
        struck.clear();
        struck.addAll(in.readIds());
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
        var next = overTheGround(world, here.x() + dx / distance * stepPerFrame,
                here.y() + dy / distance * stepPerFrame);
        if (seal.parts(world, loosedFrom, next)) {
            owner.markDestroyed(); // spent on the shut gate
            return;
        }
        owner.setPosition(next);
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
        if (pierceWidth > 0f) {
            passThrough(owner, world);
        }
        var hit = pierceWidth > 0f ? null : world.findClosestInReach(owner, stepPerFrame, candidate ->
                candidate != owner
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && !candidate.isContained()
                        && world.getRelationship(owner, candidate) == Relationship.ENEMIES);
        if (hit != null) {
            strike(world, hit);
            return;
        }
        float facing = owner.getOrientation();
        var here = owner.getPosition();
        var next = overTheGround(world,
                here.x() + (float) StrictMath.cos(facing) * stepPerFrame,
                here.y() + (float) StrictMath.sin(facing) * stepPerFrame);
        if (world.isGroundBlocked(next) || seal.parts(world, loosedFrom, next)) {
            owner.markDestroyed(); // spent against a wall, or the shut gate
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
        if (seal.parts(world, loosedFrom, victim.getPosition())) {
            getOwner().markDestroyed(); // it came to the shut gate first, and is spent on it
            return;
        }
        victim.getBody().damage(damage, damageType, blow());
        stun(world, victim);
        // Every blow it lands, this one and each its burst deals, its archer drinks from if a
        // skill of his says so -- see SkillBook.drink.
        var archer = world.findObject(shooter);
        SkillBook.drink(archer, damage);
        splash(world, victim, archer);
        if (victim.isEffectivelyDead()) {
            var earned = victim.findModule(ExperienceModule.class);
            var his = archer == null ? null : archer.findModule(ExperienceModule.class);
            if (his != null && earned != null) {
                his.addExperience(earned.getExperienceValue());
            }
        }
        getOwner().markDestroyed();
    }

    /** Whose blow it is: its archer's, and his side's as the arrow carries it, so a kill is credited to him. */
    private uz.dukeengine.core.module.Death blow() {
        return new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL, shooter,
                getOwner().getPlayerIndex(), critical);
    }

    /** Whether the roll that set its damage made it a critical one: told on each hurt it deals. */
    private boolean critical;

    void critical(boolean critical) {
        this.critical = critical;
    }

    /** Everyone a wave is over this frame and has not hurt yet, hurt in id order: it goes on through them. */
    private void passThrough(GameObject owner, World world) {
        var archer = world.findObject(shooter);
        var over = new java.util.ArrayList<>(world.objectsInRange(owner.getPosition(),
                Math.max(stepPerFrame, pierceWidth / 2f), candidate ->
                        candidate != owner
                                && candidate.getBody() != null
                                && !candidate.isEffectivelyDead()
                                && !candidate.isContained()
                                && !struck.contains(candidate.getId())
                                && world.getRelationship(owner, candidate)
                                        == Relationship.ENEMIES
                                && !seal.parts(world, loosedFrom, candidate.getPosition())));
        over.sort(java.util.Comparator.comparingInt(thing -> thing.getId().value()));
        for (var victim : over) {
            struck.add(victim.getId());
            victim.getBody().damage(damage, damageType, blow());
            stun(world, victim);
            SkillBook.drink(archer, damage);
            if (victim.isEffectivelyDead()) {
                var earned = victim.findModule(ExperienceModule.class);
                var his = archer == null ? null : archer.findModule(ExperienceModule.class);
                if (his != null && earned != null) {
                    his.addExperience(earned.getExperienceValue());
                }
            }
        }
    }

    /**
     * What a bursting shot does to everyone standing near what it hit.
     *
     * <p>The one it struck has already taken the full blow and is left alone here:
     * a fireball that hit you is not also a fireball that went off beside you.
     * Everyone else within the burst takes the same figure, which is the simplest
     * rule a player can hold in his head -- a falloff would be a second number to
     * explain and nothing on screen could show it. Nor does it reach through the
     * seal from where it burst.
     */
    private void splash(World world, GameObject struck, GameObject archer) {
        if (blastRadius <= 0f) {
            return;
        }
        var owner = getOwner();
        int side = owner.getPlayerIndex();
        var burst = struck.getPosition();
        for (var caught : world.objectsInRange(burst, blastRadius, candidate ->
                candidate != struck
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(side, candidate)
                                == Relationship.ENEMIES
                        && !seal.parts(world, burst, candidate.getPosition()))) {
            caught.getBody().damage(damage, damageType, blow());
            SkillBook.drink(archer, damage);
            stun(world, caught);
        }
    }

    /**
     * Leave whoever this hurt standing dazed, if it was thrown to: the engine's own {@code DISABLED} for
     * {@link #stunFrames}, set on the creature's own timers -- so its legs and its weapon stand still under it, its
     * skills refuse (see {@code SkillBook.cast}), it wears off by itself whoever threw it, and a second stun starts
     * the count again rather than adding to it. And the stars over its head, riding it for as long: the client is
     * told how long by The game.
     *
     * <p>A creature whose file never asked for a {@code StatusUpdate} is not stunned, as it is not slowed: better
     * that than the game deciding what a creature is made of behind its own file's back.
     */
    private void stun(World world, GameObject hurt) {
        var timers = stunFrames <= 0 || hurt.isEffectivelyDead() ? null : hurt.findModule(StatusUpdate.class);
        if (timers == null) {
            return;
        }
        timers.apply(ObjectStatus.DISABLED, stunFrames);
        if (!stunLook.isBlank()) {
            world.effect(stunLook, hurt);
        }
    }
}
