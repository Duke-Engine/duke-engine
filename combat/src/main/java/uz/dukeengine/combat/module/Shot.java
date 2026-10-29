package uz.dukeengine.combat.module;

import java.util.List;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.combat.ArmedWorld;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.UnsavedModuleException;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;

/**
 * One shot a weapon took, as a {@link ProjectileLauncher} is handed it and {@link WeaponUpdate#land lands} it:
 * who fired it and for which side, which weapon from which slot, and the damage it carries.
 *
 * <p>Everything a landing needs, carried rather than looked up again when it arrives — by then the shooter may
 * be dead and gone, and the side it fired for is still the side its blast spares.
 *
 * @param shooter what fired it
 * @param side    the player it fired for: whose enemies its blast catches
 * @param weapon  the weapon that fired, with its name, splash radius and damage type
 * @param slot    which of the set's slots it fired from, the first 0
 * @param damage          what it deals before the victim's armour, every modifier and bonus already applied — and
 *                        its blast within {@code radius}
 * @param radius          how wide its blast is, the game's bonuses for the shooter applied as it fired
 * @param secondaryDamage what its blast deals beyond {@code radius} and within {@code secondaryRadius}, applied as
 *                        {@code damage} is
 * @param secondaryRadius how far its blast's second ring reaches, as {@code radius} is; 0 for none
 * @param wentOff         the template of what went off where it is not the shooter — a shell the shooter launched —
 *                        or null: its blast spares things of that template allied to it ({@code NOT_SIMILAR}), and
 *                        its launcher rather than the shooter's maker
 */
public record Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage, float radius,
        float secondaryDamage, float secondaryRadius, String wentOff, boolean critical) {

    /** A shot no roll made harder. */
    public Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage, float radius,
            float secondaryDamage, float secondaryRadius, String wentOff) {
        this(shooter, side, weapon, slot, damage, radius, secondaryDamage, secondaryRadius, wentOff, false);
    }

    /** A shot the shooter itself goes off with, as every shot was before one could say otherwise. */
    public Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage, float radius,
            float secondaryDamage, float secondaryRadius) {
        this(shooter, side, weapon, slot, damage, radius, secondaryDamage, secondaryRadius, null);
    }

    /** This shot gone off as {@code template} — the shell a launcher made of it — rather than as its shooter. */
    public Shot wentOffAs(String template) {
        return new Shot(shooter, side, weapon, slot, damage, radius, secondaryDamage, secondaryRadius, template,
                critical);
    }

    /**
     * {@code shot}, carried in {@code world}, for the save of a launcher that holds it in flight: read back by
     * {@link #read}. Its weapon is written as the game's, by name ({@link uz.dukeengine.combat.Armoury#findWeapon}), or
     * as the one its shooter's template writes in place, by that template and the module it is; refused where it is
     * neither.
     */
    public static void save(Shot shot, World world, SaveOut out) {
        String template = null;
        int module = -1;
        var game = world instanceof ArmedWorld arms
                ? arms.armoury().findWeapon(shot.weapon().name()) : null;
        if (!shot.weapon().equals(game)) {
            var shooter = world.findObject(shot.shooter());
            var modules = shooter == null ? List.<ModuleData>of() : shooter.getTemplate().modules();
            for (int at = 0; at < modules.size() && template == null; at++) {
                if (modules.get(at) instanceof WeaponUpdate.Data written && written.weaponSets().isEmpty()
                        && Weapon.of(written).equals(shot.weapon())) {
                    template = shooter.getTemplate().name();
                    module = at;
                }
            }
            if (template == null) {
                throw new UnsavedModuleException(Shot.class.getName(), "a shot in flight of "
                        + shot.weapon().name() + " is neither the game's weapon nor one its shooter's template writes"
                        + " in place: a load could not find it again");
            }
        }
        out.writeId(shot.shooter()).writeInt(shot.side()).writeString(shot.weapon().name()).writeString(template)
                .writeInt(module).writeInt(shot.slot()).writeFloat(shot.damage()).writeFloat(shot.radius())
                .writeFloat(shot.secondaryDamage()).writeFloat(shot.secondaryRadius()).writeString(shot.wentOff())
                .writeBoolean(shot.critical());
    }

    /** What {@link #save} wrote, read into the world {@code in} loads. */
    public static Shot read(SaveIn in) {
        var shooter = in.readId();
        int side = in.readInt();
        var name = in.readString();
        var template = in.readString();
        int module = in.readInt();
        var world = in.world();
        Weapon weapon = null;
        if (template == null) {
            weapon = world instanceof ArmedWorld arms ? arms.armoury().findWeapon(name) : null;
        } else {
            var made = world.findTemplate(template);
            var modules = made == null ? List.<ModuleData>of() : made.modules();
            if (module >= 0 && module < modules.size() && modules.get(module) instanceof WeaponUpdate.Data written) {
                weapon = Weapon.of(written);
            }
        }
        if (weapon == null) {
            throw new UnsavedModuleException(Shot.class.getName(), "a shot in flight was saved with the weapon " + name
                    + (template == null ? " of the game's" : " of " + template + "'s") + ", which this world does not"
                    + " have");
        }
        return new Shot(shooter, side, weapon, in.readInt(), in.readFloat(), in.readFloat(), in.readFloat(),
                in.readFloat(), in.readString(), in.readBoolean());
    }

    /** A shot whose blast is its weapon's own, with no bonus to it. */
    public Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage) {
        this(shooter, side, weapon, slot, damage, weapon == null ? 0f : weapon.splashRadius());
    }

    /** A shot whose second ring is its weapon's own, with no bonus to it. */
    public Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage, float radius) {
        this(shooter, side, weapon, slot, damage, radius, weapon == null ? 0f : weapon.secondaryDamage(),
                weapon == null ? 0f : weapon.secondaryRadius());
    }
}
