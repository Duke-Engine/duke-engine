package uz.dukeengine.dungeon.combat;

import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.module.ProjectileLauncher;

/**
 * Remembers the frame this creature last struck a blow.
 *
 * <p>It launches nothing, which is why it is worth explaining. A monster's blow
 * lands where it stands and always did; what the game needs is not to change that
 * but to <em>know when it happened</em>, so that a monster which has swung stands
 * and finishes the swing instead of walking off mid-blow — see
 * {@code MonsterBrain}.
 *
 * <p>{@link ProjectileLauncher} is the engine's one hook at the moment a weapon
 * lets go, and declining the shot is a documented answer to it: the weapon lands
 * the damage the ordinary way, exactly as it would with no launcher at all. So
 * this is the callback used for what it is, and the only unusual thing about it is
 * that it takes nothing.
 *
 * <p>The alternatives were worse. Watching the victim's health cannot tell two
 * monsters apart when both are hitting the same hero; watching the weapon's
 * reload would need the engine to expose it; and guessing from distance — which
 * is what this replaced — made a monster pay for a blow every time it merely came
 * close, so anything faster than it could walk away for free.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class Swing extends Module implements ProjectileLauncher {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    /** Long before any run begins, so nothing counts as recently struck at first. */
    private int struckOn = Integer.MIN_VALUE / 2;

    public Swing(GameObject owner, uz.dukeengine.core.module.ModuleData ignored) {
        super(owner);
    }

    /** Whether the blow it struck is still in progress at {@code frame}. */
    public boolean stillSwinging(int frame, int swingFrames) {
        return frame - struckOn < swingFrames;
    }

    @Override
    public boolean launch(GameObject striker, GameObject victim, float damage, DamageType type) {
        var world = striker.getWorld();
        if (world != null) {
            struckOn = world.getFrame();
        }
        return false; // nothing flies; the weapon lands it where it stands
    }
}
