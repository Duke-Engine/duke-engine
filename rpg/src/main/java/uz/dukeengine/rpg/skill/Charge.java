package uz.dukeengine.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.Seal;

/**
 * A {@code SKEWER} under way: how far it has left to go, from where it set off (its side of a seal), what each
 * one caught takes, and who it has caught, in the order it caught them. On the book, so a frame taken from the
 * middle of one goes on from where it stood.
 */
final class Charge {

    private final GameObject owner;
    private final Blows blows;
    private final Seal seal;
    private final Skill skill;
    private final float each;
    private final boolean critical;
    private float left;
    private final Coord3D from;
    private final List<ObjectId> caught = new ArrayList<>();

    Charge(GameObject owner, Blows blows, Seal seal, Skill skill, float each, boolean critical, float distance) {
        this.owner = owner;
        this.blows = blows;
        this.seal = seal;
        this.skill = skill;
        this.each = each;
        this.critical = critical;
        this.left = distance;
        this.from = owner.getPosition();
    }

    /**
     * One frame of the charge: he drives on {@code ProjectileSpeed} a second — the whole way at once where it names
     * none — catching every enemy within {@code Radius} of him, each hurt once as it is caught, and holding all of them
     * at his front as he goes. Stone, a seal, his fall or a stun end it where he stands; so does the end of its
     * {@code Distance}. What it caught is dropped at his front then, spread, stunned {@code StunFrames}.
     *
     * @return whether it goes on; false once it is over, and then {@link #end} is owed
     */
    boolean drive() {
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead() || owner.hasStatus(ObjectStatus.DISABLED)) {
            return false;
        }
        float speed = skill.projectileSpeed();
        float step = speed <= 0f ? left : Math.min(left, speed * GameConstants.SECONDS_PER_LOGICFRAME);
        float facing = owner.getOrientation();
        var here = owner.getPosition();
        // In steps no longer than a dash's, so a fast charge does not jump a wall or a body.
        for (float gone = 0f; gone < step; ) {
            float bit = Math.min(Moves.DASH_STEP, step - gone);
            var at = new Coord3D(here.x() + (float) StrictMath.cos(facing) * bit,
                    here.y() + (float) StrictMath.sin(facing) * bit, 0f);
            var next = new Coord3D(at.x(), at.y(), world.groundHeight(at));
            if (world.isGroundBlocked(next) || seal.parts(world, from, next)
                    || world.findBlocker(owner, next, thing -> caught.contains(thing.getId())) != null) {
                return false;
            }
            owner.setPosition(next);
            here = next;
            gone += bit;
            left -= bit;
            catchWhatHeMeets(world);
        }
        holdThemAtHisFront(world, 2);
        return left > 0.0001f;
    }

    private void catchWhatHeMeets(World world) {
        for (var victim : blows.enemiesWithin(world, owner.getGeometry().footprintRadius() + skill.radius())) {
            if (caught.contains(victim.getId())) {
                continue;
            }
            caught.add(victim.getId());
            Blows.hurt(owner, victim, each, critical);
            SkillBook.drink(owner, each);
        }
    }

    /** Everyone caught moved to his front, spread, their legs stopped and held still {@code frames}. */
    private void holdThemAtHisFront(World world, int frames) {
        float reach = owner.getGeometry().footprintRadius() + skill.radius();
        var at = owner.getPosition();
        var front = new Coord3D(at.x() + (float) StrictMath.cos(owner.getOrientation()) * reach,
                at.y() + (float) StrictMath.sin(owner.getOrientation()) * reach, at.z());
        for (var id : caught) {
            var victim = world.findObject(id);
            if (victim == null || victim.isEffectivelyDead()) {
                continue;
            }
            blows.placeNear(victim, world, front, from, owner.getOrientation());
            Blows.daze(victim, frames);
        }
    }

    /** The charge over: what it caught dropped at his front and stunned. */
    void end() {
        var world = owner.getWorld();
        if (world != null) {
            holdThemAtHisFront(world, skill.stunFrames());
        }
    }
}
