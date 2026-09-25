package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import uz.dukeengine.core.content.EffectList;

/**
 * What an effect list plays besides particle systems, as this client draws it: a sound, a light pulse, the
 * camera knocked, a scorch mark, a tracer — each the reference game's way, stepped at its 30 frames a second.
 */
final class ListShow implements EffectLists.Show {

    private static final Logger LOG = Logger.getLogger(ListShow.class.getName());

    /** The reference's {@code MAX_SCORCH_MARKS}: past this many, the oldest mark is let go of. */
    static final int MOST_SCORCHES = 500;

    /** How strong each of the six shakes is: {@code GlobalData}'s {@code ShakeSubtleIntensity} and the rest. */
    private static final float[] SHAKE = {0.5f, 1f, 2.5f, 5f, 8f, 12f};
    /** {@code MaxShakeRange}: no knock from further than this from where the camera looks. */
    private static final float SHAKE_RANGE = 150f;
    /** {@code MaxShakeIntensity}, and the three a knock past it is held to — as {@code W3DView::shake} does. */
    private static final float SHAKE_MOST = 10f;
    private static final float SHAKE_PAST_MOST = 3f;
    /** The stiff spring {@code W3DView} fakes: what is left of a knock after each frame. */
    private static final float SHAKE_DAMPING = 0.75f;

    /** Laid this far over the ground, so a mark does not flicker against it. */
    private static final float SCORCH_LIFT = 0.05f;

    private final AssetManager assets;
    private final Node node;
    private final LightPool lights;
    private final BiConsumer<String, Vector3f> sound;
    private final Supplier<Vector3f> looking;
    private final WorldMoments.Floor floor;
    private final java.util.function.BiFunction<String, String, com.jme3.scene.Spatial> pieces;
    private final Random random = new Random();

    private final List<Pulse> pulses = new ArrayList<>();
    private final Deque<Scorch> scorches = new ArrayDeque<>();
    private final List<Tracer> tracers = new ArrayList<>();
    private final List<Flung> flung = new ArrayList<>();
    private final Set<String> missing = new HashSet<>();

    private float shake;
    private float shakeCos;
    private float shakeSin;
    private float owed;

    /**
     * @param node    where marks and streaks are hung
     * @param lights  the lights every effect shares
     * @param sound   plays a cue of the game's sound bank at a place
     * @param looking where on the ground the camera looks, which a shake is measured from
     * @param pieces  a fresh copy of a model, or of one named piece of it, for debris; null where it will not load
     */
    ListShow(AssetManager assets, Node node, LightPool lights, BiConsumer<String, Vector3f> sound,
            Supplier<Vector3f> looking, WorldMoments.Floor floor,
            java.util.function.BiFunction<String, String, com.jme3.scene.Spatial> pieces) {
        this.assets = assets;
        this.node = node;
        this.lights = lights;
        this.sound = sound;
        this.looking = looking;
        this.floor = floor;
        this.pieces = pieces;
    }

    @Override
    public void sound(String cue, Vector3f at) {
        sound.accept(cue, at);
    }

    // ---- light pulses ----

    private static final class Pulse {
        final PointLight light;
        final ColorRGBA colour;
        final int rise;
        final int fall;
        int age;

        Pulse(PointLight light, ColorRGBA colour, int rise, int fall) {
            this.light = light;
            this.colour = colour;
            this.rise = rise;
            this.fall = fall;
        }
    }

    @Override
    public void light(Vector3f at, int colour, float radius, int riseFrames, int fallFrames) {
        if (radius <= 0f || lights == null) {
            return;
        }
        var light = lights.take();
        if (light == null) {
            return; // every light is burning: the pulse is the glow, and the glow is what is lost
        }
        light.setPosition(at.clone());
        light.setRadius(radius);
        var pulse = new Pulse(light, rgb(colour, 1f), Math.max(0, riseFrames), Math.max(0, fallFrames));
        brighten(pulse);
        pulses.add(pulse);
    }

    /** Up to its colour over its rise, then down to nothing over its fall — {@code W3DDynamicLight}'s pulse. */
    private static void brighten(Pulse pulse) {
        float share;
        if (pulse.age < pulse.rise) {
            share = (pulse.age + 1f) / pulse.rise;
        } else {
            int down = pulse.age - pulse.rise;
            share = pulse.fall == 0 ? (down == 0 ? 1f : 0f) : 1f - (float) down / pulse.fall;
        }
        pulse.light.setColor(pulse.colour.mult(Math.max(0f, share)));
    }

    // ---- the camera ----

