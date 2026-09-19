package uz.duke.dungeon.level;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.rts.module.WeaponUpdate;

/**
 * A hero's creature block before his attributes: the health, speed and blow they are
 * added to.
 *
 * <p>Read off the template because the engine's locomotor and weapon do not hand their
 * numbers back once built — and because the base is what the file wrote, not what the
 * body in the world has grown into.
 */
public record HeroBase(float maxHealth, float speed, float turnRate, float damage) {

    public static HeroBase of(ThingTemplate template) {
        GrowableBody.Data body = null;
        MoveUpdate.Data legs = null;
        WeaponUpdate.Data weapon = null;
        // The first of each, as every other reader of a template takes it.
        for (var entry : template.modules()) {
            switch (entry) {
                case GrowableBody.Data found -> body = body == null ? found : body;
                case MoveUpdate.Data found -> legs = legs == null ? found : legs;
                case WeaponUpdate.Data found -> weapon = weapon == null ? found : weapon;
                case null, default -> {
                }
            }
        }
        return new HeroBase(body == null ? 0f : body.maxHealth(),
                legs == null ? 0f : legs.speed(),
                legs == null ? 0f : legs.turnRate(),
                weapon == null ? 0f : weapon.damage());
    }
}
