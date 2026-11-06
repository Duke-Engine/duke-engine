package uz.dukeengine.render.bench;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.instancing.InstancedNode;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.system.AppSettings;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.jme3.util.mikktspace.MikktspaceTangentGenerator;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import uz.dukeengine.render.AntiAliasing;
import uz.dukeengine.render.Atmosphere;
import uz.dukeengine.render.AcesToneMap;
import uz.dukeengine.render.AtmosphereSky;
import uz.dukeengine.render.AutoInstancing;
import uz.dukeengine.render.AutoLod;
import uz.dukeengine.render.ClusteredLights;
import uz.dukeengine.render.GtaoFilter;
import uz.dukeengine.render.HeightFogFilter;
import uz.dukeengine.render.Lens;
import uz.dukeengine.render.LocalProbes;
import uz.dukeengine.render.LocalShadowFilter;
import uz.dukeengine.render.MipBloomFilter;
import uz.dukeengine.render.PictureChain;
import uz.dukeengine.render.PictureSettings;
import uz.dukeengine.render.SceneBuffers;
import uz.dukeengine.render.SsrFilter;
import uz.dukeengine.render.SkyProbe;
import uz.dukeengine.render.SsgiFilter;
import uz.dukeengine.render.SunShadowFilter;
import uz.dukeengine.render.Water;

/**
 * Draws one fixed scene, measures its frames, saves a picture and quits, so a change to the renderer can be seen and
 * priced. Args: scene (materials, materials-plus, bleed, interior, outdoor, night, buffers, water, furnace), what to leave out or add ("full", or joined by
 * commas: room-probe (the interior's room, or a box over the balls of materials, as a box probe baked from its middle: parallax reflections of its walls, its own diffuse light) show-ssgi (the bounce of screen space global illumination alone, through the rest of the chain), no-ssgi, the scene bleed (a red wall and a white strip of floor before it) and show-probe-left (what the picture holds with the probes' specular taken out: 0 where SSR's probes are PBRLighting's), no-ao, no-bloom, no-aa, fxaa, taa (the edges' smoothing; else the default of FULL), no-pcss, no-contact, no-shadows, no-local-shadows, local-&lt;n&gt; (how many local lights have a shadow), lamp (one lamp over any scene); no-clusters (the spot and point lights a pass each, the fork's way, not from clusters in one), lights-&lt;n&gt; (n small coloured lamps going round: over the field in the night instead of its torches, through the room in the interior beside its lamps) and show-clusters (how many lights each cluster has, over the picture); feet, which looks at the outdoor units' feet from close by; buffers, which draws the scene buffers for nothing to read, to price them; show-normals,
 * show-depth, show-roughness, show-velocity, which show one instead of the picture; show-ao, which shows the ambient
 * occlusion alone, and ao-radius-&lt;m&gt;, which sets its reach in world units; show-bloom, which shows the bloom alone; auto-exposure, which lets the eye adapt, and walk, which (with it) goes from a
 * dark view to a bright one and back, logging what the eye holds (EYE lines) and shooting at the cuts; fog (height fog and the
 * sun's shafts), fog-&lt;density&gt; (its density at height 0, per metre) and no-shafts; no-ssr, show-ssr (how much of the reflection the screen gives, alone) and wet (the floor of materials and outdoor made glossy); move-camera, move-units, fast (them at up to 25 times the speed, to and fro);
 * dof and focus-&lt;m&gt; (a lens in focus, on the thing each scene is about or that far away), motion-blur (the shutter half open: with move-camera or move-units), lut-&lt;name&gt; (a grade
 * from Common/Luts), vignette, and cinematic, which is all four; head and parallax (materials-plus from close to the bust, or low over the brick floor) no-layers (its balls and bricks without the coat, sheen, scatter and height map, to price them) and fill (with coat, cloth, skin, bricks, pom or all: 16 layers of one material over every pixel, for the cost of a layer per pixel; run it with the chain left out);
 * {@code hour-<h>}, which turns the atmosphere on with the sun where a made-up summer day has it at that hour, 6 and 18
 * being just over the horizon; sky-view, which looks toward the sun from near the ground, and shaft-view, which does so from behind the scene, so that what stands in it breaks the light; move-sun, which lets the sun
 * walk 0.15 degrees a frame and so prices the sky made anew); in the scene water, a lake with a sloping bottom, boxes about 0.6, 3.4 and 5 metres down, a pier, a pillar and a buoy, the words low (the camera at the water's level, looking across), glint (the same with the sun low over the far shore), over (the camera over the lake, which fills most of the picture, for pricing), pair (the waves still, and a second shot a frame after the first, to tell what flickers), shore, prop (close on a pillar and its reflection) and no-water (the lake with no water over it, to price it), output folder. {@code -Dbench.models=<folder>} dresses the scenes
 * in real models ({@code <folder>/<name>/out/<name>.glb}); without it they are made of boxes. With an atmosphere the
 * CSV line has two more columns: the last sky's painting (off the frame) and probe baking (on it), in milliseconds.
 */
public final class RenderBench extends SimpleApplication {

    private static final int WARMUP = 120;
    private static final int MEASURED = 240;
    /** The density of the fog at height 0 that the word {@code fog} asks for: a light morning haze, 5% of the light through 750 metres. */
    private static final float FOG = 0.004f;
    /** The share of a frame the shutter is open for the word {@code motion-blur}: the film's 180 degrees. */
    private static final float SHUTTER = 0.5f;
    /** The strength of the vignette of the word {@code vignette}. */
    private static final float VIGNETTE = 0.4f;

    private final String scene;
    private final String label;
    private PictureSettings picture;
    private final Path out;
    private final String models = System.getProperty("bench.models");
    private final Map<String, Spatial> loaded = new HashMap<>();
    private final List<Float> frames = new ArrayList<>();
    private final Vector3f sunDirection = new Vector3f();
    private final List<String> asked;
    private final DirectionalLight sunLight = new DirectionalLight();
    private final DirectionalLight shadowLight = new DirectionalLight();
    private ColorRGBA sunAbove;
    private ColorRGBA fill;
    private ColorRGBA ambientNow;
    private HeightFogFilter fog;
    private LocalShadowFilter localFilter;
    private ClusteredLights clusters;
    private final List<PointLight> swarm = new ArrayList<>();
    private final List<Vector3f> swarmAt = new ArrayList<>();
    private double sortMillis;
    private float circle;
    private SunShadowFilter shadowsFilter;
    private AtmosphereSky atmosphere;
    private float hour;
    private final List<Spatial> movers = new ArrayList<>();
    private Texture2D noise;
    private ScreenshotAppState shots;
    private int frame;
    private AcesToneMap tone;
    private Geometry dark;
    private float clock;
    private float lastLogged;
    private int walkStep;
    private Water.Waves waves;
    private int[] timers;
    private long submitted;
    private final List<Double> submits = new ArrayList<>();
    private final List<Double> gpus = new ArrayList<>();
    private final List<Integer> calls = new ArrayList<>();
    private final List<Integer> drawnTriangles = new ArrayList<>();

    private RenderBench(String scene, String label, Path out) {
        this.scene = scene;
        this.label = label;
        this.picture = settings(scene, label);
        this.asked = words(label);
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        var out = Path.of(args[2]);
        Files.createDirectories(out);
        var settings = new AppSettings(true);
        settings.setResolution(1920, 1080);
        settings.setVSync(false);
        settings.setFrameRate(-1);
        settings.setSamples(0);
        settings.setGammaCorrection(true);
        settings.setTitle("render-bench");
        var app = new RenderBench(args[0], args[1], out);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.start();
    }

    /** The words of a label, {@code cinematic} (a lens in focus, a shutter, the warm grade and a vignette) spelled out. */
    static List<String> words(String label) {
        var words = new ArrayList<>(List.of(label.split(",")));
        if (words.remove("cinematic")) {
            words.addAll(List.of("dof", "motion-blur", "lut-warm-teal", "vignette"));
        }
        return words;
    }

    static PictureSettings settings(String scene, String label) {
        var full = PictureSettings.FULL;
        var left = words(label);
        float fogDensity = left.stream().filter(word -> word.startsWith("fog-")).map(word -> Float.valueOf(word.substring(4)))
                .findFirst().orElse(left.contains("fog") ? FOG : full.fogDensity());
        return new PictureSettings(full.ambientOcclusion() && !left.contains("no-ao"),
                full.bloom() && !left.contains("no-bloom"), antiAliasing(left, full.antiAliasing()), full.exposure(),
                hourOf(left) != null, full.softShadows() && !left.contains("no-pcss"),
                full.contactShadows() && !left.contains("no-contact"), localBudget(left, full.localShadows()),
                left.stream().filter(word -> word.startsWith("ao-radius-")).map(word -> Float.valueOf(word.substring(10)))
                        .findFirst().orElse(full.occlusionRadius()),
                left.contains("auto-exposure") || left.contains("walk"), fogDensity, full.fogFalloff(),
                full.shafts() && !left.contains("no-shafts"), full.reflections() && !left.contains("no-ssr"),
                lens(scene, left), left.stream().filter(word -> word.startsWith("lut-")).map(word -> "Common/Luts/" + word.substring(4) + ".png")
                        .findFirst().orElse(null), left.contains("vignette") ? VIGNETTE : 0f, full.clusteredLights() && !left.contains("no-clusters"),
                full.screenSpaceGi() && !left.contains("no-ssgi"), scene.equals("water") && !left.contains("no-water"),
                full.autoInstancing() && !left.contains("no-instancing"), full.autoLod() && !left.contains("no-lod"));
    }

