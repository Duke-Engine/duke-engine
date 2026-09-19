package uz.duke.game.script;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import uz.duke.core.module.Module;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.ModuleGroups;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;

/**
 * The adapter that runs a {@link UnitScript} as an engine module — the bridge
 * between user code and the deterministic simulation.
 *
 * <p>Failure isolation is deliberate: a script that throws is logged and
 * disabled for that unit, but the simulation (and everyone else's scripts)
 * keeps running — a broken mod must never crash the game.
 */
@ModuleGroup(ModuleGroups.SCRIPT)
public final class ScriptModule extends UpdateModule {

    private static final Logger LOG = Logger.getLogger(ScriptModule.class.getName());

    /** {@code Name}: which of the scripts registered with the factory the unit runs. */
    public record Data(String name) implements ModuleData {
        public Data {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("a ScriptModule names the script it runs: Name = …");
            }
        }
    }

    private final UnitScript script;
    private boolean started;
    private boolean broken;

    public ScriptModule(GameObject owner, UnitScript script) {
        super(owner);
        this.script = script;
        script.unit = owner;
        script.world = owner.getWorld();
    }

    @Override
    public void update() {
        if (broken || getOwner().isEffectivelyDead()) {
            return;
        }
        if (script.world == null) {
            script.world = getOwner().getWorld(); // world is bound after construction
        }
        try {
            if (!started) {
                started = true;
                script.onStart();
            }
            script.onUpdate();
        } catch (RuntimeException e) {
            broken = true;
            LOG.warning(() -> "script " + script.getClass().getSimpleName() + " on unit "
                    + getOwner().getTemplate().name() + " failed and was disabled: " + e);
        }
    }

    /**
     * Register a script with a module factory so a unit's block can run it:
     * {@code ScriptModule}, {@code Name = <name>}, {@code End}. Both the Studio's Play and
     * exported games go through this single entry point.
     */
    public static void registerScript(ModuleFactory factory, String name, Supplier<UnitScript> newScript) {
        var scripts = factory.builderFor(Data.class) instanceof Scripts known ? known : new Scripts();
        scripts.byName.put(name, newScript);
        factory.register(Data.class, scripts);
    }

    /** The scripts one factory knows, by name: every {@code ScriptModule} block is built through it. */
    private static final class Scripts implements ModuleFactory.Builder<Data> {
        private final Map<String, Supplier<UnitScript>> byName = new HashMap<>();

        @Override
        public Module build(GameObject owner, Data data) {
            var script = byName.get(data.name());
            if (script == null) {
                throw new IllegalArgumentException("no script is registered as '" + data.name() + "'");
            }
            return new ScriptModule(owner, script.get());
        }
    }
}
