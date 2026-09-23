package uz.dukeengine.client3d;

import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryComparator;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.TransparentComparator;
import com.jme3.scene.Geometry;

/**
 * The order the transparent bucket is drawn in: the ground's overlays first, one layer after another, and then
 * everything else back to front, exactly as jME has always sorted it.
 *
 * <p><b>Why a fixed order and not the distance.</b> jME sorts its transparent bucket by how far the camera is
 * from each geometry's bounds. An overlay's mesh is one picture across a whole map, so its bounds say nothing
 * about the cells it covers: two layers' meshes would be drawn in whichever order the camera happened to put
 * their boxes, and where a cell carries both, the second would lie under the first half the time. Depth
 * cannot settle it either. The two lie on exactly the same triangles — the same five points a cell, pulled
 * toward the eye by the same offset — and neither writes depth, so what is drawn last is what is seen, and
 * nothing is left to fight over a pixel. Which layer is drawn last is therefore decided here, by the layer
 * written on each overlay's geometry ({@link #LAYER}), and not by where anybody is standing.
 *
 * <p><b>The ground's overlays before every other transparent thing</b>, not only before each other. The ground
 * is under everything; a spark sorted ahead of the overlay it hangs over was drawn and then painted over by
 * the ground, since neither writes depth. Everything that is not an overlay keeps the order it had.
 *
 * <p>A map with one layer, or none, is drawn as it was: its overlays never cover each other, so no order
 * among them could show.
 */
final class OverlayOrder implements GeometryComparator {

    /** The user-data key a ground overlay's geometry carries: its layer, 0 for the first. */
    static final String LAYER = "groundOverlayLayer";

    private final TransparentComparator distance = new TransparentComparator();

    /** Draws a view's transparent bucket in this order: how the client's own view is set up. */
    static void install(ViewPort view) {
        view.getQueue().setGeometryComparator(RenderQueue.Bucket.Transparent, new OverlayOrder());
    }

    @Override
    public void setCamera(Camera cam) {
        distance.setCamera(cam);
    }

    @Override
    public int compare(Geometry a, Geometry b) {
        Integer first = a.getUserData(LAYER);
        Integer second = b.getUserData(LAYER);
        if (first == null && second == null) {
            return distance.compare(a, b);
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        int byLayer = Integer.compare(first, second);
        return byLayer != 0 ? byLayer : distance.compare(a, b);
    }
}
