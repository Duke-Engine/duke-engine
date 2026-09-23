package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.logging.Logger;
import uz.dukeengine.core.content.EffectList;

/**
 * Plays an effect list: every entry of it at once, where and for what it is played — the reference game's
 * {@code FXList}, its nuggets one by one. Drawing only, and every number drawn here is the client's own.
 *
 * <p>Particle systems are started here, in their own Z-up frame; everything else a list plays — a sound, a
 * light, the camera, a mark on the ground, a streak — is handed to a {@link Show}, which is the client's.
 */
final class EffectLists {

    private static final Logger LOG = Logger.getLogger(EffectLists.class.getName());

    /** Lists played at bones within lists, deepest: a list that plays itself at a bone ends here, not never. */
    private static final int DEEPEST = 4;

    private static final float[] UNTURNED = {1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};

    /** What a list plays besides particle systems. */
    interface Show {

        /** A cue of the game's sound bank, heard at a place. */
        void sound(String cue, Vector3f at);

        /** A light swelling for {@code riseFrames} and dying away for {@code fallFrames}. */
        void light(Vector3f at, int colour, float radius, int riseFrames, int fallFrames);

        /** The camera knocked, as hard as the strength says and less the further it is looking from here. */
        void shake(Vector3f at, EffectList.Shake.Strength strength);

        /** A mark laid on the ground, kept. */
        void scorch(Vector3f at, String picture, float radius);

        /** A streak from one place toward another. */
        void tracer(Vector3f from, Vector3f to, EffectList.Tracer tracer);
    }

    /**
     * Where a list plays and for what: everything an entry may be handed.
     *
     * @param at     where, in the client's frame
     * @param turn   how it is turned there — the way the thing or the bone faces — or null for not at all
     * @param thing  what it plays at — for an entry that rides it, takes its size or finds its bones — or null
     * @param other  the other party: whoever caused it, for a ricochet; what was shot at, for a tracer. Or null
     * @param radius the radius it was played with — a blast's — for a system that takes it; 0 for none
     */
    record Cue(Vector3f at, Quaternion turn, Spatial thing, Vector3f other, float radius) {

        static Cue at(Vector3f at) {
            return new Cue(at, null, null, null, 0f);
        }
    }

    private final Function<String, EffectList> named;
    private final Particles systems;
    private final Show show;
    private final SplittableRandom random;
    private final Set<String> said = new HashSet<>();

    /**
     * @param named   the game's lists, by name
     * @param systems where particle systems are started, or null for a client that draws none
     * @param show    everything else
     */
    EffectLists(Function<String, EffectList> named, Particles systems, Show show, long seed) {
        this.named = named;
        this.systems = systems;
        this.show = show;
        this.random = new SplittableRandom(seed);
    }

    /** Whether this is the name of one of the game's lists. */
    boolean has(String name) {
        return name != null && named.apply(name) != null;
    }

    /** Play the list of this name where the cue says; false where the game has no list of that name. */
    boolean play(String name, Cue cue) {
        var list = name == null ? null : named.apply(name);
        if (list == null) {
            return false;
        }
        play(list, cue, 0);
        return true;
    }

    private void play(EffectList list, Cue cue, int depth) {
        if (cue == null || cue.at() == null) {
            return;
        }
        var at = system(cue.at());
        var turn = cue.turn() == null ? null : system(cue.turn());
        for (var entry : list.entries()) {
            switch (entry) {
                case EffectList.ParticleSystem system -> start(system, cue, at, turn);
                case EffectList.Sound sound -> {
                    if (sound.name() != null) {
                        show.sound(sound.name(), cue.at());
                    }
                }
                case EffectList.LightPulse pulse -> show.light(cue.at(), pulse.colour(),
                        pulse.radiusShare() > 0f && cue.thing() != null
                                ? sizeOf(cue.thing()) * pulse.radiusShare() : pulse.radius(),
                        pulse.riseFrames(), pulse.fallFrames());
                case EffectList.Shake shake -> show.shake(cue.at(), shake.strength());
                case EffectList.Scorch scorch -> {
                    if (!scorch.pictures().isEmpty()) {
                        show.scorch(cue.at(), scorch.pictures().get(random.nextInt(scorch.pictures().size())),
                                scorch.radius());
                    }
                }
                case EffectList.Tracer tracer -> {
                    // The reference needs both ends, and draws its share of them: 0.3 is three shots in ten.
                    if (cue.other() != null && random.nextDouble() < tracer.probability()) {
                        show.tracer(cue.at(), cue.other(), tracer);
                    }
                }
                case EffectList.AtBone atBone -> atBones(list, atBone, cue, depth);
            }
        }
    }

