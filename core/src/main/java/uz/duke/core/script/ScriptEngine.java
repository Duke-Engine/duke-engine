package uz.duke.core.script;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.GameLogic;
import uz.duke.core.SubsystemInterface;

/**
 * Evaluates {@link Trigger}s each frame, ported in spirit from SAGE's
 * {@code ScriptEngine}.
 *
 * <p>This is how maps and missions react to the game: spawn reinforcements when
 * a player reaches a location, declare victory when a side is wiped out, and so
 * on. Triggers are evaluated in registration order; one-shot triggers are
 * removed after they fire. New triggers added by an action take effect from the
 * next frame, so a frame's trigger set is well-defined.
 */
public final class ScriptEngine extends SubsystemInterface {

    private final List<Trigger> triggers = new ArrayList<>();

    @Override
    public void init() {
        triggers.clear();
    }

    @Override
    public void reset() {
        triggers.clear();
    }

    @Override
    public void update() {
        // Driven by evaluate(logic) from GameLogic, which has the context.
    }

    public void addTrigger(Trigger trigger) {
        triggers.add(trigger);
    }

    public int getTriggerCount() {
        return triggers.size();
    }

    /** Fire any triggers whose condition holds; remove one-shots that fired. */
    public void evaluate(GameLogic logic) {
        List<Trigger> fired = null;
        int count = triggers.size(); // triggers added by actions wait until next frame
        for (int i = 0; i < count; i++) {
            var trigger = triggers.get(i);
            if (trigger.condition().test(logic)) {
                trigger.action().accept(logic);
                if (trigger.oneShot()) {
                    if (fired == null) {
                        fired = new ArrayList<>();
                    }
                    fired.add(trigger);
                }
            }
        }
        if (fired != null) {
            triggers.removeAll(fired);
        }
    }
}
