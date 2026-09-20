package uz.dukeengine.dungeon.skill;

import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;

/**
 * Holy light on its way down onto one of the dungeon's own, and the mark it lies as
 * while it comes.
 *
 * <p>The meteor's mark turned round: it lies where it was called for a moment, and
 * when it lands it mends rather than burns. A thing in the world rather than a picture
 * for the same reason the mark is -- the client draws what the world holds, and takes
 * a mark that lay still and then went as a landing, so the column comes down on the
 * frame the mending lands and the fog hides it like anything else.
 *
 * <p>It mends whoever it was called down for, wherever he has stepped to in the moment
 * it took. If he died in that moment it mends nobody.
 *
 * <p>No body and no geometry: nothing shoots at it and nothing walks into it.
 */
@ModuleGroup({ModuleGroups.EFFECT, ModuleGroups.BODY})
public final class MendingUpdate extends UpdateModule {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    private ObjectId patient;
    private float amount;
    private int landsIn;
    private boolean called;

    public MendingUpdate(GameObject owner, ModuleData ignored) {
        super(owner);
    }

    /** Call it down on {@code on}: it lies for {@code frames} and then gives back {@code by}. */
    public void callDown(GameObject on, float by, int frames) {
        this.patient = on.getId();
        this.amount = by;
        this.landsIn = Math.max(1, frames);
        this.called = true;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || !called) {
            owner.markDestroyed(); // never called down: a light with nobody under it
            return;
        }
        if (--landsIn > 0) {
            return;
        }
        var him = world.findObject(patient);
        if (him != null && him.getBody() != null && !him.isEffectivelyDead()) {
            him.getBody().heal(amount);
        }
        owner.markDestroyed();
    }
}
