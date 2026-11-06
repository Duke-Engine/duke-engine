package com.jme3.renderer;

import com.jme3.renderer.queue.GeometryList;

/**
 * duke-engine: draws a sorted list of solid geometries (the opaque bucket, a shadow map's casters) the way
 * {@link RenderManager#renderGeometry(com.jme3.scene.Geometry)} for each would, perhaps in fewer calls
 * (uz.dukeengine.render.AutoInstancing). Set on the manager with {@link RenderManager#setGeometryBatcher}.
 */
public interface GeometryBatcher {

    /** Draws every geometry of {@code list}, in this order or a different one; leaves the list as it is. */
    void render(GeometryList list, RenderManager renderManager);
}
