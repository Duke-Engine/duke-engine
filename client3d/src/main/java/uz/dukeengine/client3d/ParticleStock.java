package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.texture.Texture;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * What {@link LayeredEffects} draws particles with: {@link ParticleLayer}s lent from pools
 * by size, counted against a ceiling, and the textures they wear.
 */
final class ParticleStock {

    private static final Logger LOG = Logger.getLogger(ParticleStock.class.getName());

    /** The smallest pool a layer is lent from; the rest double. */
    private static final int SMALLEST = 4;

    private final AssetManager assets;
    private final int ceiling;
    private final Map<Integer, Deque<ParticleLayer>> spare = new HashMap<>();
    private final Map<String, Texture> textures = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private Texture dot;
    private int inUse;
    private int made;

    ParticleStock(AssetManager assets, int ceiling) {
        this.assets = assets;
        this.ceiling = Math.max(0, ceiling);
    }

    /** How many more particles may be lent before the ceiling. */
    int room() {
        return ceiling - inUse;
    }

    ParticleLayer borrow(int wanted) {
        int size = SMALLEST;
        while (size < wanted) {
            size *= 2;
        }
        // The pool is by size and a layer is lent at least as big as it asked;
        // what it did not ask for stays dark and is not counted against anybody.
        var pool = spare.computeIfAbsent(size, s -> new ArrayDeque<>());
        ParticleLayer drawn;
        if (pool.isEmpty()) {
            made++;
            drawn = new ParticleLayer(assets, size);
        } else {
            drawn = pool.pop();
        }
        inUse += drawn.capacity;
        return drawn;
    }

    void giveBack(ParticleLayer drawn) {
        drawn.show(false);
        drawn.geometry().removeFromParent();
        drawn.geometry().setLocalTranslation(0f, 0f, 0f);
        inUse -= drawn.capacity;
        spare.computeIfAbsent(drawn.capacity, size -> new ArrayDeque<>()).push(drawn);
    }

    Texture textureFor(String path) {
        if (path == null || path.isBlank()) {
            return softDot();
        }
        var known = textures.get(path);
        if (known != null) {
            return known;
        }
        try {
            var texture = assets.loadTexture(path);
            texture.setWrap(Texture.WrapMode.EdgeClamp);
            texture.setMinFilter(Texture.MinFilter.Trilinear);
            textures.put(path, texture);
            return texture;
        } catch (RuntimeException notThere) {
            if (missing.add(path)) {
                LOG.warning(() -> "an effect names a texture that is not there: " + path
                        + " -- drawn as a plain glow instead");
            }
            textures.put(path, softDot());
            return textures.get(path);
        }
    }

    private Texture softDot() {
        if (dot == null) {
            dot = ParticleLayer.softDot();
        }
        return dot;
    }

    /** How many particles are lent out now. */
    int inUse() {
        return inUse;
    }

    /** How many layers have been built rather than lent again, for a test. */
    int made() {
        return made;
    }
}
