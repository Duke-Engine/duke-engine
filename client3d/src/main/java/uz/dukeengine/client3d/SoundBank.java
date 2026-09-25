package uz.dukeengine.client3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a game has to say about noise: a name for each moment, and the files that
 * moment can sound like.
 *
 * <p>The client raises the moments — an arrow left a bow, a floor changed, a key
 * was pressed — and knows the name of each. What any of them <em>sounds</em> like
 * it does not know and must not: it draws four games, and which file plays when a
 * dungeon's hero is hurt is the dungeon's business. So a game hands over this,
 * and adding a sound afterwards is a block in a settings file rather than a line
 * of Java.
 *
 * <p>Immutable and jME-free on purpose. What to play is a question about the
 * configuration; playing it is {@link Sounds}, and separating them is what lets
 * the first be checked without a speaker.
 */
public final class SoundBank {

    /**
     * Which knob turns this sound down.
     *
     * <p>Four rather than one because they are wanted at different volumes by the
     * same person: a player who keeps the music low still wants to hear the
     * monster behind him, and one who finds a hero's voice lines grating wants
     * exactly those off and nothing else.
     */
    public enum Channel { EFFECTS, VOICE, UI, MUSIC }

    /**
     * Who hears a moment that is about a thing.
     *
     * <p>A unit's voice is its owner's: in the RTS this was measured in, 428 voice lines are heard only by
     * the player whose unit spoke, so selecting an enemy says nothing and an enemy's orders are silent. A
     * shot, a death, a footstep is everyone's.
     */
    public enum Audience {
        /** Anyone near enough, as every cue has always been. */
        EVERYONE,
        /** Only the player the thing belongs to. */
        OWNER
    }

    /**
     * Which of two sounds gives way when there is room for one: the reference's five ({@code AudioPriority}). A new
     * sound with the budget full stops the lowest playing if that is lower than itself, and is not played otherwise.
     */
    public enum Priority { LOWEST, LOW, NORMAL, HIGH, CRITICAL }

    /**
     * One moment, and what it can sound like.
     *
     * @param files  one or more. More than one is not decoration: the bow is
     *     heard hundreds of times in a run, and a sound that is bit-identical
     *     every time stops being a bow and becomes a click
     * @param positional  whether it happens somewhere — a shot, a death, a
     *     footstep — or simply happens, like a menu click or a voice line
     * @param gain  how loud this one is against the rest of its channel, because
     *     packs are not mastered to each other
     * @param gapSeconds  the least time between two of these, or zero for none.
     *     A footstep needs a stride; a voice line needs the last one to finish
     * @param label  what to call it on a settings screen, for the one channel a
     *     player picks from by name. Null for the many that are never named
     * @param audience  who hears it, when it is about a thing — see {@link Audience}. Everyone by default
     * @param interrupts  whether a new one stops the last of this cue still playing: a voice that answers a
     *     second order cuts off its answer to the first rather than talking over itself. In the RTS this was
     *     measured in, 145 sounds do. No by default, and two overlap
     * @param limit  how many of it play at once, those at a place and those flat counted apart: the next one past
     *     it is not played — or, for a cue that interrupts, stops the oldest of itself playing, and plays. The
     *     reference's {@code Limit}; 0 for as many as are asked
     * @param priority  which gives way when the game's budget of sounds at once is full — see {@link Priority}
     */
    public record Cue(String name, Channel channel, boolean positional, float gain,
            float gapSeconds, List<String> files, String label, Audience audience, boolean interrupts, int limit,
            Priority priority) {

        public Cue {
            files = List.copyOf(files);
            gain = gain <= 0f ? 1f : gain;
            audience = audience == null ? Audience.EVERYONE : audience;
            limit = Math.max(0, limit);
            priority = priority == null ? Priority.NORMAL : priority;
        }

        /** As many at once as are asked, of the middle priority — every cue before either could be said. */
        public Cue(String name, Channel channel, boolean positional, float gain, float gapSeconds,
                List<String> files, String label, Audience audience, boolean interrupts) {
            this(name, channel, positional, gain, gapSeconds, files, label, audience, interrupts, 0, Priority.NORMAL);
        }

        /** Heard by everyone, and never cutting itself off — every cue before either could be said. */
        public Cue(String name, Channel channel, boolean positional, float gain, float gapSeconds,
                List<String> files, String label) {
            this(name, channel, positional, gain, gapSeconds, files, label, Audience.EVERYONE, false);
        }

        /**
         * What to call this on screen.
         *
         * <p>Falls back to the part of the name after the last dot, so a game that
         * wrote no label still shows something a person can read rather than a
         * key. A label the game did write wins: what a piece of music is called is
         * not the client's to invent.
         */
        public String shown() {
            if (label != null && !label.isBlank()) {
                return label;
            }
            int dot = name.lastIndexOf('.');
            return dot < 0 ? name : name.substring(dot + 1);
        }
    }

    private final Map<String, Cue> cues;
    private final float voiceGapSeconds;
    private final int placedBudget;
    private final int flatBudget;

