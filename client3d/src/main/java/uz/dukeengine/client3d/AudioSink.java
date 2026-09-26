package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.math.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link SoundSink} over jME's audio: one node per file, kept, and told to play
 * again.
 *
 * <p>Kept rather than built per shot because a bow is heard hundreds of times in
 * a run and decoding it each time is work in the frame the arrow leaves — the
 * frame that can least afford any. {@code playInstance} is what allows it: the
 * same node overlaps with itself, so two arrows in flight are two sounds.
 *
 * <p>A file that will not load is reported once and never asked for again. A
 * missing sound is a missing file, not a broken client: the dungeon should go on
 * being playable in silence, and say in the log which file went missing.
 */
final class AudioSink implements SoundSink {

    private static final Logger LOG = Logger.getLogger(AudioSink.class.getName());

    /**
     * How far a sound carries before it starts falling away.
     *
     * <p>Roughly a room. Shorter and a fight across the hall is inaudible; longer
     * and everything on the floor sounds like it is happening at the hero's feet,
     * which throws away the only thing positional audio is for.
     */
    private static final float REFERENCE_DISTANCE = 40f;

    private final AssetManager assets;
    private final com.jme3.scene.Node root;
    private final Map<String, AudioNode> nodes = new HashMap<>();
    private final Set<String> missing = new java.util.HashSet<>();

    AudioSink(AssetManager assets, com.jme3.scene.Node root) {
        this.assets = assets;
        this.root = root;
    }

    @Override
    public void play(String assetPath, float gain, Vector3f at) {
        play(assetPath, gain, at, Range.OWN);
    }

    @Override
    public void play(String assetPath, float gain, Vector3f at, Range range) {
        var node = nodeFor(assetPath, false, at != null);
        if (node == null) {
            return;
        }
        fallingAway(node, range);
        node.setVolume(gain);
        if (at != null) {
            node.setLocalTranslation(at);
        }
        node.playInstance();
    }

    /**
     * The sounds that may be stopped, each on a node of its own: an instance {@code playInstance} started cannot be
     * stopped, and two of one file may play at once where a cue's limit allows. A node that has played out is let go
     * of as the next is started.
     */
    private final java.util.List<AudioNode> stoppable = new java.util.ArrayList<>();

    @Override
    public Playing playStoppable(String assetPath, float gain, Vector3f at) {
        return playStoppable(assetPath, gain, at, Range.OWN);
    }

    /**
     * A placed node's fall with distance: full within the range's near, near over distance beyond — OpenAL's inverse
     * distance, which it keeps when none is set — clamped at its far; the client's own 40 and jME's where it names
     * none.
     */
    private static void fallingAway(AudioNode node, Range range) {
        if (!node.isPositional()) {
            return;
        }
        node.setRefDistance(range.near() > 0f ? range.near() : REFERENCE_DISTANCE);
        if (range.far() > 0f) {
            node.setMaxDistance(range.far());
        }
    }

    @Override
    public Playing playStoppable(String assetPath, float gain, Vector3f at, Range range) {
        stoppable.removeIf(done -> {
            if (done.getStatus() != com.jme3.audio.AudioSource.Status.Stopped) {
                return false;
            }
            done.removeFromParent();
            return true;
        });
        var node = fresh(assetPath, at != null);
        if (node == null) {
            return Playing.NONE;
        }
        stoppable.add(node);
        fallingAway(node, range);
        node.setVolume(gain);
        if (at != null) {
            node.setLocalTranslation(at);
        }
        node.play();
        return new Playing() {
            @Override
            public void stop() {
                node.stop();
            }

            /** Played out, or stopped. */
            @Override
            public boolean ended() {
                return node.getStatus() == com.jme3.audio.AudioSource.Status.Stopped;
            }
        };
    }

    /**
     * A loop is a node of its own, every time: it has a place of its own to follow, and it is let go of — stopped
     * and taken out of the scene — when what it belongs to is gone, so nothing is left behind a thing that died.
     */
    @Override
    public Playing loop(String assetPath, float gain, Vector3f at) {
        return loop(assetPath, gain, at, Range.OWN);
    }

    @Override
    public Playing loop(String assetPath, float gain, Vector3f at, Range range) {
        var node = fresh(assetPath, at != null);
        if (node == null) {
            return Playing.NONE;
        }
        fallingAway(node, range);
        node.setLooping(true);
        node.setVolume(gain);
        if (at != null) {
            node.setLocalTranslation(at);
        }
        node.play();
        return new Playing() {
            @Override
            public void stop() {
                node.stop();
                node.removeFromParent();
            }

            @Override
            public void moveTo(Vector3f where) {
                if (where != null) {
                    node.setLocalTranslation(where);
                }
            }
        };
    }

