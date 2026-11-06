package uz.dukeengine.rpg.skill;

import java.util.List;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Facing;
import uz.dukeengine.rpg.fight.Arrows;
import uz.dukeengine.rpg.fight.FallingUpdate;

/**
 * What a skill throws: a {@code STRIKE} -- at once, or drawn and loosed later -- a {@code SKILLSHOT} and a {@code WAVE}
 * down a lane, and a {@code METEOR} called down on a spot.
 */
final class Shots {

    private final GameObject owner;
    private final Blows blows;
    private final SkillBook.Rules rules;

    /**
     * The heavy shot in progress: which skill, at what level, and at whom.
     *
     * <p>A skill that takes time to cast needs somewhere to be while it is being
     * cast, and this is it. Held rather than queued because a hero draws one arrow
     * at a time: casting again is refused by the cooldown, which started the moment
     * he committed.
     */
    private Skill drawing;
    private int drawnAtLevel;
    private ObjectId drawnFor;
    private int loosesIn;

    /**
     * What his last cast pointed his weapon at: for whatever chooses for the hero, which has to tell the player's
     * orders from everything else the hero does. The target a cast aims at is the skill's own and must not be read
     * as one.
     */
    private ObjectId lastAimedAt;

    Shots(GameObject owner, Blows blows, SkillBook.Rules rules) {
        this.owner = owner;
        this.blows = blows;
        this.rules = rules;
    }

    boolean isDrawing() {
        return drawing != null;
    }

    ObjectId lastAimedAt() {
        return lastAimedAt;
    }

    /** A {@code STRIKE}: at {@code at}, refused where it cannot reach it, or at the nearest enemy where it names none. */
    boolean strike(Skill skill, int level, World world, ObjectId at) {
        var victim = at == null ? blows.nearestEnemy(world, skill.range()) : blows.aimedAt(world, at, skill.range());
        if (at != null && victim == null) {
            return false;
        }
        if (victim == null) {
            return true; // nothing in reach; he has still spent the cast
        }
        if (skill.windUpFrames() > 0) {
            beginDrawing(skill, level, victim);
        } else {
            land(skill, level, victim);
        }
        return true;
    }

    /**
     * A {@code SKILLSHOT}. Radius is the burst where it lands, and zero leaves it an arrow:
     * both fly the same way and stop at the first body, and one of them
     * takes the rest of the room with it. StunFrames is how long whoever
     * it hurts stands dazed after, and zero leaves them free.
     */
    boolean skillshot(Skill skill, int level, Coord3D towards) {
        if (towards == null) {
            return false;
        }
        Facing.turnToward(owner, towards);
        if (!Arrows.looseAlong(owner, towards, blows.damageOf(skill, level), DamageType.NORMAL,
                skill.projectile(), blows.speedOf(skill), rules.muzzleOffset(), skill.range(), skill.radius(),
                skill.stunFrames(), blows.critical())) {
            return false; // no arrow to throw; the cooldown is not spent
        }
        Blows.fired(owner, towards);
        return true;
    }

    boolean wave(Skill skill, int level, Coord3D towards) {
        if (towards == null) {
            return false;
        }
        Facing.turnToward(owner, towards);
        if (!Arrows.waveAlong(owner, towards, blows.damageOf(skill, level), DamageType.NORMAL, skill.projectile(),
                blows.speedOf(skill), rules.muzzleOffset(), skill.distance(), skill.hitWidth(),
                skill.stunFrames(), blows.critical())) {
            return false; // no wave to throw; the cooldown is not spent
        }
        Blows.fired(owner, towards);
        return true;
    }

