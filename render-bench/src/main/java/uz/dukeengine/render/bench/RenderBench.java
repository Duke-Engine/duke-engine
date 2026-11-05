package uz.dukeengine.render.bench;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.light.DirectionalLight;
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
import uz.dukeengine.render.Atmosphere;
import uz.dukeengine.render.AcesToneMap;
import uz.dukeengine.render.AtmosphereSky;
import uz.dukeengine.render.GtaoFilter;
import uz.dukeengine.render.HeightFogFilter;
import uz.dukeengine.render.LocalShadowFilter;
import uz.dukeengine.render.MipBloomFilter;
import uz.dukeengine.render.PictureChain;
import uz.dukeengine.render.PictureSettings;
import uz.dukeengine.render.SceneBuffers;
import uz.dukeengine.render.SsrFilter;
import uz.dukeengine.render.SkyProbe;
import uz.dukeengine.render.SunShadowFilter;

/**
 * Draws one fixed scene, measures its frames, saves a picture and quits, so a change to the renderer can be seen and
 * priced. Args: scene (materials, interior, outdoor, night, buffers, furnace), what to leave out or add ("full", or joined by
 * commas: no-ao, no-bloom, no-fxaa, no-pcss, no-contact, no-shadows, no-local-shadows, local-&lt;n&gt; (how many local lights have a shadow), lamp (one lamp over any scene); feet, which looks at the outdoor units' feet from close by; buffers, which draws the scene buffers for nothing to read, to price them; show-normals,
 * show-depth, show-roughness, show-velocity, which show one instead of the picture; show-ao, which shows the ambient
 * occlusion alone, and ao-radius-&lt;m&gt;, which sets its reach in world units; show-bloom, which shows the bloom alone; auto-exposure, which lets the eye adapt, and walk, which (with it) goes from a
 * dark view to a bright one and back, logging what the eye holds (EYE lines) and shooting at the cuts; fog (height fog and the
 * sun's shafts), fog-&lt;density&gt; (its density at height 0, per metre) and no-shafts; no-ssr, show-ssr (how much of the reflection the screen gives, alone) and wet (the floor of materials and outdoor made glossy); move-camera, move-units;
 * {@code hour-<h>}, which turns the atmosphere on with the sun where a made-up summer day has it at that hour, 6 and 18
 * being just over the horizon; sky-view, which looks toward the sun from near the ground, and shaft-view, which does so from behind the scene, so that what stands in it breaks the light; move-sun, which lets the sun
 * walk 0.15 degrees a frame and so prices the sky made anew), output folder. {@code -Dbench.models=<folder>} dresses the scenes
 * in real models ({@code <folder>/<name>/out/<name>.glb}); without it they are made of boxes. With an atmosphere the
 * CSV line has two more columns: the last sky's painting (off the frame) and probe baking (on it), in milliseconds.
 */
public final class RenderBench extends SimpleApplication {

    private static final int WARMUP = 120;
    private static final int MEASURED = 240;
    /** The density of the fog at height 0 that the word {@code fog} asks for: a light morning haze, 5% of the light through 750 metres. */
    private static final float FOG = 0.004f;

    private final String scene;
    private final String label;
    private final PictureSettings picture;
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

    private RenderBench(String scene, String label, Path out) {
        this.scene = scene;
        this.label = label;
        this.picture = settings(label);
        this.asked = List.of(label.split(","));
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

    static PictureSettings settings(String label) {
        var full = PictureSettings.FULL;
        var left = List.of(label.split(","));
        float fogDensity = left.stream().filter(word -> word.startsWith("fog-")).map(word -> Float.valueOf(word.substring(4)))
                .findFirst().orElse(left.contains("fog") ? FOG : full.fogDensity());
        return new PictureSettings(full.ambientOcclusion() && !left.contains("no-ao"),
                full.bloom() && !left.contains("no-bloom"), full.fxaa() && !left.contains("no-fxaa"), full.exposure(),
                hourOf(left) != null, full.softShadows() && !left.contains("no-pcss"),
                full.contactShadows() && !left.contains("no-contact"), localBudget(left, full.localShadows()),
                left.stream().filter(word -> word.startsWith("ao-radius-")).map(word -> Float.valueOf(word.substring(10)))
                        .findFirst().orElse(full.occlusionRadius()),
                left.contains("auto-exposure") || left.contains("walk"), fogDensity, full.fogFalloff(),
                full.shafts() && !left.contains("no-shafts"), full.reflections() && !left.contains("no-ssr"));
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
            stop();
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
            case "interior" -> {
                sunDirection.set(-0.3f, -0.33f, -0.9f);
                sun = new ColorRGBA(1f, 0.93f, 0.82f, 1f).mult(1.5f);
                interior(world);
            }
            case "outdoor" -> {
                sunDirection.set(-0.45f, -0.75f, -0.48f);
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2.4f);
                outdoor(world);
            }
            case "buffers" -> {
                sunDirection.set(-0.45f, -0.75f, -0.48f);
                sun = new ColorRGBA(1f, 0.95f, 0.88f, 1f).mult(2.4f);
                bufferCases(world);
            }
            case "night" -> {
                sunDirection.set(0.3f, -0.8f, 0.4f);
                sun = new ColorRGBA(0.45f, 0.55f, 0.8f, 1f).mult(0.12f);
                ambient = new ColorRGBA(0.03f, 0.04f, 0.08f, 1f);
                outdoor(world);
                torches();
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
        if (asked.contains("sky-view") || asked.contains("shaft-view")) {
            lookAtTheSun(toSun, asked.contains("shaft-view") ? world : null);
        }
        filters();
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

    private void filters() {
        var post = new FilterPostProcessor(assetManager);
        post.setFrameBufferFormat(Image.Format.RGB16F);
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
        if (asked.contains("show-bloom")) {
            PictureChain.filters(picture).stream().filter(MipBloomFilter.class::isInstance)
                    .forEach(filter -> post.addFilter(((MipBloomFilter) filter).showBloomOnly()));
            viewPort.addProcessor(post);
            return;
        }
        var shown = asked.stream().filter(word -> word.startsWith("show-")).findFirst();
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
            post.addFilter(filter);
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
        if (asked.contains("walk")) {
            walk(tpf);
            return;
        }
        // By the frame, not the clock: the same frame of a run is the same picture, on any machine.
        if (asked.contains("move-camera")) {
            cam.setLocation(cam.getLocation().add(cam.getLeft().mult(0.02f)));
            cam.setRotation(new Quaternion().fromAngleAxis(0.0004f, Vector3f.UNIT_Y).mult(cam.getRotation()));
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
            movers.forEach(mover -> mover.move(0.02f, 0f, 0f));
        }
        if (frame > WARMUP && frame <= WARMUP + MEASURED) {
            frames.add(tpf);
        }
        if (frame == WARMUP + MEASURED + 1) {
            shots.takeScreenshot();
        }
        if (frame == WARMUP + MEASURED + 5) {
            report();
            stop();
        }
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
        System.out.print("RESULT " + line);
        if (localFilter != null) {
            System.out.println("local shadows: maps drawn " + localFilter.mapsDrawn() + ", kept " + localFilter.mapsKept() + " (last frame)");
        }
    }
}
