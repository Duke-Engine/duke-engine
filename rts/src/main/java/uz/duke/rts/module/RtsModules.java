package uz.duke.rts.module;

import java.util.List;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleFactory;

/**
 * The RTS module set: everything an RTS adds on top of the engine's
 * genre-neutral modules — weapons, production, economy, veterancy, power,
 * transports, superweapons and timed status effects.
 *
 * <p>This is the seam between {@code core} and this module. A plain game calls
 * {@link ModuleFactory#withDefaults()} and gets a body and a locomotor; an RTS
 * calls {@link #withDefaults()} here and gets the full RTS vocabulary, each
 * module written in a file as a block named after its class.
 */
public final class RtsModules {

    /** Every RTS module, by its data: the words an RTS's files may add to the engine's. */
    public static final List<Class<? extends ModuleData>> MODULES = List.of(
            WeaponUpdate.Data.class, ProductionUpdate.Data.class, ExperienceModule.Data.class,
            AutoHealUpdate.Data.class, StatusUpdate.Data.class, PowerModule.Data.class,
            CapacityGate.Data.class, SpecialPowerModule.Data.class, ContainModule.Data.class,
            SupplyModule.Data.class, HarvestUpdate.Data.class);

    private RtsModules() {
    }

    /** The engine's default modules plus the RTS ones. */
    public static ModuleFactory withDefaults() {
        var factory = ModuleFactory.withDefaults();
        register(factory);
        return factory;
    }

    /** Add the RTS modules to an existing factory. */
    public static void register(ModuleFactory factory) {
        factory.register(WeaponUpdate.Data.class, WeaponUpdate::new)
                .register(ProductionUpdate.Data.class, ProductionUpdate::new)
                .register(ExperienceModule.Data.class, ExperienceModule::new)
                .register(AutoHealUpdate.Data.class, AutoHealUpdate::new)
                .register(StatusUpdate.Data.class, StatusUpdate::new)
                .register(PowerModule.Data.class, PowerModule::new)
                .register(CapacityGate.Data.class, CapacityGate::new)
                .register(SpecialPowerModule.Data.class, SpecialPowerModule::new)
                .register(ContainModule.Data.class, ContainModule::new)
                .register(SupplyModule.Data.class, SupplyModule::new)
                .register(HarvestUpdate.Data.class, HarvestUpdate::new);
    }
}
