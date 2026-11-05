package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.environment.FastLightProbeFactory;
import com.jme3.light.LightProbe;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.TextureCubeMap;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.jme3.util.SkyFactory;
import java.util.List;

/**
 * What {@link Visuals#physicallyBased} adds to a scene: the light physically based materials take their ambient from,
 * a probe baked from a sky worked out of the scene's own sun and ambient, and the filters over the finished picture.
 *
 * <p>Light is drawing. Nothing in the simulation reads it.
 */
final class PhysicalLook {

    private static final int SKY_SIZE = 64;
    private static final int PROBE_SIZE = 128;
    private static final float EVERYWHERE = 100000f;
    /**
     * shortcut: measured so a model is as bright as under the flat lighting on the skirmish's default sun, not derived
     * from anything physical, measure again on a night map or indoors when a game has one.
     */
    private static final float SKY_STRENGTH = 0.3f;

    private final AssetManager assets;
    private final RenderManager renderManager;
    private final Node root;
    private LightProbe probe;

    PhysicalLook(AssetManager assets, RenderManager renderManager, Node root, SceneLight light) {
        this.assets = assets;
        this.renderManager = renderManager;
        this.root = root;
        bake(light);
        light.shadows().finishWith(finishing());
    }

    /**
     * The probe baked anew from the sun the scene is lit by now: when a new world brings its own.
     * shortcut: a sun easing to another hour keeps the probe it had, re-bake every few minutes of game time if a day
     * and night cycle needs it.
     */
    void bake(SceneLight light) {
        var sky = SkyFactory.createSky(assets, skyPicture(light.sunDirection().negate(), light.sunColour(),
                light.ambientColour()), SkyFactory.EnvMapType.CubeMap);
        // Never in the scene: a game's background stays its own, only the materials see this sky.
        var scene = new Node("the probe's sky");
        scene.attachChild(sky);
        scene.updateLogicalState(0f);
        scene.updateGeometricState();
        if (probe != null) {
            root.removeLight(probe);
        }
        probe = FastLightProbeFactory.makeProbe(renderManager, assets, PROBE_SIZE, Vector3f.ZERO, 1f, 1000f, scene);
        probe.getArea().setRadius(EVERYWHERE);
        root.addLight(probe);
    }

    /** shortcut: one SSAO radius for every game's world size, make it a Visuals value when a game needs another. */
    private List<Filter> finishing() {
        var bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloom.setExposurePower(4f);
        bloom.setBloomIntensity(0.6f);
        bloom.setBlurScale(1.5f);
        // The ground and the game's other colours were chosen for a picture with no curve: kept where they were.
        var toneMap = new ToneMapFilter(new Vector3f(4f, 4f, 4f));
        toneMap.setExposure(1.47f);
        return List.of(new SSAOFilter(1.6f, 1.4f, 0.6f, 0.1f), bloom, toneMap, new FXAAFilter());
    }

    /**
     * The sky seen along {@code d}: the ambient, brighter toward the horizon and dimmer under it, with the sun's own
     * colour gathered around {@code toSun}.
     */
    static ColorRGBA skyAt(Vector3f d, Vector3f toSun, ColorRGBA sun, ColorRGBA ambient) {
        float up = FastMath.clamp(d.y, -1f, 1f);
        float share = up >= 0f ? FastMath.interpolateLinear((float) Math.pow(up, 0.45f), 1.2f, 0.8f)
                : FastMath.interpolateLinear(Math.min(1f, -up * 8f), 1.2f, 0.5f);
        float toward = Math.max(d.dot(toSun), 0f);
        float glow = (float) (Math.pow(toward, 900f) * 40f + Math.pow(toward, 12f) * 0.3f);
        return new ColorRGBA(ambient.r * share + sun.r * glow, ambient.g * share + sun.g * glow,
                ambient.b * share + sun.b * glow, 1f);
    }

    private static Texture skyPicture(Vector3f toSun, ColorRGBA sun, ColorRGBA ambient) {
        var image = new Image();
        image.setFormat(Image.Format.RGBA16F);
        image.setWidth(SKY_SIZE);
        image.setHeight(SKY_SIZE);
        image.setColorSpace(ColorSpace.Linear);
        var towardSun = toSun.normalize();
        var d = new Vector3f();
        for (int face = 0; face < 6; face++) {
            var bytes = BufferUtils.createByteBuffer(SKY_SIZE * SKY_SIZE * 8);
            for (int y = 0; y < SKY_SIZE; y++) {
                for (int x = 0; x < SKY_SIZE; x++) {
                    float u = (x + 0.5f) / SKY_SIZE * 2f - 1f;
                    float v = (y + 0.5f) / SKY_SIZE * 2f - 1f;
                    switch (face) {
                        case 0 -> d.set(1, -v, -u);
                        case 1 -> d.set(-1, -v, u);
                        case 2 -> d.set(u, 1, v);
                        case 3 -> d.set(u, -1, -v);
                        case 4 -> d.set(u, -v, 1);
                        default -> d.set(-u, -v, -1);
                    }
                    d.normalizeLocal();
                    var colour = skyAt(d, towardSun, sun, ambient).mult(SKY_STRENGTH);
                    bytes.putShort(FastMath.convertFloatToHalf(colour.r));
                    bytes.putShort(FastMath.convertFloatToHalf(colour.g));
                    bytes.putShort(FastMath.convertFloatToHalf(colour.b));
                    bytes.putShort(FastMath.convertFloatToHalf(1f));
                }
            }
            bytes.flip();
            image.addData(bytes);
        }
        var cube = new TextureCubeMap(image);
        cube.setMagFilter(Texture.MagFilter.Bilinear);
        cube.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        return cube;
    }
}
