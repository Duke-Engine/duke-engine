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

    /**
     * A factory pre-loaded with the engine's genre-neutral modules: a body that
     * holds health and a locomotor that walks to a goal. Whatever a specific
     * genre needs on top is registered by that genre's own module set — see
     * {@code uz.duke.rts.module.RtsModules}.
     */
    public static ModuleFactory withDefaults() {
        var factory = new ModuleFactory();
        factory.register("ActiveBody",
                (owner, data) -> new ActiveBody(owner, (ActiveBody.Data) data),
                ActiveBody::parseData);
        factory.register("MoveUpdate",
                (owner, data) -> new MoveUpdate(owner, (MoveUpdate.Data) data),
                MoveUpdate::parseData);
        return factory;
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
