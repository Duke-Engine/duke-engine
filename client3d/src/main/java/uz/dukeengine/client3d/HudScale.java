package uz.dukeengine.client3d;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 * The HUD's own pixels, on a screen that is denser than the design (a phone): the HUD is laid out and hit as if the
 * window were {@code 1 / scale} as big, and its roots drawn {@code scale} times larger, so the one number is the only
 * place a density enters. At 1, the desktop's, every answer is the argument itself.
 *
 * <p>Only the interface counts in these pixels. What is projected from the world (bars, numbers, the drag box) and the
 * world's own picking stay in the window's.
 */
final class HudScale {

    static final HudScale NONE = new HudScale(1f);

    private final float scale;

    HudScale(float scale) {
        this.scale = scale > 0f && Float.isFinite(scale) ? scale : 1f;
    }

    float scale() {
        return scale;
    }

    /** A length of the window, in the HUD's pixels. */
    float of(float windowPixels) {
        return windowPixels / scale;
    }

    int of(int windowPixels) {
        return scale == 1f ? windowPixels : Math.max(1, Math.round(windowPixels / scale));
    }

    /** A point of the window (a cursor, a touch), in the HUD's pixels. */
    Vector2f of(Vector2f window) {
        return scale == 1f ? window : new Vector2f(window.x / scale, window.y / scale);
    }

    /** A canvas point of the window's pixels, in the HUD's; null stays null. */
    Canvas.Point of(Canvas.Point window) {
        return window == null || scale == 1f ? window : new Canvas.Point(window.x() / scale, window.y() / scale);
    }

    /** A bar placed in the window's pixels, {left, bottom, width, height}, in the HUD's; null stays null. */
    float[] of(float[] placed) {
        if (placed == null || scale == 1f) {
            return placed;
        }
        var scaled = placed.clone();
        for (int i = 0; i < 4; i++) {
            scaled[i] /= scale;
        }
        return scaled;
    }

    /** A length of the HUD, in the window's pixels. */
    float inWindow(float hudPixels) {
        return hudPixels * scale;
    }

    /** Draw a root of the HUD {@code scale} times larger, from the window's bottom left; the root is returned. */
    <T extends Spatial> T draw(T root) {
        root.setLocalScale(scale, scale, 1f);
        return root;
    }

    /** A new root for pieces of the HUD, drawn at this scale. */
    Node root(String name) {
        return draw(new Node(name));
    }
}
