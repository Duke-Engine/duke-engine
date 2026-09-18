package uz.duke.game.script;

import java.util.function.Supplier;
import java.util.logging.Logger;
import uz.duke.core.ini.FieldParseTable;
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

    /** The INI tag prefix scripts are registered under: {@code Script:<name>}. */
    public static final String TAG_PREFIX = "Script:";

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
     * Register a script with a module factory so INI can reference it as
     * {@code Update = Script:<name>}. Both the Studio's Play and exported games
     * go through this single entry point.
     */
    public static void registerScript(ModuleFactory factory, String name, Supplier<UnitScript> newScript) {
        factory.register(TAG_PREFIX + name,
                (owner, data) -> new ScriptModule(owner, newScript.get()),
                ini -> {
                    ini.initFromIni(new Object(), new FieldParseTable<>()); // consume the empty block
                    return ModuleData.NONE;
                });
    }
}
