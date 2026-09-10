package uz.duke.client3d;

import com.jme3.math.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The game's noise, played: which of a cue's files, whether enough time has
 * passed, and how loud after four knobs.
 *
 * <p>Everything here is presentation and nothing else. It reads what the client
 * already has — the snapshot, the events, the player's own keypresses — and makes
 * a sound. It never writes to the simulation, is never read by it, and its one
 * source of randomness is deliberately its own: two players watching the same
 * replay may hear different footsteps and still see the same world, because the
 * choice of file reaches nothing that is checksummed.
 *
 * <p>Time arrives as a parameter rather than being read from a clock. That is
 * what makes a gap testable — a footstep's stride and a voice line's silence are
 * rules, and a rule you can only observe by waiting is a rule nobody checks.
 */
final class Sounds {

    private final SoundBank bank;
    private final SoundSink sink;
    private final RandomGenerator random;

    /** Which file of each cue was heard last, so the next one is a different one. */
    private final Map<String, Integer> lastFile = new HashMap<>();
    /** When each cue last played, for the cues that insist on a gap. */
    private final Map<String, Float> lastPlayed = new HashMap<>();
    /** And when the voice last spoke, which is a gap across every line at once. */
    private float lastVoiceAt = Float.NEGATIVE_INFINITY;
    private float voiceGapSeconds;

    private final Map<SoundBank.Channel, Float> volumes = new HashMap<>();
    private float master = 1f;
    private String playing; // the music, if any

    Sounds(SoundBank bank, SoundSink sink) {
        this(bank, sink, RandomGenerator.getDefault());
    }

    Sounds(SoundBank bank, SoundSink sink, RandomGenerator random) {
        this.bank = bank == null ? SoundBank.silent() : bank;
        this.sink = sink == null ? SoundSink.SILENT : sink;
        this.random = random;
        for (var channel : SoundBank.Channel.values()) {
            volumes.put(channel, 1f);
        }
    }

    /**
     * The least time between two voice lines, whatever they are.
     *
     * <p>Per channel rather than per cue because the thing being prevented is two
     * people talking at once, and it is no better when they are saying different
     * things. A player clicking around the floor issues an order every frame he
     * feels like it; the hero answers the first and then keeps quiet.
     */
    void voiceGap(float seconds) {
        this.voiceGapSeconds = Math.max(0f, seconds);
    }

    void volume(SoundBank.Channel channel, float zeroToOne) {
        volumes.put(channel, Math.clamp(zeroToOne, 0f, 1f));
        if (channel == SoundBank.Channel.MUSIC) {
            sink.musicGain(gainOf(SoundBank.Channel.MUSIC, 1f));
        }
    }

    void masterVolume(float zeroToOne) {
        this.master = Math.clamp(zeroToOne, 0f, 1f);
        sink.musicGain(gainOf(SoundBank.Channel.MUSIC, 1f));
    }

    float volumeOf(SoundBank.Channel channel) {
        return volumes.get(channel);
    }

    /**
     * Raise a moment. Somewhere, if it happened somewhere.
     *
     * @return whether anything was played, which is what a test asks and nothing
     *     in the game does — a cue the game never named is not a failure, and a
     *     cue held back by its own gap is the gap working
     */
    boolean play(String cueName, Vector3f at, float now) {
        var cue = bank.find(cueName);
        if (cue == null) {
            return false;
        }
        if (gainOf(cue.channel(), cue.gain()) <= 0f) {
            return false; // turned off; not worth choosing a file for
        }
        if (cue.channel() == SoundBank.Channel.VOICE
                && now - lastVoiceAt < voiceGapSeconds) {
            return false; // she is still speaking
        }
        var last = lastPlayed.get(cue.name());
        if (cue.gapSeconds() > 0f && last != null && now - last < cue.gapSeconds()) {
            return false;
        }
        lastPlayed.put(cue.name(), now);
        if (cue.channel() == SoundBank.Channel.VOICE) {
            lastVoiceAt = now;
        }
        sink.play(pick(cue), gainOf(cue.channel(), cue.gain()),
                cue.positional() ? at : null);
        return true;
    }

    boolean play(String cueName, float now) {
        return play(cueName, null, now);
    }

    /**
     * A file of this cue that is not the one heard last.
     *
     * <p>Which is the whole point of having several. Left to chance alone, a
     * three-file bow plays the same file twice in a row one shot in three, and
     * twice in a row is exactly what the ear notices — it is the repetition that
     * gives the sample away, not the sample.
     */
    private String pick(SoundBank.Cue cue) {
        var files = cue.files();
        if (files.size() == 1) {
            return files.get(0);
        }
        var previous = lastFile.get(cue.name());
        int index = random.nextInt(files.size() - (previous == null ? 0 : 1));
        if (previous != null && index >= previous) {
            index++; // step over the one just heard rather than drawing again
        }
        lastFile.put(cue.name(), index);
        return files.get(index);
    }

    private float gainOf(SoundBank.Channel channel, float cueGain) {
        return master * volumes.get(channel) * cueGain;
    }

    // ---- music ----

    /**
     * Put this cue's file on a loop, or stop when there is none.
     *
     * <p>Idempotent: asking for the loop already playing changes nothing, so this
     * can be called on any frame that thinks the music might want to be different
     * without stopping and restarting the same track every time.
     */
    void music(String cueName) {
        var cue = cueName == null ? null : bank.find(cueName);
        var wanted = cue == null || cue.files().isEmpty() ? null : cue.files().get(0);
        if (java.util.Objects.equals(wanted, playing)) {
            return;
        }
        playing = wanted;
        sink.music(wanted, gainOf(SoundBank.Channel.MUSIC,
                cue == null ? 1f : cue.gain()));
    }

    /** Every file the game may ask for, so it can be read before it is wanted. */
    java.util.List<String> everyFile() {
        return bank.all().stream().flatMap(cue -> cue.files().stream()).distinct().toList();
    }
}
