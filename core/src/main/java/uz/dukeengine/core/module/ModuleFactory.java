package uz.dukeengine.core.module;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.SubsystemInterface;
import uz.dukeengine.core.thing.GameObject;

/**
 * Builds {@link Module}s from their data, ported from SAGE's {@code ModuleFactory}.
 *
 * <p>Each module is registered by its data record, which is also how a file names it: by the
 * class the record is written in, {@code MoveUpdate.Data} for a {@code MoveUpdate} block in a
 * {@code Modules = [ … ]} list. {@link
 * uz.dukeengine.core.thing.ThingFactory} consults this factory to build a new object's modules from its
 * template. Registrations are code, not game data, so they survive {@link #reset()}; a second
 * registration of the same data replaces the first, which is how a game builds an engine module
 * its own way.
 */
public final class ModuleFactory extends SubsystemInterface {

    /** Constructs a module of one kind for an owner from the data its block was read into. */
    @FunctionalInterface
    public interface Builder<D extends ModuleData> {
        Module build(GameObject owner, D data);
    }

    /** The engine's genre-neutral modules: a body that holds health and a locomotor that walks to a goal. */
    public static final List<Class<? extends ModuleData>> ENGINE_MODULES = List.of(ActiveBody.Data.class, MoveUpdate.Data.class);

    private final Map<Class<? extends ModuleData>, Builder<?>> builders = new LinkedHashMap<>();

    /**
     * A factory pre-loaded with {@link #ENGINE_MODULES}. Whatever a specific genre needs on top is
     * registered by that genre's own module set — see {@code uz.dukeengine.rts.module.RtsModules}.
     */
    public static ModuleFactory withDefaults() {
        var factory = new ModuleFactory();
        factory.register(ActiveBody.Data.class, ActiveBody::new);
        factory.register(MoveUpdate.Data.class, MoveUpdate::new);
        return factory;
    }

    /** Modules whose block reads into {@code data} are built by {@code builder}. */
    public <D extends ModuleData> ModuleFactory register(Class<D> data, Builder<? super D> builder) {
        builders.put(data, builder);
        return this;
    }

    /** The builder {@code data} was registered with, or null: for a registration that adds to an earlier one. */
    public Builder<?> builderFor(Class<? extends ModuleData> data) {
        return builders.get(data);
    }

    /** What a module's block is called: its class's name, {@code MoveUpdate} for {@code MoveUpdate.Data}. */
    public static String nameOf(Class<? extends ModuleData> data) {
        var module = data.getEnclosingClass();
        return (module != null ? module : data).getSimpleName();
    }

    /** Every module this factory builds, by the word a block names it with. */
    public Map<String, Class<? extends ModuleData>> vocabulary() {
        return vocabularyOf(builders.keySet());
    }

    /** {@code modules} by the word a block names each with. */
    public static Map<String, Class<? extends ModuleData>> vocabularyOf(Collection<Class<? extends ModuleData>> modules) {
        var words = new LinkedHashMap<String, Class<? extends ModuleData>>();
        for (var data : modules) {
            var clash = words.put(nameOf(data), data);
            if (clash != null && clash != data) {
                throw new IllegalArgumentException("two modules are called " + nameOf(data) + ": "
                        + clash.getName() + " and " + data.getName());
            }
        }
        return words;
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

    /** Build the module {@code data} was read for, on {@code owner}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Module newModule(GameObject owner, ModuleData data) {
        Builder builder = builders.get(data.getClass());
        if (builder == null) {
            throw new IllegalArgumentException("no module registered for " + nameOf(data.getClass()));
        }
        return builder.build(owner, data);
    }
}
