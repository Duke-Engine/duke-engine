package uz.dukeengine.client3d;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The files the game named that would not load, each said once.
 *
 * <p>Concurrent because the art is read on a thread of its own while the render thread draws — see {@link ArtLoad}.
 */
final class MissingAssets {

    private static final Logger LOG = Logger.getLogger(MissingAssets.class.getName());

    private final Set<String> known = ConcurrentHashMap.newKeySet();

    /** Say that {@code asset}, a {@code kind}, would not load — the first time only. */
    void warn(String asset, String kind) {
        if (known.add(asset)) {
            LOG.warning(() -> "could not load " + kind + " '" + asset + "' — using fallback");
        }
    }

    /** Note {@code key} as missing; true the first time, so the caller says it in its own words. */
    boolean firstTime(String key) {
        return known.add(key);
    }

    boolean contains(String key) {
        return known.contains(key);
    }
}