    private SoundBank(Map<String, Cue> cues, float voiceGapSeconds, int placedBudget, int flatBudget) {
        // Linked and not Map.copyOf: the order a game declares its sounds in is
        // the order anything walking them sees — the order a loading bar reads
        // them in, and the order a player cycles through the music.
        this.cues = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cues));
        this.voiceGapSeconds = voiceGapSeconds;
        this.placedBudget = placedBudget;
        this.flatBudget = flatBudget;
    }

    public static Builder create() {
        return new Builder();
    }

    /** A game that says nothing about sound, which is silence rather than a fault. */
    public static SoundBank silent() {
        return new SoundBank(Map.of(), 0f, 0, 0);
    }

    /**
     * The least time between two lines of speech, whatever they are.
     *
     * <p>Per bank rather than per cue because what is being prevented is two
     * voices at once, and it is no better when they are saying different things.
     */
    public float voiceGapSeconds() {
        return voiceGapSeconds;
    }

    /** How many sounds at a place play at once — see {@link Builder#budget}; 0 for as many as are asked. */
    public int placedBudget() {
        return placedBudget;
    }

    /** How many flat sounds play at once; 0 for as many as are asked. */
    public int flatBudget() {
        return flatBudget;
    }

    /**
     * The cue of that name, or the nearest thing the game did name.
     *
     * <p>Names are dotted so a game can be as particular as it likes and no more:
     * {@code died.Boss} is looked up first, and a game that never wrote one falls
     * back to {@code died}. That is what keeps the client free of every creature's
     * name — it asks for the specific thing and gets whatever answer exists.
     *
     * <p>Public because a game may reasonably want to check its own work: that
     * every creature it named has a sound, and that the ones it did not still fall
     * through to something. What the client does with the answer stays the
     * client's.
     *
     * @return null when the game named neither, and then nothing is played
     */
    public Cue find(String name) {
        for (var key = name; key != null; key = shorten(key)) {
            var found = cues.get(key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String shorten(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : name.substring(0, dot);
    }

    /**
     * The tracks a player may choose between, in the order the game listed them.
     *
     * <p>Music is the one channel where the choice is the player's rather than the
     * moment's. An effect belongs to whatever just happened; what plays underneath
     * a dungeon for an hour is taste, and taste belongs on a settings screen.
     */
    public List<Cue> musicCues() {
        return cues.values().stream().filter(cue -> cue.channel() == Channel.MUSIC).toList();
    }

    /** Whether the game named any cue that {@code name} or one of its dotted children would find. */
    boolean names(String name) {
        for (var key : cues.keySet()) {
            if (key.equals(name) || key.startsWith(name + ".")) {
                return true;
            }
        }
        return false;
    }

    /** Every cue, for whatever wants to read every file that may be needed. */
    java.util.Collection<Cue> all() {
        return cues.values();
    }

    public boolean isEmpty() {
        return cues.isEmpty();
    }

    /** Builds one cue at a time, in the order the game declares them. */
    public static final class Builder {

        private final Map<String, Cue> cues = new LinkedHashMap<>();
        private float voiceGapSeconds;
        private int placedBudget;
        private int flatBudget;

        private Builder() {
        }

        /** How long the game wants between two of its spoken lines. */
        public Builder voiceGap(float seconds) {
            this.voiceGapSeconds = Math.max(0f, seconds);
            return this;
        }

        /**
         * How many sounds at a place, and flat, play at once — the reference's {@code SampleCount3D} and {@code
         * SampleCount2D}, 25 and 4. With those full a new sound stops the lowest-priority one of its kind playing if
         * that is lower than itself, and is not played otherwise ({@link Cue#priority}). Left alone, as many as are
         * asked, until the sound device runs out.
         */
        public Builder budget(int placed, int flat) {
            this.placedBudget = Math.max(0, placed);
            this.flatBudget = Math.max(0, flat);
            return this;
        }

        public Builder cue(String name, Channel channel, boolean positional, float gain,
                float gapSeconds, List<String> files) {
            return cue(name, channel, positional, gain, gapSeconds, files, null);
        }

        public Builder cue(String name, Channel channel, boolean positional, float gain,
                float gapSeconds, List<String> files, String label) {
            return cue(name, channel, positional, gain, gapSeconds, files, label, Audience.EVERYONE, false);
        }

        public Builder cue(String name, Channel channel, boolean positional, float gain,
                float gapSeconds, List<String> files, String label, Audience audience, boolean interrupts) {
            return cue(name, channel, positional, gain, gapSeconds, files, label, audience, interrupts, 0,
                    Priority.NORMAL);
        }

        /** A cue with a limit to how many of it play at once, and a priority — see {@link Cue}. */
        public Builder cue(String name, Channel channel, boolean positional, float gain,
                float gapSeconds, List<String> files, String label, Audience audience, boolean interrupts, int limit,
                Priority priority) {
            if (name == null || name.isBlank() || files.isEmpty()) {
                return this; // a cue with no files is a cue nobody has recorded yet
            }
            cues.put(name, new Cue(name, channel, positional, gain, gapSeconds, files, label, audience,
                    interrupts, limit, priority));
            return this;
        }

        public SoundBank build() {
            return new SoundBank(cues, voiceGapSeconds, placedBudget, flatBudget);
        }
    }
}