    /**
     * Mark the floor, and start whatever is coming counting down.
     *
     * <p>The mark is a thing in the world and not a line on the caster's screen,
     * so it is spawned exactly as an arrow is: the client draws whatever the world
     * holds, the fog hides it like anything else, and everyone who can see that
     * patch of floor gets the same warning he does. A hint drawn for the caster
     * alone would have been half the skill.
     *
     * @return whether one was really called down; false leaves the cooldown unspent
     */
    boolean meteor(Skill skill, int level, World world, Coord3D towards) {
        if (towards == null) {
            return false;
        }
        var spot = Blows.withinReach(owner, towards, skill.range());
        if (!skill.hasProjectile()) {
            return false; // the file named nothing to drop
        }
        var falling = Blows.spawnOnFloor(owner, world, skill.projectile(), spot, FallingUpdate.class);
        if (falling == null) {
            return false; // no such thing, or not something that falls
        }
        // Worth what it was worth when he called for it, like every other shot
        // here -- he may level, or die, in the second it spends on its way.
        falling.callDown(owner, blows.damageOf(skill, level), skill.radius(), skill.windUpFrames());
        Blows.fired(owner, spot);
        return true;
    }

    /**
     * Take aim. Nothing is hurt yet — that is the whole point of a wind-up.
     *
     * <p>He turns to the target and his weapon is pointed at it, so the archer on
     * screen is visibly drawing on something rather than standing idle while a
     * monster's health drops for no reason the player can see. Pointing the weapon
     * is not a cheat: he really is aiming at it, and if it is inside his ordinary
     * range he would have been shooting at it anyway.
     */
    private void beginDrawing(Skill skill, int level, GameObject victim) {
        drawing = skill;
        drawnAtLevel = level;
        drawnFor = victim.getId();
        loosesIn = skill.windUpFrames();
        Facing.turnToward(owner, victim);
        var weapon = owner.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.attack(victim.getId());
            // Pointing his weapon is part of drawing, not an order. Said out loud
            // so his brain does not read it as one and send him chasing.
            lastAimedAt = victim.getId();
        }
    }

    /** A frame of the drawing: the shot goes once its wind-up is done. */
    void drawOn() {
        if (drawing != null && --loosesIn <= 0) {
            looseTheDrawnShot();
        }
    }

    /**
     * The drawn shot goes.
     *
     * <p>Its target may have died while he was drawing, and then the shot is simply
     * lost — he committed when he pressed the key, and the cooldown went with it.
     * That is the cost of a skill that takes time, and it is the reason it hits
     * harder than the one that does not.
     */
    private void looseTheDrawnShot() {
        var skill = drawing;
        var world = owner.getWorld();
        drawing = null;
        if (world == null || owner.isEffectivelyDead()) {
            return;
        }
        var victim = world.findObject(drawnFor);
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            return;
        }
        Facing.turnToward(owner, victim);
        land(skill, drawnAtLevel, victim);
    }

    /**
     * Deal a strike's damage — as an arrow if the skill has one, otherwise where
     * the victim stands.
     *
     * <p>The figure is settled here rather than when the key was pressed, the same
     * instant an ordinary shot settles its own: what he is worth is what he is
     * worth as the string leaves his fingers.
     */
    private void land(Skill skill, int level, GameObject victim) {
        float damage = blows.damageOf(skill, level);
        if (skill.hasProjectile() && Arrows.loose(owner, victim, damage, DamageType.NORMAL,
                skill.projectile(), blows.speedOf(skill), rules.muzzleOffset(), blows.critical())) {
            // The client draws a muzzle flash and plays the shooting sound off this
            // — the same moment the bow announces, for the same reason.
            Blows.fired(owner, victim.getId(), owner.getPosition(), victim.getPosition());
            return;
        }
        blows.strike(victim, damage);
    }

    void save(SaveOut out, List<Skill> skills) {
        out.writeInt(SkillSlots.of(skills, drawing)).writeInt(drawnAtLevel).writeId(drawnFor).writeInt(loosesIn)
                .writeId(lastAimedAt);
    }

    void load(SaveIn in, List<Skill> skills) {
        drawing = SkillSlots.at(skills, in.readInt());
        drawnAtLevel = in.readInt();
        drawnFor = in.readId();
        loosesIn = in.readInt();
        lastAimedAt = in.readId();
    }
}
