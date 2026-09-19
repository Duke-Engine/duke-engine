package uz.duke.dungeon.combat;

import uz.duke.core.data.Link;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.Module;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.ModuleGroups;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.Projectile;
import uz.duke.rts.module.ProjectileLauncher;

/**
 * Turns a creature's shots into things that have to get there.
 *
 * <p>Its weapon still decides everything else — what it is shooting at, how
 * often, how hard — and hands over only the landing. What arrives is an object in
 * the world with a distance to cross, so the damage happens where and when the
 * projectile does.
 *
 * <p>That is the whole difference between an archer and a man who points: at
 * ninety-five units the shot is in the air for about a third of a second, which
 * is long enough to see, long enough to walk out from under, and long enough that
 * a monster can die from an arrow loosed before it started moving.
 *
 * <p><b>What it looses is its own business.</b> It was the hero's bow when the
 * hero was the only thing down here that shot at all, and it read the one arrow
 * the settings file named. A dungeon with a crossbow skeleton in it and a mage
 * throwing fire has three shooters and three projectiles, so each block says what
 * leaves it — and a block that says nothing still gets the hero's arrow, which is
 * what keeps his own creature file a single empty pair of lines.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class Bow extends Module implements ProjectileLauncher {

    /**
     * What this one looses, or {@code null} in any field to take the game's own
     * answer from {@code Combat}.
     *
     * @param projectile   the template that leaves the weapon
     * @param speed        how fast it crosses the distance, in units a second
     * @param muzzleOffset how far out in front of the shooter it appears, so it
     *     does not squeeze out of its own chest
     */
    public record Data(@Link(Projectile.class) String projectile, float speed, float muzzleOffset) implements ModuleData {

        /** A block that leaves the offset out means the game's own, and zero is an offset. */
        static final Data DEFAULTS = new Data(null, 0f, -1f);
    }

    private final DungeonSettings settings;
    private final Data data;

    public Bow(GameObject owner, Data data, DungeonSettings settings) {
        super(owner);
        this.data = data;
        this.settings = settings;
    }

    /**
     * Declining rather than swallowing the shot: a creature whose projectile is
     * missing from the data files should still be able to fight, and the weapon
     * then lands it the old way. See {@link Shot} for where it comes out and how it
     * flies — shared with the hero's heavy shot, which is a skill rather than a
     * weapon.
     */
    @Override
    public boolean launch(GameObject shooter, GameObject victim, float damage, DamageType type) {
        return Shot.loose(shooter, victim, damage, type, projectile(), speed(), muzzleOffset());
    }

    private String projectile() {
        return data == null || data.projectile() == null
                ? settings.combat().arrowTemplate() : data.projectile();
    }

    private float speed() {
        return data == null || data.speed() <= 0f ? settings.combat().arrowSpeed() : data.speed();
    }

    private float muzzleOffset() {
        return data == null || data.muzzleOffset() < 0f
                ? settings.combat().arrowMuzzleOffset() : data.muzzleOffset();
    }
}
