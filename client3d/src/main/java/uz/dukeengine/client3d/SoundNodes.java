package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import java.util.HashMap;
import java.util.Map;

/** The sounds a look names by file — a thing's fire and death — each read once and played where it happens. */
final class SoundNodes {

    private final AssetManager assetManager;
    private final Node rootNode;
    private final MissingAssets missing;
    private final Map<String, AudioNode> audioCache = new HashMap<>();

    SoundNodes(AssetManager assetManager, Node rootNode, MissingAssets missing) {
        this.assetManager = assetManager;
        this.rootNode = rootNode;
        this.missing = missing;
    }

    void play(String assetPath, Vector3f position) {
        var audio = soundNode(assetPath);
        if (audio == null) {
            return;
        }
        audio.setLocalTranslation(position);
        audio.playInstance();
    }

    /**
     * This sound, ready to play, or {@code null} if it is not there.
     *
     * <p>Kept rather than built per shot, and built ahead of the first shot by
     * {@link ArtLoad}: decoding a sound is not much work, but it is work in the
     * frame the arrow leaves the bow, which is the frame that can least afford it.
     */
    AudioNode soundNode(String assetPath) {
        if (assetPath == null || missing.contains(assetPath)) {
            return null;
        }
        var audio = audioCache.computeIfAbsent(assetPath, path -> {
            try {
                var node = new AudioNode(assetManager, path, AudioData.DataType.Buffer);
                node.setPositional(true);
                node.setRefDistance(40f);
                rootNode.attachChild(node);
                return node;
            } catch (RuntimeException e) {
                missing.warn(path, "sound");
                return null;
            }
        });
        if (audio == null) {
            missing.firstTime(assetPath);
        }
        return audio;
    }
}
