package uz.duke.core.module;

import java.util.HashMap;
import java.util.Map;
import uz.duke.core.SubsystemInterface;
import uz.duke.core.ini.Ini;
import uz.duke.core.thing.GameObject;

/**
 * Builds {@link Module}s by their INI tag, ported from SAGE's
 * {@code ModuleFactory}.
 *
 * <p>Each concrete module registers a builder under the name it is referenced by
 * in INI (e.g. {@code ActiveBody}). {@link uz.duke.core.thing.ThingFactory}
 * consults this factory to populate a new object's modules from its template.
 * Builder registrations are code, not game data, so they survive {@link #reset()}.
 */
public final class ModuleFactory extends SubsystemInterface {

    /** Constructs a module of one kind for an owner from its parsed data. */
    @FunctionalInterface
    public interface Builder {
        Module build(GameObject owner, ModuleData data);
    }

    /** Reads a module's INI sub-block (up to {@code End}) into its data. */
    @FunctionalInterface
    public interface DataParser {
        ModuleData parse(Ini ini);
    }

    private final Map<String, Builder> builders = new HashMap<>();
    private final Map<String, DataParser> dataParsers = new HashMap<>();

    /** A factory pre-loaded with the engine's built-in module types. */
    public static ModuleFactory withDefaults() {
        var factory = new ModuleFactory();
        factory.registerDefaults();
        return factory;
    }

    private void registerDefaults() {
        register("ActiveBody",
                (owner, data) -> new ActiveBody(owner, (ActiveBody.Data) data),
                ActiveBody::parseData);
        register("AIUpdate",
                (owner, data) -> new AIUpdate(owner, (AIUpdate.Data) data),
                AIUpdate::parseData);
        register("WeaponUpdate",
                (owner, data) -> new WeaponUpdate(owner, (WeaponUpdate.Data) data),
                WeaponUpdate::parseData);
        register("ProductionUpdate",
                (owner, data) -> new ProductionUpdate(owner, (ProductionUpdate.Data) data),
                ProductionUpdate::parseData);
        register("ExperienceModule",
                (owner, data) -> new ExperienceModule(owner, (ExperienceModule.Data) data),
                ExperienceModule::parseData);
        register("AutoHealUpdate",
                (owner, data) -> new AutoHealUpdate(owner, (AutoHealUpdate.Data) data),
                AutoHealUpdate::parseData);
        register("StatusUpdate",
                (owner, data) -> new StatusUpdate(owner, (StatusUpdate.Data) data),
                StatusUpdate::parseData);
        register("PowerModule",
                (owner, data) -> new PowerModule(owner, (PowerModule.Data) data),
                PowerModule::parseData);
        register("SpecialPowerModule",
                (owner, data) -> new SpecialPowerModule(owner, (SpecialPowerModule.Data) data),
                SpecialPowerModule::parseData);
        register("ContainModule",
                (owner, data) -> new ContainModule(owner, (ContainModule.Data) data),
                ContainModule::parseData);
        register("SupplyModule",
                (owner, data) -> new SupplyModule(owner, (SupplyModule.Data) data),
                SupplyModule::parseData);
        register("HarvestUpdate",
                (owner, data) -> new HarvestUpdate(owner, (HarvestUpdate.Data) data),
                HarvestUpdate::parseData);
    }

    /** Register a module that is only created in code (no INI sub-block). */
    public void register(String tag, Builder builder) {
        builders.put(tag, builder);
    }

    /** Register a module that can also be loaded from an INI sub-block. */
    public void register(String tag, Builder builder, DataParser dataParser) {
        builders.put(tag, builder);
        dataParsers.put(tag, dataParser);
    }

    /** Parse a module's data sub-block by module type, reading up to {@code End}. */
    public ModuleData parseData(String tag, Ini ini) {
        var parser = dataParsers.get(tag);
        if (parser == null) {
            throw new IllegalArgumentException("no INI data parser registered for module '" + tag + "'");
        }
        return parser.parse(ini);
    }

    @Override
    public void init() {
    }

    @Override
    public void reset() {
        // Builders are code registrations; nothing game-specific to clear.
    }

    @Override
    public void update() {
    }

    /** Build a module for {@code owner} from its tag and data. */
    public Module newModule(String tag, GameObject owner, ModuleData data) {
        var builder = builders.get(tag);
        if (builder == null) {
            throw new IllegalArgumentException("no module registered for tag '" + tag + "'");
        }
        return builder.build(owner, data);
    }
}
