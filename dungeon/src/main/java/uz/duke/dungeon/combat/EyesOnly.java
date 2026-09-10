package uz.duke.dungeon.combat;

import uz.duke.core.module.Module;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.ai.SightLine;
import uz.duke.rts.module.WeaponHold;
import uz.duke.rts.module.WeaponUpdate;

/**
 * He shoots at what he can see, and at nothing else.
 *
 * <p>The engine's weapon finds its own target and fires in the same call, so
 * anything outside it can only ever disarm it a frame too late — that is written
 * down in {@code WeaponUpdate} and it is why {@code AttackOnTheMove} had to be a
 * weapon setting rather than a script. {@link WeaponHold} is the one hook that
 * runs <em>before</em> all of it, and it is what this uses.
 *
 * <p>Two things are held, and the second is the interesting one:
 *
 * <ul>
 *   <li>A target with stone between him and it. He keeps it — it may be what he
 *       was sent at, and then he walks round until he can see it — but he does not
 *       shoot through the wall at it.
 *   <li><em>Having no target at all.</em> That stops the weapon acquiring one,
 *       which hands the choice to {@link uz.duke.dungeon.ai.HeroBrain}. The
 *       engine's own acquisition is a nearest-enemy search with no notion of
 *       walls, so leaving it switched on would mean the first shot of every fight
 *       going through one before anything could object.
 * </ul>
 *
 * <p>The cost of the second is one frame: the brain names a target and the weapon
 * fires at it on the frame after. That is invisible next to a reload, and it buys
 * a rule that cannot be got round.
 *
 * <p>Only the hero carries this. The monsters fight at arm's length, where there
 * is nothing between them and what they are hitting.
 */
public final class EyesOnly extends Module implements WeaponHold {

    public EyesOnly(GameObject owner) {
        super(owner);
    }

    /** The empty block that puts this on a creature; it has nothing to configure. */
    public static uz.duke.core.module.ModuleData parseData(uz.duke.core.ini.Ini ini) {
        ini.initFromIni(new Object(), NO_FIELDS);
        return null;
    }

    private static final uz.duke.core.ini.FieldParseTable<Object> NO_FIELDS =
            new uz.duke.core.ini.FieldParseTable<>();

    @Override
    public boolean holdingFire() {
        var owner = getOwner();
        var weapon = owner.findModule(WeaponUpdate.class);
        var world = owner.getWorld();
        if (weapon == null || world == null) {
            return false;
        }
        var target = weapon.getTarget();
        if (target == null) {
            return true; // nothing named yet, and the weapon is not to name one
        }
        var victim = world.findObject(target);
        return victim != null && !SightLine.clear(owner, victim);
    }
}
