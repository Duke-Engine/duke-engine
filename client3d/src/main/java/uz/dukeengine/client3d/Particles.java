package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.content.ParticleSystem;

/**
 * Every particle system burning, stepped at the game's own rate — the reference game's
 * {@code ParticleSystemManager}.
 *
 * <p><b>Frames, not seconds.</b> Every rate a system is written with is per frame of the game's 30 a second, and
 * the reference moves its particles once a logic frame whatever the screen does. So this is handed the client's
 * time and runs whole frames of it, one after another: a system looks the same at 30 frames a second and at
 * 144, and no curve is bent to fit a frame of another length.
 *
 * <p><b>Its own random numbers.</b> Everything a particle draws comes from this, seeded, and never from the
 * simulation's: sparks are something each machine sees for itself, and a spark that took a number the
 * simulation wanted would move every draw after it on one machine and not the other. The same seed makes the
 * same systems.
 *
 * <p><b>A budget.</b> Past its most, making a particle first lets go of the oldest of a lower priority, the
 * lowest first; one that cannot make room is not made, and one at or above the highest priority is never
 * refused. Left unset there is no most, and nothing is let go of.
 */
final class Particles {

    private static final Logger LOG = Logger.getLogger(Particles.class.getName());

    /** One of the game's frames, in the client's seconds. */
    static final float FRAME_SECONDS = 1f / GameConstants.LOGICFRAMES_PER_SECOND;

    /** The most frames run for one of the client's: a longer stall is let go rather than run all at once. */
    private static final int MOST_FRAMES_AT_ONCE = 8;

    /**
     * Where a system stands, in its own Z-up frame, and how it is turned: about up by {@code turn}, in radians —
     * or, where {@code axes} is given, by that rotation, its nine numbers row by row, which is how a system
     * turned with a bone or tipped over by an effect list stands.
     */
    record Placement(float x, float y, float z, float turn, float[] axes) {

        Placement(float x, float y, float z, float turn) {
            this(x, y, z, turn, null);
        }

        /** Its rotation, row by row: {@code axes}, or the turn about up. */
        float[] rotation() {
            if (axes != null) {
                return axes;
            }
            float cos = (float) Math.cos(turn);
            float sin = (float) Math.sin(turn);
            return new float[] {cos, -sin, 0f, sin, cos, 0f, 0f, 0f, 1f};
        }
    }

    /** How high the ground stands at a place, in the same frame. */
    @FunctionalInterface
    interface Ground {
        Ground FLAT = (x, y) -> 0f;

        float heightAt(float x, float y);
    }

    private final Function<String, ParticleSystem> named;
    private final SplittableRandom random;
    private final Ground ground;
    private final int most;
    private final int neverRefusedFrom;

    private final List<Emitter> emitters = new ArrayList<>();
    /** The particles alive, by priority, each set oldest first — what the budget lets go of. */
    private final TreeMap<Integer, LinkedHashSet<Particle>> living = new TreeMap<>();
    private final Set<String> unknown = new HashSet<>();
    private int count;
    private long made;
    private int frame;
    private float owed;

    /**
     * @param named            the game's systems, by name
     * @param seed             where its random numbers start
     * @param ground           the height of the ground, for systems that emit above it only
     * @param most             the budget's most particles; {@link Integer#MAX_VALUE} for none
     * @param neverRefusedFrom the priority from which a particle is never refused
     */
    Particles(Function<String, ParticleSystem> named, long seed, Ground ground, int most, int neverRefusedFrom) {
        this.named = named;
        this.random = new SplittableRandom(seed);
        this.ground = ground == null ? Ground.FLAT : ground;
        this.most = Math.max(0, most);
        this.neverRefusedFrom = neverRefusedFrom;
    }

    // ---- starting and stopping ----

    /**
     * Start the system of this name where {@code where} says, asked again every frame: a thing's place and turn
     * to ride it, the same place always to stand still. A supplier that says null has lost what it followed,
     * and the system is destroyed with it.
     *
     * @return the system, or null where the game has none of that name — said once
     */
    Emitter start(String name, Supplier<Placement> where) {
        var data = find(name);
        return data == null ? null : start(data, where, null);
    }

    /** A system standing still at one place. */
    Emitter startAt(String name, Placement at) {
        return start(name, () -> at);
    }

