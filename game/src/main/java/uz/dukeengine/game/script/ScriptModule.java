package uz.dukeengine.game.script;

import java.util.function.Supplier;
import java.util.logging.Logger;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;

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
     * Register a script so a unit's block can run it, the way every other module is registered: by the
     * {@code Data} record nested in the script's own class, whose enclosing class is the word the block
     * opens with.
     *
     * <pre>{@code
     * // in the script:
     * @ModuleGroup(ModuleGroups.SCRIPT)
     * public final class Guard extends UnitScript {
     *     public record Data() implements ModuleData { }
     *     …
     * }
     *
     * // when the game is built:
     * game.customModules(factory -> ScriptModule.registerScript(factory, Guard.Data.class, Guard::new));
     *
     * // in the unit's block:
     * Modules = [
     *   Guard
     *   End
     * ]
     * }</pre>
     *
     * <p>There is deliberately no name here. A script used to be reached through a string —
     * {@code ScriptModule}, {@code Name = BruteBrain} — and a string is a thing the editor cannot
     * complete, cannot open and cannot check: a typo was found when the game started, if then. The block
     * is the class now, so all three work and there is nothing to keep in step.
     */
    public static <D extends ModuleData> void registerScript(ModuleFactory factory, Class<D> data,
            Supplier<? extends UnitScript> newScript) {
        factory.register(data, (owner, ignored) -> new ScriptModule(owner, newScript.get()));
    }
}
