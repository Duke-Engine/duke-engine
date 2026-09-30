package uz.dukeengine.combat;

import java.util.List;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.combat.module.AutoHealUpdate;
import uz.dukeengine.combat.module.ExperienceModule;
import uz.dukeengine.combat.module.PursueUpdate;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * The modules of what fights, whatever the genre — weapons, closing on a target, experience, healing over time and
 * timed statuses — each written in a file as a block named after its class. An RTS's module set includes them, and so
 * may any game's that arms its things.
 */
public final class CombatModules {

    /** Every combat module, by its data: the words a game's files may add to the engine's. */
    public static final List<Class<? extends ModuleData>> MODULES = List.of(
            WeaponUpdate.Data.class, PursueUpdate.Data.class, ExperienceModule.Data.class, AutoHealUpdate.Data.class,
            StatusUpdate.Data.class);

    private CombatModules() {
    }

    /** The engine's default modules plus the combat ones. */
    public static ModuleFactory withDefaults() {
        var factory = ModuleFactory.withDefaults();
        register(factory);
        return factory;
    }

    /** Add the combat modules to an existing factory. */
    public static void register(ModuleFactory factory) {
        factory.register(WeaponUpdate.Data.class, WeaponUpdate::new)
                .register(PursueUpdate.Data.class, PursueUpdate::new)
                .register(ExperienceModule.Data.class, ExperienceModule::new)
                .register(AutoHealUpdate.Data.class, AutoHealUpdate::new)
                .register(StatusUpdate.Data.class, StatusUpdate::new);
    }
}