    // ---- particle systems ----

    /**
     * {@code ParticleSystemFXNugget::reallyDoFX}: the offset turned the way the list is turned — or, for a
     * ricochet, the way away from whoever caused it — each copy on a ring at an angle of its own, and the system
     * itself turned that way only if it orients to the thing, then tipped by its own rotations.
     */
    private void start(EffectList.ParticleSystem entry, Cue cue, float[] at, float[] turn) {
        if (systems == null || entry.name() == null) {
            return;
        }
        var facing = turn;
        if (entry.ricochet() && cue.other() != null) {
            var other = system(cue.other());
            facing = aboutZ((float) Math.atan2(at[1] - other[1], at[0] - other[0]));
        }
        var local = vector(entry.offset());
        var offset = turned(facing, local);
        var tilt = times(aboutX(entry.rotateX()), times(aboutY(entry.rotateY()), aboutZ(entry.rotateZ())));
        var axes = times(entry.orientToObject() && facing != null ? facing : UNTURNED, tilt);
        for (int copy = 0; copy < entry.count(); copy++) {
            float radius = draw(entry.radius());
            float angle = (float) (random.nextDouble() * 2 * Math.PI);
            var ring = new float[] {radius * (float) Math.cos(angle), radius * (float) Math.sin(angle),
                entry.createAtGroundHeight() ? 0f : draw(entry.height())};
            Emitter emitter;
            if (entry.attachToObject() && cue.thing() != null) {
                var thing = cue.thing();
                emitter = systems.start(entry.name(), () -> riding(thing, local, ring, tilt));
            } else {
                float x = at[0] + offset[0] + ring[0];
                float y = at[1] + offset[1] + ring[1];
                float z = entry.createAtGroundHeight() ? systems.groundAt(x, y) : at[2] + offset[2] + ring[2];
                emitter = systems.startAt(entry.name(), new Particles.Placement(x, y, z, 0f, axes));
            }
            if (emitter == null) {
                continue; // no such system, said once by the systems
            }
            if (!entry.initialDelay().isEmpty()) {
                emitter.delay((int) Math.ceil(draw(entry.initialDelay())));
            }
            if (entry.useCallersRadius() && cue.radius() > 0f) {
                emitter.volumeRadius(cue.radius());
            }
        }
    }

    /**
     * Where a system riding a thing stands this frame: at the thing, the offset turned the way it now faces, and
     * turned with it — the reference's attached system, drawn in its parent's frame. Null once the thing is gone
     * from the scene, which ends the system.
     */
    private static Particles.Placement riding(Spatial thing, float[] local, float[] ring, float[] tilt) {
        if (thing.getParent() == null) {
            return null;
        }
        var where = system(thing.getWorldTranslation());
        var turn = system(thing.getWorldRotation());
        var offset = turned(turn, local);
        return new Particles.Placement(where[0] + offset[0] + ring[0], where[1] + offset[1] + ring[1],
                where[2] + offset[2] + ring[2], 0f, times(turn, tilt));
    }

    // ---- lists at bones ----

