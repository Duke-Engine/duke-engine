package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * A few hundred quads whose every corner is told once where it was born, and a
 * shader that does the rest.
 *
 * <p><b>Why not jME's own emitter.</b> {@code ParticleEmitter} moves every particle
 * on the processor every frame, and it changes colour and size along a straight
 * line between two values and nothing else. The first is what makes a fight warm a
 * laptop — sixty sparks is sixty updates a frame whether or not anything is
 * looking — and the second is what makes effects look mechanical, because nothing
 * in the world grows at a constant speed. Here a particle is written into its four
 * corners once, on the frame it is born, and {@code Particles.vert} works out
 * where it is from how old it is: the processor pays for the birth and the graphics
 * card for the rest, and the curves are whatever the settings file says.
 *
 * <p><b>Built once, dressed many times.</b> A layer owns a mesh with buffers sized
 * for {@link #capacity} particles and a material of its own. It is lent out for one
 * effect, dressed in that effect's texture and numbers, and handed back — so the
 * scene does not grow as a fight goes on, and a busy frame costs what the busiest
 * frame before it cost.
 */
final class ParticleLayer {

    static final String DEFINITION = "MatDefs/duke/Particles.j3md";

    /** A birth so far off it never comes: how an unused slot is kept dark. */
    private static final float NEVER = 1.0e9f;

    /** How wide the drawn fallback dot is. A blur, so it need not be big. */
    private static final int DOT_PIXELS = 32;

    final int capacity;
    private final Mesh mesh = new Mesh();
    private final Geometry geometry;
    private final Material material;
    private final FloatBuffer origins;
    private final FloatBuffer velocities;
    private final FloatBuffer lives;
    private final FloatBuffer extras;

    ParticleLayer(AssetManager assets, int capacity) {
        this.capacity = Math.max(1, capacity);
        int corners = this.capacity * 4;
        origins = BufferUtils.createFloatBuffer(corners * 3);
        velocities = BufferUtils.createFloatBuffer(corners * 3);
        lives = BufferUtils.createFloatBuffer(corners * 4);
        extras = BufferUtils.createFloatBuffer(corners * 4);

        var corner = BufferUtils.createFloatBuffer(corners * 2);
        var order = BufferUtils.createIntBuffer(this.capacity * 6);
        for (int particle = 0; particle < this.capacity; particle++) {
            corner.put(0f).put(0f).put(1f).put(0f).put(1f).put(1f).put(0f).put(1f);
            int first = particle * 4;
            order.put(first).put(first + 1).put(first + 2)
                    .put(first).put(first + 2).put(first + 3);
        }
        corner.flip();
        order.flip();

        mesh.setBuffer(VertexBuffer.Type.Position, 3, origins);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, velocities);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, corner);
        mesh.setBuffer(VertexBuffer.Type.TexCoord2, 4, lives);
        mesh.setBuffer(VertexBuffer.Type.TexCoord3, 4, extras);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, order);
        empty();

        material = new Material(assets, DEFINITION);
        var state = material.getAdditionalRenderState();
        // Seen through nothing but hiding nothing: a wall still stands in front
        // of a flame, and a flame never cuts a hole in the flame behind it.
        state.setDepthWrite(false);
        state.setFaceCullMode(RenderState.FaceCullMode.Off);

        geometry = new Geometry("particles", mesh);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        geometry.setShadowMode(RenderQueue.ShadowMode.Off);
        geometry.setCullHint(Spatial.CullHint.Always);
    }

    Geometry geometry() {
        return geometry;
    }

    /**
     * Put this layer's look on: the shape, the colours, the curves, and which way
     * it faces.
     *
     * <p>Every slot is emptied at the same time. A layer handed back half-full and
     * lent out again would otherwise show the last effect's embers for a frame in
     * the middle of the next one.
     */
    void dress(EffectLayer layer, Texture texture) {
        material.setTexture("Texture", texture);
        material.setFloat("Time", 0f);
        material.setColor("StartColour", colour(layer.colourStart(), layer.alphaStart()));
        material.setColor("EndColour", colour(layer.colourEnd(), layer.alphaEnd()));
        material.setFloat("ColourEase", layer.colourEase());
        material.setFloat("SizeStart", layer.sizeStart());
        material.setFloat("SizeEnd", layer.sizeEnd());
        material.setFloat("SizeEase", layer.sizeEase());
        material.setFloat("FadeIn", layer.fadeIn());
        material.setFloat("FadeOut", layer.fadeOut());
        material.setVector3("Gravity", new Vector3f(0f, -layer.gravity(), 0f));
        material.setFloat("Drag", layer.drag());
        material.setFloat("Stretch", layer.stretch());
        material.setFloat("Spin", (float) Math.toRadians(layer.spin()));
        pulse(layer.pulseRate(), layer.pulseDepth());
        opacity(1f);
        // One blend for all of it, premultiplied: the colour is added, and Cover's
        // share of what was behind is taken away first. Cover 0 is light -- two
        // sparks overlapping are brighter than one, as light is -- and Cover 1 is
        // stuff, so smoke hides the floor instead of lighting it up.
        material.setFloat("Cover", layer.cover());
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.PremultAlpha);
        face("Ground", layer.lying());
        face("Axis", EffectLayer.BEAM.equals(layer.type()));
        face("Streak", !layer.lying() && !EffectLayer.BEAM.equals(layer.type())
                && layer.stretch() > 0f);
        empty();
    }

    /**
     * One particle, into all four of its corners.
     *
     * @param extraW its angle at birth in radians — or, for a beam, its length
     */
    void put(int index, float x, float y, float z, float vx, float vy, float vz,
            float birth, float life, float seed, float scale,
            float extraX, float extraY, float extraZ, float extraW) {
        int at = Math.floorMod(index, capacity) * 4;
        for (int corner = 0; corner < 4; corner++) {
            int vertex = at + corner;
            origins.put(vertex * 3, x).put(vertex * 3 + 1, y).put(vertex * 3 + 2, z);
            velocities.put(vertex * 3, vx).put(vertex * 3 + 1, vy).put(vertex * 3 + 2, vz);
            lives.put(vertex * 4, birth).put(vertex * 4 + 1, life)
                    .put(vertex * 4 + 2, seed).put(vertex * 4 + 3, scale);
            extras.put(vertex * 4, extraX).put(vertex * 4 + 1, extraY)
                    .put(vertex * 4 + 2, extraZ).put(vertex * 4 + 3, extraW);
        }
    }

    /**
     * Send what was written to the graphics card.
     *
     * <p>Once for a burst, when it goes off; once a frame only for a layer that is
     * still letting particles out, and then only because new ones were born.
     */
    void upload() {
        mesh.getBuffer(VertexBuffer.Type.Position).updateData(origins);
        mesh.getBuffer(VertexBuffer.Type.Normal).updateData(velocities);
        mesh.getBuffer(VertexBuffer.Type.TexCoord2).updateData(lives);
        mesh.getBuffer(VertexBuffer.Type.TexCoord3).updateData(extras);
    }

    /** How long the layer has been burning, which is all the shader asks. */
    void time(float seconds) {
        material.setFloat("Time", seconds);
    }

    float time() {
        var param = material.getParam("Time");
        return param == null ? 0f : (Float) param.getValue();
    }

    /**
     * How far anything in it can get, around where it was lit.
     *
     * <p>Set rather than measured: the mesh only knows where each particle was
     * BORN, and a spark thrown ten units is still inside a box measured round its
     * birthplace. Set generously, this is what lets the camera leave an effect it
     * cannot see undrawn — which is the cheapest particle there is.
     */
    void reach(Vector3f middle, float distance) {
        float extent = Math.max(0.5f, distance);
        geometry.setModelBound(new BoundingBox(middle.clone(), extent, extent, extent));
    }

    /** The same, round a stretch rather than a spot -- a trail along the way it was laid. */
    void reach(BoundingBox box) {
        geometry.setModelBound(box.clone());
    }

    void opacity(float amount) {
        material.setFloat("Opacity", Math.clamp(amount, 0f, 1f));
    }

    void pulse(float rate, float depth) {
        material.setFloat("PulseRate", Math.max(0f, rate));
        material.setFloat("PulseDepth", Math.clamp(depth, 0f, 1f));
    }

    void show(boolean shown) {
        geometry.setCullHint(shown ? Spatial.CullHint.Dynamic : Spatial.CullHint.Always);
    }

    /** Every slot dark again. */
    void empty() {
        for (int vertex = 0; vertex < capacity * 4; vertex++) {
            lives.put(vertex * 4, NEVER).put(vertex * 4 + 1, 1f);
        }
        var buffer = mesh.getBuffer(VertexBuffer.Type.TexCoord2);
        if (buffer != null) {
            buffer.updateData(lives);
        }
    }

    /** Whether any slot is waiting to be born or still alive at this time. */
    boolean anyAliveAt(float seconds) {
        for (int particle = 0; particle < capacity; particle++) {
            float birth = lives.get(particle * 16);
            float life = lives.get(particle * 16 + 1);
            if (birth < NEVER && seconds <= birth + life) {
                return true;
            }
        }
        return false;
    }

    /** The birth written into a slot, for a test that wants to see it. */
    float birthOf(int index) {
        return lives.get(Math.floorMod(index, capacity) * 16);
    }

    Material material() {
        return material;
    }

    private void face(String define, boolean on) {
        if (on) {
            material.setBoolean(define, true);
        } else {
            material.clearParam(define);
        }
    }

    private static ColorRGBA colour(int packed, float alpha) {
        return new ColorRGBA(((packed >> 16) & 0xFF) / 255f, ((packed >> 8) & 0xFF) / 255f,
                (packed & 0xFF) / 255f, Math.clamp(alpha, 0f, 1f));
    }

    /**
     * A soft round dot, drawn rather than shipped — what a layer gets when the
     * texture it named cannot be found.
     *
     * <p>A misspelt file name in the settings is a line in a log and a plain glow
     * on screen, never a missing effect and never an exception in the middle of a
     * fight.
     */
    static Texture softDot() {
        var pixels = ByteBuffer.allocateDirect(DOT_PIXELS * DOT_PIXELS * 4);
        float middle = (DOT_PIXELS - 1) / 2f;
        for (int y = 0; y < DOT_PIXELS; y++) {
            for (int x = 0; x < DOT_PIXELS; x++) {
                float away = (float) Math.hypot(x - middle, y - middle) / middle;
                float strength = Math.max(0f, 1f - away);
                byte value = (byte) Math.round(255f * strength * strength);
                pixels.put((byte) 255).put((byte) 255).put((byte) 255).put(value);
            }
        }
        pixels.flip();
        return new Texture2D(new Image(Image.Format.RGBA8, DOT_PIXELS, DOT_PIXELS, pixels,
                com.jme3.texture.image.ColorSpace.sRGB));
    }
}