    /** {@code W3DView::shake}: stronger the nearer the camera looks, added to what is left, and held under a most. */
    @Override
    public void shake(Vector3f at, EffectList.Shake.Strength strength) {
        var eye = looking.get();
        float distance = (float) Math.hypot(at.x - eye.x, at.z - eye.z);
        if (distance > SHAKE_RANGE) {
            return;
        }
        float angle = random.nextFloat() * FastMath.TWO_PI;
        shakeCos = FastMath.cos(angle);
        shakeSin = FastMath.sin(angle);
        shake += SHAKE[strength.ordinal()] * (1f - distance / SHAKE_RANGE);
        if (shake > SHAKE_MOST) {
            shake = SHAKE_PAST_MOST;
        }
    }

    /** Where the knock puts the camera this frame, across the ground. */
    Vector3f shakeNow() {
        return shake > 0.01f ? new Vector3f(shake * shakeCos, 0f, shake * shakeSin) : Vector3f.ZERO;
    }

    // ---- scorch marks ----

    private record Scorch(Geometry mark, float x, float z, float radius, String picture) {
    }

    @Override
    public void scorch(Vector3f at, String picture, float radius) {
        if (radius <= 0f) {
            return;
        }
        // BaseHeightMapRenderObjClass::addScorch: one next to one just like it is the same mark.
        float near = radius / 4f;
        for (var mark : scorches) {
            if (Math.abs(at.x - mark.x()) < near && Math.abs(at.z - mark.z()) < near
                    && Math.abs(radius - mark.radius()) < near && picture.equals(mark.picture())) {
                return;
            }
        }
        var material = material(picture);
        if (material == null) {
            return;
        }
        if (scorches.size() >= MOST_SCORCHES) {
            scorches.pollFirst().mark().removeFromParent();
        }
        var mark = new Geometry("scorch", new Quad(radius * 2f, radius * 2f));
        mark.setMaterial(material);
        mark.setQueueBucket(RenderQueue.Bucket.Transparent);
        mark.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        mark.setLocalTranslation(at.x - radius, floor.at(at.x, at.z) + SCORCH_LIFT, at.z + radius);
        node.attachChild(mark);
        scorches.addLast(new Scorch(mark, at.x, at.z, radius, picture));
    }

    private Material material(String picture) {
        try {
            var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            material.setTexture("ColorMap", assets.loadTexture(picture));
            var state = material.getAdditionalRenderState();
            state.setBlendMode(RenderState.BlendMode.Alpha);
            state.setDepthWrite(false);
            state.setPolyOffset(-1f, -1f);
            return material;
        } catch (RuntimeException notThere) {
            if (missing.add(picture)) {
                LOG.warning(() -> "a scorch mark names a picture that will not load: " + picture);
            }
            return null;
        }
    }

    int scorchCount() {
        return scorches.size();
    }

    // ---- tracers ----

    private static final class Tracer {
        final Geometry streak;
        final Vector3f from;
        final Vector3f way;
        final float distance;
        final float speed;
        final float length;
        final float width;
        final int life;
        final ColorRGBA colour;
        int age;

        Tracer(Geometry streak, Vector3f from, Vector3f way, float distance, float speed, float length,
                float width, int life, ColorRGBA colour) {
            this.streak = streak;
            this.from = from;
            this.way = way;
            this.distance = distance;
            this.speed = speed;
            this.length = length;
            this.width = width;
            this.life = life;
            this.colour = colour;
        }
    }

