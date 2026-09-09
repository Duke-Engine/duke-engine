package uz.duke.rts.module;

import uz.duke.core.module.ModuleFactory;

/**
 * The RTS module set: everything an RTS adds on top of the engine's
 * genre-neutral modules — weapons, production, economy, veterancy, power,
 * transports, superweapons and timed status effects.
 *
 * <p>This is the seam between {@code core} and this module. A plain game calls
 * {@link ModuleFactory#withDefaults()} and gets a body and a locomotor; an RTS
 * calls {@link #withDefaults()} here and gets the full RTS vocabulary, still
 * addressed from INI by tag.
 */
public final class RtsModules {

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
        factory.register("WeaponUpdate",
                (owner, data) -> new WeaponUpdate(owner, (WeaponUpdate.Data) data),
                WeaponUpdate::parseData);
        factory.register("ProductionUpdate",
                (owner, data) -> new ProductionUpdate(owner, (ProductionUpdate.Data) data),
                ProductionUpdate::parseData);
        factory.register("ExperienceModule",
                (owner, data) -> new ExperienceModule(owner, (ExperienceModule.Data) data),
                ExperienceModule::parseData);
        factory.register("AutoHealUpdate",
                (owner, data) -> new AutoHealUpdate(owner, (AutoHealUpdate.Data) data),
                AutoHealUpdate::parseData);
        factory.register("StatusUpdate",
                (owner, data) -> new StatusUpdate(owner, (StatusUpdate.Data) data),
                StatusUpdate::parseData);
        factory.register("PowerModule",
                (owner, data) -> new PowerModule(owner, (PowerModule.Data) data),
                PowerModule::parseData);
        factory.register("CapacityGate",
                (owner, data) -> new CapacityGate(owner, (CapacityGate.Data) data),
                CapacityGate::parseData);
        factory.register("SpecialPowerModule",
                (owner, data) -> new SpecialPowerModule(owner, (SpecialPowerModule.Data) data),
                SpecialPowerModule::parseData);
        factory.register("ContainModule",
                (owner, data) -> new ContainModule(owner, (ContainModule.Data) data),
                ContainModule::parseData);
        factory.register("SupplyModule",
                (owner, data) -> new SupplyModule(owner, (SupplyModule.Data) data),
                SupplyModule::parseData);
        factory.register("HarvestUpdate",
                (owner, data) -> new HarvestUpdate(owner, (HarvestUpdate.Data) data),
                HarvestUpdate::parseData);
    }
}
