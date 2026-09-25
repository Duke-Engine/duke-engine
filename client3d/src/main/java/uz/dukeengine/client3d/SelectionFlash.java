package uz.dukeengine.client3d;

import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import java.util.HashMap;
import java.util.Map;

/**
 * Things flashing as they are picked, and as an order is given on them — the reference's selection flash ({@code
 * Drawable::flashAsSelected}, a {@code TintEnvelope} played with no attack and its frames of decay): a colour added to
 * the thing at once, held the frame the envelope turns from rising to falling, and eased out over its frames. Added as
 * a light of that colour on the thing alone, as a word's tint is ({@link WordTints}).
 */
final class SelectionFlash {

    private static final class Flash {
        private final AmbientLight light = new AmbientLight(ColorRGBA.Black.clone());
        private Spatial on;
        private ColorRGBA peak;
        private int frames;
        private float age;
    }

    private final Map<Integer, Flash> flashing = new HashMap<>();

    /** A thing flashed from now as {@code look} says, {@code owner} the colour it is drawn in for its own colour's. */
    void flash(int id, Spatial root, Visuals.SelectionFlashLook look, ColorRGBA owner) {
        var one = flashing.computeIfAbsent(id, key -> new Flash());
        if (one.on != root) {
            if (one.on != null) {
                one.on.removeLight(one.light);
            }
            root.addLight(one.light);
            one.on = root;
        }
        one.peak = look.peakFor(owner);
        one.frames = look.frames();
        one.age = 0f;
        one.light.setColor(one.peak.clone());
    }

    /** The game's time passing by {@code frames} of its frames: every flash eased on, and those gone out taken off. */
    void update(float frames) {
        for (var id : java.util.List.copyOf(flashing.keySet())) {
            var one = flashing.get(id);
            one.age += frames;
            float share = share(one.age, one.frames);
            if (share <= 0f) {
                forget(id);
                continue;
            }
            one.light.setColor(one.peak.mult(share));
        }
    }

    /**
     * How much of its colour a flash {@code age} frames old adds: all of it the frame it is played and the next, where
     * {@code TintEnvelope::update} finds itself at the peak and turns, then a {@code frames}th less a frame.
     */
    static float share(float age, int frames) {
        return Math.clamp((frames + 1 - age) / frames, 0f, 1f);
    }

    /** A thing gone: no flash left on it. */
    void forget(int id) {
        var one = flashing.remove(id);
        if (one != null && one.on != null) {
            one.on.removeLight(one.light);
        }
    }

    /** Every flash off at once, for a new world. */
    void clear() {
        for (var id : java.util.List.copyOf(flashing.keySet())) {
            forget(id);
        }
    }

    /** The colour a thing's flash adds now, or null where it is not flashing. */
    ColorRGBA colourOf(int id) {
        var one = flashing.get(id);
        return one == null ? null : one.light.getColor().clone();
    }
}