    private Emitter start(ParticleSystem data, Supplier<Placement> where, Particle control) {
        var emitter = new Emitter(this, data, where, control);
        emitters.add(emitter);
        if (data.slaveSystem() != null) {
            var slave = find(data.slaveSystem());
            if (slave != null) {
                // A slave makes nothing of its own; it is here to be given its master's particles. It is given
                // no slave of its own, which is what keeps a system named as its own slave from never ending.
                new Emitter(this, slave, where, null).slaveTo(emitter);
                emitters.add(emitter.slave());
            }
        }
        return emitter;
    }

    /** A whole system riding a particle, which destroys it when it dies. */
    void ride(Particle particle, String name) {
        if (name.equals(particle.system.data().name())) {
            if (unknown.add("rides itself: " + name)) {
                LOG.warning(() -> "the particle system '" + name + "' rides its own particles, which never ends;"
                        + " it is not ridden");
            }
            return;
        }
        var data = find(name);
        if (data != null) {
            particle.riding = start(data, null, particle);
        }
    }

    private ParticleSystem find(String name) {
        var data = name == null ? null : named.apply(name);
        if (data == null && name != null && unknown.add(name)) {
            LOG.warning(() -> "no particle system is called '" + name + "'; nothing is drawn for it");
        }
        return data;
    }

    /** Every system gone at once, for a new world. */
    void clear() {
        emitters.clear();
        living.clear();
        count = 0;
    }

    // ---- time ----

    /** The client's time passing: as many of the game's frames as it holds, and the rest kept for the next. */
    void step(float seconds) {
        owed += Math.max(0f, seconds);
        int run = 0;
        while (owed >= FRAME_SECONDS && run < MOST_FRAMES_AT_ONCE) {
            update();
            owed -= FRAME_SECONDS;
            run++;
        }
        if (run == MOST_FRAMES_AT_ONCE) {
            owed = 0f; // a stall: what it missed is not played back all at once
        }
    }

    /** One of the game's frames, for every system — a system started this frame by another included. */
    void update() {
        frame++;
        var over = new ArrayList<Emitter>();
        for (int at = 0; at < emitters.size(); at++) {
            var emitter = emitters.get(at);
            if (!emitter.update(frame)) {
                over.add(emitter);
            }
        }
        if (!over.isEmpty()) {
            emitters.removeAll(over);
        }
    }

    List<Emitter> emitters() {
        return Collections.unmodifiableList(emitters);
    }

    int count() {
        return count;
    }

    int frame() {
        return frame;
    }

    // ---- for its systems ----

    /** A particle made for {@code emitter}, or null where the budget has no room for it. */
    Particle make(Emitter emitter, Particle.Born born) {
        int priority = emitter.data().priority();
        if (priority < neverRefusedFrom) {
            // The reference's arithmetic, room made for whatever is over the most before this one is added.
            int over = count - most;
            if (over > 0 && letGo(over, priority) != over) {
                return null;
            }
            if (most == 0) {
                return null;
            }
        }
        var particle = new Particle(emitter, made++, frame, born);
        emitter.particles().add(particle);
        living.computeIfAbsent(priority, level -> new LinkedHashSet<>()).add(particle);
        count++;
        return particle;
    }

    /** A particle's life is over by its own rules. */
    void over(Particle particle) {
        untrack(particle);
    }

    /** Let go of up to {@code wanted} of the oldest particles below {@code priority}, the lowest first. */
    private int letGo(int wanted, int priority) {
        int let = 0;
        while (let < wanted) {
            Particle oldest = null;
            for (var level : living.headMap(priority, false).values()) {
                if (!level.isEmpty()) {
                    oldest = level.iterator().next();
                    break;
                }
            }
            if (oldest == null) {
                break;
            }
            oldest.gone = true;
            untrack(oldest);
            let++;
        }
        return let;
    }

    private void untrack(Particle particle) {
        var level = living.get(particle.system.data().priority());
        if (level != null && level.remove(particle)) {
            count--;
        }
        if (particle.riding != null) {
            particle.riding.destroy(); // what rode it goes with it
            particle.riding = null;
        }
    }

    /** A number from a range written in data: none is 0, one is itself, two is anywhere between. */
    float draw(List<Float> range) {
        if (range == null || range.isEmpty()) {
            return 0f;
        }
        float least = Emitter.at(range, 0);
        return range.size() == 1 ? least : between(least, Emitter.at(range, 1));
    }

    /** Anywhere from {@code least} to {@code most}; the one number where they are one, drawing nothing. */
    float between(float least, float most) {
        return least == most ? least : least + (most - least) * (float) random.nextDouble();
    }

    /** One of a box's six faces, each as likely. */
    int face() {
        return random.nextInt(6);
    }

    float groundAt(float x, float y) {
        return ground.heightAt(x, y);
    }
}
