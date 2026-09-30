package uz.dukeengine.combat.module;

import java.util.List;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.thing.GameObject;

/**
 * An order a unit carries out over many frames — an attack-move, a guard — held as a module of its own while it
 * lasts. A new order to the unit gives it up: whatever the player says last is what the unit does.
 *
 * <p>Given up, it is taken off its unit and told so ({@link uz.dukeengine.core.module.Module#onRemoved}) before the
 * unit's modules next update — where an errand that put words on its unit for its show, a flag being raised or an
 * arm unfolded, takes them off again.
 */
public interface Errand {

    /** Give up every errand {@code unit} is on. Between frames of its modules' updates, as orders are applied. */
    static void giveUpAll(GameObject unit) {
        for (Module module : List.copyOf(unit.getModules())) {
            if (module instanceof Errand) {
                unit.removeModule(module);
            }
        }
    }
}
