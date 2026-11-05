package uz.dukeengine.rpg.fight;

import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.Seal;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.combat.module.ProjectileLauncher;

/**
 * Remembers the frame this creature last struck a blow, and lets it drink from the blow if a skill of its says so --
 * see {@link SkillBook#drink}.
 *
 * <p>It launches nothing, which is why it is worth explaining. A monster's blow
 * lands where it stands and always did; what the game needs is not to change that
 * but to <em>know when it happened</em>, so that a monster which has swung stands
 * and finishes the swing instead of walking off mid-blow — see
 * a creature's brain.
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
 *
 * <p>The one blow it takes is one struck through a seal (see {@link uz.dukeengine.rpg.Seal}): that meets the gate, and
 * nothing lands — see {@link Seal}. So the knight carries one too, his sword being the one blow of a hero's that lands
 * where he stands.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class Swing extends Module implements ProjectileLauncher, Saveable {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    /** Long before any run begins, so nothing counts as recently struck at first. */
    private int struckOn = Integer.MIN_VALUE / 2;

    private final Seal seal;

    public Swing(GameObject owner, Seal seal) {
        super(owner);
        this.seal = seal;
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
        // A creature with a Bow lands its blows through its arrows, which the shut gate stops where they reach
        // it, and which drink where they land.
        if (striker.findModule(Bow.class) != null) {
            return false;
        }
        if (world != null && seal.parts(world, striker.getPosition(), victim.getPosition())) {
            return true; // struck at the shut gate between them: taken, and nothing lands
        }
        // Declined, the weapon lands it where it stands, this frame: a blow landed, and its
        // striker drinks from it if a skill of its says so.
        SkillBook.drink(striker, damage);
        return false; // nothing flies; the weapon lands it where it stands
    }

    @Override
    public void save(SaveOut out) {
        out.writeInt(struckOn);
    }

    @Override
    public void load(SaveIn in) {
        struckOn = in.readInt();
    }
}
