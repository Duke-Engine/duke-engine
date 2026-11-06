package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Facing;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.SightLine;
import uz.dukeengine.rpg.hero.LevelBonus;

/** What is cast for its own side: a {@code HEAL} called down, a {@code HASTE} lent, a {@code SUMMON}'s rifts opened. */
final class Allies {

    private final GameObject owner;
    private final Seal seal;
    private final SkillBook.Rules rules;

    /**
     * What this caster has called up and is still standing, or still climbing out -- in
     * the order it was called. A rift counts as well as what comes out of it, so a cast
     * made while one is still open cannot overshoot {@code MaxSummoned}.
     */
    private final List<ObjectId> summoned = new ArrayList<>();

    Allies(GameObject owner, Seal seal, SkillBook.Rules rules) {
        this.owner = owner;
        this.seal = seal;
        this.rules = rules;
    }

    /**
     * Call holy light down on one of its own, if he is still someone it may mend.
     *
     * <p>Asked again here rather than taken on the brain's word: the book is what spends
     * the cooldown, and a mending that landed on a whole skeleton or through a wall would
     * be the two of them disagreeing about the rule. See {@link Mending}.
     *
     * <p>Worth what it was worth when it was called for, as every shot here is, and
     * grown by its level as the healer's blows are.
     *
     * @return whether it was called down; false leaves the cooldown unspent
     */
    boolean mend(Skill skill, World world, ObjectId at) {
        var patient = at == null ? null : world.findObject(at);
        if (!skill.hasProjectile()
                || !Mending.canMend(owner, patient, skill.range(), skill.healBelowPercent(), seal)) {
            return false;
        }
        var spot = patient.getPosition();
        var mending = Blows.spawnOnFloor(owner, world, skill.projectile(), spot, MendingUpdate.class);
        if (mending == null) {
            return false; // no such thing, or not a light that mends
        }
        mending.callDown(patient, skill.heal() * Blows.bonusOf(owner), skill.windUpFrames());
        Facing.turnToward(owner, patient);
        Blows.fired(owner, spot);
        return true;
    }

    /**
     * A {@code HASTE}: the sturdiest of its own near it, or itself -- never nobody. Given to the hastened's own book as
     * a buff from the caster, holding DurationFrames with this frame the first: a second haste from the same caster
     * starts it again, and the strongest of those that hold is the one that counts, never the sum.
     */
    boolean haste(Skill skill, int level, World world) {
        var hastened = sturdiestNear(world, skill.range());
        var its = hastened.findModule(SkillBook.class);
        its.give(SkillEffect.HASTE, owner.getId().value(), skill.boostAt(level),
                world.getFrame() + skill.durationFrames() - 1);
        // It turns to the one it hastens and is seen casting, as a mending is; and the one it hastens wears
        // the haste's look for as long as it lasts -- the game gives the look its DurationFrames,
        // and its layers say Renews, which carries it on to a second haste's end.
        Facing.turnToward(owner, hastened);
        Blows.fired(owner, hastened.getPosition());
        if (skill.hasLook()) {
            world.effect(skill.look(), hastened);
        }
        return true;
    }

    /**
     * Whom a haste is for: of the caster's own side, living, carrying a book, within {@code range} of it, middle to
     * middle, on its side of a seal (see {@link uz.dukeengine.rpg.Seal}) and in its plain sight -- itself always among
     * them -- the one of the highest level, then the most health at its fullest, then the nearer, then the one the
     * world made first. Sturdiest rather than hardest-hitting: health is on every body, and a blow's worth is not. A
     * total order ending in the object id, so every machine picks the same one.
     */
    private GameObject sturdiestNear(World world, float range) {
        var here = owner.getPosition();
        var sturdier = Comparator.<GameObject>comparingInt(LevelBonus::levelOf)
                .thenComparingDouble(one -> one.getBody().getMaxHealth())
                .thenComparingDouble(one -> -here.distance(one.getPosition()))
                .thenComparingInt(one -> -one.getId().value());
        return world.objectsInRange(here, range, one -> one.getPlayerIndex() == owner.getPlayerIndex()
                        && one.getBody() != null && !one.isEffectivelyDead()
                        && one.findModule(SkillBook.class) != null
                        && !seal.parts(world, here, one.getPosition()) && SightLine.clear(owner, one))
                .stream().max(sturdier).orElse(owner);
    }

    /** See {@link SkillBook#summonedStanding}. */
    int standing() {
        var world = owner.getWorld();
        if (world != null) {
            summoned.removeIf(id -> {
                var one = world.findObject(id);
                return one == null || one.isDestroyed() || one.isEffectivelyDead();
            });
        }
        return summoned.size();
    }

    /** See {@link SkillBook#risenInPlaceOf}. */
    void risenInPlaceOf(ObjectId rift, ObjectId risen) {
        int at = summoned.indexOf(rift);
        if (at >= 0) {
            summoned.set(at, risen);
        }
    }

    /**
     * Open rifts for what it calls up: every kind its {@code Summons} names, as many of
     * each as it says -- as many as there is room for under its ceiling, and floor round
     * it to open them on. See {@link Summoning} for where.
     *
     * <p>The kinds take the rifts in the order the file wrote them, so where there is
     * room for fewer than the cast asks, the first kinds are the ones that rise: two
     * swordsmen before two archers.
     *
     * @return whether one opened at all; false leaves the cooldown unspent
     */
    boolean summon(Skill skill, World world, Coord3D towards) {
        var rising = new ArrayList<String>();
        float body = 0f;
        for (var kind : skill.summons().entrySet()) {
            var creature = world.findTemplate(kind.getKey());
            if (creature == null) {
                continue;
            }
            for (int one = 0; one < kind.getValue(); one++) {
                rising.add(kind.getKey());
            }
            body = Math.max(body, Solid.of(creature).footprintRadius());
        }
        int room = Math.min(rising.size(), skill.maxSummoned() - standing());
        var rift = skill.hasProjectile() ? world.findTemplate(skill.projectile()) : null;
        if (room <= 0 || rift == null) {
            return false;
        }
        // Two that rise together stand a body apart -- the widest body of what rises.
        var spots = Summoning.spots(world, owner, towards, skill.radius(), room, 2f * body,
                rules.summonTurnDegrees(), rules.summonTurns(), seal);
        int opened = 0;
        for (var spot : spots) {
            var opening = world.spawn(rift, spot, owner.getPlayerIndex());
            var summoning = opening.findModule(SummoningUpdate.class);
            if (summoning == null) {
                opening.markDestroyed(); // the template exists but is not a rift
                continue;
            }
            summoning.open(owner, rising.get(opened), skill.durationFrames(),
                    skill.summonExperiencePercent(), skill.windUpFrames());
            summoned.add(opening.getId());
            opened++;
        }
        if (opened == 0) {
            return false;
        }
        if (towards != null) {
            Facing.turnToward(owner, towards);
        }
        Blows.fired(owner, spots.getFirst());
        return true;
    }

    void save(SaveOut out) {
        out.writeIds(summoned);
    }

    void load(SaveIn in) {
        summoned.clear();
        summoned.addAll(in.readIds());
    }
}
