package uz.dukeengine.client3d;

import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The effects' lights, made once and lent out.
 *
 * <p>Dynamic lights are the expensive kind: every one of them is another pass over
 * everything it touches, and the floor's own shader takes a fixed number and no
 * more. So there is a set of them, made when the world is, and a burning thing
 * that finds none spare simply burns without lighting the floor — it still has its
 * fire, so what is lost is the glow rather than the effect.
 *
 * <p>Shared between everything that burns. Two pools of four would be eight lights
 * as far as the renderer is concerned and four as far as either pool knows, which
 * is how a budget is quietly doubled.
 *
 * <p>Free ones are kept in a list of their own rather than recognised by looking at
 * them. A light's own state cannot say whether it is in use: jME reads a radius of
 * zero as <em>infinite</em>, and a black light is still a light the renderer has to
 * consider.
 */
final class LightPool {

    /** Where a doused light is put: far below anything that could be lit. */
    private static final Vector3f NOWHERE = new Vector3f(0f, -10_000f, 0f);

    private final List<PointLight> all = new ArrayList<>();
    private final Deque<PointLight> free = new ArrayDeque<>();

    LightPool(Node root, int size) {
        for (int i = 0; i < Math.max(0, size); i++) {
            var light = new PointLight(NOWHERE.clone(), ColorRGBA.BlackNoAlpha, 1f);
            all.add(light);
            free.add(light);
            root.addLight(light);
        }
    }

    /** A spare light, or {@code null} when every one of them is burning. */
    PointLight take() {
        return free.poll();
    }

    /** Put one out and give it back. Giving back a light twice is harmless. */
    void give(PointLight light) {
        if (light == null) {
            return;
        }
        light.setColor(ColorRGBA.BlackNoAlpha);
        light.setRadius(1f);
        light.setPosition(NOWHERE.clone());
        if (!free.contains(light)) {
            free.add(light);
        }
    }

    /** How many are burning right now. */
    int lit() {
        return all.size() - free.size();
    }

    int size() {
        return all.size();
    }

    /** Every light, lit or not, in a fixed order — the floor's shader reads them by slot. */
    List<PointLight> all() {
        return List.copyOf(all);
    }

    /** Everything out: a new world has nothing burning in it. */
    void clear() {
        for (var light : all) {
            give(light);
        }
    }
}
