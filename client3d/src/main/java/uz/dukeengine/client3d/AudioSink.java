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
    private AudioNode music;

    AudioSink(AssetManager assets, com.jme3.scene.Node root) {
        this.assets = assets;
        this.root = root;
    }

    @Override
    public void play(String assetPath, float gain, Vector3f at) {
        var node = nodeFor(assetPath, false, at != null);
        if (node == null) {
            return;
        }
        node.setVolume(gain);
        if (at != null) {
            node.setLocalTranslation(at);
        }
        node.playInstance();
    }

    @Override
    public void music(String assetPath, float gain) {
        if (music != null) {
            music.stop();
            music.removeFromParent();
            music = null;
        }
        if (assetPath == null) {
            return;
        }
        // Streamed rather than held in memory: a loop is a minute of audio where
        // a footstep is a tenth of a second, and it is played once from start to
        // finish rather than fired off a hundred times.
        music = nodeFor(assetPath, true, false);
        if (music == null) {
            return;
        }
        music.setLooping(true);
        music.setPositional(false);
        music.setVolume(gain);
        root.attachChild(music);
        music.play();
    }

    @Override
    public void musicGain(float gain) {
        if (music != null) {
            music.setVolume(gain);
        }
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
