package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingBox;
import com.jme3.material.RenderState;
import com.jme3.math.Ray;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

/**
 * Where a ray meets what is drawn of a thing — the reference's {@code RTS3DScene::castRay}, which casts against each
 * render object's drawn geometry. Here, every piece drawn now as the box round its own mesh, turned and placed as the
 * piece is: a box turned with a building rather than the box of the world's axes round the whole of it, which for one
 * put down at 45 degrees is twice its area. A skinned piece is its bind pose placed as the thing is — boxes rather than
 * triangles, since a skinned mesh is posed on the graphics card and this side never sees the pose on screen.
 *
 * <p>Pieces hidden now are skipped, and so are pieces drawn see-through or added to what is behind them — a muzzle
 * flash, a headlight's beam, a glow — unless the thing has nothing else to be clicked by.
 */
final class Picking {

    private Picking() {
    }

    /** How far along the ray it first meets a piece of {@code body} drawn now; {@link Float#POSITIVE_INFINITY} for never. */
    static float drawnHit(Spatial body, Ray ray) {
        float[] solid = {Float.POSITIVE_INFINITY};
        float[] clear = {Float.POSITIVE_INFINITY};
        boolean[] anySolid = {false};
        body.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry) || geometry.getCullHint() == Spatial.CullHint.Always
                    || geometry.getMesh() == null || !(geometry.getMesh().getBound() instanceof BoundingBox box)) {
                return;
            }
            float hit = meets(geometry, box, ray);
            if (seeThrough(geometry)) {
                clear[0] = Math.min(clear[0], hit);
            } else {
                anySolid[0] = true;
                solid[0] = Math.min(solid[0], hit);
            }
        });
        return anySolid[0] ? solid[0] : clear[0];
    }

    /** Drawn see-through, or added to what is behind it: a flash, a beam, a glow. */
    private static boolean seeThrough(Geometry geometry) {
        var bucket = geometry.getQueueBucket();
        if (bucket == RenderQueue.Bucket.Transparent || bucket == RenderQueue.Bucket.Translucent) {
            return true;
        }
        var material = geometry.getMaterial();
        return material != null && material.getAdditionalRenderState().getBlendMode() != RenderState.BlendMode.Off;
    }

    /**
     * Where the ray first meets the box of a piece's own mesh, placed as the piece is — its distance along the ray, 0
     * where it starts inside — or {@link Float#POSITIVE_INFINITY} for never.
     */
    static float meets(Geometry geometry, BoundingBox box, Ray ray) {
        var transform = geometry.getWorldTransform();
        var origin = transform.transformInverseVector(ray.getOrigin(), null);
        // Taken into the piece's frame by the turn and the scale alone and left unnormalised there, so a distance along
        // it is the same distance along the ray in the world.
        var way = transform.getRotation().inverse().mult(ray.getDirection()).divideLocal(transform.getScale());
        var centre = box.getCenter();
        float[] low = {centre.x - box.getXExtent(), centre.y - box.getYExtent(), centre.z - box.getZExtent()};
        float[] high = {centre.x + box.getXExtent(), centre.y + box.getYExtent(), centre.z + box.getZExtent()};
        float[] from = {origin.x, origin.y, origin.z};
        float[] along = {way.x, way.y, way.z};
        float enters = 0f;
        float leaves = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(along[axis]) < 1e-9f) {
                if (from[axis] < low[axis] || from[axis] > high[axis]) {
                    return Float.POSITIVE_INFINITY; // running alongside this pair of faces, outside them
                }
                continue;
            }
            float first = (low[axis] - from[axis]) / along[axis];
            float second = (high[axis] - from[axis]) / along[axis];
            enters = Math.max(enters, Math.min(first, second));
            leaves = Math.min(leaves, Math.max(first, second));
            if (enters > leaves) {
                return Float.POSITIVE_INFINITY;
            }
        }
        return enters;
    }
}
