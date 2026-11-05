package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.control.AbstractControl;
import com.jme3.util.BufferUtils;

/**
 * A water surface: a flat sheet whose waves lean its normal (a sum of six travelling waves, from a hand's width to seven metres,
 * faded out where a pixel is too big to see them, so that distance goes calm and does not shimmer), lit as a dielectric of F0 0.02
 * by the scene's lights and probes, with the picture behind it seen through the waves' refraction and dimmed by what the water
 * between takes of each colour (Beer-Lambert, {@link #CLEAR}: red first), foam where it is shallow. It writes {@link SceneBuffers}
 * as a smooth surface of F0 0.02, so {@link SsrFilter} reflects the screen in it, and a flag the occlusion reads to leave it be.
 *
 * <p>The picture behind it is only there for {@link WaterFilter}, which the picture's {@link PictureSettings#water} puts in the
 * chain: the surface is in the Translucent bucket and the engine draws that after the chain, onto the screen, where this
 * material is not what to draw. shortcut: the waves lean the mesh's normal by world x and z, so the sheet is meant to lie flat,
 * a slope keeps its slope; a camera under the surface sees nothing of it (back faces are culled); the sun's shadow multiplies the
 * picture after the water is in it, so its glint is not shadowed; one picture behind the water for all the water of the view; the scene is not multisampled.
 */
public final class Water {

    /** What a metre of clear water takes of the light of each colour, red first, so that depth turns it blue-green. */
    public static final Vector3f CLEAR = new Vector3f(0.45f, 0.10f, 0.055f);

    private Water() {
    }

    /** A sheet of water {@code width} by {@code depth} metres about its origin, flat (+y up), its waves running. */
    public static Geometry surface(AssetManager assets, float width, float depth) {
        float x = width * 0.5f;
        float z = depth * 0.5f;
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(-x, 0f, -z, x, 0f, -z, x, 0f, z, -x, 0f, z));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(0, 3, 2, 0, 2, 1));
        mesh.updateBound();
        var water = new Geometry("water", mesh);
        var material = new Material(assets, "Common/MatDefs/Duke/Water.j3md");
        material.setVector3("Extinction", CLEAR);
        water.setMaterial(material);
        water.setQueueBucket(RenderQueue.Bucket.Translucent);
        water.setShadowMode(ShadowMode.Off);
        water.addControl(new Waves());
        return water;
    }

    /** How much of the light of a colour is left after {@code metres} of water that takes {@code extinction} of it a metre (Beer-Lambert). */
    public static float transmittance(float extinction, float metres) {
        return (float) Math.exp(-extinction * metres);
    }

    /** Keeps the waves of the surface it is on running: the material's time, which a game or the bench may set by hand. */
    public static final class Waves extends AbstractControl {

        private float seconds;

        /** Puts the waves at this time. */
        public void seconds(float seconds) {
            this.seconds = seconds;
            apply();
        }

        @Override
        protected void controlUpdate(float tpf) {
            seconds(seconds + tpf);
        }

        @Override
        protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {
        }

        private void apply() {
            if (spatial instanceof Geometry water) {
                water.getMaterial().setFloat("Time", seconds);
            }
        }
    }
}
