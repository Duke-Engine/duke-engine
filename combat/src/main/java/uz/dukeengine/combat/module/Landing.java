package uz.dukeengine.combat.module;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.combat.event.ShotLanded;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;

/**
 * What a shot does where it comes down — the same for one that hit at once and one a {@link ProjectileLauncher}
 * carried: the direct hit, the blast round it, the kill experience and the moment told. Nothing here keeps state.
 */
final class Landing {

    private Landing() {
    }

    /** See {@link WeaponUpdate#land}. */
    static void land(World world, Shot shot, GameObject victim, Coord3D where, Coord3D from) {
        var shooter = world.findObject(shot.shooter());
        var hit = victim == null || victim.isEffectivelyDead() || victim.getBody() == null || offsetOf(victim) != null
                ? null : victim;
        if (hit != null) {
            deal(shooter, shot, hit, shot.damage(), middleOf(hit), from == null ? where : from);
        }
        struck(world, shot, shooter, hit, where, where, from == null ? where : from);
    }

    /**
     * A blow of {@code shot}'s on {@code victim}: {@code damage} dealt as its weapon deals, and what it took — the
     * amount its {@code ObjectHurt} says — told to the shooter's {@link ShotListener}s, the shooter being there to hear.
     */
    static void deal(GameObject shooter, Shot shot, GameObject victim, float damage, Coord3D at, Coord3D from) {
        var body = victim.getBody();
        float worth = body.worthOf(damage, shot.weapon().damageType());
        body.damage(damage, shot.weapon().damageType(), blow(shot), at, from);
        if (worth <= 0f || shooter == null) {
            return;
        }
        for (var listener : shotListeners(shooter)) {
            listener.onDealt(shot, victim, worth);
        }
    }

    /** {@code shooter}'s {@link ShotListener}s, in module order; none, and nothing made, for a thing that has none. */
    static List<ShotListener> shotListeners(GameObject shooter) {
        List<ShotListener> found = List.of();
        for (var module : shooter.getModules()) {
            if (module instanceof ShotListener listener) {
                if (found.isEmpty()) {
                    found = new ArrayList<>(2);
                }
                found.add(listener);
            }
        }
        return found;
    }

    /** See {@link WeaponUpdate#aimPoint}. */
    static Coord3D aimPoint(GameObject victim) {
        var offset = offsetOf(victim);
        return offset == null ? victim.getPosition() : victim.getPosition().add(offset);
    }

    /** The first aim offset a module of {@code victim} gives now, or null. */
    static Coord3D offsetOf(GameObject victim) {
        for (var module : victim.getModules()) {
            if (module instanceof AimOffset thrower && thrower.aimOffset() != null) {
                return thrower.aimOffset();
            }
        }
        return null;
    }

    /** See {@link WeaponUpdate#middleOf}. */
    static Coord3D middleOf(GameObject thing) {
        var at = thing.getPosition();
        return new Coord3D(at.x(), at.y(), at.z() + thing.getGeometry().height() / 2f);
    }

    /** See {@link WeaponUpdate#nearestOf}. */
    static Coord3D nearestOf(GameObject thing, Coord3D blast) {
        var middle = middleOf(thing);
        return Footprint.of(thing).nearestTo(new Coord3D(blast.x(), blast.y(), middle.z()));
    }

    /** The death a shot deals if it kills, and whose it is: its weapon's, its shooter's, and the side it fired for. */
    private static Death blow(Shot shot) {
        return new Death(shot.weapon().deathType(), shot.shooter(), shot.side(), shot.critical());
    }

    /**
     * Everything that follows the direct hit, one way for a shot that hit at once and one that was carried: the
     * blast, the kill experience, and the moment it landed.
     */
    static void struck(World world, Shot shot, GameObject shooter, GameObject victim, Coord3D where, Coord3D shown,
            Coord3D from) {
        if (shot.radius() > 0f || shot.secondaryRadius() > 0f) {
            splash(world, shot, shooter, victim, where);
        }
        if (victim != null && victim.isEffectivelyDead() && shooter != null) {
            grantKillExperience(shooter, victim);
        }
        world.post(new ShotLanded(world.getFrame(), shot.shooter(),
                victim == null ? null : victim.getId(), shot.weapon().name(), shown, from,
                shot.radius()));
    }

