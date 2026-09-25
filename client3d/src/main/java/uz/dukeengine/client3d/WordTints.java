package uz.dukeengine.client3d;

import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Colours added to things while they hold words — {@link Visuals.UnitVisual#tint(Set, float, float, float, int)}, the
 * reference's tint envelopes (a frenzied unit's red): eased in over the frames the tint names when its words come, and
 * back out over them when they go.
 *
 * <p>Added as a light of that colour on the thing alone, so it lies over everything its materials already say — its own
 * colours, its owner's, a blow's flash — without taking any of them over, and a colour below nothing takes away.
 */
final class WordTints {

    private static final class Easing {
        private final AmbientLight light = new AmbientLight(ColorRGBA.Black.clone());
        private Spatial on;
        private Visuals.UnitVisual.WordTint wanted;
        private float[] from = new float[3];
        private float[] to = new float[3];
        private final float[] now = new float[3];
        private float through = 1f;
        private int frames = 1;
    }

    private final Map<Integer, Easing> easing = new HashMap<>();

    /**
     * A thing seen this frame: its tint eased toward the one its words best fit, or back to none.
     *
     * @param frames how many of the game's frames have passed since the last time
     */
    void see(int id, Spatial root, Visuals.UnitVisual look, Set<String> holding, float frames) {
        int chosen = look.tintFor(holding);
        var wanted = chosen < 0 ? null : look.wordTints.get(chosen);
        var one = easing.get(id);
        if (one == null) {
            if (wanted == null) {
                return;
            }
            one = new Easing();
            easing.put(id, one);
        }
        if (one.on != root) {
            if (one.on != null) {
                one.on.removeLight(one.light);
            }
            root.addLight(one.light);
            one.on = root;
        }
        if (wanted != one.wanted) {
            // From wherever it is now, over the frames of the tint it goes to — or of the one it leaves.
            int over = wanted != null ? wanted.easeFrames() : one.wanted.easeFrames();
            one.from = one.now.clone();
            one.to = wanted == null ? new float[3] : new float[] {wanted.red(), wanted.green(), wanted.blue()};
            one.through = 0f;
            one.frames = Math.max(1, over);
            one.wanted = wanted;
        }
        one.through = Math.min(1f, one.through + frames / one.frames);
        for (int channel = 0; channel < 3; channel++) {
            one.now[channel] = one.from[channel] + (one.to[channel] - one.from[channel]) * one.through;
        }
        one.light.setColor(new ColorRGBA(one.now[0], one.now[1], one.now[2], 1f));
        if (wanted == null && one.through >= 1f) {
            forget(id); // back to its own colour: nothing left over it
        }
    }

    /** A thing gone: no tint left on it. */
    void forget(int id) {
        var one = easing.remove(id);
        if (one != null && one.on != null) {
            one.on.removeLight(one.light);
        }
    }

    /** Every tint off at once, for a new world. */
    void clear() {
        for (var id : java.util.List.copyOf(easing.keySet())) {
            forget(id);
        }
    }

    /** The colour a thing is tinted by now, or null for none. */
    ColorRGBA tintOf(int id) {
        var one = easing.get(id);
        return one == null ? null : one.light.getColor().clone();
    }
}
