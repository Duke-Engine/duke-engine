package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.light.LightProbe;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.TextureCubeMap;
import com.jme3.util.SkyFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link Atmosphere} under the sun the scene has now: a sky to draw, with the sun's disc in it, and the light probe
 * physically based materials take their ambient from, baked from the same sky.
 *
 * <p>The disc is in the drawn sky only. The probe is baked without it: the directional light already carries the sun,
 * and a disc a hundred thousand times brighter than the sky, folded into nine harmonics, would light every surface
 * twice and shimmer as it moved. The sky around the sun, its aureole, is in both.
 *
 * <p>Ask {@link #follow} every frame. The disc turns with the sun at once; the sky is painted and the probe baked again
 * when the sun has moved three degrees or changed colour, the painting on another thread so the frame does
 * not wait for it. What waits is the bake of the probe itself, which needs the GPU: see {@link #probeMillis}.
 */
public final class AtmosphereSky {

    /** The drawn sky's faces: smooth for air, but a hard edge such as a cloud will need more. */
    private static final int SIZE = 48;
    /**
     * The probe's faces. Baking it is the cost a frame pays, 140 ms at 128, 60 at 64 and 40 at 32 (RTX 3070 Laptop),
     * and a sky has nothing sharp for a bigger one to keep.
     * shortcut: baked by the GPU on the frame's thread; work the nine harmonics and a blurred chain of mips out on the
     * CPU, off the frame, if 40 ms every few degrees of sun is too much.
     */
    private static final int PROBE_SIZE = 32;
    /** How far the sun may move, or how much its colour may change, before the sky is made again. */
    private static final float STEP_DEGREES = 3f;
    private static final float COLOUR_STEP = 0.05f;
    /**
     * What the disc shines with, as a multiple of the sun's colour. The sun's own radiance is a hundred thousand times
     * that, which a half float and a bloom cannot hold; this is enough to clip to white through the film curve.
     */
    private static final float DISC_GAIN = 60f;
    private static final float SUN_RADIUS = 0.2665f * FastMath.DEG_TO_RAD;
    private static final float QUAD_RADIUS = 3f * FastMath.DEG_TO_RAD;
    private static final float DISTANCE = 10f;
    private static final Logger LOG = Logger.getLogger(AtmosphereSky.class.getName());
    private static final Executor PAINTER = Executors.newSingleThreadExecutor(job -> {
        var thread = new Thread(job, "sky painter");
        thread.setDaemon(true);
        return thread;
    });

    private final AssetManager assets;
    private final RenderManager renderManager;
    private final Node scene;
    private final Node drawn = new Node("atmosphere sky");
    private final Geometry disc;
    private final Material discMaterial;
    private Spatial cube;
    private LightProbe probe;
    private Vector3f askedToSun;
    private ColorRGBA askedSun;
    private CompletableFuture<Painted> painting;
    private double paintMillis;
    private double probeMillis;
    private int bakes;

    /** @param scene the node the probe lights: the whole world. The drawn sky is not in it until a game puts it where it wants. */
    public AtmosphereSky(AssetManager assets, RenderManager renderManager, Node scene) {
        this.assets = assets;
        this.renderManager = renderManager;
        this.scene = scene;
        discMaterial = new Material(assets, "Common/MatDefs/Duke/SunDisc.j3md");
        discMaterial.setFloat("Radius", SUN_RADIUS / QUAD_RADIUS);
        float half = DISTANCE * (float) Math.tan(QUAD_RADIUS);
        var quad = new Mesh();
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[] {-half, -half, 0, half, -half, 0, half, half, 0, -half, half, 0});
        quad.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {-1, -1, 1, -1, 1, 1, -1, 1});
        quad.setBuffer(VertexBuffer.Type.Index, 3, new short[] {0, 1, 2, 0, 2, 3});
        quad.updateBound();
        disc = new Geometry("sun disc", quad);
        disc.setMaterial(discMaterial);
        disc.setQueueBucket(RenderQueue.Bucket.Sky);
        disc.setCullHint(Spatial.CullHint.Never);
        disc.setShadowMode(ShadowMode.Off);
    }

    /**
     * The sky to draw: put it under a node that does not move, and it is the sky of the sun last followed. It has
     * nothing in it before the first {@link #follow}.
     */
    public Spatial sky() {
        return drawn;
    }

    /** The light probe of the sky, in the scene it was given; null before the first {@link #follow}. */
    public LightProbe probe() {
        return probe;
    }

    /** How long painting the last sky took, off the frame. */
    public double paintMillis() {
        return paintMillis;
    }

    /** How long baking the last probe took, on the frame: the cost a frame pays when the sun has moved enough. */
    public double probeMillis() {
        return probeMillis;
    }

    /** The skies made so far. */
    public int bakes() {
        return bakes;
    }

    /**
     * The sky under a sun toward {@code toSun} of colour {@code sun}, the light at the top of the air. Every frame, on
     * the thread that draws: the first call paints and bakes before it returns, later ones only when the sun has moved.
     */
    public void follow(Vector3f toSun, ColorRGBA sun) {
        var direction = toSun.normalize();
        placeDisc(direction, sun);
        if (painting != null && painting.isDone()) {
            try {
                install(painting.join());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "the sky could not be painted; the last one stays", e);
            }
            painting = null;
        }
        if (painting == null && moved(direction, sun)) {
            askedToSun = direction;
            askedSun = sun.clone();
            if (probe == null) {
                install(paint(direction, sun.clone()));
            } else {
                var to = direction;
                var colour = sun.clone();
                painting = CompletableFuture.supplyAsync(() -> paint(to, colour), PAINTER);
            }
        }
    }

    private boolean moved(Vector3f toSun, ColorRGBA sun) {
        if (askedToSun == null) {
            return true;
        }
        if (toSun.dot(askedToSun) < FastMath.cos(STEP_DEGREES * FastMath.DEG_TO_RAD)) {
            return true;
        }
        return Math.abs(sun.r - askedSun.r) > COLOUR_STEP * Math.max(askedSun.r, 0.02f)
                || Math.abs(sun.g - askedSun.g) > COLOUR_STEP * Math.max(askedSun.g, 0.02f)
                || Math.abs(sun.b - askedSun.b) > COLOUR_STEP * Math.max(askedSun.b, 0.02f);
    }

    private record Painted(TextureCubeMap texture, double millis) {
    }

    private static Painted paint(Vector3f toSun, ColorRGBA sun) {
        long start = System.nanoTime();
        var texture = SkyProbe.paint(new Atmosphere(toSun, sun), SIZE);
        return new Painted(texture, (System.nanoTime() - start) / 1e6);
    }

    /** The disc where the sun is, shining as much of the sun's colour as the air lets through. */
    private void placeDisc(Vector3f toSun, ColorRGBA sun) {
        disc.setLocalTranslation(toSun.mult(DISTANCE));
        disc.lookAt(Vector3f.ZERO, Math.abs(toSun.y) > 0.99f ? Vector3f.UNIT_Z : Vector3f.UNIT_Y);
        var through = Atmosphere.transmittance(toSun);
        discMaterial.setVector3("Colour", new Vector3f(sun.r * through.r, sun.g * through.g, sun.b * through.b).mult(DISC_GAIN));
    }

    /** On the thread that draws: the painted sky into the drawn one, and a probe baked from it. */
    private void install(Painted painted) {
        paintMillis = painted.millis();
        if (cube == null) {
            cube = SkyFactory.createSky(assets, painted.texture(), SkyFactory.EnvMapType.CubeMap);
            cube.setShadowMode(ShadowMode.Off);
            drawn.attachChild(cube);
            drawn.attachChild(disc);
        } else {
            ((Geometry) cube).getMaterial().setTexture("Texture", painted.texture());
        }
        long start = System.nanoTime();
        if (probe != null) {
            scene.removeLight(probe);
        }
        probe = SkyProbe.bake(assets, renderManager, painted.texture(), PROBE_SIZE);
        scene.addLight(probe);
        probeMillis = (System.nanoTime() - start) / 1e6;
        bakes++;
    }
}
