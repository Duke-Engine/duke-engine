package uz.dukeengine.core.script;

import java.util.function.Consumer;
import java.util.function.Predicate;
import uz.dukeengine.core.GameLogic;

/**
 * A scripted condition→action rule, ported in spirit from SAGE's map scripts.
 *
 * <p>Each frame the {@link ScriptEngine} evaluates the {@link #condition}; when
 * it holds, the {@link #action} runs. A {@link #oneShot} trigger fires at most
 * once (victory/defeat, a one-time reinforcement); a repeating trigger fires
 * every frame its condition is true. Both must be deterministic — they run
 * inside the logic path on every peer.
 *
 * @param name      a label for debugging
 * @param condition tested against the simulation each frame
 * @param action    run when the condition holds
 * @param oneShot   whether the trigger is removed after firing once
 */
public record Trigger(String name, Predicate<GameLogic> condition,
        Consumer<GameLogic> action, boolean oneShot) {

    /** A trigger that fires once, the first frame its condition becomes true. */
    public static Trigger once(String name, Predicate<GameLogic> condition, Consumer<GameLogic> action) {
        return new Trigger(name, condition, action, true);
    }

    /** A trigger that fires every frame its condition is true. */
    public static Trigger repeating(String name, Predicate<GameLogic> condition, Consumer<GameLogic> action) {
        return new Trigger(name, condition, action, false);
    }
}