    /** {@code FXListAtBonePosFXNugget}: the list at the bone by that name, and at every one numbered after it. */
    private void atBones(EffectList from, EffectList.AtBone entry, Cue cue, int depth) {
        if (cue.thing() == null || entry.effect() == null || entry.bone() == null) {
            return;
        }
        var list = named.apply(entry.effect());
        if (list == null) {
            once("no effect list is called '" + entry.effect() + "', named at a bone by '" + from.name() + "'");
            return;
        }
        if (depth >= DEEPEST) {
            once("'" + from.name() + "' plays lists at bones more than " + DEEPEST + " deep; the rest are not");
            return;
        }
        var bones = new java.util.ArrayList<Spatial>();
        var bare = Bones.named(cue.thing(), entry.bone());
        if (bare != null) {
            bones.add(bare);
        }
        for (int number = 1; number <= Bones.MOST_NUMBERED; number++) {
            var bone = Bones.named(cue.thing(), Bones.numbered(entry.bone(), number));
            if (bone == null) {
                break;
            }
            bones.add(bone);
        }
        for (var bone : bones) {
            play(list, new Cue(bone.getWorldTranslation().clone(),
                    entry.orientToBone() ? bone.getWorldRotation().clone() : cue.turn(), cue.thing(), cue.other(),
                    cue.radius()), depth + 1);
        }
    }

    // ---- sizes, draws and frames ----

    /** How wide a thing is drawn: the circle round its bound on the ground, the reference's bounding circle. */
    static float sizeOf(Spatial thing) {
        return switch (thing.getWorldBound()) {
            case BoundingBox box -> (float) Math.sqrt(box.getXExtent() * box.getXExtent()
                    + box.getZExtent() * box.getZExtent());
            case BoundingSphere sphere -> sphere.getRadius();
            case null, default -> 0f;
        };
    }

    /** A number from a range written in data: none is 0, one is itself, two is anywhere between. */
    private float draw(List<Float> range) {
        if (range == null || range.isEmpty()) {
            return 0f;
        }
        float least = Emitter.at(range, 0);
        float most = range.size() == 1 ? least : Emitter.at(range, 1);
        return least == most ? least : least + (most - least) * (float) random.nextDouble();
    }

    private void once(String message) {
        if (said.add(message)) {
            LOG.warning(message);
        }
    }

    /** A place in the client's frame as the systems' Z-up frame has it: see {@link LayeredEffects#placementAt}. */
    static float[] system(Vector3f at) {
        return new float[] {at.x, -at.z, at.y};
    }

    /** A place in the systems' frame as the client's has it. */
    static Vector3f client(float[] at) {
        return new Vector3f(at[0], at[2], -at[1]);
    }

    /** A turn in the client's frame as the systems' frame has it, row by row: the same turn, the axes renamed. */
    static float[] system(Quaternion turn) {
        var r = turn.toRotationMatrix();
        // P R P^T, P taking the client's (x, y, z) to the systems' (x, -z, y).
        return new float[] {
            r.get(0, 0), -r.get(0, 2), r.get(0, 1),
            -r.get(2, 0), r.get(2, 2), -r.get(2, 1),
            r.get(1, 0), -r.get(1, 2), r.get(1, 1)};
    }

    private static float[] vector(List<Float> written) {
        return new float[] {Emitter.at(written, 0), Emitter.at(written, 1), Emitter.at(written, 2)};
    }

    private static float[] turned(float[] m, float[] v) {
        if (m == null) {
            return v;
        }
        return new float[] {
            m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
            m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
            m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    private static float[] times(float[] a, float[] b) {
        var product = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                product[row * 3 + column] = a[row * 3] * b[column] + a[row * 3 + 1] * b[3 + column]
                        + a[row * 3 + 2] * b[6 + column];
            }
        }
        return product;
    }

    private static float[] aboutX(float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        return new float[] {1f, 0f, 0f, 0f, c, -s, 0f, s, c};
    }

    private static float[] aboutY(float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        return new float[] {c, 0f, s, 0f, 1f, 0f, -s, 0f, c};
    }

    static float[] aboutZ(float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        return new float[] {c, -s, 0f, s, c, 0f, 0f, 0f, 1f};
    }
}
