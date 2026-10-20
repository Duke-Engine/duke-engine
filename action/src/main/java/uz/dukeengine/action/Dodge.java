package uz.dukeengine.action;

import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Facing;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillCaster;
import uz.dukeengine.rpg.skill.SkillEffect;

/**
 * A dodge — what a skill of {@link SkillEffect#MODULE} does on a hero that has this: aimed at a point, he rolls the
 * skill's {@code Distance} toward it, whatever its distance, over {@code DurationFrames} — playing {@code CastAnim} as
 * long — out of harm's way ({@link ObjectStatus#INVULNERABLE}) for its first {@code TickFrames}. He passes through
 * creatures and never ends inside one, stops at stone, is taken over a low way across his line, and is refused over a
 * seal as a dash is, nothing spent; not while held, and it cuts his own weapon's wind-up.
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class Dodge extends UpdateModule implements SkillCaster {

    /** No configuration: the skill says how far, how long and how long unharmed. */
    public record Data() implements ModuleData {
    }

    /** How far past his own size a dodge's end looks for creatures it would stand in. */
    private static final float LOOK = 20f;

    /** How finely a dodge's line is walked: a unit at a time, so a roll ends exactly where it is sent. */
    private static final float STEP = 1f;

    /** How near a dodge's line a low way's near end must lie, and how nearly the way must head as he rolls. */
    private static final float LOW_LINK_REACH = 6f;
    private static final float LOW_LINK_HEADING = 0.7f;

    /** The roll under way — from where, to where, over how many frames and how many are gone — or none. */
    private Coord3D from;
    private Coord3D to;
    private int frames;
    private int gone;

    public Dodge(GameObject owner, Data data) {
        super(owner);
    }

    @Override
    public boolean cast(Skill skill, Coord3D towards, Seal seal) {
        var owner = getOwner();
        var world = owner.getWorld();
        if (owner.hasStatus(ObjectStatus.HELD) || towards == null || world == null) {
            return false;
        }
        var stood = owner.getPosition();
        float facing = owner.getOrientation();
        Facing.turnToward(owner, towards);
        var over = lowLinkAcross(owner, world, skill.distance());
        var end = over != null ? over : rollEnd(owner, world, skill.distance());
        if (seal.parts(world, stood, end)) {
            owner.setOrientation(facing);
            return false; // over a seal: refused, nothing spent
        }
        var weapon = owner.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.holdFire(); // its wind-up cut: no blow of it lands
        }
        int length = Math.max(1, skill.durationFrames());
        var timers = owner.findModule(StatusUpdate.class);
        if (timers != null) {
            timers.apply(ObjectStatus.INVULNERABLE, skill.tickFrames());
        }
        owner.busy(skill.castAnim(), length);
        from = stood;
        to = end;
        frames = length;
        gone = 0;
        return true;
    }

    /** A frame of the roll: a share of the way, on the ground under it. */
    @Override
    public void update() {
        if (to == null) {
            return;
        }
        var owner = getOwner();
        var world = owner.getWorld();
        gone++;
        float share = Math.min(1f, gone / (float) frames);
        var at = new Coord3D(from.x() + (to.x() - from.x()) * share, from.y() + (to.y() - from.y()) * share, 0f);
        owner.setPosition(new Coord3D(at.x(), at.y(), world == null ? at.z() : world.groundHeight(at)));
        if (gone >= frames) {
            to = null;
        }
    }

    /**
     * Where a dodge of {@code distance} along his facing ends: walked out a step at a time to the last open ground
     * before stone or anything that stands, then back to where no creature stands, so he passes through them and ends
     * inside none.
     */
    private static Coord3D rollEnd(GameObject owner, World world, float distance) {
        float facing = owner.getOrientation();
        float dx = (float) StrictMath.cos(facing);
        float dy = (float) StrictMath.sin(facing);
        var start = owner.getPosition();
        float reach = 0f;
        for (float walked = STEP; walked <= distance + 1e-3f; walked += STEP) {
            var step = new Coord3D(start.x() + dx * walked, start.y() + dy * walked, start.z());
            if (world.isGroundBlocked(step) || world.findBlocker(owner, step) != null) {
                break;
            }
            reach = walked;
        }
        float radius = Solid.of(owner.getTemplate()).footprintRadius();
        for (float walked = reach; walked > 0f; walked -= STEP) {
            var at = new Coord3D(start.x() + dx * walked, start.y() + dy * walked, start.z());
            var shape = Footprint.of(owner, at);
            boolean clear = world.objectsInRange(at, radius + LOOK, other -> other != owner
                    && other.getLocomotor() != null && !other.isEffectivelyDead()
                    && shape.overlaps(Footprint.of(other))).isEmpty();
            if (clear) {
                return new Coord3D(at.x(), at.y(), world.groundHeight(at));
            }
        }
        return start;
    }

    /**
     * The far end of a low way across ({@code TraversalLink.low}) whose near end lies on the dodge's line — within its
     * distance along it and {@link #LOW_LINK_REACH} of it — and whose far end lies the way he rolls: the roll takes him
     * over it. The first such on the map's list; null for none.
     */
    private static Coord3D lowLinkAcross(GameObject owner, World world, float distance) {
        float facing = owner.getOrientation();
        float dx = (float) StrictMath.cos(facing);
        float dy = (float) StrictMath.sin(facing);
        var start = owner.getPosition();
        for (var link : world.traversalLinks()) {
            if (!link.low()) {
                continue;
            }
            for (int way = 0; way < (link.bothWays() ? 2 : 1); way++) {
                var entry = way == 0 ? link.a() : link.b();
                var exit = way == 0 ? link.b() : link.a();
                float ex = entry.x() - start.x();
                float ey = entry.y() - start.y();
                float along = ex * dx + ey * dy;
                float aside = Math.abs(ex * dy - ey * dx);
                float wx = exit.x() - entry.x();
                float wy = exit.y() - entry.y();
                float length = (float) Math.sqrt(wx * wx + wy * wy);
                if (along >= 0f && along <= distance && aside <= LOW_LINK_REACH && length > 0f
                        && (wx * dx + wy * dy) / length >= LOW_LINK_HEADING) {
                    return new Coord3D(exit.x(), exit.y(), world.groundHeight(exit));
                }
            }
        }
        return null;
    }
}
