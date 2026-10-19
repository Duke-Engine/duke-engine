package uz.dukeengine.rpg.story;

import java.util.List;

/**
 * When something happens, do a list of things, in order. What it waits for is a word ({@code When}) — the few a story
 * itself says are {@link When}'s, the rest the game's own, heard from its world — and what it does is the game's: its
 * own {@link Action} record, read from the {@code Action} blocks of its files ({@code Binder.vocabulary(Trigger.Action
 * .class, Map.of("Action", TheGamesAction.class))}). The script says when a trigger is due; the game, which has the
 * world, does it.
 *
 * @param map    the map it belongs to, by its name; blank for every map
 * @param target what of: a zone, a creature, a quest, a conversation, a trigger, a flag — by {@code when}
 * @param step   for {@link When#STEP_DONE}, which step, 1 its first
 * @param delay  frames after the event before it acts
 * @param repeat whether it fires every time, rather than once
 * @param If     what must hold, every one, the moment its When is heard; one that does not leaves it unfired, and a
 *               once-only trigger waits for the next time. Named as the file writes it, {@code If = [...]}
 */
public record Trigger(String name, String map, String when, String target, int step, int delay, boolean repeat,
        List<Condition> If, List<Action> actions) {

    public static final Trigger DEFAULTS = new Trigger("", "", "", "", 0, 0, false, List.of(), List.of());

    public Trigger {
        If = If == null ? List.of() : List.copyOf(If);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** One thing a trigger does: the game's own record, read from its files' {@code Action} blocks. */
    public interface Action {
    }

    /** One thing that must hold: {@code Is}, of {@code Target} — a flag, an item, a quest, a creature. */
    public record Condition(String is, String target) {

        public static final Condition DEFAULTS = new Condition("", "");
    }

    /** The words a story itself hears; a game hears more of its own ({@link Script#heard}). */
    public static final class When {

        public static final String MAP_START = "MAP_START";
        public static final String ZONE = "ZONE";
        public static final String QUEST_STARTED = "QUEST_STARTED";
        public static final String QUEST_DONE = "QUEST_DONE";
        public static final String STEP_DONE = "STEP_DONE";
        public static final String HEARD = "HEARD";
        public static final String AFTER = "AFTER";
        public static final String FLAG = "FLAG";

        private When() {
        }
    }

    /** The conditions a script judges itself; any other its game judges ({@link Script#judgedBy}). */
    public static final class Is {

        public static final String FLAG_SET = "FLAG_SET";
        public static final String FLAG_UNSET = "FLAG_UNSET";

        private Is() {
        }
    }
}