    /**
     * The words {@code dof} (an 85 mm f/1.2 lens in the interior, a 600 mm f/1.0 in the scenes that are tens of metres across), {@code focus-<m>} (its
     * distance; without it the focus is put on the thing each scene is about, see {@link #focusOn}) and {@code motion-blur}.
     */
    static Lens lens(String scene, List<String> words) {
        var lens = Lens.OFF;
        var focus = words.stream().filter(word -> word.startsWith("focus-")).map(word -> Float.valueOf(word.substring(6))).findFirst();
        if (words.contains("dof") || focus.isPresent()) {
            lens = new Lens(focus.orElse(5f), scene.equals("interior") ? 1.2f : 1.0f, scene.equals("interior") ? 85f : 600f, 0f);
        }
        return words.contains("motion-blur") ? lens.withShutter(SHUTTER) : lens;
    }

    /** The word {@code no-aa}, {@code fxaa} or {@code taa}, or the default. */
    static AntiAliasing antiAliasing(List<String> words, AntiAliasing fallback) {
        if (words.contains("no-aa")) {
            return AntiAliasing.NONE;
        }
        return words.contains("fxaa") ? AntiAliasing.FXAA : words.contains("taa") ? AntiAliasing.TAA : fallback;
    }

    /** How many local lights may have a shadow: {@code no-local-shadows} none, {@code local-<n>} that many, else the default. */
    static int localBudget(List<String> words, int fallback) {
        if (words.contains("no-local-shadows")) {
            return 0;
        }
        return words.stream().filter(word -> word.startsWith("local-")).map(word -> Integer.valueOf(word.substring(6)))
                .findFirst().orElse(fallback);
    }

    /** The hour a {@code hour-<h>} word asks for, or null. */
    static Float hourOf(List<String> words) {
        return words.stream().filter(word -> word.startsWith("hour-")).map(word -> Float.valueOf(word.substring(5)))
                .findFirst().orElse(null);
    }

    /** Toward the sun at {@code hour} of a made-up summer day: 71 degrees up at noon, 8 over the horizon at 6 and 18. */
    static Vector3f sunAt(float hour) {
        float turn = (hour - 6f) / 12f * FastMath.PI;
        return new Vector3f(FastMath.cos(turn), FastMath.sin(turn) * 0.9f + 0.15f, 0.35f).normalizeLocal();
    }

