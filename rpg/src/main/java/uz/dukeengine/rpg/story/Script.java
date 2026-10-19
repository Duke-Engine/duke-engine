package uz.dukeengine.rpg.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A run's triggers, as the simulation keeps them: which have fired, which flags are set, which are waiting out their
 * delay, and which zones a hero stood in when last looked. It hears events and says which triggers are due; doing
 * what they say is the game's, which has the world. Plain lists in the order things happened, so the same on every
 * machine and saved as they are.
 */
public final class Script {

    /** A trigger waiting out its delay: so many frames left. */
    public record Waiting(String trigger, int framesLeft) {
    }

    /** The script as a save keeps it. */
    public record State(List<String> fired, List<String> flags, List<Waiting> waiting, List<String> occupied) {

        public State {
            fired = List.copyOf(fired);
            flags = List.copyOf(flags);
            waiting = List.copyOf(waiting);
            occupied = List.copyOf(occupied);
        }
    }

    private final List<Trigger> triggers;
    private final Map<String, Trigger> byName = new LinkedHashMap<>();
    private final List<String> fired = new ArrayList<>();
    private final List<String> flags = new ArrayList<>();
    private final List<Waiting> waiting = new ArrayList<>();
    private final List<String> occupied = new ArrayList<>();
    /** The map now played, by its name: null for none named. */
    private String map;
    /** Whether a trigger's condition holds now: the game's, which has the world; the flags alone until it says. */
    private Predicate<Trigger.Condition> holds = this::flagsAlone;

    /** The triggers of the game's files, in their order. */
    public Script(List<Trigger> triggers) {
        this.triggers = List.copyOf(triggers);
        for (var trigger : this.triggers) {
            byName.putIfAbsent(trigger.name(), trigger);
        }
    }

    /** The trigger of that name, or null. */
    public Trigger trigger(String name) {
        return byName.get(name);
    }

    /** Who judges a trigger's {@code If}: asked the moment its When is heard. */
    public void judgedBy(Predicate<Trigger.Condition> judge) {
        holds = judge == null ? this::flagsAlone : judge;
    }

    /** A condition as the script alone can judge it: a flag's; anything else holds. */
    public boolean flagsAlone(Trigger.Condition condition) {
        return switch (condition.is()) {
            case Trigger.Is.FLAG_SET -> flagged(condition.target());
            case Trigger.Is.FLAG_UNSET -> !flagged(condition.target());
            default -> true;
        };
    }

    /** Everything back to nothing: a run has ended. */
    public void reset() {
        fired.clear();
        flags.clear();
        waiting.clear();
        occupied.clear();
    }

    /** The map whose floor is now laid, and its MAP_START. */
    public void mapStarted(String name) {
        map = name;
        occupied.clear();
        heard(Trigger.When.MAP_START, "", 0);
    }

    /** Something happened: every trigger waiting on it, of this map, is set going, in file order. */
    public void heard(String when, String target, int step) {
        for (var trigger : triggers) {
            if (trigger.when().equals(when) && (when.equals(Trigger.When.MAP_START) || trigger.target().equals(target))
                    && (!when.equals(Trigger.When.STEP_DONE) || trigger.step() == step)
                    && (trigger.map().isBlank() || trigger.map().equals(map))
                    && (trigger.repeat() || !fired.contains(trigger.name()))
                    && trigger.If().stream().allMatch(holds)) {
                if (!fired.contains(trigger.name())) {
                    fired.add(trigger.name());
                }
                waiting.add(new Waiting(trigger.name(), trigger.delay()));
            }
        }
    }

    /** The zones a hero stands in now: every one he has come into since last looked is heard. */
    public void standing(List<String> zones) {
        for (var zone : zones) {
            if (!occupied.contains(zone)) {
                heard(Trigger.When.ZONE, zone, 0);
            }
        }
        occupied.clear();
        occupied.addAll(zones);
    }

    /** Set {@code flag}, and hear it. */
    public void flag(String flag) {
        if (!flags.contains(flag)) {
            flags.add(flag);
            heard(Trigger.When.FLAG, flag, 0);
        }
    }

    public boolean flagged(String flag) {
        return flags.contains(flag);
    }

    /**
     * A frame gone by: the triggers whose time has come, in the order they were set going — each heard as firing, so
     * what comes AFTER it is set going in turn.
     */
    public List<Trigger> due() {
        var now = new ArrayList<Trigger>();
        for (int at = 0; at < waiting.size(); at++) {
            var each = waiting.get(at);
            if (each.framesLeft() <= 0) {
                waiting.remove(at--);
                now.add(byName.get(each.trigger()));
                heard(Trigger.When.AFTER, each.trigger(), 0); // one with no delay comes up later in this same loop
            }
        }
        // And a frame gone for the rest: one heard with a Delay of 3 at frame f acts at f + 3.
        waiting.replaceAll(each -> new Waiting(each.trigger(), each.framesLeft() - 1));
        return now;
    }

    public State state() {
        return new State(fired, flags, waiting, occupied);
    }

    /** As {@code saved} left it; a trigger the files no longer have is left out. */
    public void restore(State saved) {
        reset();
        saved.fired().stream().filter(byName::containsKey).forEach(fired::add);
        flags.addAll(saved.flags());
        saved.waiting().stream().filter(each -> byName.containsKey(each.trigger())).forEach(waiting::add);
        occupied.addAll(saved.occupied());
    }
}
