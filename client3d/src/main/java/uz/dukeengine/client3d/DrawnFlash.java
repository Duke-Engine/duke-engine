package uz.dukeengine.client3d;

import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import java.util.HashMap;
import java.util.Map;

/**
 * The colour the simulation flashes a thing in — {@code GameObject.flash}, the reference's {@code
 * Drawable::flashAsSelected} — laid over it as a light of that colour on the thing alone, its strength the view's alpha
 * as the simulation eases it out. Drawing only.
 */
final class DrawnFlash {

    private record Lit(Spatial on, AmbientLight light) {
    }

    private final Map<Integer, Lit> lit = new HashMap<>();

    /** A thing seen this frame, flashed in {@code argb} — its alpha the strength, 0 for none. */
    void see(int id, Spatial root, int argb) {
        var one = lit.get(id);
        if (argb >>> 24 == 0) {
            forget(id);
            return;
        }
        if (one == null || one.on != root) {
            forget(id);
            one = new Lit(root, new AmbientLight());
            root.addLight(one.light);
            lit.put(id, one);
        }
        float strength = (argb >>> 24) / 255f;
        one.light.setColor(new ColorRGBA(((argb >> 16) & 0xFF) / 255f * strength,
                ((argb >> 8) & 0xFF) / 255f * strength, (argb & 0xFF) / 255f * strength, 1f));
    }

    /** A thing gone, or flashed no more: no flash left on it. */
    void forget(int id) {
        var one = lit.remove(id);
        if (one != null) {
            one.on.removeLight(one.light);
        }
    }

    /** Every flash off at once, for a new world. */
    void clear() {
        for (var id : java.util.List.copyOf(lit.keySet())) {
            forget(id);
        }
    }

    /** The colour a thing is lit by now, or null for none. */
    ColorRGBA colourOf(int id) {
        var one = lit.get(id);
        return one == null ? null : one.light.getColor().clone();
    }
}
