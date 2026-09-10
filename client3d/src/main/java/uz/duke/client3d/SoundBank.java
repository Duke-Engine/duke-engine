package uz.duke.client3d;

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
     */
    public record Cue(String name, Channel channel, boolean positional, float gain,
            float gapSeconds, List<String> files, String label) {

        public Cue {
            files = List.copyOf(files);
            gain = gain <= 0f ? 1f : gain;
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

    private SoundBank(Map<String, Cue> cues, float voiceGapSeconds) {
        // Linked and not Map.copyOf: the order a game declares its sounds in is
        // the order anything walking them sees — the order a loading bar reads
        // them in, and the order a player cycles through the music.
        this.cues = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cues));
        this.voiceGapSeconds = voiceGapSeconds;
    }

    public static Builder create() {
        return new Builder();
    }

    /** A game that says nothing about sound, which is silence rather than a fault. */
    public static SoundBank silent() {
        return new SoundBank(Map.of(), 0f);
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

        private Builder() {
        }

        /** How long the game wants between two of its spoken lines. */
        public Builder voiceGap(float seconds) {
            this.voiceGapSeconds = Math.max(0f, seconds);
            return this;
        }

        public Builder cue(String name, Channel channel, boolean positional, float gain,
                float gapSeconds, List<String> files) {
            return cue(name, channel, positional, gain, gapSeconds, files, null);
        }

        public Builder cue(String name, Channel channel, boolean positional, float gain,
                float gapSeconds, List<String> files, String label) {
            if (name == null || name.isBlank() || files.isEmpty()) {
                return this; // a cue with no files is a cue nobody has recorded yet
            }
            cues.put(name, new Cue(name, channel, positional, gain, gapSeconds, files, label));
            return this;
        }

        public SoundBank build() {
            return new SoundBank(cues, voiceGapSeconds);
        }
    }
}