    /**
     * Area damage round where the shot struck, to whom its weapon's blast hurts ({@link Weapon.Affects}) — not the
     * victim, which took the direct hit: the reference's {@code Weapon::dealDamageInternal}, its damage within the
     * first ring and its second damage beyond it, within the second. Each ring is measured to a thing's bounding
     * sphere ({@code FROM_BOUNDINGSPHERE_3D}): caught where the gap is under the larger radius, the first damage where
     * it is within the first. What the blast kills is the shooter's to be credited with, if it is there.
     */
    private static void splash(World world, Shot shot, GameObject shooter, GameObject victim, Coord3D where) {
        float reach = Math.max(shot.radius(), shot.secondaryRadius());
        var caught = world.objectsInRange(where, Float.MAX_VALUE, candidate ->
                candidate != victim
                        && gapTo(candidate, where) < reach
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && hurts(world, shot, shooter, candidate));
        for (var bystander : caught) {
            float damage = gapTo(bystander, where) <= shot.radius() ? shot.damage() : shot.secondaryDamage();
            if (damage <= 0f) {
                continue;
            }
            deal(shooter, shot, bystander, damage, nearestOf(bystander, where), where);
            if (bystander.isEffectivelyDead() && shooter != null) {
                grantKillExperience(shooter, bystander);
            }
        }
    }

    /**
     * How far {@code point} is from {@code thing}'s bounding sphere — from its centre, less its radius, never below
     * nothing: the reference's {@code distCalcProc_BoundaryAndBoundary_3D} for a point and a thing.
     */
    private static float gapTo(GameObject thing, Coord3D point) {
        var shape = thing.getGeometry();
        var at = thing.getPosition();
        float dx = at.x() - point.x();
        float dy = at.y() - point.y();
        float dz = at.z() + shape.sphereCentreHeight() - point.z();
        return Math.max(0f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - shape.boundingSphereRadius());
    }

    /**
     * Whether the shot's blast hurts {@code candidate}, by whom its weapon says it hurts; enemies where it says none.
     * Read off what went off, as the reference reads it: {@code SELF} is what went off and its maker, {@code
     * NOT_SIMILAR} its template and its allies.
     */
    private static boolean hurts(World world, Shot shot, GameObject shooter, GameObject candidate) {
        var affects = shot.weapon().affects();
        if (isSelf(shot, shooter, candidate)) {
            return affects.contains(Weapon.Affects.SELF);
        }
        if (candidate.getTargetableFrom() > world.getFrame() || candidate.hasStatus(ObjectStatus.HIDDEN)) {
            return false; // nobody's target yet, a blast's no more than a gun's; or not there to be caught
        }
        var relationship = candidate.getPlayerIndex() == shot.side() ? Relationship.ALLIES
                : world.getRelationship(shot.side(), candidate);
        if (affects.isEmpty()) {
            return relationship == Relationship.ENEMIES;
        }
        if (affects.contains(Weapon.Affects.NOT_SIMILAR) && relationship == Relationship.ALLIES
                && candidate.getTemplate().name().equals(wentOff(shot, shooter))) {
            return false; // a Terrorist's charge spares the Terrorists beside him, not an enemy's
        }
        if (affects.contains(Weapon.Affects.NOT_AIRBORNE) && candidate.hasStatus(ObjectStatus.AIRBORNE)) {
            return false;
        }
        return affects.contains(switch (relationship) {
            case ALLIES -> Weapon.Affects.ALLIES;
            case ENEMIES -> Weapon.Affects.ENEMIES;
            case NEUTRAL -> Weapon.Affects.NEUTRALS;
        });
    }

    /**
     * What went off and its maker, as the reference's {@code source == victim || source->getProducerID() == victim}:
     * the shooter and the thing that made it — or, a shell having gone off, the shell's launcher, the shooter.
     */
    private static boolean isSelf(Shot shot, GameObject shooter, GameObject candidate) {
        return candidate.getId().equals(shot.shooter())
                || shot.wentOff() == null && shooter != null && candidate.getId().equals(shooter.getProducer());
    }

    /** The template of what went off: the shell's where one carried the shot, else the shooter's; null if gone. */
    private static String wentOff(Shot shot, GameObject shooter) {
        return shot.wentOff() != null ? shot.wentOff() : shooter == null ? null : shooter.getTemplate().name();
    }

    /** Award the killer the victim's experience value, if both track experience. */
    private static void grantKillExperience(GameObject killer, GameObject victim) {
        var killerXp = killer.findModule(ExperienceModule.class);
        var victimXp = victim.findModule(ExperienceModule.class);
        if (killerXp != null && victimXp != null) {
            killerXp.addExperience(victimXp.getExperienceValue());
        }
    }
}
