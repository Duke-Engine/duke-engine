package com.jme3.light;

import com.jme3.renderer.Camera;
import com.jme3.renderer.Renderer;
import com.jme3.shader.Shader;

/**
 * duke-engine: lights a view has sorted into the clusters of its frustum, which the physically based lighting reads per
 * pixel in a geometry's first pass instead of drawing a pass for each (uz.dukeengine.render.ClusteredLights). The fork's
 * lighting logic needs this much of it, the rest lives with the renderer's own code.
 */
public interface LightClusters {

    /** Whether the clusters were sorted for this camera this frame, so what it draws may read them. */
    boolean drawsFor(Camera camera);

    /** Whether the clusters draw this light, so that a geometry's own passes leave it out. */
    boolean holds(Light light);

    /**
     * Binds what the shader reads the clusters from (see {@code Common/ShaderLib/LightClusters.glsllib}) to texture units
     * from {@code unit} on.
     *
     * @return the next free texture unit
     */
    int bind(Renderer renderer, Shader shader, int unit);
}
