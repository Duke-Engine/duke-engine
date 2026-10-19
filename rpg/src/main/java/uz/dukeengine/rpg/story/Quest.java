package uz.dukeengine.rpg.story;

import java.util.List;

/**
 * Something to do, step by step: its kind (the tracker's main or side), its title and its steps, done in order. Its
 * description is what a quest log says of it under its steps, in the game's words.
 */
public record Quest(String name, Kind kind, String title, List<Step> steps, String description) {

    public static final Quest DEFAULTS = new Quest("", Kind.SIDE, "", List.of(), "");

    public Quest {
        steps = steps == null ? List.of() : List.copyOf(steps);
        description = description == null ? "" : description;
    }

    /** Which list of the tracker it is on. */
    public enum Kind {
        MAIN, SIDE
    }

    /** What a step waits for. */
    public enum Goal {
        /** A hero standing in a zone of the map. */
        REACH,
        /** {@code count} deaths of a creature's template. */
        KILL,
        /** {@code count} of an item in the heroes' bags. */
        COLLECT,
        /** A hero talking to a person. */
        TALK,
        /** A hero who holds the item talking to a person; it leaves his bag. */
        GIVE,
        /** A hero following a person to the step's {@code where}, keeping near; done when they stand in it with him. */
        FOLLOW
    }

    /**
     * One step: its goal, what the goal is of, and the tracker's line — {@code %d/%d} in it the count so far and the
     * count asked.
     *
     * @param target a zone (REACH), a creature (KILL), an item (COLLECT) or a person (TALK, GIVE, FOLLOW)
     * @param item   what a GIVE hands over
     * @param onDone what finishing it does, or null for nothing
     * @param glow   whether its target wears the game's glow while the step wants it
     * @param where  the zone of the map where it is done, for its marks; blank for the target itself
     */
    public record Step(Goal goal, String target, int count, String item, String text, Done onDone, boolean glow,
            String where) {

        public static final Step DEFAULTS = new Step(null, "", 1, "", "", null, false, "");

        public Step {
            where = where == null ? "" : where;
        }

        /** The tracker's line at {@code soFar}. */
        public String said(int soFar) {
            return text.replaceFirst("%d", Integer.toString(soFar)).replaceFirst("%d", Integer.toString(count));
        }
    }

    /**
     * What finishing a step does: a conversation played, experience to every hero, an item into the bag of the hero
     * who finished it, and another quest started.
     */
    public record Done(String says, int experience, String item, String starts) {

        public static final Done DEFAULTS = new Done("", 0, "", "");
    }
}
