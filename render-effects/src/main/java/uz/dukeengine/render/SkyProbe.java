package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.environment.FastLightProbeFactory;
import com.jme3.light.LightProbe;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.TextureCubeMap;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.jme3.util.SkyFactory;
import java.nio.ByteBuffer;
import java.util.stream.IntStream;

/** A sky worked out of a sun and an ambient colour, and the light probe baked from it for materials to take light from. */
public final class SkyProbe {

    private static final int SKY_SIZE = 64;
    private static final int PROBE_SIZE = 128;
    private static final float EVERYWHERE = 100000f;

    private SkyProbe() {
    }

    /** The light along a direction: what a sky is, to be drawn or baked. */
    public interface Sky {
        ColorRGBA at(Vector3f d);
    }

    /** A probe of the gradient sky, reaching everywhere. The sky itself is not in any scene: a game's background stays its own. */
    public static LightProbe bake(AssetManager assets, RenderManager renderManager, Vector3f toSun, ColorRGBA sun,
            ColorRGBA ambient) {
        return bake(assets, renderManager, paint(gradient(toSun, sun, ambient), SKY_SIZE));
    }

    /** A probe of a sky already painted, which {@link #paint} can do off the frame. */
    public static LightProbe bake(AssetManager assets, RenderManager renderManager, Texture painted) {
        return bake(assets, renderManager, painted, PROBE_SIZE);
    }

    /** {@code painted} baked into a probe whose faces are {@code size} wide: the smaller, the cheaper, and the blurrier its reflections. */
    public static LightProbe bake(AssetManager assets, RenderManager renderManager, Texture painted, int size) {
        var scene = new Node("the probe's sky");
        scene.attachChild(SkyFactory.createSky(assets, painted, SkyFactory.EnvMapType.CubeMap));
        scene.updateLogicalState(0f);
        scene.updateGeometricState();
        var probe = FastLightProbeFactory.makeProbe(renderManager, assets, size, Vector3f.ZERO, 1f, 1000f, scene);
        probe.getArea().setRadius(EVERYWHERE);
        return probe;
    }

    /** The gradient sky as something to draw. */
    public static com.jme3.scene.Spatial sky(AssetManager assets, Vector3f toSun, ColorRGBA sun, ColorRGBA ambient) {
        return SkyFactory.createSky(assets, paint(gradient(toSun, sun, ambient), SKY_SIZE), SkyFactory.EnvMapType.CubeMap);
    }

    /** The ambient sky of {@link #skyAt}, as a {@link Sky}. */
    public static Sky gradient(Vector3f toSun, ColorRGBA sun, ColorRGBA ambient) {
        var towardSun = toSun.normalize();
        return d -> skyAt(d, towardSun, sun, ambient);
    }

    /**
     * The sky seen along {@code d}: the ambient, brighter toward the horizon and dimmer under it, with the sun's own
     * colour gathered around {@code toSun}.
     */
    public static ColorRGBA skyAt(Vector3f d, Vector3f toSun, ColorRGBA sun, ColorRGBA ambient) {
        float up = FastMath.clamp(d.y, -1f, 1f);
        float share = up >= 0f ? FastMath.interpolateLinear((float) Math.pow(up, 0.45f), 1.2f, 0.8f)
                : FastMath.interpolateLinear(Math.min(1f, -up * 8f), 1.2f, 0.5f);
        float toward = Math.max(d.dot(toSun), 0f);
        float glow = (float) (Math.pow(toward, 900f) * 40f + Math.pow(toward, 12f) * 0.3f);
        return new ColorRGBA(ambient.r * share + sun.r * glow, ambient.g * share + sun.g * glow,
                ambient.b * share + sun.b * glow, 1f);
    }

    /**
     * {@code sky} seen from inside a cube of faces {@code size} wide. It touches no GPU, so any thread may paint, and
     * {@code sky} must be fine to ask from several at once.
     */
    public static TextureCubeMap paint(Sky sky, int size) {
        var image = new Image();
        image.setFormat(Image.Format.RGBA16F);
        image.setWidth(size);
        image.setHeight(size);
        image.setColorSpace(ColorSpace.Linear);
        IntStream.range(0, 6).parallel().mapToObj(face -> face(sky, size, face)).toList().forEach(image::addData);
        var cube = new TextureCubeMap(image);
        cube.setMagFilter(Texture.MagFilter.Bilinear);
        cube.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        return cube;
    }

    private static ByteBuffer face(Sky sky, int size, int face) {
        var bytes = BufferUtils.createByteBuffer(size * size * 8);
        var d = new Vector3f();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = (x + 0.5f) / size * 2f - 1f;
                float v = (y + 0.5f) / size * 2f - 1f;
                switch (face) {
                    case 0 -> d.set(1, -v, -u);
                    case 1 -> d.set(-1, -v, u);
                    case 2 -> d.set(u, 1, v);
                    case 3 -> d.set(u, -1, -v);
                    case 4 -> d.set(u, -v, 1);
                    default -> d.set(-u, -v, -1);
                }
                d.normalizeLocal();
                // SkyFactory's sphere has inward normals, so what is drawn along d is the texel at -d.
                var colour = sky.at(d.negateLocal());
                bytes.putShort(FastMath.convertFloatToHalf(colour.r));
                bytes.putShort(FastMath.convertFloatToHalf(colour.g));
                bytes.putShort(FastMath.convertFloatToHalf(colour.b));
                bytes.putShort(FastMath.convertFloatToHalf(1f));
            }
        }
        bytes.flip();
        return bytes;
    }
}
