package uz.duke.dungeon.combat;

import uz.duke.core.module.Module;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.ai.SightLine;
import uz.duke.rts.module.WeaponHold;
import uz.duke.rts.module.WeaponUpdate;

/**
 * He shoots at what he can see, and at nothing else.
 *
 * <p><em>See</em>, not <em>have a clear line to</em>. Stone is one of three things
 * that hide a creature and the other two are as ordinary: it may be further off
 * than his eyes reach, or standing on a floor above his own. {@link SightLine#sees}
 * holds all three, and holding them here is what lets his bow outrange his eyes
 * without him shooting into the dark.
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
 * <p>It was the hero's alone while everything else down here fought at arm's
 * length, where there is nothing between a creature and what it is hitting. The
 * crossbow skeleton and the mage carry it too, and for the plainer reason: the
 * first thing with a reach worth the name would otherwise shoot him through the
 * stone between them, which is the one thing a dungeon must not allow.
 */
public final class EyesOnly extends Module implements WeaponHold {

    /**
     * Read for one number: how far apart two storeys are.
     *
     * <p>Which is a fact about the floor rather than about him, and there is no
     * asking the world for it — so it arrives the way {@code Bow}'s numbers do,
     * from the game that registered this module.
     */
    private final uz.duke.dungeon.content.DungeonSettings settings;

    public EyesOnly(GameObject owner, uz.duke.dungeon.content.DungeonSettings settings) {
        super(owner);
        this.settings = settings;
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
        return victim != null && !SightLine.sees(owner, victim, settings.storeyHeight());
    }
}