    /**
     * {@code TracerFXNugget}: a streak {@code length} long setting off toward the other end at {@code speed} a
     * frame, living {@code decayAt} of the frames it takes to get there and fading away over them.
     */
    @Override
    public void tracer(Vector3f from, Vector3f to, EffectList.Tracer tracer) {
        var way = to.subtract(from);
        float distance = way.length();
        if (distance <= 0f || tracer.width() <= 0f) {
            return;
        }
        way.divideLocal(distance);
        boolean atOnce = tracer.speed() <= 0f;
        float length = atOnce ? distance : Math.min(tracer.length(), distance);
        float frames = atOnce ? 1f : Math.max(1f, (distance - length) / tracer.speed());
        int life = Math.max(1, (int) Math.ceil(frames * tracer.decayAt()));
        var colour = rgb(tracer.colour(), 1f);
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", colour.clone());
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        var streak = new Geometry("tracer", new Quad(length, tracer.width()));
        streak.setMaterial(material);
        streak.setQueueBucket(RenderQueue.Bucket.Transparent);
        // Its length along the way it flies, its width across it and flat to the sky.
        var along = WorldMoments.along(way);
        streak.setLocalRotation(along.mult(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X)));
        node.attachChild(streak);
        var flying = new Tracer(streak, from.clone(), way, distance, atOnce ? distance : tracer.speed(), length,
                tracer.width(), life, colour);
        place(flying);
        tracers.add(flying);
    }

    private static void place(Tracer tracer) {
        float head = Math.min(tracer.distance, tracer.length + tracer.age * tracer.speed);
        var tail = tracer.from.add(tracer.way.mult(head - tracer.length));
        // A quad's corner is its origin: moved back half its width, so the streak runs down the middle of the way.
        tracer.streak.setLocalTranslation(tail.addLocal(
                tracer.streak.getLocalRotation().mult(new Vector3f(0f, -tracer.width / 2f, 0f))));
        float opacity = 1f - (float) tracer.age / tracer.life;
        tracer.streak.getMaterial().setColor("Color",
                new ColorRGBA(tracer.colour.r, tracer.colour.g, tracer.colour.b, Math.max(0f, opacity)));
    }

    int tracerCount() {
        return tracers.size();
    }

    // ---- debris ----

    private record Flung(com.jme3.scene.Spatial piece, Thrown thrown, Quaternion start) {
    }

    @Override
    public void debris(String model, String piece, Quaternion turn, Thrown thrown) {
        var drawn = pieces == null ? null : pieces.apply(model, piece);
        if (drawn == null) {
            return;
        }
        // Materials of its own, to fade without fading every other copy of the model.
        drawn.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry && geometry.getMaterial() != null) {
                geometry.setMaterial(geometry.getMaterial().clone());
            }
        });
        node.attachChild(drawn);
        var one = new Flung(drawn, thrown, turn == null ? new Quaternion() : turn.clone());
        place(one);
        flung.add(one);
    }

    private static void place(Flung one) {
        one.piece().setLocalTranslation(one.thrown().at());
        one.piece().setLocalRotation(one.thrown().turn().mult(one.start()));
        float opacity = one.thrown().opacity();
        if (opacity < 1f) {
            fade(one.piece(), opacity);
        }
    }

    /** Every surface of a piece drawn {@code opacity} seen, whichever of the usual materials it is drawn with. */
    private static void fade(com.jme3.scene.Spatial piece, float opacity) {
        piece.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry) || geometry.getMaterial() == null) {
                return;
            }
            var material = geometry.getMaterial();
            for (var name : new String[] {"BaseColor", "Diffuse", "Color"}) {
                if (material.getMaterialDef().getMaterialParam(name) == null) {
                    continue;
                }
                var colour = material.getParamValue(name) instanceof ColorRGBA was ? was.clone() : ColorRGBA.White.clone();
                colour.a = opacity;
                material.setColor(name, colour);
                if (name.equals("Diffuse")) {
                    material.setBoolean("UseMaterialColors", true);
                }
                material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
                return;
            }
        });
    }

    int flungCount() {
        return flung.size();
    }

    // ---- time ----

    /** The client's time passing, a game frame at a time. */
    void update(float seconds) {
        owed += Math.max(0f, seconds);
        int run = 0;
        while (owed >= Particles.FRAME_SECONDS && run < 8) {
            frame();
            owed -= Particles.FRAME_SECONDS;
            run++;
        }
        if (run == 8) {
            owed = 0f;
        }
    }

    /** One of the game's frames for every pulse, the camera's knock and every streak. */
    void frame() {
        for (var each = pulses.iterator(); each.hasNext();) {
            var pulse = each.next();
            pulse.age++;
            if (pulse.age >= pulse.rise + Math.max(1, pulse.fall)) {
                lights.give(pulse.light);
                each.remove();
            } else {
                brighten(pulse);
            }
        }
        if (shake > 0.01f) {
            shake *= SHAKE_DAMPING;
            shakeCos = -shakeCos; // so stiff it pulls back past where it rests, every frame
            shakeSin = -shakeSin;
        } else {
            shake = 0f;
        }
        for (var each = tracers.iterator(); each.hasNext();) {
            var tracer = each.next();
            tracer.age++;
            if (tracer.age >= tracer.life) {
                tracer.streak.removeFromParent();
                each.remove();
            } else {
                place(tracer);
            }
        }
        for (var each = flung.iterator(); each.hasNext();) {
            var one = each.next();
            if (one.thrown().frame(floor)) {
                place(one);
            } else {
                one.piece().removeFromParent();
                each.remove();
            }
        }
    }

    /** Everything gone at once, for a new world. */
    void clear() {
        pulses.forEach(pulse -> lights.give(pulse.light));
        pulses.clear();
        scorches.forEach(mark -> mark.mark().removeFromParent());
        scorches.clear();
        tracers.forEach(tracer -> tracer.streak.removeFromParent());
        tracers.clear();
        flung.forEach(one -> one.piece().removeFromParent());
        flung.clear();
        shake = 0f;
    }

    private static ColorRGBA rgb(int packed, float alpha) {
        return new ColorRGBA((packed >> 16 & 0xFF) / 255f, (packed >> 8 & 0xFF) / 255f, (packed & 0xFF) / 255f,
                alpha);
    }
}
