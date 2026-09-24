package uz.dukeengine.client3d;

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

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(Sounds.class.getName());

    private final SoundBank bank;
    private final SoundSink sink;
    private final RandomGenerator random;

    /** Which file of each cue was heard last, so the next one is a different one. */
    private final Map<String, Integer> lastFile = new HashMap<>();
    /** The last of each cue that cuts itself off, so the next one can. */
    private final Map<String, SoundSink.Playing> lastOf = new HashMap<>();
    /** When each cue last played, for the cues that insist on a gap. */
    private final Map<String, Float> lastPlayed = new HashMap<>();
    /** And when the voice last spoke, which is a gap across every line at once. */
    private float lastVoiceAt = Float.NEGATIVE_INFINITY;
    private float voiceGapSeconds;

    private final Map<SoundBank.Channel, Float> volumes = new HashMap<>();
    /**
     * The game's own volume for each channel, beside the player's: the reference keeps a script volume and a
     * system volume per channel and plays at their product ({@code AudioManager::setVolume}), so a menu that turns
     * the battle behind it down to 5% leaves the player's own setting where he put it.
     */
    private final Map<SoundBank.Channel, Float> gameVolumes = new HashMap<>();
    /**
     * A cue's volume against its own, by the cue's name, as the game last set it — the reference's
     * {@code setAudioEventVolumeOverride}, which holds until changed.
     */
    private final Map<String, Float> cueVolumes = new HashMap<>();
    /** The names played flat that the game never named, each said once. */
    private final java.util.Set<String> unknown = new java.util.HashSet<>();
    private float master = 1f;
    private String playing; // the music, if any
    /** The playing track's own loudness: its cue's gain, and the game's volume for that cue. */
    private float playingGain = 1f;
    /** The track playing, and how loud it was last made. */
    private SoundSink.Playing track = SoundSink.Playing.NONE;
    private float trackLoudness;
    /** Tracks on their way out, and the one on its way in. */
    private final java.util.List<Fade> fades = new java.util.ArrayList<>();

    /** A track going from how loud it was to silence, or from silence to as loud as the knobs say, over a while. */
    private static final class Fade {
        final SoundSink.Playing sound;
        final float from;
        final boolean out;
        final float seconds;
        float elapsed;

        Fade(SoundSink.Playing sound, float from, boolean out, float seconds) {
            this.sound = sound;
            this.from = from;
            this.out = out;
            this.seconds = seconds;
        }
    }

    Sounds(SoundBank bank, SoundSink sink) {
        this(bank, sink, RandomGenerator.getDefault());
    }

    Sounds(SoundBank bank, SoundSink sink, RandomGenerator random) {
        this.bank = bank == null ? SoundBank.silent() : bank;
        this.sink = sink == null ? SoundSink.SILENT : sink;
        this.random = random;
        for (var channel : SoundBank.Channel.values()) {
            volumes.put(channel, 1f);
            gameVolumes.put(channel, 1f);
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
            followTheKnobs();
        }
    }

    /** The game's volume for a channel, which the player's is multiplied by; see {@link #gameVolumes}. */
    void gameVolume(SoundBank.Channel channel, float zeroToOne) {
        gameVolumes.put(channel, Math.clamp(zeroToOne, 0f, 1f));
        if (channel == SoundBank.Channel.MUSIC) {
            followTheKnobs();
        }
    }

    /**
     * How loud a cue plays against its own loudness, until changed: 1 is its own, 0 silences it, 6 is six times
     * as loud — as loud as the sink will go, since the gain is handed on as it is and the sink clamps where it
     * clamps.
     */
    void cueVolume(String cueName, float multiplier) {
        if (cueName == null) {
            return;
        }
        float kept = Math.max(0f, multiplier);
        if (kept == 1f) {
            cueVolumes.remove(cueName);
        } else {
            cueVolumes.put(cueName, kept);
        }
    }

    void masterVolume(float zeroToOne) {
        this.master = Math.clamp(zeroToOne, 0f, 1f);
        followTheKnobs();
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
        return play(cueName, at, now, true);
    }

    /**
     * Raise a moment about a thing, which its cue may keep for the thing's owner — see
     * {@link SoundBank.Audience}.
     *
     * @param owned whether the thing it is about is the listening player's
     */
    boolean play(String cueName, Vector3f at, float now, boolean owned) {
        var cue = bank.find(cueName);
        if (cue == null) {
            return false;
        }
        if (cue.audience() == SoundBank.Audience.OWNER && !owned) {
            return false; // his voice, and not this player's to hear
        }
        if (gainOf(cue) <= 0f) {
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
        if (cue.interrupts()) {
            var cutOff = lastOf.remove(cue.name());
            if (cutOff != null) {
                cutOff.stop();
            }
            lastOf.put(cue.name(), sink.playStoppable(pick(cue), gainOf(cue), cue.positional() ? at : null));
            return true;
        }
        sink.play(pick(cue), gainOf(cue), cue.positional() ? at : null);
        return true;
    }

    /**
     * A cue the game plays itself, now and flat: on its own channel, at no place, so no distance takes anything
     * off it — with every other rule of the cue kept, which of its files, its loudness, its gap, cutting off the
     * last of itself. A name the game never wrote plays nothing and is said once, being a name in the game's own
     * code rather than a moment the game chose to leave silent.
     */
    boolean flat(String cueName, float now) {
        if (bank.find(cueName) == null) {
            if (unknown.add(String.valueOf(cueName))) {
                LOG.warning("no sound named " + cueName);
            }
            return false;
        }
        return play(cueName, null, now, true);
    }

    /**
     * Start a moment's loop, following a thing, and hand back what stops it — or {@code null} where the game
     * named no such cue, or keeps it for another player's ears, or has it turned off.
     */
    SoundSink.Playing loop(String cueName, Vector3f at, boolean owned) {
        var cue = bank.find(cueName);
        if (cue == null || cue.audience() == SoundBank.Audience.OWNER && !owned || gainOf(cue) <= 0f) {
            return null;
        }
        return sink.loop(pick(cue), gainOf(cue), cue.positional() ? at : null);
    }

    /** The name of the cue {@code cueName} would play — itself, or what it falls back to — or null for none. */
    String resolved(String cueName) {
        var cue = bank.find(cueName);
        return cue == null ? null : cue.name();
    }

    /** Whether the game named any cue under {@code name}, for a moment not worth working out for nobody. */
    boolean names(String name) {
        return bank.names(name);
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

    /** The player's knobs and the game's for one channel, multiplied. */
    private float channelGain(SoundBank.Channel channel) {
        return master * volumes.get(channel) * gameVolumes.get(channel);
    }

    /** How loud this cue plays: its channel, its own gain, and the game's volume for it. */
    private float gainOf(SoundBank.Cue cue) {
        return channelGain(cue.channel()) * ownGain(cue);
    }

    private float ownGain(SoundBank.Cue cue) {
        return cue.gain() * cueVolumes.getOrDefault(cue.name(), 1f);
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
        music(cueName, 0f, 0f);
    }

    /**
     * The same, the track playing fading out over {@code fadeOut} seconds while this one comes in over
     * {@code fadeIn} — at once for none. What the game asks for by name: the reference's {@code MUSIC_SET_TRACK}.
     */
    void music(String cueName, float fadeOut, float fadeIn) {
        var cue = cueName == null ? null : bank.find(cueName);
        var wanted = cue == null || cue.files().isEmpty() ? null : cue.files().get(0);
        if (java.util.Objects.equals(wanted, playing)) {
            return;
        }
        var leaving = track;
        fades.removeIf(fade -> fade.sound == leaving && !fade.out);
        if (fadeOut > 0f) {
            fades.add(new Fade(leaving, trackLoudness, true, fadeOut));
        } else {
            leaving.stop();
        }
        playing = wanted;
        playingGain = cue == null ? 1f : ownGain(cue);
        if (wanted == null) {
            track = SoundSink.Playing.NONE;
            return;
        }
        trackLoudness = fadeIn > 0f ? 0f : musicLoudness();
        track = sink.music(wanted, trackLoudness);
        if (fadeIn > 0f) {
            fades.add(new Fade(track, 0f, false, fadeIn));
        }
    }

    /** Time passing, for whatever is fading. */
    void update(float seconds) {
        for (var going = fades.iterator(); going.hasNext(); ) {
            var fade = going.next();
            fade.elapsed += seconds;
            float share = Math.min(1f, fade.elapsed / fade.seconds);
            if (fade.out) {
                fade.sound.volume(fade.from * (1f - share));
                if (share >= 1f) {
                    fade.sound.stop();
                    going.remove();
                }
            } else {
                trackLoudness = musicLoudness() * share;
                fade.sound.volume(trackLoudness);
                if (share >= 1f) {
                    going.remove();
                }
            }
        }
    }

    /** How loud the track playing is to be, by every knob and its own gain. */
    private float musicLoudness() {
        return channelGain(SoundBank.Channel.MUSIC) * playingGain;
    }

    /** A knob turned: the track playing follows at once, unless it is still fading in, which follows as it goes. */
    private void followTheKnobs() {
        var current = track;
        if (fades.stream().anyMatch(fade -> fade.sound == current && !fade.out)) {
            return;
        }
        trackLoudness = musicLoudness();
        current.volume(trackLoudness);
    }

    /** Every file the game may ask for, so it can be read before it is wanted. */
    java.util.List<String> everyFile() {
        return bank.all().stream().flatMap(cue -> cue.files().stream()).distinct().toList();
    }
}
