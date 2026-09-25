package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;

/**
 * Runs over what it drives into: SAGE's {@code CrusherLevel}, with {@code SquishCollide}'s rule for when.
 *
 * <p>A {@link Crushable} thing whose level is below this one's dies — {@code CRUSH} damage, enough to kill
 * anything, and the {@link #CRUSHED} death — when this is moving, its footprint reaches the thing's middle
 * (the reference gives the victim a radius of 1 "so the tank has to actually hit the infantry"), the thing is
 * ahead of the way it is going, and it is not one of its own side's allies. A thing standing still crushes
 * nothing, and nothing in the air is crushed.
 *
 * <p>Moving, it also pushes over what topples ({@link ToppleUpdate}) within its own radius and that thing's reach,
 * as the reference's moving units push over its trees ({@code W3DTreeBuffer::unitMoved}).
 *
 * <p>Opt-in, and the numbers are the game's: which of its vehicles crush, and how hard each thing is to crush.
 * Where the reference crushes a car in two stages — its front, then its back — this has the one.
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class CrushUpdate extends UpdateModule {

    /** What running a thing over deals, which an armour may name like any other. */
    public static final DamageType CRUSH = DamageType.of("CRUSH");

    /** The death a thing run over dies. */
    public static final DeathType CRUSHED = DeathType.of("CRUSHED");

    /** SAGE's {@code HUGE_DAMAGE_AMOUNT}: enough that whatever is run over dies. */
    private static final float HUGE_DAMAGE = 999_999f;

    /** SquishCollide's victim radius: the crusher's own footprint has to reach this close to its middle. */
    private static final float VICTIM_RADIUS = 1f;

    public record Data(int crusherLevel) implements ModuleData {
    }

    private final int level;
    private Coord3D last;

    public CrushUpdate(GameObject owner, Data data) {
        super(owner);
        this.level = data.crusherLevel();
    }

    @Override
    public void update() {
        var owner = getOwner();
        var here = owner.getPosition();
        var was = last;
        last = here;
        var world = owner.getWorld();
        if (was == null || world == null || level <= 0 || owner.isEffectivelyDead()
                || owner.hasStatus(ObjectStatus.AIRBORNE)) {
            return;
        }
        // "Unless I am moving right now, I may not crush anything" — PhysicsBehavior::checkForOverlapCollision.
        float headingX = here.x() - was.x();
        float headingY = here.y() - was.y();
        if (headingX == 0f && headingY == 0f) {
            return;
        }
        var footprint = Footprint.of(owner);
        var victims = world.objectsInRange(here, owner.getGeometry().footprintRadius() + VICTIM_RADIUS,
                candidate -> candidate != owner
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && !candidate.isContained()
                        && !candidate.hasStatus(ObjectStatus.AIRBORNE)
                        && crushableBelow(candidate)
                        && world.getRelationship(owner.getPlayerIndex(), candidate.getPlayerIndex())
                                != Relationship.ALLIES);
        for (var victim : victims) {
            var there = victim.getPosition();
            boolean ahead = (there.x() - here.x()) * headingX + (there.y() - here.y()) * headingY > 0f;
            if (ahead && footprint.distanceTo(there) <= VICTIM_RADIUS) {
                victim.getBody().damage(HUGE_DAMAGE, CRUSH, new Death(CRUSHED, owner.getId(), owner.getPlayerIndex()),
                        WeaponUpdate.middleOf(victim), here);
            }
        }
        pushOver(world, here);
    }

    /** What topples within its own radius and the thing's reach, pushed over away from it. */
    private void pushOver(uz.dukeengine.core.thing.World world, Coord3D here) {
        var owner = getOwner();
        float radius = pushingRadius(owner.getGeometry());
        var standing = world.objectsInRange(here, radius + ToppleUpdate.MOST_REACH,
                candidate -> candidate != owner && candidate.findModule(ToppleUpdate.class) != null);
        for (var thing : standing) {
            var topple = thing.findModule(ToppleUpdate.class);
            float dx = thing.getPosition().x() - here.x();
            float dy = thing.getPosition().y() - here.y();
            float within = radius + topple.reach();
            if (topple.topplesUnder(level) && dx * dx + dy * dy < within * within) {
                topple.topple(owner, here);
            }
        }
    }

    /** {@code unitMoved}: a mover's radius, the smaller of a box's two. */
    private static float pushingRadius(uz.dukeengine.core.thing.Geometry geometry) {
        return switch (geometry) {
            case uz.dukeengine.core.thing.Geometry.Box box -> Math.min(box.majorRadius(), box.minorRadius());
            case uz.dukeengine.core.thing.Geometry.Cylinder cylinder -> cylinder.radius();
            case uz.dukeengine.core.thing.Geometry.Sphere sphere -> sphere.radius();
        };
    }

    /** How hard a thing it runs over may be to crush: the reference's {@code CrusherLevel}. */
    public int getLevel() {
        return level;
    }

    private boolean crushableBelow(GameObject candidate) {
        var crushable = candidate.findModule(Crushable.class);
        return crushable != null && crushable.getLevel() < level;
    }
}
