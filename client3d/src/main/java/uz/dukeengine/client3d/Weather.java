package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.SplittableRandom;

/**
 * A weather of flakes falling round the camera, as the reference draws its snow ({@code W3DSnowManager::render}): one
 * flake for each column of a grid fixed to the world, in a box round the eye; each column's own starting height from
 * a 64 by 64 table, repeating; falling by the view's clock and wrapping from the box's bottom to its top, keeping its
 * height in the world as the eye rises and falls; swaying with its height; drawn as a point facing the camera, its size
 * over its distance. Each machine's own, and nothing of it in the simulation.
 */
final class Weather {

    /** {@code SNOW_NOISE_X} and {@code _Y}: the table of starting heights, repeating every 64 columns. */
    private static final int NOISE = 64;
    /** A frame of the reference's view's clock, in seconds: what a flake falls by in one frame of the game. */
    static final float FRAME_SECONDS = 0.033f;
    /** {@code MAXIMUM_CAMERA_DISTANCE}: added to a column before it is taken round the table, to keep it positive. */
    private static final int FAR = 100000;

    private final WeatherLook look;
    private final float[] heights = new float[NOISE * NOISE];
    private final int half;
    private float time;
    private float[] places = new float[0];
    private Geometry flakes;

    Weather(WeatherLook look) {
        this.look = look;
        var random = new SplittableRandom();
        for (int at = 0; at < heights.length; at++) {
            heights[at] = random.nextInt(Math.max(1, (int) look.box())); // whole heights below the box's size
        }
        this.half = (int) Math.floor(look.box() / look.spacing() * 0.5f);
    }

    /** {@code frames} of the game gone by: the flakes fall by as many frames of the view's clock, none while paused. */
    void step(int frames) {
        if (frames <= 0 || look.speed() <= 0f) {
            return;
        }
        time = (time + frames * FRAME_SECONDS) % (look.box() / look.speed());
    }

    /** How many flakes fall round the eye: a column for each spacing across the box, each way. */
    int count() {
        return 4 * half * half;
    }

    /**
     * Every flake's place round an eye at {@code (x, height, z)} — the scene's axes, its height up — three numbers a
     * flake: the columns from half the box on the one side of the eye's to half on the other, each at its height in
     * the world, swayed.
     */
    float[] lay(float eyeX, float eyeHeight, float eyeZ) {
        if (places.length != count() * 3) {
            places = new float[count() * 3];
        }
        float ceiling = eyeHeight + look.box() / 2f;
        float travelled = time * look.speed() + mod(eyeHeight, look.box());
        int middleX = (int) Math.floor(eyeX / look.spacing());
        int middleZ = (int) Math.floor(eyeZ / look.spacing());
        int at = 0;
        for (int z = middleZ - half; z < middleZ + half; z++) {
            for (int x = middleX - half; x < middleX + half; x++) {
                int noise = Math.floorMod(x + FAR, NOISE) + Math.floorMod(z + FAR, NOISE) * NOISE;
                float height = ceiling - mod(travelled + heights[noise], look.box());
                places[at++] = x * look.spacing() + look.amplitude() * (float) Math.sin(height * look.frequencyX() + x);
                places[at++] = height;
                places[at++] = z * look.spacing() + look.amplitude() * (float) Math.sin(height * look.frequencyY() + z);
            }
        }
        return places;
    }

    /** How many pixels across a flake {@code distance} from the eye is drawn, in a view {@code viewHeight} high. */
    static float pixels(WeatherLook look, float viewHeight, float distance) {
        return Math.clamp(look.size() * viewHeight / Math.max(distance, 1e-3f), look.leastPixels(),
                look.mostPixels());
    }

    /**
     * The size a flake's pixels are reckoned from, {@code tanHalfHeight} the tangent of half the view's height: the
     * look's own, or for a square of world units the pixels it spans per unit of view height over its distance.
     */
    static float scale(WeatherLook look, float tanHalfHeight) {
        return look.square() > 0f ? look.square() / (2f * Math.max(tanHalfHeight, 1e-6f)) : look.size();
    }

    /**
     * Drawn under {@code parent}, round an eye there, in a view {@code viewHeight} pixels high, {@code
     * tanHalfHeight} the tangent of half its height.
     */
    void show(AssetManager assets, Node parent, float eyeX, float eyeHeight, float eyeZ, float viewHeight,
            float tanHalfHeight) {
        if (flakes == null) {
            var mesh = new Mesh();
            mesh.setMode(Mesh.Mode.Points);
            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(count() * 3));
            mesh.setDynamic();
            var material = new Material(assets, "MatDefs/duke/Flakes.j3md");
            material.setTexture("Picture", assets.loadTexture(look.picture()));
            material.setFloat("Size", scale(look, tanHalfHeight));
            material.setFloat("Least", look.leastPixels());
            material.setFloat("Most", look.mostPixels());
            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            material.getAdditionalRenderState().setDepthWrite(false);
            flakes = new Geometry("weather", mesh);
            flakes.setMaterial(material);
            flakes.setQueueBucket(RenderQueue.Bucket.Translucent); // after the particle systems
            flakes.setShadowMode(RenderQueue.ShadowMode.Off);
            parent.attachChild(flakes);
        } else if (flakes.getParent() == null) {
            parent.attachChild(flakes); // the scene built again for a new match
        }
        var buffer = (FloatBuffer) flakes.getMesh().getBuffer(VertexBuffer.Type.Position).getData();
        buffer.clear();
        buffer.put(lay(eyeX, eyeHeight, eyeZ)).flip();
        flakes.getMesh().getBuffer(VertexBuffer.Type.Position).updateData(buffer);
        flakes.getMesh().updateBound();
        flakes.getMaterial().setFloat("ViewHeight", viewHeight);
        flakes.getMaterial().setFloat("Size", scale(look, tanHalfHeight));
    }

    /** Taken out of the scene. */
    void remove() {
        if (flakes != null) {
            flakes.removeFromParent();
            flakes = null;
        }
    }

    private static float mod(float value, float by) {
        float left = value % by;
        return left < 0f ? left + by : left;
    }
}
