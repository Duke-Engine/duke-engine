package uz.dukeengine.rpg.skill;

import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;

/**
 * Something a caster called up, for as long as it lasts.
 *
 * <p>When its time is out it falls down where it stands, as though it had been killed --
 * so it is drawn collapsing rather than blinking out -- but by nobody, so its going is
 * worth nothing to anyone.
 */
@ModuleGroup({ModuleGroups.EFFECT, ModuleGroups.BODY})
public final class Summoned extends UpdateModule implements Saveable {

    private int framesLeft;

    public Summoned(GameObject owner, int frames) {
        super(owner);
        this.framesLeft = Math.max(1, frames);
    }

    /** Frames until it falls down. */
    public int framesLeft() {
        return framesLeft;
    }

    @Override
    public void update() {
        var owner = getOwner();
        if (owner.isEffectivelyDead() || --framesLeft > 0) {
            return;
        }
        if (owner.getBody() != null) {
            owner.getBody().setHealth(0f);
        } else {
            owner.markDestroyed();
        }
    }

    @Override
    public void save(SaveOut out) {
        out.writeInt(framesLeft);
    }

    @Override
    public void load(SaveIn in) {
        throw new IllegalStateException("a summoned mark is made again by Summoned.revive, never by its template");
    }

    /** One put on what climbed out of a rift, made again with the frames it had left: what {@code ModuleFactory.revives} is given. */
    public static Summoned revive(GameObject owner, SaveIn in) {
        return new Summoned(owner, in.readInt());
    }
}