    @Override
    public void simpleInitApp() {
        setDisplayStatView(false);
        setDisplayFps(false);
        flyCam.setEnabled(false);
        if (models != null) {
            assetManager.registerLocator(models, FileLocator.class);
        }
        if (scene.equals("furnace")) {
            furnace();
            return;
        }
        var world = new Node("world");
        var sun = ColorRGBA.White;
        var ambient = new ColorRGBA(0.5f, 0.55f, 0.65f, 1f);
        switch (scene) {
            case "materials" -> {
                sunDirection.set(-0.45f, -0.75f, -0.48f);
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2f);
                materials(world);
            }
            case "materials-plus" -> {
                sunDirection.set(-0.45f, -0.75f, -0.48f);
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2f);
                if (asked.contains("fill")) {
                    fill(world);
                } else {
                    materialsPlus(world);
                }
            }
            case "interior" -> {
                sunDirection.set(-0.3f, -0.33f, -0.9f);
                sun = new ColorRGBA(1f, 0.93f, 0.82f, 1f).mult(1.5f);
                interior(world);
                lightsAsked().ifPresent(n -> swarm(n, new Vector3f(0f, 0.1f, 0f), new Vector3f(13f, 3f, 9.6f), 1.5f));
            }
            case "bleed" -> {
                sunDirection.set(-0.45f, -0.75f, -0.48f);
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2f);
                bleed(world);
            }
            case "outdoor" -> {
                sunDirection.set(-0.45f, -0.75f, -0.48f);
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2.4f);
                outdoor(world);
            }
            case "water" -> {
                // With glint the sun stands low over the far shore, so that the camera looks into its path on the water.
                sunDirection.set(asked.contains("glint") ? new Vector3f(-0.15f, -0.3f, 0.94f) : new Vector3f(-0.45f, -0.75f, -0.48f));
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2.4f);
                lake(world);
            }
            case "buffers" -> {
                sunDirection.set(-0.45f, -0.75f, -0.48f);
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2.4f);
                bufferCases(world);
            }
            case "forest" -> {
                sunDirection.set(-0.45f, -0.75f, -0.48f);
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2.4f);
                forest(world);
            }
            case "night" -> {
                sunDirection.set(0.3f, -0.8f, 0.4f);
                sun = new ColorRGBA(0.45f, 0.55f, 0.8f, 1f).mult(0.12f);
                ambient = new ColorRGBA(0.03f, 0.04f, 0.08f, 1f);
                outdoor(world);
                var many = lightsAsked();
                if (many.isPresent()) {
                    swarm(many.get(), new Vector3f(0f, 1.1f, 3f), new Vector3f(44f, 1f, 42f), 4f);
                } else {
                    torches();
                }
            }
            default -> throw new IllegalArgumentException("no scene called " + scene);
        }
        var atHour = hourOf(asked);
        if (atHour != null) {
            sun = ColorRGBA.White.mult(sun.r); // above the air the sun is white
            hour = atHour;
            sunDirection.set(sunAt(hour).negateLocal());
        }
        sunDirection.normalizeLocal();
        // What lights a surface but the local lights: the ambient, and the sun at about half its strength.
        fill = ambient.add(sun.mult(0.5f));
        if (asked.contains("lamp")) {
            // One lamp over any scene, for the casters of that scene to throw a shadow from (the buffers scene has instanced ones).
            rootNode.addLight(new PointLight(new Vector3f(-3f, 6f, 7f), new ColorRGBA(1f, 0.7f, 0.4f, 1f).mult(14f), 22f));
        }
        world.setShadowMode(ShadowMode.CastAndReceive);
        rootNode.attachChild(world);
        sunAbove = sun;
        var toSun = sunDirection.negate();
        if (atHour != null) {
            atmosphere = new AtmosphereSky(assetManager, renderManager, rootNode);
            atmosphere.follow(toSun, sun);
            rootNode.attachChild(atmosphere.sky());
            sunLight.setDirection(sunDirection);
            sunLight.setColor(atTheGround(toSun));
            rootNode.addLight(sunLight);
        } else {
            rootNode.addLight(new DirectionalLight(sunDirection, sun));
            rootNode.addLight(SkyProbe.bake(assetManager, renderManager, toSun, sun, ambient));
            var sky = SkyProbe.sky(assetManager, toSun, sun, ambient);
            sky.setShadowMode(ShadowMode.Off);
            rootNode.attachChild(sky);
        }
        ambientNow = ambient;
        if (asked.contains("room-probe") && (scene.equals("interior") || scene.equals("materials"))) {
            // The room's inside: its floor top, its ceiling underside and its walls' inner faces are the box's faces. Over the balls, a box
            // that some of them stand in, some out of and some on the edge of.
            var room = scene.equals("interior") ? LocalProbes.box(new Vector3f(0f, 0.2f, 0f), new Vector3f(7f, 1.8f, 5f))
                    : LocalProbes.box(new Vector3f(0f, 0f, 0f), new Vector3f(6f, 4f, 3f));
            double ms = LocalProbes.bake(assetManager, renderManager, rootNode, room);
            rootNode.addLight(room);
            System.out.printf(Locale.ROOT, "PROBE room baked in %.1f ms%n", ms);
        }
        if (asked.contains("sky-view") || asked.contains("shaft-view")) {
            lookAtTheSun(toSun, asked.contains("shaft-view") ? world : null);
        }
        filters();
        renderer.getStatistics().setEnabled(true);
        try {
            timers = renderer.generateProfilingTasks(4);
        } catch (RuntimeException e) {
            timers = null;
        }
        shots = new ScreenshotAppState(out.toString().replace('\\', '/') + "/", scene + "_" + label.replace(',', '+'));
        shots.setIsNumbered(false);
        stateManager.attach(shots);
    }

    /** The sun's colour once the air has had its share, which is what the light on the ground has. */
    private ColorRGBA atTheGround(Vector3f toSun) {
        var through = Atmosphere.transmittance(toSun);
        return new ColorRGBA(sunAbove.r * through.r, sunAbove.g * through.g, sunAbove.b * through.b, 1f);
    }

    /**
     * The camera on the ground and turned toward the sun: as high as it stands, up to 25 degrees. From the edge of the
     * scene, or, with {@code throughTheScene}, from behind its middle and with a row of columns for the light to pass through.
     */
    private void lookAtTheSun(Vector3f toSun, Node throughTheScene) {
        var flat = new Vector3f(toSun.x, 0f, toSun.z);
        flat = flat.lengthSquared() < 1e-6f ? new Vector3f(0f, 0f, -1f) : flat.normalizeLocal();
        float pitch = FastMath.clamp(FastMath.asin(toSun.y), 4f * FastMath.DEG_TO_RAD, 25f * FastMath.DEG_TO_RAD);
        cam.setFrustumPerspective(60f, (float) cam.getWidth() / cam.getHeight(), 0.3f, 400f);
        cam.setLocation(throughTheScene != null ? new Vector3f(0f, 2.5f, 3f).subtractLocal(flat.mult(42f)) : new Vector3f(0f, 2.5f, 30f));
        if (throughTheScene != null) {
            colonnade(throughTheScene, flat);
        }
        cam.lookAt(cam.getLocation().add(flat.mult(100f)).add(0f, 100f * FastMath.tan(pitch), 0f), Vector3f.UNIT_Y);
    }

    /**
     * What a white matte surface takes from a baked probe, against the same sky integrated by hand. It says FAIL when
     * they part by more than 10%: three bands of harmonics are a blur, but no further off than that. With an
     * {@code hour-<h>} word the sky is the atmosphere under that hour's sun, which has a far steeper horizon.
     */
    private void furnace() {
        var atHour = hourOf(asked);
        var toSun = atHour != null ? sunAt(atHour) : new Vector3f(0.4f, 0.8f, 0.45f).normalizeLocal();
        var ambient = new ColorRGBA(0.5f, 0.55f, 0.65f, 1f);
        SkyProbe.Sky sky = atHour != null ? new Atmosphere(toSun, ColorRGBA.White.mult(2.4f))
                : SkyProbe.gradient(toSun, ColorRGBA.Black, ambient);
        var probe = SkyProbe.bake(assetManager, renderManager, SkyProbe.paint(sky, 64));
        var sh = probe.getShCoeffs();
        var normals = new Vector3f[] {Vector3f.UNIT_Y, Vector3f.UNIT_Y.negate(), Vector3f.UNIT_X, Vector3f.UNIT_Z.negate()};
        for (var n : normals) {
            float baked = sh[0].x + sh[1].x * n.y + sh[2].x * n.z + sh[3].x * n.x + sh[4].x * n.y * n.x
                    + sh[5].x * n.y * n.z + sh[6].x * (3f * n.z * n.z - 1f) + sh[7].x * n.z * n.x
                    + sh[8].x * (n.x * n.x - n.y * n.y);
            float true_ = irradiance(n, sky);
            boolean fine = Math.abs(baked - true_) <= 0.1f * true_;
            System.out.printf(Locale.ROOT, "RESULT furnace n=%s baked %.3f true %.3f %s%n", n, baked, true_, fine ? "ok" : "FAIL");
        }
        furnaceSpheres(SkyProbe.bake(assetManager, renderManager, SkyProbe.paint(d -> ColorRGBA.White, 64)));
    }

    private final List<Runnable> furnaceReads = new ArrayList<>();

    /**
     * White spheres under a sky of 1 and no other light, drawn into half float targets: what each layer of PBRLighting may
     * take from the base and put back. The plain sphere is the bound, not 1: its diffuse and its specular are not
     * traded against each other, so it reads a little over 1 at the rim already; a layer must not read over it.
     */
    private void furnaceSpheres(LightProbe sky) {
        record Case(String name, Consumer<Material> layer) {}
        var cases = List.of(new Case("plain", m -> {}),
                new Case("coat-0.05", m -> { m.setFloat("ClearCoat", 1f); m.setFloat("ClearCoatRoughness", 0.05f); }),
                // A flat map of its own: the same picture as coat-0.05, which says the map's path (tangents, sampling) works.
                new Case("coat-flat-normal-map", m -> { m.setFloat("ClearCoat", 1f); m.setFloat("ClearCoatRoughness", 0.05f);
                    m.setTexture("ClearCoatNormalMap", picture(Image.Format.RGB8, ColorSpace.Linear, (x, y) -> new int[] {128, 128, 255})); }),
                new Case("coat-0.5-rough-base", m -> { m.setFloat("Roughness", 0.8f); m.setFloat("ClearCoat", 1f); m.setFloat("ClearCoatRoughness", 0.5f); }),
                new Case("sheen-0.3", m -> { m.setColor("SheenColor", ColorRGBA.White); m.setFloat("SheenRoughness", 0.3f); }),
                new Case("sheen-1.0", m -> { m.setColor("SheenColor", ColorRGBA.White); m.setFloat("SheenRoughness", 1f); }),
                new Case("sheen+coat", m -> { m.setColor("SheenColor", ColorRGBA.White); m.setFloat("ClearCoat", 1f); }),
                new Case("skin", m -> m.setColor("Subsurface", new ColorRGBA(0.7f, 0.25f, 0.1f, 1f))));
        var results = new HashMap<String, float[]>();
        for (var one : cases) {
            var material = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
            material.setColor("BaseColor", ColorRGBA.White);
            material.setFloat("Metallic", 0f);
            material.setFloat("Roughness", 0.4f);
            one.layer().accept(material);
            var ball = new Geometry("ball", new Sphere(48, 48, 1f));
            ball.setMaterial(material);
            MikktspaceTangentGenerator.generate(ball);
            var root = new Node("furnace " + one.name());
            root.attachChild(ball);
            root.addLight(sky);
            root.updateGeometricState();
            var camera = new Camera(96, 96);
            camera.setFrustumPerspective(30f, 1f, 1f, 20f);
            camera.setLocation(new Vector3f(0f, 0f, 5f));
            camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
            var target = new FrameBuffer(96, 96, 1);
            target.setDepthTarget(FrameBuffer.FrameBufferTarget.newTarget(Image.Format.Depth));
            target.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(Image.Format.RGBA16F));
            var view = renderManager.createPreView("furnace " + one.name(), camera);
            view.setClearFlags(true, true, true);
            view.setBackgroundColor(ColorRGBA.BlackNoAlpha);
            view.setOutputFrameBuffer(target);
            view.attachScene(root);
            furnaceReads.add(() -> {
                var bytes = BufferUtils.createByteBuffer(96 * 96 * 16);
                renderer.readFrameBufferWithFormat(target, bytes, Image.Format.RGBA32F);
                var floats = bytes.asFloatBuffer();
                float max = 0f;
                double sum = 0.0;
                int n = 0;
                for (int i = 0; i < 96 * 96; i++) {
                    if (floats.get(i * 4 + 3) > 0.5f) {
                        float v = Math.max(floats.get(i * 4), Math.max(floats.get(i * 4 + 1), floats.get(i * 4 + 2)));
                        max = Math.max(max, v);
                        sum += v;
                        n++;
                    }
                }
                results.put(one.name(), new float[] {max, (float) (sum / n)});
                float bound = results.get("plain")[0];
                boolean fine = one.name().equals("plain") || max <= bound + 0.01f;
                System.out.printf(Locale.ROOT, "RESULT furnace sphere %s max %.3f mean %.3f %s%n", one.name(), max, sum / n, fine ? "ok" : "FAIL");
            });
        }
    }

    /** The sky's red, cosine weighted over the half sphere round {@code n}, divided by pi. */
    private static float irradiance(Vector3f n, SkyProbe.Sky sky) {
        float sum = 0f;
        float weight = 0f;
        var random = new Random(1);
        var d = new Vector3f();
        for (int i = 0; i < 400000; i++) {
            d.set(random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f);
            float length = d.length();
            if (length > 1f || length < 1e-3f) {
                continue;
            }
            d.divideLocal(length);
            float cos = d.dot(n);
            if (cos > 0f) {
                sum += sky.at(d).r * cos;
                weight += cos;
            }
        }
        return sum / weight;
    }

    // ---- scenes ----

    /** Rows of balls, smooth at the left and rough at the right: gold, iron, red paint, white paint. */
    private void materials(Node world) {
        wall(world, surface(new ColorRGBA(0.25f, 0.25f, 0.25f, 1f), floorRoughness(0.8f), 0f), 0f, -1.1f, 0f, 16f, 0.1f, 16f, 8f);
        var rows = new ColorRGBA[] {new ColorRGBA(1f, 0.77f, 0.34f, 1f), new ColorRGBA(0.56f, 0.57f, 0.58f, 1f),
            new ColorRGBA(0.7f, 0.05f, 0.05f, 1f), new ColorRGBA(0.85f, 0.85f, 0.85f, 1f)};
        var mesh = new Sphere(48, 48, 1f);
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < 7; column++) {
                var ball = new Geometry("ball", mesh);
                var material = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
                material.setColor("BaseColor", rows[row]);
                material.setFloat("Metallic", row < 2 ? 1f : 0f);
                material.setFloat("Roughness", 0.04f + column * 0.16f);
                ball.setMaterial(material);
                ball.setLocalTranslation((column - 3f) * 2.4f, 0f, (row - 1.5f) * 2.4f);
                world.attachChild(ball);
            }
        }
        cam.setFrustumPerspective(35f, (float) cam.getWidth() / cam.getHeight(), 1f, 400f);
        cam.setLocation(new Vector3f(0f, 13f, 19f));
        cam.lookAt(new Vector3f(0f, -0.5f, 0f), Vector3f.UNIT_Y);
    }

    /**
     * A red wall in the sun, a white strip of floor and three white balls before it, grey floor round them: what is red on the
     * strip, and on the balls' sides toward the wall, is the wall's light, and the shade sides are lit by the strip.
     */
    private void bleed(Node world) {
        wall(world, surface(new ColorRGBA(0.25f, 0.25f, 0.25f, 1f), floorRoughness(0.8f), 0f), 0f, -1.1f, 0f, 16f, 0.1f, 16f, 8f);
        wall(world, surface(new ColorRGBA(0.75f, 0.05f, 0.04f, 1f), 0.9f, 0f), 0f, 3f, -6f, 16f, 4.1f, 0.2f, 4f);
        wall(world, surface(new ColorRGBA(0.85f, 0.85f, 0.85f, 1f), 0.9f, 0f), 0f, -1f, -2.5f, 8f, 0.02f, 3.3f, 4f);
        var mesh = new Sphere(48, 48, 1f);
        for (int i = 0; i < 3; i++) {
            world.attachChild(ballAt(mesh, ball(new ColorRGBA(0.85f, 0.85f, 0.85f, 1f), 0f, 0.7f), (i - 1) * 3f, -2.6f));
        }
        cam.setFrustumPerspective(40f, (float) cam.getWidth() / cam.getHeight(), 0.5f, 400f);
        cam.setLocation(new Vector3f(0f, 5f, 11f));
        cam.lookAt(new Vector3f(0f, 0f, -3f), Vector3f.UNIT_Y);
    }

    /**
     * The layers of PBRLighting on rows of balls, from the back: skin (and, with models, a bust) without and with scatter and
     * transmission, velvet at growing sheen roughness, red car paint under a coat of growing roughness, and two patches of brick floor, flat
     * on the left and with parallax on the right. The first ball of each row is the same without the layer. A weak light from behind lights
     * the rims. The words {@code head} and {@code parallax} move the camera close to the bust or low over the bricks.
     */
    private void materialsPlus(Node world) {
        wall(world, surface(new ColorRGBA(0.25f, 0.25f, 0.25f, 1f), floorRoughness(0.8f), 0f), 0f, -1.1f, 0f, 16f, 0.1f, 16f, 8f);
        var mesh = new Sphere(48, 48, 1f);
        float[] backRow = {-3.6f, -1.2f, 1.2f};
        boolean layers = !asked.contains("no-layers");
        for (int column = 0; column < 7; column++) {
            float x = (column - 3f) * 2.4f;
            // skin
            if (column < 5) {
                var skin = ball(new ColorRGBA(0.5f, 0.3f, 0.23f, 1f), 0f, 0.5f);
                var scatter = new ColorRGBA[] {null, new ColorRGBA(0.4f, 0.12f, 0.05f, 1f), new ColorRGBA(0.7f, 0.25f, 0.1f, 1f),
                    new ColorRGBA(1f, 0.4f, 0.2f, 1f), new ColorRGBA(0.7f, 0.25f, 0.1f, 1f)}[column];
                if (scatter != null && layers) {
                    skin.setColor("Subsurface", scatter);
                    skin.setFloat("SubsurfaceTransmission", column == 4 ? 1f : 0f);
                }
                world.attachChild(ballAt(mesh, skin, x, backRow[0]));
            }
            // velvet
            var cloth = ball(new ColorRGBA(0.18f, 0.015f, 0.05f, 1f), 0f, 0.9f);
            if (column > 0 && layers) {
                cloth.setColor("SheenColor", new ColorRGBA(0.7f, 0.33f, 0.42f, 1f));
                cloth.setFloat("SheenRoughness", new float[] {0f, 0.2f, 0.3f, 0.4f, 0.55f, 0.75f, 1f}[column]);
            }
            world.attachChild(ballAt(mesh, cloth, x, backRow[1]));
            // car paint: a red metallic flake base under a clear coat
            var paint = ball(new ColorRGBA(0.55f, 0.02f, 0.03f, 1f), 0.5f, 0.45f);
            if (column > 0 && layers) {
                paint.setFloat("ClearCoat", 1f);
                paint.setFloat("ClearCoatRoughness", new float[] {0f, 0.05f, 0.1f, 0.2f, 0.3f, 0.45f, 0.6f}[column]);
            }
            world.attachChild(ballAt(mesh, paint, x, backRow[2]));
        }
        bricks(world, layers);
        if (models != null) {
            var bust = place(world, "old_woman", 12f, 9.6f, -10.4f, backRow[0], -15f);
            bust.depthFirstTraversal(s -> {
                if (layers && s instanceof Geometry g && g.getMaterial() != null && "Skin".equals(g.getMaterial().getName())) {
                    g.getMaterial().setColor("Subsurface", new ColorRGBA(0.7f, 0.25f, 0.1f, 1f));
                    g.getMaterial().setFloat("SubsurfaceTransmission", 0.6f);
                }
            });
        }
        rootNode.addLight(new DirectionalLight(new Vector3f(0.4f, -0.35f, 0.85f).normalizeLocal(), new ColorRGBA(0.9f, 0.95f, 1f, 1f).mult(1.2f)));
        cam.setFrustumPerspective(38f, (float) cam.getWidth() / cam.getHeight(), 0.3f, 400f);
        cam.setLocation(new Vector3f(1.2f, 10f, 15.5f));
        cam.lookAt(new Vector3f(1.2f, -0.5f, 0.8f), Vector3f.UNIT_Y);
        if (asked.contains("head")) {
            cam.setFrustumPerspective(25f, (float) cam.getWidth() / cam.getHeight(), 0.3f, 400f);
            cam.setLocation(new Vector3f(9f, 1.2f, 3f));
            cam.lookAt(new Vector3f(9.6f, 0.7f, backRow[0]), Vector3f.UNIT_Y);
        }
        if (asked.contains("parallax")) {
            cam.setLocation(new Vector3f(0f, 3.4f, 13f));
            cam.lookAt(new Vector3f(0f, -1f, 4f), Vector3f.UNIT_Y);
        }
    }

    /** 16 quads one over the other, each covering the whole picture with the material the words ask for: what a pixel of it costs is the rest of the frame's time over 16 pixels' worth. */
    private void fill(Node world) {
        var material = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
        material.setColor("BaseColor", new ColorRGBA(0.5f, 0.3f, 0.23f, 1f));
        material.setFloat("Metallic", 0f);
        material.setFloat("Roughness", 0.5f);
        boolean all = asked.contains("all");
        if (all || asked.contains("coat")) {
            material.setFloat("ClearCoat", 1f);
            material.setFloat("ClearCoatRoughness", 0.1f);
        }
        if (all || asked.contains("cloth")) {
            material.setColor("SheenColor", new ColorRGBA(0.7f, 0.33f, 0.42f, 1f));
            material.setFloat("SheenRoughness", 0.5f);
        }
        if (all || asked.contains("skin")) {
            material.setColor("Subsurface", new ColorRGBA(0.7f, 0.25f, 0.1f, 1f));
            material.setFloat("SubsurfaceTransmission", 0.5f);
        }
        if (all || asked.contains("bricks") || asked.contains("pom")) {
            material.setTexture("BaseColorMap", noise());
            material.setTexture("NormalMap", picture(Image.Format.RGB8, ColorSpace.Linear, (x, y) -> {
                float dx = (brickHeight(x + 1, y) - brickHeight(x - 1, y)) * 3f;
                float dy = (brickHeight(x, y + 1) - brickHeight(x, y - 1)) * 3f;
                float length = FastMath.sqrt(dx * dx + dy * dy + 1f);
                return new int[] {Math.round((-dx / length * 0.5f + 0.5f) * 255f), Math.round((dy / length * 0.5f + 0.5f) * 255f),
                    Math.round((1f / length * 0.5f + 0.5f) * 255f), 255};
            }));
            material.setFloat("NormalType", 1f);
        }
        if (all || asked.contains("pom")) {
            material.setTexture("ParallaxMap", picture(Image.Format.RGB8, ColorSpace.Linear, (x, y) -> {
                int v = Math.round(brickHeight(x, y) * 255f);
                return new int[] {v, v, v, 255};
            }));
            material.setBoolean("SteepParallax", true);
            material.setFloat("ParallaxHeight", 0.1f);
        }
        var quad = new Quad(60f, 60f);
        quad.scaleTextureCoordinates(new Vector2f(24f, 24f));
        for (int i = 0; i < 16; i++) {
            var layer = new Geometry("fill " + i, quad);
            layer.setMaterial(material);
            MikktspaceTangentGenerator.generate(layer);
            layer.rotate(-FastMath.DEG_TO_RAD * 60f, 0f, 0f);
            layer.setLocalTranslation(-30f, -32f, -5f);
            world.attachChild(layer);
        }
        cam.setFrustumPerspective(35f, (float) cam.getWidth() / cam.getHeight(), 1f, 400f);
        cam.setLocation(new Vector3f(0f, 8f, 10f));
        cam.lookAt(new Vector3f(0f, -1f, 0f), Vector3f.UNIT_Y);
    }

    private Material ball(ColorRGBA colour, float metallic, float roughness) {
        var material = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
        material.setColor("BaseColor", colour);
        material.setFloat("Metallic", metallic);
        material.setFloat("Roughness", roughness);
        return material;
    }

    private static Geometry ballAt(Sphere mesh, Material material, float x, float z) {
        var ball = new Geometry("ball", mesh);
        ball.setMaterial(material);
        ball.setLocalTranslation(x, 0f, z);
        return ball;
    }

    /** Brick floor patches in front of the rows: the left plain (colour and normal map), the right with the height map marched. */
    private void bricks(Node world, boolean parallax) {
        var colour = picture(Image.Format.RGB8, ColorSpace.sRGB, (x, y) -> {
            float h = brickHeight(x, y);
            float tone = 0.75f + 0.25f * hash(x / 4, y / 4);
            return h < 0.2f ? new int[] {110, 108, 100} : new int[] {Math.round(150 * tone), Math.round(70 * tone), Math.round(52 * tone)};
        });
        var normals = picture(Image.Format.RGB8, ColorSpace.Linear, (x, y) -> {
            float dx = (brickHeight(x + 1, y) - brickHeight(x - 1, y)) * 3f;
            float dy = (brickHeight(x, y + 1) - brickHeight(x, y - 1)) * 3f;
            float length = FastMath.sqrt(dx * dx + dy * dy + 1f);
            // Image rows run down the quad's v axis, so the slope in y is flipped (OpenGL style map).
            return new int[] {Math.round((-dx / length * 0.5f + 0.5f) * 255f), Math.round((dy / length * 0.5f + 0.5f) * 255f),
                Math.round((1f / length * 0.5f + 0.5f) * 255f), 255};
        });
        var heights = picture(Image.Format.RGB8, ColorSpace.Linear, (x, y) -> {
            int v = Math.round(brickHeight(x, y) * 255f);
            return new int[] {v, v, v, 255};
        });
        for (int side = 0; side < 2; side++) {
            var quad = new Quad(8f, 3.2f);
            quad.scaleTextureCoordinates(new Vector2f(4f, 1.6f));
            var patch = new Geometry(side == 0 ? "flat bricks" : "parallax bricks", quad);
            var material = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
            material.setTexture("BaseColorMap", colour);
            material.setTexture("NormalMap", normals);
            material.setFloat("NormalType", 1f);
            material.setFloat("Metallic", 0f);
            material.setFloat("Roughness", 0.8f);
            if (side == 1 && parallax) {
                material.setTexture("ParallaxMap", heights);
                material.setBoolean("SteepParallax", true);
                material.setFloat("ParallaxHeight", 0.1f);
            }
            patch.setMaterial(material);
            MikktspaceTangentGenerator.generate(patch);
            patch.rotate(-FastMath.HALF_PI, 0f, 0f);
            patch.setLocalTranslation(side == 0 ? -8.2f : 0.2f, -0.97f, 5.7f);
            world.attachChild(patch);
        }
    }

    /** A running bond of bricks 64 by 32 in a 256 square, with 3 pixels of mortar, rounded at the edges; 0 to 1. */
    private static float brickHeight(int x, int y) {
        x = Math.floorMod(x, 256);
        y = Math.floorMod(y, 256);
        int row = y / 32;
        int shifted = Math.floorMod(x + (row % 2 == 1 ? 32 : 0), 256);
        int across = shifted % 64;
        int down = y % 32;
        int edge = Math.min(Math.min(across, 63 - across), Math.min(down, 31 - down));
        if (edge < 3) {
            return 0.05f;
        }
        float round = Math.min(1f, (edge - 3) / 4f);
        return 0.35f + 0.5f * round + 0.12f * hash(shifted / 64 + row * 7, row);
    }

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535f;
    }

    /** A workshop lit through one big window with one lamp: what a first-person game looks at from arm's length. */
    private void interior(Node world) {
        var room = new Node("room");
        room.setLocalTranslation(0f, -1.6f, 0f);
        world.attachChild(room);
        float w = 7f, d = 5f, h = 3.6f, t = 0.3f;
        var concrete = surface(new ColorRGBA(0.42f, 0.41f, 0.39f, 1f), 0.9f, 0f);
        var plaster = surface(new ColorRGBA(0.62f, 0.6f, 0.56f, 1f), 0.85f, 0f);
        wall(room, concrete, 0f, -t, 0f, w, t, d, 2f);
        wall(room, plaster, 0f, h + t, 0f, w, t, d, 2f);
        wall(room, plaster, -w - t, h / 2f, 0f, t, h / 2f + t, d, 2f);
        wall(room, plaster, 0f, h / 2f, -d - t, w + 2f * t, h / 2f + t, t, 2f);
        wall(room, plaster, 0f, 0.45f, d + t, w + 2f * t, 0.45f, t, 2f);
        wall(room, plaster, 0f, h - 0.3f, d + t, w + 2f * t, 0.3f, t, 2f);
        wall(room, plaster, -w + 1.2f, h / 2f, d + t, 1.2f + 2f * t, h / 2f, t, 2f);
        wall(room, plaster, w - 1.2f, h / 2f, d + t, 1.2f + 2f * t, h / 2f, t, 2f);
        wall(room, plaster, w + t, h / 2f, 0f, t, h / 2f + t, d, 2f);
        var table = place(room, "metal_table", 2f, -0.2f, 0f, -1.6f, 8f);
        room.updateGeometricState();
        var box = (BoundingBox) table.getWorldBound();
        float top = box.getCenter().y + box.getYExtent() + 1.6f;
        place(room, "desktop_computer", 0.6f, -0.65f, top, -1.75f, 12f);
        place(room, "drill", 0.3f, 0.3f, top, -1.35f, 70f);
        place(room, "material_shelf", 2.6f, -4.5f, 0f, -3.8f, 0f);
        place(room, "cardboard_box", 0.5f, 1.9f, 0f, -2.2f, 15f);
        place(room, "cardboard_box", 0.5f, 2.15f, 0f, -1.55f, -25f);
        place(room, "rubber_roll", 1f, -2.6f, 0f, -1.2f, 0f);
        place(room, "rebar_bundle", 3f, 3.4f, 0.1f, -3.4f, 90f);
        rootNode.addLight(new PointLight(new Vector3f(-1f, 1.6f, -1f), new ColorRGBA(1f, 0.82f, 0.6f, 1f).mult(10f), 9f));
        // A second lamp, a spot lamp over the table, and one outside the back wall that the wall must keep out.
        rootNode.addLight(new PointLight(new Vector3f(4f, 1.5f, -2.5f), new ColorRGBA(0.7f, 0.85f, 1f, 1f).mult(6f), 7f));
        var spot = new SpotLight(new Vector3f(-0.2f, 1.9f, -0.4f), new Vector3f(0f, -1f, -0.35f).normalizeLocal(),
                new ColorRGBA(1f, 0.9f, 0.7f, 1f).mult(12f));
        spot.setSpotRange(6f);
        spot.setSpotInnerAngle(22f * FastMath.DEG_TO_RAD);
        spot.setSpotOuterAngle(36f * FastMath.DEG_TO_RAD);
        rootNode.addLight(spot);
        rootNode.addLight(new PointLight(new Vector3f(-2f, 1f, -7.5f), new ColorRGBA(1f, 0.3f, 0.2f, 1f).mult(40f), 10f));
        cam.setFrustumPerspective(70f, (float) cam.getWidth() / cam.getHeight(), 0.05f, 200f);
        cam.setLocation(new Vector3f(0.35f, 0.02f, 0.75f));
        cam.lookAt(new Vector3f(-0.2f, -0.55f, -1.6f), Vector3f.UNIT_Y);
    }

    /** A field from high up: animated people and a few buildings. */
    private void outdoor(Node world) {
        wall(world, surface(new ColorRGBA(0.26f, 0.31f, 0.17f, 1f), floorRoughness(0.95f), 0f), 0f, -0.5f, 0f, 80f, 0.5f, 80f, 40f);
        var kinds = new String[] {"archer", "brute", "commander"};
        int n = 12;
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                var unit = place(world, kinds[(x + z) % 3], 1.8f, (x - n / 2f) * 1.9f, 0f, (z - n / 2f) * 1.9f + 3f, 180f);
                animate(unit);
                movers.add(unit);
            }
        }
        place(world, "house_01", 8f, -22f, 0f, -12f, 30f);
        place(world, "house_01", 8f, 22f, 0f, -14f, -40f);
        place(world, "house_01", 8f, -24f, 0f, 12f, 120f);
        place(world, "house_01", 8f, 24f, 0f, 14f, 200f);
        cam.setFrustumPerspective(35f, (float) cam.getWidth() / cam.getHeight(), 1f, 400f);
        cam.setLocation(new Vector3f(0f, 30f, 44f));
        cam.lookAt(new Vector3f(0f, 0f, 2f), Vector3f.UNIT_Y);
        if (asked.contains("feet")) {
            cam.setFrustumPerspective(35f, (float) cam.getWidth() / cam.getHeight(), 0.1f, 400f);
            cam.setLocation(new Vector3f(1.6f, 1.3f, 17f));
            cam.lookAt(new Vector3f(-0.2f, 0.45f, 12.6f), Vector3f.UNIT_Y);
        }
    }

    /** The word {@code forest-<n>}: how many props the forest has. */
    private int forestSize() {
        return asked.stream().filter(word -> word.startsWith("forest-")).map(word -> Integer.valueOf(word.substring(7))).findFirst().orElse(5000);
    }

    /**
     * A field of props, {@code forest-<n>} of them (5000 without the word): bins, computers and tables of the models (about 900 triangles each), on a grid of about
     * 2.3 metres turned and set off by chance, each its own clone of its model (so its own copy of the material), every twentieth going
     * to and fro with {@code move-units}.
     */
    private void forest(Node world) {
        int n = forestSize();
        wall(world, surface(new ColorRGBA(0.26f, 0.31f, 0.17f, 1f), floorRoughness(0.95f), 0f), 0f, -0.5f, 0f, 90f, 0.5f, 90f, 40f);
        var kinds = new String[] {"trash_can", "desktop_computer", "metal_table"};
        var random = new Random(41);
        int side = (int) Math.ceil(Math.sqrt(n));
        for (int i = 0; i < n; i++) {
            float x = (i % side - side / 2f) * 2.3f + (random.nextFloat() - 0.5f) * 0.8f;
            float z = (i / side - side / 2f) * 2.3f + (random.nextFloat() - 0.5f) * 0.8f;
            var prop = place(world, kinds[random.nextInt(kinds.length)], 1.2f + random.nextFloat() * 0.4f, x, 0f, z, random.nextFloat() * 360f);
            if (i % 20 == 0) {
                movers.add(prop);
            }
        }
        float reach = side * 1.15f;
        cam.setFrustumPerspective(35f, (float) cam.getWidth() / cam.getHeight(), 1f, 600f);
        cam.setLocation(new Vector3f(0f, reach * 1.1f, reach * 1.6f));
        cam.lookAt(new Vector3f(0f, 0f, reach * 0.1f), Vector3f.UNIT_Y);
    }

    /** Tall columns across the way to the sun, 30 metres from the camera: the light between them falls through the air as shafts. */
    private void colonnade(Node to, Vector3f flat) {
        var stone = surface(new ColorRGBA(0.5f, 0.47f, 0.42f, 1f), 0.9f, 0f);
        var across = new Vector3f(-flat.z, 0f, flat.x);
        for (int i = -5; i <= 5; i++) {
            var at = new Vector3f(0f, 0f, 3f).subtractLocal(flat.mult(12f)).addLocal(across.mult(i * 2.6f));
            wall(to, stone, at.x, 9f, at.z, 0.45f, 9f, 0.45f, 3f);
        }
    }

    private static void animate(Spatial unit) {
        unit.depthFirstTraversal(s -> {
            var anim = s.getControl(AnimComposer.class);
            if (anim != null && !anim.getAnimClipsNames().isEmpty()) {
                anim.setCurrentAction(anim.getAnimClipsNames().iterator().next());
            }
        });
    }

    /** The ground of the lake at x, z: a basin about 52 by 36 metres across, deepest at the far end, a beach round it. */
    private static float lakeGround(float x, float z) {
        float r = FastMath.sqrt((x / 26f) * (x / 26f) + ((z - 2f) / 18f) * ((z - 2f) / 18f));
        if (r >= 1f) {
            return (r - 1f) * 3f;
        }
        float deepest = 7f * FastMath.clamp(0.3f + 0.7f * (0.5f - (z - 2f) / 36f), 0.15f, 1f);
        float t = FastMath.clamp((1f - r) / 0.6f, 0f, 1f);
        // A slope from the shore, easing into the bottom.
        return -deepest * (0.5f * t + 0.5f * t * t * (3f - 2f * t));
    }

    /**
     * A lake: sand shaped as a basin that slopes from a shore at the camera's end to six metres at the far end, the water over it at
     * height 0, and in and about it a pier, a pillar, a buoy, rocks, a row of boxes on the bottom 0.6, 3.4 and 5 metres down, a house and
     * people on the far shore. The words {@code low}, {@code shore} and {@code prop} move the camera; {@code no-water} leaves the
     * water out.
     */
    private void lake(Node world) {
        int n = 320;
        float size = 160f;
        var positions = BufferUtils.createFloatBuffer((n + 1) * (n + 1) * 3);
        var normals = BufferUtils.createFloatBuffer((n + 1) * (n + 1) * 3);
        var uvs = BufferUtils.createFloatBuffer((n + 1) * (n + 1) * 2);
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i <= n; i++) {
                float x = (i / (float) n - 0.5f) * size;
                float z = (j / (float) n - 0.5f) * size;
                positions.put(x).put(lakeGround(x, z)).put(z);
                float dx = lakeGround(x + 0.2f, z) - lakeGround(x - 0.2f, z);
                float dz = lakeGround(x, z + 0.2f) - lakeGround(x, z - 0.2f);
                var normal = new Vector3f(-dx / 0.4f, 1f, -dz / 0.4f).normalizeLocal();
                normals.put(normal.x).put(normal.y).put(normal.z);
                uvs.put(x / 6f).put(z / 6f);
            }
        }
        var indices = BufferUtils.createIntBuffer(n * n * 6);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int a = j * (n + 1) + i;
                indices.put(a).put(a + n + 1).put(a + n + 2).put(a).put(a + n + 2).put(a + 1);
            }
        }
        var mesh = new com.jme3.scene.Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        var ground = new Geometry("lake ground", mesh);
        ground.setMaterial(surface(new ColorRGBA(0.62f, 0.52f, 0.36f, 1f), 0.9f, 0f));
        world.attachChild(ground);

        // The boxes on the bottom, at 1, 3 and 5 metres down, in colours that tell how much of each the water keeps.
        var colours = new ColorRGBA[] {new ColorRGBA(0.8f, 0.1f, 0.08f, 1f), new ColorRGBA(0.9f, 0.9f, 0.9f, 1f), new ColorRGBA(0.9f, 0.75f, 0.1f, 1f)};
        float[] boxZ = {16f, 9f, -2f};
        float[] depths = {1f, 3f, 5f};
        for (int i = 0; i < 3; i++) {
            float z = boxZ[i];
            float x = 8f - i * 1.5f;
            float down = lakeGround(x, z);
            wall(world, surface(colours[i], 0.7f, 0f), x, down + 0.5f, z, 0.5f, 0.5f, 0.5f, 1f);
            System.out.printf(Locale.ROOT, "LAKE box %d at %.1f m down (asked %.0f)%n", i, -down, depths[i]);
        }
        // A pier on posts, running out from the near shore.
        for (int i = 0; i < 5; i++) {
            float z = 20f - i * 3.5f;
            for (int side = -1; side <= 1; side += 2) {
                float x = -8f + side * 0.8f;
                float bottom = lakeGround(x, z);
                wall(world, surface(new ColorRGBA(0.35f, 0.24f, 0.14f, 1f), 0.8f, 0f), x, (bottom + 0.6f) / 2f, z, 0.12f, (0.6f - bottom) / 2f, 0.12f, 1f);
            }
        }
        wall(world, surface(new ColorRGBA(0.45f, 0.32f, 0.2f, 1f), 0.8f, 0f), -8f, 0.65f, 12f, 1.1f, 0.06f, 9f, 3f);
        // A pillar on the shore to the left, for its reflection; a red buoy and rocks that the water cuts across.
        wall(world, surface(new ColorRGBA(0.75f, 0.7f, 0.62f, 1f), 0.6f, 0f), -22f, 3f, 2f, 0.5f, 3f, 0.5f, 3f);
        var buoy = new Geometry("buoy", new Sphere(32, 32, 0.45f));
        buoy.setMaterial(ball(new ColorRGBA(0.85f, 0.08f, 0.05f, 1f), 0f, 0.4f));
        buoy.setLocalTranslation(5f, 0.1f, 4f);
        world.attachChild(buoy);
        var rock = surface(new ColorRGBA(0.4f, 0.4f, 0.42f, 1f), 0.85f, 0f);
        for (var at : new float[][] {{13f, 14f, 1.2f}, {15f, 11f, 0.8f}, {-14f, 17f, 1f}, {-1f, -14f, 1.4f}}) {
            var stone = new Geometry("rock", new Sphere(24, 24, at[2]));
            stone.setMaterial(rock);
            stone.setLocalTranslation(at[0], lakeGround(at[0], at[1]) + at[2] * 0.45f, at[1]);
            world.attachChild(stone);
        }
        place(world, "house_01", 9f, 8f, 0.5f, -23f, 20f);
        for (int i = 0; i < 4; i++) {
            animate(place(world, new String[] {"archer", "brute", "commander", "archer"}[i], 1.8f, 12f + i * 1.8f, lakeGround(12f + i * 1.8f, 24f) + 0.05f, 24f, 180f));
        }

        if (!asked.contains("no-water")) {
            var water = Water.surface(assetManager, 72f, 54f);
            water.setLocalTranslation(0f, 0f, 2f);
            var waves = water.getControl(Water.Waves.class);
            // By the frame, not the clock, like everything the bench moves.
            waves.setEnabled(false);
            this.waves = waves;
            rootNode.attachChild(water);
        }
        cam.setFrustumPerspective(40f, (float) cam.getWidth() / cam.getHeight(), 0.3f, 400f);
        cam.setLocation(new Vector3f(2f, 9f, 40f));
        cam.lookAt(new Vector3f(0f, -1f, -6f), Vector3f.UNIT_Y);
        if (asked.contains("low") || asked.contains("glint")) {
            cam.setFrustumPerspective(55f, (float) cam.getWidth() / cam.getHeight(), 0.2f, 400f);
            cam.setLocation(new Vector3f(3f, 1.1f, 25f));
            cam.lookAt(new Vector3f(-2f, 1.4f, -20f), Vector3f.UNIT_Y);
        }
        if (asked.contains("over")) {
            // Water over most of the picture, for pricing it.
            cam.setFrustumPerspective(70f, (float) cam.getWidth() / cam.getHeight(), 0.3f, 400f);
            cam.setLocation(new Vector3f(0f, 7f, 14f));
            cam.lookAt(new Vector3f(0f, -2f, -4f), Vector3f.UNIT_Y);
        }
        if (asked.contains("shore")) {
            cam.setFrustumPerspective(50f, (float) cam.getWidth() / cam.getHeight(), 0.1f, 400f);
            cam.setLocation(new Vector3f(5f, 1.6f, 25f));
            cam.lookAt(new Vector3f(10f, -0.8f, 14f), Vector3f.UNIT_Y);
        }
        if (asked.contains("prop")) {
            cam.setFrustumPerspective(45f, (float) cam.getWidth() / cam.getHeight(), 0.2f, 400f);
            cam.setLocation(new Vector3f(-12f, 1.4f, 17f));
            cam.lookAt(new Vector3f(-21f, 0.2f, 3f), Vector3f.UNIT_Y);
        }
    }

    /** One of each thing the scene buffers must tell apart, on a field. */
    private void bufferCases(Node world) {
        wall(world, surface(new ColorRGBA(0.26f, 0.31f, 0.17f, 1f), 0.95f, 0f), 0f, -0.5f, 0f, 30f, 0.5f, 30f, 10f);

        var cutOut = new Geometry("cut out", new Quad(4f, 4f));
        var holes = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
        holes.setTexture("BaseColorMap", picture(Image.Format.RGBA8, ColorSpace.sRGB, (x, y) -> new int[] {200, 120, 60,
            (x / 32 + y / 32) % 2 == 0 ? 255 : 0}));
        holes.setFloat("AlphaDiscardThreshold", 0.5f);
        holes.setFloat("Metallic", 0f);
        holes.setFloat("Roughness", 0.3f);
        holes.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        cutOut.setMaterial(holes);
        cutOut.setLocalTranslation(-9f, 0f, 0f);
        world.attachChild(cutOut);

        var instanced = new InstancedNode("instanced");
        var shared = surface(new ColorRGBA(0.5f, 0.5f, 0.6f, 1f), 0.5f, 0f);
        shared.setBoolean("UseInstancing", true);
        for (int i = 0; i < 9; i++) {
            var cube = new Geometry("cube", new Box(0.5f, 0.5f, 0.5f));
            cube.setMaterial(shared);
            cube.setLocalTranslation(-4f + (i % 3) * 1.6f, 0.5f + i / 3 * 1.2f, -2f);
            cube.rotate(0f, i * 0.3f, 0f);
            instanced.attachChild(cube);
        }
        instanced.instance();
        world.attachChild(instanced);

        var ridges = surface(new ColorRGBA(0.6f, 0.6f, 0.6f, 1f), 0.25f, 0f);
        ridges.setTexture("NormalMap", picture(Image.Format.RGB8, ColorSpace.Linear, (x, y) -> {
            float slope = FastMath.cos(x * FastMath.TWO_PI / 32f) * 0.8f;
            float length = FastMath.sqrt(1f + slope * slope);
            return new int[] {Math.round((-slope / length * 0.5f + 0.5f) * 255f), 128, Math.round((1f / length * 0.5f + 0.5f) * 255f), 255};
        }));
        var plate = new Geometry("ridged plate", new Box(2f, 1.5f, 0.1f));
        plate.setMaterial(ridges);
        MikktspaceTangentGenerator.generate(plate);
        plate.setLocalTranslation(1.5f, 1.5f, 0f);
        world.attachChild(plate);

        var lit = new Geometry("not physical", new Sphere(32, 32, 1f));
        lit.setMaterial(new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md"));
        lit.setLocalTranslation(6f, 1f, 0f);
        world.attachChild(lit);

        var glass = new Geometry("see through", new Box(1f, 1f, 1f));
        var clear = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        clear.setColor("Color", new ColorRGBA(0.2f, 0.5f, 1f, 0.4f));
        clear.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        glass.setMaterial(clear);
        glass.setQueueBucket(RenderQueue.Bucket.Transparent);
        glass.setLocalTranslation(9.5f, 1f, 0f);
        world.attachChild(glass);

        var mover = new Geometry("mover", new Sphere(32, 32, 1f));
        mover.setMaterial(surface(new ColorRGBA(0.8f, 0.2f, 0.2f, 1f), 0.7f, 0f));
        mover.setLocalTranslation(-5f, 1f, 4f);
        world.attachChild(mover);
        movers.add(mover);

        for (int i = 0; i < 4; i++) {
            animate(place(world, new String[] {"archer", "brute", "commander", "archer"}[i], 2f, -1f + i * 2f, 0f, 4f, 180f));
        }
        cam.setFrustumPerspective(35f, (float) cam.getWidth() / cam.getHeight(), 1f, 400f);
        cam.setLocation(new Vector3f(0f, 8f, 22f));
        cam.lookAt(new Vector3f(0f, 1.2f, 0f), Vector3f.UNIT_Y);
    }

    private interface Texel {
        int[] at(int x, int y);
    }

    private static Texture2D picture(Image.Format format, ColorSpace space, Texel texel) {
        int size = 256;
        int channels = format == Image.Format.RGB8 ? 3 : 4;
        ByteBuffer bytes = BufferUtils.createByteBuffer(size * size * channels);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                var value = texel.at(x, y);
                for (int c = 0; c < channels; c++) {
                    bytes.put((byte) value[c]);
                }
            }
        }
        bytes.flip();
        var texture = new Texture2D(new Image(format, size, size, bytes, space));
        texture.setWrap(Texture.WrapMode.Repeat);
        return texture;
    }

    /** The number of a {@code lights-<n>} word. */
    private java.util.Optional<Integer> lightsAsked() {
        return asked.stream().filter(word -> word.startsWith("lights-")).map(word -> Integer.valueOf(word.substring(7))).findFirst();
    }

    /**
     * {@code n} small coloured lamps of this reach, spread through a box, each going round its own circle: the price of
     * many lights. Dimmer as there are more, so that the picture stays one to look at.
     */
    private void swarm(int n, Vector3f centre, Vector3f size, float reach) {
        var random = new Random(12);
        float strength = 4f * FastMath.sqrt(16f / Math.max(n, 16));
        circle = reach * 0.375f;
        for (int i = 0; i < n; i++) {
            var at = centre.add((random.nextFloat() - 0.5f) * size.x, (random.nextFloat() - 0.5f) * size.y, (random.nextFloat() - 0.5f) * size.z);
            // A hue each, spread by the golden ratio, a fifth white.
            float hue = (i * 0.618034f) % 1f * 6f;
            var colour = new ColorRGBA(0.2f + 0.8f * FastMath.clamp(Math.abs(hue - 3f) - 1f, 0f, 1f),
                    0.2f + 0.8f * FastMath.clamp(2f - Math.abs(hue - 2f), 0f, 1f),
                    0.2f + 0.8f * FastMath.clamp(2f - Math.abs(hue - 4f), 0f, 1f), 1f).mult(strength);
            var lamp = new PointLight(at.clone(), colour, reach);
            rootNode.addLight(lamp);
            swarm.add(lamp);
            swarmAt.add(at);
        }
    }

    private void torches() {
        // A torch's colour is radiance at the flame, in the sun's units (2.4 at noon here): about 5 reads at a few metres
        // under the fixed exposure. The first versions had 40, which is white to the ACES curve (see the 3.3 report).
        float torch = Float.parseFloat(System.getProperty("bench.torch", "5"));
        var warm = new ColorRGBA(1f, 0.6f, 0.3f, 1f).mult(torch);
        rootNode.addLight(new PointLight(new Vector3f(-8f, 3f, 6f), warm, 18f));
        rootNode.addLight(new PointLight(new Vector3f(8f, 3f, 6f), warm, 18f));
        rootNode.addLight(new PointLight(new Vector3f(0f, 3f, -6f), warm, 18f));
        // More than the budget has shadows for: three by the houses, and a floodlight from a tower, a spot.
        rootNode.addLight(new PointLight(new Vector3f(-17f, 3f, -9f), warm, 14f));
        rootNode.addLight(new PointLight(new Vector3f(18f, 3f, -11f), warm, 14f));
        rootNode.addLight(new PointLight(new Vector3f(-17f, 3f, 14f), warm, 14f));
        var flood = new SpotLight(new Vector3f(0f, 10f, 20f), new Vector3f(0f, -10f, -18f).normalizeLocal(),
                new ColorRGBA(0.7f, 0.8f, 1f, 1f).mult(torch * 12f));
        flood.setSpotRange(30f);
        flood.setSpotInnerAngle(14f * FastMath.DEG_TO_RAD);
        flood.setSpotOuterAngle(24f * FastMath.DEG_TO_RAD);
        rootNode.addLight(flood);
    }

    private void wall(Node to, Material m, float x, float y, float z, float hx, float hy, float hz, float tile) {
        var box = new Box(hx, hy, hz);
        box.scaleTextureCoordinates(new Vector2f(tile, tile));
        var g = new Geometry("wall", box);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        to.attachChild(g);
    }

    /** A model of the given longest size standing on {@code y}, or a grey box of that size when no models were given. */
    private Spatial place(Node to, String name, float size, float x, float y, float z, float turn) {
        var model = loaded.computeIfAbsent(name, this::load).clone();
        var holder = new Node(name);
        holder.attachChild(model);
        var box = (BoundingBox) model.getWorldBound();
        float longest = Math.max(box.getYExtent(), Math.max(box.getXExtent(), box.getZExtent())) * 2f;
        float scale = size / longest;
        model.setLocalScale(model.getLocalScale().mult(scale));
        model.move(-box.getCenter().x * scale, -(box.getCenter().y - box.getYExtent()) * scale, -box.getCenter().z * scale);
        holder.setLocalTranslation(x, y, z);
        holder.rotate(0f, turn * FastMath.DEG_TO_RAD, 0f);
        to.attachChild(holder);
        return holder;
    }

    private Spatial load(String name) {
        if (models == null) {
            var stand = new Geometry(name, new Box(0.5f, 0.5f, 0.5f));
            stand.setMaterial(surface(new ColorRGBA(0.5f, 0.5f, 0.5f, 1f), 0.6f, 0f));
            stand.updateGeometricState();
            return stand;
        }
        var model = assetManager.loadModel(name + "/out/" + name + ".glb");
        model.depthFirstTraversal(s -> {
            // Mikktspace needs UVs; a piece without them has no normal map to serve anyway.
            if (s instanceof Geometry g && g.getMesh().getBuffer(VertexBuffer.Type.TexCoord) != null
                    && g.getMesh().getBuffer(VertexBuffer.Type.Tangent) == null) {
                MikktspaceTangentGenerator.generate(g);
            }
        });
        model.updateGeometricState();
        return model;
    }

    /** What the floor is rough by: after rain (the word {@code wet}), a glossy one. */
    private float floorRoughness(float dry) {
        return asked.contains("wet") ? 0.08f : dry;
    }

    /** A plain surface mottled by one shared noise picture. */
    private Material surface(ColorRGBA colour, float roughness, float metallic) {
        var m = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
        m.setColor("BaseColor", colour);
        m.setTexture("BaseColorMap", noise());
        m.setFloat("Roughness", roughness);
        m.setFloat("Metallic", metallic);
        return m;
    }

    private Texture2D noise() {
        if (noise != null) {
            return noise;
        }
        int size = 256;
        var level = new float[size * size];
        var random = new Random(7);
        for (int octave = 4; octave <= 128; octave *= 2) {
            var grid = new float[octave * octave];
            for (int i = 0; i < grid.length; i++) {
                grid[i] = random.nextFloat();
            }
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float fx = x * octave / (float) size;
                    float fy = y * octave / (float) size;
                    int ix = (int) fx;
                    int iy = (int) fy;
                    float top = FastMath.interpolateLinear(fx - ix, grid[iy * octave + ix], grid[iy * octave + (ix + 1) % octave]);
                    float low = FastMath.interpolateLinear(fx - ix, grid[(iy + 1) % octave * octave + ix],
                            grid[(iy + 1) % octave * octave + (ix + 1) % octave]);
                    level[y * size + x] += FastMath.interpolateLinear(fy - iy, top, low) * (8f / octave);
                }
            }
        }
        ByteBuffer bytes = BufferUtils.createByteBuffer(size * size * 3);
        for (float l : level) {
            byte v = (byte) Math.round(FastMath.clamp(0.82f + (l - 2f) * 0.12f, 0f, 1f) * 255f);
            bytes.put(v).put(v).put(v);
        }
        bytes.flip();
        noise = new Texture2D(new Image(Image.Format.RGB8, size, size, bytes, ColorSpace.sRGB));
        noise.setWrap(Texture.WrapMode.Repeat);
        noise.setMinFilter(Texture.MinFilter.Trilinear);
        return noise;
    }

    /** Where the focus goes without a {@code focus-<m>} word: the drill in the interior, the middle of the third row of balls, the middle of the crowd. */
    private float focusOn() {
        var at = switch (scene) {
            case "interior" -> rootNode.getChild("drill").getWorldTranslation();
            case "materials" -> new Vector3f(0f, 0f, 1.2f);
            default -> new Vector3f(0f, 1f, 3f);
        };
        return at.subtract(cam.getLocation()).dot(cam.getDirection());
    }

    private void filters() {
        if (picture.autoInstancing() && AutoInstancing.supported(renderer.getCaps())) {
            AutoInstancing.on(renderManager);
        }
        if (picture.autoLod()) {
            viewPort.addProcessor(new AutoLod());
        }
        if (picture.clusteredLights() && ClusteredLights.supported(renderer.getCaps())) {
            clusters = new ClusteredLights();
            viewPort.addProcessor(clusters);
        }
        if (picture.lens().depthOfField() && asked.stream().noneMatch(word -> word.startsWith("focus-"))) {
            picture = picture.withLens(picture.lens().withFocus(focusOn()));
        }
        var post = new FilterPostProcessor(assetManager);
        post.setFrameBufferFormat(Image.Format.RGB16F);
        if (asked.contains("show-clusters")) {
            post.addFilter(new ClusterView(clusters));
            viewPort.addProcessor(post);
            return;
        }
        if (asked.contains("show-ao")) {
            PictureChain.filters(picture).stream().filter(GtaoFilter.class::isInstance)
                    .forEach(filter -> post.addFilter(((GtaoFilter) filter).showOcclusionOnly()));
            viewPort.addProcessor(post);
            return;
        }
        if (asked.contains("show-ssr")) {
            PictureChain.filters(picture).stream().filter(SsrFilter.class::isInstance)
                    .forEach(filter -> post.addFilter(((SsrFilter) filter).showReflectionOnly()));
            viewPort.addProcessor(post);
            return;
        }
        if (asked.contains("show-probe-left")) {
            PictureChain.filters(picture).stream().filter(SsrFilter.class::isInstance)
                    .forEach(filter -> post.addFilter(((SsrFilter) filter).showProbeLeft()));
            viewPort.addProcessor(post);
            return;
        }
        if (asked.contains("show-bloom")) {
            PictureChain.filters(picture).stream().filter(MipBloomFilter.class::isInstance)
                    .forEach(filter -> post.addFilter(((MipBloomFilter) filter).showBloomOnly()));
            viewPort.addProcessor(post);
            return;
        }
        var shown = asked.stream().filter(word -> word.startsWith("show-") && !word.equals("show-ssgi")).findFirst();
        if (shown.isPresent()) {
            post.addFilter(new BufferView(BufferView.Shown.valueOf(shown.get().substring(5).toUpperCase(Locale.ROOT))));
            viewPort.addProcessor(post);
            return;
        }
        if (!asked.contains("no-shadows")) {
            shadowLight.setDirection(sunDirection);
            var shadows = new SunShadowFilter(assetManager, shadowLight, picture, true, geometry -> true);
            shadowsFilter = shadows;
            shadows.getShadowMaterial().setColor("ShadowColor", new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
            post.addFilter(shadows);
        }
        if (picture.localShadows() > 0 && LocalShadowFilter.supported(renderer.getCaps())) {
            var local = new LocalShadowFilter(assetManager, picture.localShadows());
            localFilter = local;
            local.fill(fill);
            post.addFilter(local);
        }
        PictureChain.filters(picture).forEach(filter -> {
            post.addFilter(filter instanceof SsgiFilter bounce && asked.contains("show-ssgi") ? bounce.showBounceOnly() : filter);
            if (filter instanceof AcesToneMap map) {
                tone = map;
            }
            if (filter instanceof HeightFogFilter found) {
                fog = found;
                if (shadowsFilter != null) {
                    found.cascadesFrom(shadowsFilter);
                }
                found.sun(sunDirection.negate(), sunAbove, ambientNow);
            }
        });
        viewPort.addProcessor(post);
        if (asked.contains("buffers")) {
            SceneBuffers.on(assetManager, renderManager, viewPort, cam.getWidth(), cam.getHeight());
        }
    }

    // ---- measuring ----

    @Override
    public void simpleUpdate(float tpf) {
        frame++;
        // The statistics view, off, turns them off each frame before this.
        renderer.getStatistics().setEnabled(true);
        if (scene.equals("furnace")) {
            if (frame == 4) {
                furnaceReads.forEach(Runnable::run);
                stop();
            }
            return;
        }
        if (asked.contains("walk")) {
            walk(tpf);
            return;
        }
        // By the frame, not the clock: the same frame of a run is the same picture, on any machine.
        // With fast, the same motion at up to 25 times the speed, to and fro so that nothing leaves the scene.
        float speed = asked.contains("fast") ? 25f * FastMath.sin(frame * 0.05f) : 1f;
        if (waves != null) {
            waves.seconds(asked.contains("pair") ? (WARMUP + MEASURED + 1) / 60f : frame / 60f);
            // With pair the waves stand still (and a second shot comes a frame after the first): what is left to differ is the chain's noise, TAA's settling.
        }
        if (asked.contains("move-camera")) {
            cam.setLocation(cam.getLocation().add(cam.getLeft().mult(0.02f * speed)));
            cam.setRotation(new Quaternion().fromAngleAxis(0.0004f * speed, Vector3f.UNIT_Y).mult(cam.getRotation()));
        }
if (asked.contains("move-sun") && atmosphere != null) {
    hour += 0.01f;
    var toSun = sunAt(hour);
    sunDirection.set(toSun.negate());
    sunLight.setDirection(sunDirection);
    sunLight.setColor(atTheGround(toSun));
    shadowLight.setDirection(sunDirection);
    atmosphere.follow(toSun, sunAbove);
    if (fog != null) {
        fog.sun(toSun, sunAbove, ambientNow);
    }
}
if (asked.contains("move-units")) {
            movers.forEach(mover -> mover.move(0.02f * speed, 0f, 0f));
        }
        for (int i = 0; i < swarm.size(); i++) {
            float turn = frame * 0.03f + i;
            swarm.get(i).setPosition(swarmAt.get(i).add(circle * FastMath.cos(turn), 0f, circle * FastMath.sin(turn)));
        }
        if (frame > WARMUP && frame <= WARMUP + MEASURED) {
            frames.add(tpf);
            var stats = new int[13];
            renderer.getStatistics().getData(stats);
            calls.add(stats[3]);
            drawnTriangles.add(stats[1]);
            submits.add(submitted / 1e6);
            if (clusters != null) {
                sortMillis += clusters.sortMillis();
            }
        }
        if (frame == WARMUP + MEASURED + 1) {
            shots.takeScreenshot();
        }
        if (asked.contains("pair") && frame == WARMUP + MEASURED + 2) {
            shots.setFileName(scene + "_" + label.replace(',', '+') + "_next");
            shots.takeScreenshot();
        }
        if (frame == WARMUP + MEASURED + 5) {
            report();
            stop();
        }
        if (timers != null) {
            // The GPU's time for the frame about to be drawn, read two frames on (a query is not ready sooner).
            if (frame > 2 + WARMUP && frame <= 2 + WARMUP + MEASURED) {
                int old = timers[(frame - 2) % timers.length];
                if (renderer.isTaskResultAvailable(old)) {
                    gpus.add(renderer.getProfilingTime(old) / 1e6);
                }
            }
            renderer.startProfiling(timers[frame % timers.length]);
        }
        submitted = -System.nanoTime();
    }

    /** After the frame is drawn: how long the commands took to give. */
    @Override
    public void simpleRender(com.jme3.renderer.RenderManager rm) {
        if (timers != null && !scene.equals("furnace")) {
            renderer.stopProfiling();
        }
        submitted += System.nanoTime();
    }

    /**
     * A dark box round the camera, off from 2 s to 5 s: the eye goes from dark to bright and back, the first by a cut and the
     * eye catching up after it. Shots at each cut and once the eye has settled; EYE lines say what it holds.
     */
    private void walk(float tpf) {
        clock += Math.min(tpf, 0.1f);
        if (dark == null) {
            dark = new Geometry("dark", new Box(0.6f, 0.6f, 0.6f));
            var black = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            black.setColor("Color", new ColorRGBA(0.01f, 0.01f, 0.01f, 1f));
            black.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            dark.setMaterial(black);
            dark.setShadowMode(ShadowMode.Off);
            dark.setLocalTranslation(cam.getLocation());
            rootNode.attachChild(dark);
        }
        boolean inTheDark = clock < 2f || clock >= 5f;
        if (inTheDark != (dark.getParent() != null)) {
            if (inTheDark) {
                rootNode.attachChild(dark);
            } else {
                dark.removeFromParent();
            }
        }
        float[] shotAt = {1.5f, 2.1f, 4.5f, 5.1f, 11f};
        String[] named = {"dark", "cut-to-bright", "bright-settled", "cut-to-dark", "dark-settled"};
        if (walkStep < shotAt.length && clock >= shotAt[walkStep]) {
            shots.setFileName(scene + "_walk_" + named[walkStep]);
            shots.takeScreenshot();
            walkStep++;
        }
        if (tone != null && clock - lastLogged >= 0.25f && frame > 3) {
            lastLogged = clock;
            var eye = tone.eyeNow(renderer);
            System.out.printf(Locale.ROOT, "EYE t=%.2f frame=%d held=%.2f stops (luminance %.3f) exposure x%.2f measured %.2f stops%n", clock,
                    frame, eye[0], Math.pow(2.0, eye[0]), eye[1], eye[2]);
        }
        if (clock > 11.3f) {
            stop();
        }
    }

    private static double mean(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);
    }

    private void report() {
        var sorted = new ArrayList<>(frames);
        sorted.sort(null);
        double total = 0;
        for (float f : frames) {
            total += f;
        }
        double p99 = sorted.get((int) (sorted.size() * 0.99)) * 1000.0;
        var line = String.format(Locale.ROOT, "%s;%s;%d;%.2f;%.2f%s%n", scene, label, rootNode.getTriangleCount(),
                total / frames.size() * 1000.0, p99, atmosphere == null ? ""
                        : String.format(Locale.ROOT, ";%.1f;%.1f", atmosphere.paintMillis(), atmosphere.probeMillis()));
        try {
            Files.writeString(out.resolve("results.csv"), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        line = line.stripTrailing() + String.format(Locale.ROOT, ";draws=%.0f;triangles=%.0f;submit=%.2f;gpu=%.2f%n", mean(calls), mean(drawnTriangles),
                submits.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN),
                gpus.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN));
        System.out.print("RESULT " + line);
        if (clusters != null) {
            System.out.printf(Locale.ROOT, "clusters: %d lights sorted, %.3f ms a frame to sort them%n", clusters.sorted(), sortMillis / frames.size());
        }
        if (localFilter != null) {
            System.out.println("local shadows: maps drawn " + localFilter.mapsDrawn() + ", kept " + localFilter.mapsKept() + " (last frame)");
        }
    }
}
