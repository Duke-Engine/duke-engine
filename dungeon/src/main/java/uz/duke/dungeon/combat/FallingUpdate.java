package uz.duke.dungeon.combat;

import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.World;
import uz.duke.rts.event.WeaponFired;
import uz.duke.rts.module.ExperienceModule;

/**
 * Something on its way down, and the mark it leaves on the floor while it comes.
 *
 * <p>A meteor is an area skill with a pause in the middle, and the pause is the
 * whole of it. A blast that lands the instant it is cast only ever asks where the
 * monsters are <em>now</em>; one that lands a second later asks where they are
 * going to be — and a monster that walks out of the mark has beaten the skill,
 * which is a thing worth being able to do.
 *
 * <p><b>The warning is an object, not a hint.</b> It could have been a line on the
 * caster's screen, and that would have been the lazy version: the mark would then
 * exist only for whoever cast it, would be invisible in a replay and to anybody
 * watching, and would need a private channel between the game and its client to
 * say "draw a ring here for forty frames". Made a thing in the world instead, it
 * needs none of that — the client already draws everything in the world with the
 * look its own data file gives it, exactly as it draws an arrow, so the mark
 * costs no client code at all and the fog hides it like anything else.
 *
 * <p>No body and no geometry: nothing shoots at it and nothing walks into it.
 */
public final class FallingUpdate extends UpdateModule {

    /**
     * How long it falls and what it does when it arrives.
     *
     * <p>Empty in the creature file the way an arrow's block is: what falls, how
     * hard and how wide is the skill's, and is filled in by whoever calls it down.
     */
    public static ModuleData parseData(Ini ini) {
        ini.initFromIni(new Object(), NO_FIELDS);
        return null;
    }

    private static final FieldParseTable<Object> NO_FIELDS = new FieldParseTable<>();

    private ObjectId caller;
    private float damage;
    private float radius;
    private int fallsIn;
    private boolean called;

    public FallingUpdate(GameObject owner, ModuleData ignored) {
        super(owner);
    }

    /**
     * Call it down: it marks the ground for {@code frames} and then lands.
     *
     * <p>What it is worth is settled here rather than on arrival, like every other
     * shot in this dungeon — the caster may have levelled, or died, in the second
     * it spends falling, and neither should change what was already in the air.
     */
    public void callDown(GameObject from, float carrying, float blast, int frames) {
        this.caller = from.getId();
        this.damage = carrying;
        this.radius = blast;
        this.fallsIn = Math.max(1, frames);
        this.called = true;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || !called) {
            // Never called down -- a mark with nothing behind it is not a mark.
            owner.markDestroyed();
            return;
        }
        if (--fallsIn > 0) {
            return; // still coming. The mark on the floor is the whole of this frame.
        }
        land(world, owner);
        owner.markDestroyed();
    }

    /** How much of its fall is left, as a share. For anything that wants to draw it. */
    public int framesLeft() {
        return Math.max(0, fallsIn);
    }

    private void land(World world, GameObject owner) {
        int side = owner.getPlayerIndex();
        var caster = world.findObject(caller);
        for (var victim : world.objectsInRange(owner.getPosition(), radius, candidate ->
                candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(side, candidate.getPlayerIndex())
                                == Relationship.ENEMIES)) {
            victim.getBody().damage(damage, DamageType.EXPLOSION);
            if (victim.isEffectivelyDead()) {
                award(caster, victim);
            }
        }
        // The same announcement every other blast makes, so the client draws the
        // impact from the recipe its own file names rather than from anything here.
        world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                owner.getPosition(), owner.getPosition()));
    }

    /**
     * The kill is the caster's.
     *
     * <p>It has to be said outright, because by the time this lands the thing that
     * killed the monster is a rock with no memory of who threw it. Without this a
     * hero who won a floor entirely with his ultimate would finish it at level one.
     */
    private void award(GameObject caster, GameObject victim) {
        var earned = victim.findModule(ExperienceModule.class);
        var his = caster == null ? null : caster.findModule(ExperienceModule.class);
        if (his != null && earned != null) {
            his.addExperience(earned.getExperienceValue());
        }
    }
}
