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

    /** A sound playing, kept to count against its cue's limit and the budget, and to be stopped for one above it. */
    private record Live(SoundBank.Cue cue, boolean placed, SoundSink.Playing sound) {
    }

    /** The sounds playing that are counted, oldest first — while a cue has a limit or the game a budget. */
    private final java.util.List<Live> live = new java.util.ArrayList<>();
    /** How many sounds at a place, and flat, play at once; 0 for as many as are asked. */
    private int placedBudget;
    private int flatBudget;
    /** Where the listener stands, and how far the eye is from it: null until the window says. */
    private Vector3f listener;
    private float eyeDistance;

    /** A placed sound playing that its reach may stop: its cue, where it is, and what stops it. */
    private record Placed(SoundBank.Cue cue, Vector3f at, SoundSink.Playing sound) {
    }

    private final java.util.List<Placed> placed = new java.util.ArrayList<>();

    /** The playback rates the sound device plays: jME's {@code AudioNode.setPitch}, 0.5 to 2. */
    static final float LEAST_PITCH = 0.5f;
    static final float MOST_PITCH = 2f;
    /** The window's seconds, as {@link #update} has counted them: what a cue's parts are timed by. */
    private float clock;
    /** The cues playing in parts, stepped on as their parts end and their pauses pass. */
    private final java.util.List<Parts> sequences = new java.util.ArrayList<>();
    /** The voice last started about each thing, by the thing's id — see {@link SoundBank.Voicing#voice}. */
    private final Map<Integer, SoundSink.Playing> voices = new HashMap<>();

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
    /** The playlist the game asked for, each once through in turn and round again; empty while one track loops. */
    private java.util.List<String> playlist = java.util.List.of();
    private int inTurn;
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
        budget(this.bank.placedBudget(), this.bank.flatBudget());
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

    /**
     * How many sounds at a place and flat play at once — the reference's {@code SampleCount3D} and {@code
     * SampleCount2D}, 25 and 4: with those full, a new sound stops the lowest-priority one playing of its kind if it is
     * lower than itself, and is not played otherwise. 0 for as many as are asked, as always; music and loops are
     * not counted.
     */
    void budget(int placed, int flat) {
        this.placedBudget = Math.max(0, placed);
        this.flatBudget = Math.max(0, flat);
    }

    void masterVolume(float zeroToOne) {
        this.master = Math.clamp(zeroToOne, 0f, 1f);
        followTheKnobs();
    }

    float volumeOf(SoundBank.Channel channel) {
        return volumes.get(channel);
    }

    // ---- where it is heard from ----

    /**
     * Where the listener stands this frame, and how far the eye is from it: a placed sound past its far range or under
     * the game's floor stopped — the reference's {@code MilesAudioManager::processPlayingList}.
     */
    void hear(Vector3f listener, float eyeDistance) {
        this.listener = listener;
        this.eyeDistance = eyeDistance;
        placed.removeIf(one -> {
            if (one.sound().ended()) {
                return true;
            }
            if (inReach(one.cue(), one.at())) {
                return false;
            }
            one.sound().stop();
            return true;
        });
    }

    /**
     * Where the listener stands: on the line from the ground point the view looks at to the eye, {@code height} above
     * that ground but no further along than {@code share} of the line — the reference's {@code AudioManager::update},
     * 50 up and at most a third of the way; either 0 for no limit of its own.
     */
    static Vector3f listenerAt(Vector3f ground, Vector3f eye, float height, float share) {
        float along = share > 0f ? share : 1f;
        float up = eye.y - ground.y;
        if (height > 0f && up > 0f) {
            along = Math.min(along, height / up);
        }
        return ground.add(eye.subtract(ground).multLocal(along));
    }

    /** How loud a sound {@code distance} away is, for its near range: whole within it, near over distance beyond. */
    static float fallOff(float near, float distance) {
        return near <= 0f || distance <= near ? 1f : near / distance;
    }

    /** Whether a placed sound of {@code cueName} at {@code at} is within its reach of the listener, and heard. */
    boolean inReach(String cueName, Vector3f at) {
        var cue = bank.find(cueName);
        return cue == null || inReach(cue, cue.positional() ? at : null);
    }

    /** Whether the game says fog does not hide {@code cueName} — see {@link SoundBank.Reach#throughFog}. */
    boolean throughFog(String cueName) {
        var cue = cueName == null ? null : bank.find(cueName);
        return cue != null && cue.reach().throughFog();
    }

    private boolean inReach(SoundBank.Cue cue, Vector3f at) {
        if (listener == null || at == null) {
            return true;
        }
        float distance = listener.distance(at);
        float far = farOf(cue);
        if (far > 0f && distance >= far) {
            return false;
        }
        float floor = bank.hearing().floor();
        return floor <= 0f || gainOf(cue) * fallOff(nearOf(cue), distance) >= floor;
    }

    private float nearOf(SoundBank.Cue cue) {
        return cue.reach().near() > 0f ? cue.reach().near() : bank.hearing().nearRange();
    }

    private float farOf(SoundBank.Cue cue) {
        return cue.reach().far() > 0f ? cue.reach().far() : bank.hearing().farRange();
    }

    private SoundSink.Range rangeOf(SoundBank.Cue cue) {
        return new SoundSink.Range(nearOf(cue), farOf(cue));
    }

    /**
     * What the eye's distance from the listener leaves of a placed sound — the reference's {@code
     * ZoomSoundVolumePercentageAmount}: all of it within the near distance, less the game's share at the far one and
     * past it, in proportion between.
     */
    private float zoom() {
        var hearing = bank.hearing();
        if (hearing.zoomLoss() <= 0f || hearing.zoomFar() <= hearing.zoomNear()) {
            return 1f;
        }
        float through = Math.clamp((eyeDistance - hearing.zoomNear()) / (hearing.zoomFar() - hearing.zoomNear()),
                0f, 1f);
        return 1f - hearing.zoomLoss() * through;
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
        return start(cueName, at, now, owned, false, -1) != null;
    }

    /**
     * The same, about the thing {@code about}: a voice about it is not played while one about it is playing — see
     * {@link SoundBank.Voicing#voice}.
     */
    boolean play(String cueName, Vector3f at, float now, boolean owned, int about) {
        return start(cueName, at, now, owned, false, about) != null;
    }

    /**
     * Raise a moment about a thing, and keep hold of what plays, to move it with the thing: null where nothing played,
     * {@link SoundSink.Playing#NONE} where the sink cannot keep hold of a sound.
     */
    SoundSink.Playing held(String cueName, Vector3f at, float now, boolean owned) {
        return start(cueName, at, now, owned, true, -1);
    }

    /**
     * Raise a moment, the cue's every rule kept: null where nothing played, else what plays — held on to where
     * {@code keep} asks or the cue cuts off the last of itself, {@link SoundSink.Playing#NONE} otherwise.
     */
    private SoundSink.Playing start(String cueName, Vector3f at, float now, boolean owned, boolean keep, int about) {
        var cue = bank.find(cueName);
        if (cue == null || cue.files().isEmpty()) {
            return null;
        }
        if (cue.audience() == SoundBank.Audience.OWNER && !owned) {
            return null; // his voice, and not this player's to hear
        }
        if (gainOf(cue) <= 0f) {
            return null; // turned off; not worth choosing a file for
        }
        boolean voiced = cue.voicing().voice() && about >= 0;
        if (voiced && voices.containsKey(about) && !ended(voices.get(about))) {
            return null; // it is still saying the last
        }
        if (cue.channel() == SoundBank.Channel.VOICE
                && now - lastVoiceAt < voiceGapSeconds) {
            return null; // she is still speaking
        }
        var last = lastPlayed.get(cue.name());
        if (cue.gapSeconds() > 0f && last != null && now - last < cue.gapSeconds()) {
            return null;
        }
        var where = cue.positional() ? at : null;
        if (!inReach(cue, where)) {
            return null; // past its far range, or too quiet there to be heard
        }
        boolean counted = cue.limit() > 0 || placedBudget > 0 || flatBudget > 0;
        if (counted && !makeRoom(cue, where != null)) {
            return null; // past its limit, or no room left for one of its priority
        }
        lastPlayed.put(cue.name(), now);
        if (cue.channel() == SoundBank.Channel.VOICE) {
            lastVoiceAt = now;
        }
        if (cue.interrupts() && cue.limit() == 0) {
            var cutOff = lastOf.remove(cue.name());
            if (cutOff != null) {
                cutOff.stop();
            }
        }
        float gain = gainOf(cue) * (where != null ? zoom() : 1f) * loudness(cue.voicing());
        float pitch = pitch(cue.voicing());
        boolean reached = where != null && (farOf(cue) > 0f || bank.hearing().floor() > 0f);
        SoundSink.Playing playing;
        if (cue.voicing().inParts()) {
            var parts = new Parts(cue, false, where, gain, pitch);
            sequences.add(parts);
            parts.advance();
            playing = parts;
        } else if (!counted && !cue.interrupts() && !keep && !reached && !voiced) {
            sink.play(pick(cue), gain, pitch, where, rangeOf(cue));
            return SoundSink.Playing.NONE;
        } else {
            playing = sink.playStoppable(pick(cue), gain, pitch, where, rangeOf(cue));
        }
        if (voiced) {
            voices.put(about, playing);
        }
        if (counted) {
            live.add(new Live(cue, where != null, playing));
        }
        if (reached) {
            placed.add(new Placed(cue, where, playing));
        }
        if (cue.interrupts()) {
            lastOf.put(cue.name(), playing);
        }
        return cue.interrupts() || keep ? playing : SoundSink.Playing.NONE;
    }

    /**
     * Whether there is room for one more of {@code cue}, made where the rules say — the reference's {@code
     * doesViolateLimit} and {@code killLowestPrioritySoundImmediately}: past its limit, a cue that interrupts stops
     * its oldest playing and any other is not played; with the budget full, the lowest-priority sound of its kind
     * playing, the oldest of those, is stopped where it is lower than the new one, and otherwise the new one is not
     * played.
     */
    private boolean makeRoom(SoundBank.Cue cue, boolean placed) {
        live.removeIf(one -> one.sound().ended());
        if (cue.limit() > 0) {
            Live oldest = null;
            int playing = 0;
            for (var one : live) {
                if (one.cue().name().equals(cue.name()) && one.placed() == placed) {
                    playing++;
                    oldest = oldest == null ? one : oldest;
                }
            }
            if (playing >= cue.limit()) {
                if (!cue.interrupts()) {
                    return false;
                }
                oldest.sound().stop();
                live.remove(oldest);
            }
        }
        int budget = placed ? placedBudget : flatBudget;
        if (budget <= 0) {
            return true;
        }
        Live lowest = null;
        int playing = 0;
        for (var one : live) {
            if (one.placed() == placed) {
                playing++;
                if (lowest == null || one.cue().priority().compareTo(lowest.cue().priority()) < 0) {
                    lowest = one;
                }
            }
        }
        if (playing < budget) {
            return true;
        }
        if (lowest.cue().priority().compareTo(cue.priority()) >= 0) {
            return false;
        }
        lowest.sound().stop();
        live.remove(lowest);
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

    /** Played flat and waited on: what plays, and who is told when it has played out. */
    private record Ending(SoundSink.Playing sound, Runnable ended) {
    }

    private final java.util.List<Ending> endings = new java.util.ArrayList<>();

    /**
     * The same, and {@code ended} told — from {@link #update}, on the window's thread — once what it played has played
     * out, as the reference's EVA waits on {@code isCurrentlyPlaying} before its next line. At once where it played
     * nothing: a name the game never wrote, a cue with no file, one held back by its gap or turned off — or a sink that
     * cannot say when a sound ends. A cue that cuts off the last of itself ends that one as the next begins.
     */
    boolean flat(String cueName, float now, Runnable ended) {
        if (bank.find(cueName) == null) {
            flat(cueName, now); // said once, as any unknown name is
            ended.run();
            return false;
        }
        var sound = start(cueName, null, now, true, true, -1);
        if (sound == null || sound == SoundSink.Playing.NONE) {
            ended.run();
            return sound != null;
        }
        endings.add(new Ending(sound, ended));
        return true;
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
        var where = cue.positional() ? at : null;
        if (!inReach(cue, where)) {
            return null; // out of reach: started again once it is back
        }
        float gain = gainOf(cue) * (where != null ? zoom() : 1f) * loudness(cue.voicing());
        if (cue.voicing().inParts()) {
            var parts = new Parts(cue, true, where, gain, pitch(cue.voicing()));
            sequences.add(parts);
            parts.advance();
            return parts;
        }
        return sink.loop(pick(cue), gain, pitch(cue.voicing()), where, rangeOf(cue));
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

    /** A play's rate, drawn between the cue's slowest and fastest, kept to what the device plays. */
    private float pitch(SoundBank.Voicing voicing) {
        float rate = voicing.slowest() == voicing.fastest() ? voicing.slowest()
                : voicing.slowest() + random.nextFloat() * (voicing.fastest() - voicing.slowest());
        return Math.clamp(rate, LEAST_PITCH, MOST_PITCH);
    }

    /** A play's share of its loudness, drawn between the cue's quietest and all of it. */
    private float loudness(SoundBank.Voicing voicing) {
        return voicing.quietest() >= 1f ? 1f : voicing.quietest() + random.nextFloat() * (1f - voicing.quietest());
    }

    /** A pause drawn between a cue's least and most, in seconds. */
    private float pause(SoundBank.Voicing voicing) {
        return voicing.leastPause() + (voicing.mostPause() > voicing.leastPause()
                ? random.nextFloat() * (voicing.mostPause() - voicing.leastPause()) : 0f);
    }

    /** Whether a sound has played out — one the sink cannot keep hold of, at once. */
    private static boolean ended(SoundSink.Playing sound) {
        return sound == SoundSink.Playing.NONE || sound.ended();
    }

    /**
     * A cue played in its parts over time — the reference's {@code AudioEventRTS}: a pause drawn from its delay, then
     * its attack, its sound and its decay one after another; a loop, pass after pass, each a new pick of its files
     * after a pause drawn anew, and stopped, however, ending the pass it is in and playing its decay ({@code
     * MilesAudioManager::startNextLoop}, {@code notifyOfAudioCompletion}).
     */
    private final class Parts implements SoundSink.Playing {

        private enum Step { PAUSE, ATTACK, PASS, REST, DECAY, DONE }

        private final SoundBank.Cue cue;
        private final boolean looping;
        private final float gain;
        private final float pitch;
        private Vector3f at;
        private Step step = Step.PAUSE;
        private float until;
        private boolean stopping;
        private SoundSink.Playing part = SoundSink.Playing.NONE;

        Parts(SoundBank.Cue cue, boolean looping, Vector3f at, float gain, float pitch) {
            this.cue = cue;
            this.looping = looping;
            this.at = at;
            this.gain = gain;
            this.pitch = pitch;
            this.until = clock + pause(cue.voicing());
        }

        /** On as far as the clock and the parts playing let it — a few steps at most, however quick its parts. */
        void advance() {
            for (int steps = 0; steps < 8; steps++) {
                switch (step) {
                    case PAUSE -> {
                        if (clock < until) {
                            return;
                        }
                        if (stopping) {
                            step = Step.DONE; // stopped before it began: nothing played
                        } else if (!begin(Step.ATTACK, cue.voicing().attack(), false)) {
                            begin(Step.PASS, cue.files(), true);
                        }
                    }
                    case ATTACK -> {
                        if (!Sounds.ended(part)) {
                            return;
                        }
                        if (stopping) {
                            end();
                        } else {
                            begin(Step.PASS, cue.files(), true);
                        }
                    }
                    case PASS -> {
                        if (!Sounds.ended(part)) {
                            return;
                        }
                        if (looping && !stopping) {
                            step = Step.REST;
                            until = clock + pause(cue.voicing());
                        } else {
                            end();
                        }
                    }
                    case REST -> {
                        if (stopping) {
                            end();
                        } else if (clock < until) {
                            return;
                        } else {
                            begin(Step.PASS, cue.files(), true);
                        }
                    }
                    case DECAY -> {
                        if (!Sounds.ended(part)) {
                            return;
                        }
                        step = Step.DONE;
                    }
                    case DONE -> {
                        return;
                    }
                }
            }
        }

        /** Its decay, or done where it has none. */
        private void end() {
            if (!begin(Step.DECAY, cue.voicing().decay(), false)) {
                step = Step.DONE;
            }
        }

        /** A part begun from {@code files} — its own files picked as ever — and whether there was one to begin. */
        private boolean begin(Step next, java.util.List<String> files, boolean own) {
            if (files.isEmpty()) {
                return false;
            }
            var file = own ? pick(cue) : files.get(files.size() == 1 ? 0 : random.nextInt(files.size()));
            part = sink.playStoppable(file, gain, pitch, at, rangeOf(cue));
            step = next;
            return true;
        }

        @Override
        public void stop() {
            if (!looping) {
                part.stop();
                step = Step.DONE;
                return;
            }
            stopping = true;
            advance();
        }

        @Override
        public void moveTo(Vector3f where) {
            if (where != null) {
                at = where;
                part.moveTo(where);
            }
        }

        @Override
        public boolean ended() {
            return step == Step.DONE;
        }
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
        var cue = cue(cueName);
        if (playlist.isEmpty() && java.util.Objects.equals(fileOf(cue), playing)) {
            return;
        }
        playlist = java.util.List.of(); // a track the playlist had on, asked for alone, loops from its start
        change(cue, fadeOut, fadeIn, true);
    }

    /**
     * Play these cues' tracks each once through, in turn, and round again from the first — the reference's list of
     * music, which its next-track key walks ({@code AudioManager::nextTrackName}). Asking for the list already playing
     * changes nothing; a cue with no file is passed over, and a list with none is silence.
     */
    void playlist(java.util.List<String> cueNames, float fadeOut, float fadeIn) {
        var wanted = cueNames.stream().filter(name -> fileOf(cue(name)) != null).toList();
        if (wanted.isEmpty()) {
            music(null, fadeOut, fadeIn);
            return;
        }
        if (wanted.equals(playlist)) {
            return;
        }
        playlist = wanted;
        inTurn = 0;
        change(cue(wanted.getFirst()), fadeOut, fadeIn, false);
    }

    private SoundBank.Cue cue(String name) {
        return name == null ? null : bank.find(name);
    }

    private static String fileOf(SoundBank.Cue cue) {
        return cue == null || cue.files().isEmpty() ? null : cue.files().getFirst();
    }

    /** The track playing goes — faded out, or at once — and this cue's comes in, looping or once through. */
    private void change(SoundBank.Cue cue, float fadeOut, float fadeIn, boolean looping) {
        var wanted = fileOf(cue);
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
        track = looping ? sink.music(wanted, trackLoudness) : sink.musicOnce(wanted, trackLoudness);
        if (fadeIn > 0f) {
            fades.add(new Fade(track, 0f, false, fadeIn));
        }
    }

    /**
     * A movie's sound, once, flat: at the speech volume and four fifths of it, as the reference plays a movie's sound
     * ({@code BinkVideoPlayer.cpp}).
     */
    SoundSink.Playing movieSound(String assetPath) {
        return sink.once(assetPath, channelGain(SoundBank.Channel.VOICE) * 0.8f);
    }

    /** Time passing, for whatever is fading, and for the cues playing in parts. */
    void update(float seconds) {
        clock += seconds;
        sequences.removeIf(parts -> {
            parts.advance();
            return parts.ended();
        });
        voices.values().removeIf(Sounds::ended);
        tellWhatEnded();
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
        // A track the sink could not play is passed over as one that has ended.
        if (!playlist.isEmpty() && (track.ended() || track == SoundSink.Playing.NONE)) {
            inTurn = (inTurn + 1) % playlist.size();
            change(cue(playlist.get(inTurn)), 0f, 0f, false);
        }
    }

    /** Everyone waiting on a sound that has now played out, told — after the list is walked, as one may play another. */
    private void tellWhatEnded() {
        java.util.List<Runnable> told = null;
        for (var waiting = endings.iterator(); waiting.hasNext(); ) {
            var ending = waiting.next();
            if (ending.sound().ended()) {
                waiting.remove();
                told = told == null ? new java.util.ArrayList<>() : told;
                told.add(ending.ended());
            }
        }
        if (told != null) {
            told.forEach(Runnable::run);
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