    /** A new node for a file, placed if it is meant to be and can be, or null for a file that will not load. */
    private AudioNode fresh(String assetPath, boolean positional) {
        if (assetPath == null || missing.contains(assetPath)) {
            return null;
        }
        try {
            var node = new AudioNode(assets, assetPath, AudioData.DataType.Buffer);
            node.setPositional(positional && isMono(node));
            if (node.isPositional()) {
                node.setRefDistance(REFERENCE_DISTANCE);
            }
            root.attachChild(node);
            return node;
        } catch (RuntimeException e) {
            missing.add(assetPath);
            LOG.warning(() -> "sound not found: " + assetPath + " (" + e.getMessage()
                    + ") — the game plays on without it");
            return null;
        }
    }

    @Override
    public Playing once(String assetPath, float gain) {
        var sound = nodeFor(assetPath, true, false);
        if (sound == null) {
            return Playing.NONE;
        }
        sound.setLooping(false);
        sound.setPositional(false);
        sound.setVolume(gain);
        root.attachChild(sound);
        sound.play();
        return new Playing() {
            @Override
            public void stop() {
                sound.stop();
                sound.removeFromParent();
            }

            @Override
            public float seconds() {
                return sound.getStatus() == com.jme3.audio.AudioSource.Status.Playing
                        ? sound.getPlaybackTime() : Float.NaN;
            }
        };
    }

    @Override
    public Playing music(String assetPath, float gain) {
        return track(assetPath, gain, true);
    }

    @Override
    public Playing musicOnce(String assetPath, float gain) {
        return track(assetPath, gain, false);
    }

    private Playing track(String assetPath, float gain, boolean looping) {
        // Streamed rather than held in memory: a loop is a minute of audio where
        // a footstep is a tenth of a second, and it is played once from start to
        // finish rather than fired off a hundred times. A node of its own each
        // time, so one track can fade out while the next plays.
        var track = nodeFor(assetPath, true, false);
        if (track == null) {
            return Playing.NONE;
        }
        track.setLooping(looping);
        track.setPositional(false);
        track.setVolume(gain);
        root.attachChild(track);
        track.play();
        return new Playing() {
            @Override
            public void stop() {
                track.stop();
                track.removeFromParent();
            }

            @Override
            public void volume(float loudness) {
                track.setVolume(loudness);
            }

            @Override
            public boolean ended() {
                return track.getStatus() == com.jme3.audio.AudioSource.Status.Stopped;
            }
        };
    }

    /**
     * The node for this file, built once.
     *
     * <p>Streamed audio is never cached: a stream is a position in a file as much
     * as it is a sound, and handing the same one out twice gives two players one
     * playhead.
     */
    private AudioNode nodeFor(String assetPath, boolean streamed, boolean positional) {
        if (assetPath == null || missing.contains(assetPath)) {
            return null;
        }
        if (!streamed) {
            var kept = nodes.get(assetPath);
            if (kept != null) {
                return kept;
            }
        }
        try {
            var node = new AudioNode(assets, assetPath,
                    streamed ? AudioData.DataType.Stream : AudioData.DataType.Buffer);
            if (!streamed) {
                // Placed only if it is meant to be and can be. OpenAL will not
                // place a stereo sound -- it is already two places -- and asking
                // it to throws, which is a crash rather than a wrong noise. The
                // packs ship a mixture, so the positional ones were down-mixed;
                // this keeps a stereo one that slips in later merely unplaced.
                if (!positional) {
                    node.setPositional(false); // a voice line is nowhere in particular
                } else if (isMono(node)) {
                    node.setPositional(true);
                    node.setRefDistance(REFERENCE_DISTANCE);
                } else {
                    node.setPositional(false);
                    LOG.warning(() -> assetPath + " is meant to come from somewhere but"
                            + " is stereo, so it cannot -- down-mix it to mono");
                }
                root.attachChild(node);
                nodes.put(assetPath, node);
            }
            return node;
        } catch (RuntimeException e) {
            missing.add(assetPath);
            LOG.warning(() -> "sound not found: " + assetPath + " (" + e.getMessage()
                    + ") — the game plays on without it");
            return null;
        }
    }

    private static boolean isMono(AudioNode node) {
        var data = node.getAudioData();
        return data == null || data.getChannels() == 1;
    }

    /** Read a file now so the frame that first wants it does not have to. */
    void warm(String assetPath, boolean positional) {
        nodeFor(assetPath, false, positional);
    }
}
