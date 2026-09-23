package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import uz.dukeengine.core.content.ParticleSystem;

/**
 * One particle system burning: its delay, its bursts, where it stands and the particles it has let out — the
 * reference game's {@code ParticleSystem::update} and {@code generateParticleInfo}, a frame at a time.
 *
 * <p>It stands at a place, rides a thing (following its place and its turn, and destroyed with it), or rides a
 * particle of another system. Destroyed or finished, it lets out nothing more and lives on until its last
 * particle dies, so nothing vanishes mid-air.
 */
final class Emitter {

    /** How big the running bonus of {@code startSizeRate} may grow — the reference's {@code MAX_SIZE_BONUS}. */
    static final float MOST_SIZE_BONUS = 50f;

    private final Particles world;
    private final ParticleSystem data;

    private final int[] alphaFrames;
    private final float[] alphaLeast;
    private final float[] alphaMost;
    private final int[] colourFrames;
    private final float[][] colours;

    /** Where it stands, asked each frame; null once it stands where it was put for good. */
    private Supplier<Particles.Placement> follows;
    /** The particle it rides, if it is a system riding one. */
    private final Particle control;
    private float x;
    private float y;
    private float z;
    private float turn;
    private float lastX;
    private float lastY;
    private float lastZ;
    private boolean firstPlace = true;

    private Emitter slave;
    private Emitter master;

    private int delayLeft;
    private int burstDelayLeft;
    private int lifetimeLeft;
    private final boolean forever;
    private boolean destroyed;
    private float sizeBonus;

    private final List<Particle> particles = new ArrayList<>();

    Emitter(Particles world, ParticleSystem data, Supplier<Particles.Placement> follows, Particle control) {
        this.world = world;
        this.data = data;
        this.follows = follows;
        this.control = control;

        var alpha = keysUntilTheFirstUnused(data.alphaKeys().stream().map(ParticleSystem.AlphaKey::frame).toList());
        this.alphaFrames = new int[Math.max(1, alpha)];
        this.alphaLeast = new float[alphaFrames.length];
        this.alphaMost = new float[alphaFrames.length];
        if (alpha == 0) {
            alphaLeast[0] = 1f; // none said: fully opaque
            alphaMost[0] = 1f;
        }
        for (int at = 0; at < alpha; at++) {
            var key = data.alphaKeys().get(at);
            alphaFrames[at] = key.frame();
            alphaLeast[at] = key.min();
            alphaMost[at] = key.max();
        }
        var colour = keysUntilTheFirstUnused(data.colourKeys().stream().map(ParticleSystem.ColourKey::frame).toList());
        this.colourFrames = new int[Math.max(1, colour)];
        this.colours = new float[colourFrames.length][];
        colours[0] = new float[] {1f, 1f, 1f}; // none said: white
        for (int at = 0; at < colour; at++) {
            var key = data.colourKeys().get(at);
            colourFrames[at] = key.frame();
            colours[at] = new float[] {
                (key.colour() >> 16 & 0xFF) / 255f, (key.colour() >> 8 & 0xFF) / 255f, (key.colour() & 0xFF) / 255f};
        }

        this.delayLeft = (int) world.draw(data.initialDelay());
        this.lifetimeLeft = data.systemLifetime();
        this.forever = data.systemLifetime() == 0;
        if (control != null) {
            place(control.x, control.y, control.z, 0f);
        } else if (follows != null) {
            var at = follows.get();
            if (at == null) {
                destroyed = true;
            } else {
                place(at.x(), at.y(), at.z(), at.turn());
            }
        }
    }

    /**
     * How many keys are in use: the reference keeps eight and ends the list at the first after the first whose
     * frame is 0, so a key written after one of those is never reached.
     */
    private static int keysUntilTheFirstUnused(List<Integer> frames) {
        for (int at = 1; at < frames.size(); at++) {
            if (frames.get(at) == 0) {
                return at;
            }
        }
        return frames.size();
    }

    ParticleSystem data() {
        return data;
    }

    int[] alphaFrames() {
        return alphaFrames;
    }

    int[] colourFrames() {
        return colourFrames;
    }

    float[][] colours() {
        return colours;
    }

    /** Its particles, in the order they were born — a streak joins them in this order. Some may be gone. */
    List<Particle> particles() {
        return particles;
    }

    boolean isDestroyed() {
        return destroyed;
    }

    Emitter slave() {
        return slave;
    }

    void slaveTo(Emitter master) {
        this.master = master;
        master.slave = this;
    }

    /** Let out nothing more; it is removed once its last particle dies. Its slave goes the same way. */
    void destroy() {
        destroyed = true;
        follows = null;
        if (slave != null) {
            slave.destroy();
        }
    }

    /** One frame; false once it is over and holds nothing more. */
    boolean update(int frame) {
        if (delayLeft > 0) {
            delayLeft--; // nothing at all happens until its delay has passed
            return true;
        }
        follow();
        if (!destroyed && (forever || lifetimeLeft > 0) && master == null) {
            if (burstDelayLeft == 0) {
                burst();
                burstDelayLeft = (int) world.draw(data.burstDelay());
            } else {
                burstDelayLeft--;
            }
        }
        float gravity = data.gravity();
        var drift = data.driftVelocity();
        float dx = at(drift, 0);
        float dy = at(drift, 1);
        float dz = at(drift, 2);
        for (var each = particles.iterator(); each.hasNext();) {
            var particle = each.next();
            if (particle.gone) {
                each.remove(); // let go of by the budget
                continue;
            }
            if (gravity != 0f) {
                particle.push(0f, 0f, gravity);
            }
            if (!particle.update(frame, dx, dy, dz)) {
                world.over(particle);
                each.remove();
            }
        }
        if (destroyed && particles.isEmpty()) {
            return false;
        }
        if (!forever) {
            if (lifetimeLeft > 0) {
                lifetimeLeft--;
            }
            if (!particles.isEmpty()) {
                return true; // still some in the air
            }
            return lifetimeLeft != 0;
        }
        return true;
    }

    /** Where it stands this frame: on the particle it rides, or where what it rides says it is. */
    private void follow() {
        if (control != null) {
            place(control.x, control.y, control.z, 0f);
            return;
        }
        if (follows == null) {
            return;
        }
        var at = follows.get();
        if (at == null) {
            destroy(); // what it rode is gone, and it goes with it
            return;
        }
        place(at.x(), at.y(), at.z(), at.turn());
    }

    private void place(float atX, float atY, float atZ, float turnedBy) {
        lastX = x;
        lastY = y;
        lastZ = z;
        x = atX;
        y = atY;
        z = atZ;
        turn = turnedBy;
    }

    /** A burst: its count of particles at once, each with a slave and a rider where the system has them. */
    private void burst() {
        int count = (int) world.draw(data.burstCount());
        for (int number = 0; number < count; number++) {
            var born = born(number, count);
            if (data.isEmitAboveGroundOnly() && born.z() < world.groundAt(born.x(), born.y())) {
                continue; // it would be born under the ground
            }
            var particle = world.make(this, born);
            if (particle == null) {
                continue; // refused by the budget
            }
            if (data.perParticleAttachedSystem() != null) {
                world.ride(particle, data.perParticleAttachedSystem());
            }
            if (slave != null) {
                slave.slaveOf(born, data.slavePosOffset());
            }
        }
    }

    /**
     * A particle's birth, drawn: where and how fast from the volume and the velocity type, turned and placed by
     * where the system stands; then everything else it is born with, in the reference's order.
     *
     * <p>A burst from a system that moved since the last frame is spread back along the way it came, the first
     * of the burst furthest back — so a rocket's smoke is a line behind it, not a clump at every frame's end.
     */
    Particle.Born born(int number, int count) {
        var local = position();
        var velocity = velocity(local);
        if (firstPlace) {
            lastX = x;
            lastY = y;
            lastZ = z;
            firstPlace = false;
        }
        float back = 1f - (float) number / count;
        float cos = (float) Math.cos(turn);
        float sin = (float) Math.sin(turn);
        float bornX = x + cos * local[0] - sin * local[1] - back * (x - lastX);
        float bornY = y + sin * local[0] + cos * local[1] - back * (y - lastY);
        float bornZ = z + local[2] - back * (z - lastZ);
        float velocityX = cos * velocity[0] - sin * velocity[1];
        float velocityY = sin * velocity[0] + cos * velocity[1];

        float velocityDamping = world.draw(data.velocityDamping());
        float angularDamping = world.draw(data.angularDamping());
        float angle = world.draw(data.angle());
        float angularRate = world.draw(data.angularRate());
        int lifetime = (int) world.draw(data.lifetime());
        float size = world.draw(data.size()) + sizeBonus;
        float sizeRate = world.draw(data.sizeRate());
        float sizeRateDamping = world.draw(data.sizeRateDamping());
        sizeBonus += world.draw(data.startSizeRate());
        if (sizeBonus != 0f) {
            sizeBonus = Math.min(sizeBonus, MOST_SIZE_BONUS);
        }
        var alphas = new float[alphaFrames.length];
        for (int key = 0; key < alphas.length; key++) {
            alphas[key] = world.between(alphaLeast[key], alphaMost[key]);
        }
        float colourScale = world.draw(data.colourScale()) / 255f;
        return new Particle.Born(bornX, bornY, bornZ, velocityX, velocityY, velocity[2], velocityDamping, angle,
                angularRate, angularDamping, lifetime, size, sizeRate, sizeRateDamping, alphas, colourScale, x, y);
    }

    /**
     * The particle a slave makes for one its master was born with: at the master's particle, plus the master's
     * offset, moving as it moves — and drawn with this system's own look. Its size, its size's rate and that
     * rate's damping are this system's times the master's, as the reference merges them
     * ({@code mergeRelatedParticleSystems}).
     */
    private void slaveOf(Particle.Born master, List<Float> offset) {
        int lifetime = (int) world.draw(data.lifetime());
        float size = world.draw(data.size()) + sizeBonus;
        float sizeRate = world.draw(data.sizeRate());
        float sizeRateDamping = world.draw(data.sizeRateDamping());
        sizeBonus += world.draw(data.startSizeRate());
        if (sizeBonus != 0f) {
            sizeBonus = Math.min(sizeBonus, MOST_SIZE_BONUS);
        }
        float angle = world.draw(data.angle());
        float angularRate = world.draw(data.angularRate());
        float angularDamping = world.draw(data.angularDamping());
        var alphas = new float[alphaFrames.length];
        for (int key = 0; key < alphas.length; key++) {
            alphas[key] = world.between(alphaLeast[key], alphaMost[key]);
        }
        float colourScale = world.draw(data.colourScale()) / 255f;
        world.make(this, new Particle.Born(master.x() + at(offset, 0), master.y() + at(offset, 1),
                master.z() + at(offset, 2), master.vx(), master.vy(), master.vz(), master.velocityDamping(), angle,
                angularRate, angularDamping, lifetime, master.size() * size, master.sizeRate() * sizeRate,
                master.sizeRateDamping() * sizeRateDamping, alphas, colourScale, master.emitterX(),
                master.emitterY()));
    }

    // ---- where in the volume, and how fast ----

    /** Where in its volume a particle is born, before the system's place and turn. */
    private float[] position() {
        return switch (data.volumeType()) {
            case POINT -> new float[3];
            case LINE -> {
                var start = data.volLineStart();
                var end = data.volLineEnd();
                float along = world.between(0f, 1f);
                yield new float[] {
                    at(start, 0) + along * (at(end, 0) - at(start, 0)),
                    at(start, 1) + along * (at(end, 1) - at(start, 1)),
                    at(start, 2) + along * (at(end, 2) - at(start, 2))};
            }
            case BOX -> box(data.volBoxHalfSize());
            case SPHERE -> {
                float radius = data.isHollow() ? data.volSphereRadius() : world.between(0f, data.volSphereRadius());
                var direction = pointOnUnitSphere();
                yield new float[] {direction[0] * radius, direction[1] * radius, direction[2] * radius};
            }
            case CYLINDER -> {
                float around = world.between(0f, (float) (2 * Math.PI));
                float radius = data.isHollow() ? data.volCylinderRadius()
                        : world.between(0f, data.volCylinderRadius());
                float half = data.volCylinderLength() / 2f;
                yield new float[] {radius * (float) Math.cos(around), radius * (float) Math.sin(around),
                    world.between(-half, half)};
            }
        };
    }

    /** Anywhere in the box, or — hollow — anywhere on one of its six faces, each as likely as the next. */
    private float[] box(List<Float> half) {
        float hx = at(half, 0);
        float hy = at(half, 1);
        float hz = at(half, 2);
        var inside = new float[] {world.between(-hx, hx), world.between(-hy, hy), world.between(-hz, hz)};
        if (!data.isHollow()) {
            return inside;
        }
        int face = world.face();
        int axis = face % 3;
        float reach = axis == 0 ? hx : axis == 1 ? hy : hz;
        inside[axis] = face < 3 ? -reach : reach;
        return inside;
    }

    /** How fast, and which way, a particle born at {@code local} sets off. */
    private float[] velocity(float[] local) {
        return switch (data.velocityType()) {
            case ORTHO -> new float[] {
                world.draw(data.velOrthoX()), world.draw(data.velOrthoY()), world.draw(data.velOrthoZ())};
            case SPHERICAL -> scaled(pointOnUnitSphere(), world.draw(data.velSpherical()));
            case HEMISPHERICAL -> {
                float speed = world.draw(data.velHemispherical());
                float[] direction;
                do {
                    direction = new float[] {world.between(-1f, 1f), world.between(-1f, 1f), world.between(0f, 1f)};
                } while (direction[0] == 0f && direction[1] == 0f && direction[2] == 0f);
                yield scaled(normalised(direction), speed);
            }
            case CYLINDRICAL -> {
                float radial = world.draw(data.velCylindricalRadial());
                float around = world.between(0f, (float) (2 * Math.PI));
                yield new float[] {radial * (float) Math.cos(around), radial * (float) Math.sin(around),
                    world.draw(data.velCylindricalNormal())};
            }
            case OUTWARD -> outward(local);
        };
    }

    /** Along the volume's outward normal where the particle is born, from its middle. */
    private float[] outward(float[] local) {
        float speed = world.draw(data.velOutward());
        float other = world.draw(data.velOutwardOther());
        return switch (data.volumeType()) {
            case CYLINDER -> {
                var across = normalised(new float[] {local[0], local[1], 0f});
                yield new float[] {speed * across[0], speed * across[1], other};
            }
            case BOX, SPHERE -> scaled(normalised(local.clone()), speed);
            case LINE -> {
                var start = data.volLineStart();
                var end = data.volLineEnd();
                var along = normalised(new float[] {
                    at(end, 0) - at(start, 0), at(end, 1) - at(start, 1), at(end, 2) - at(start, 2)});
                // Across the line on the ground, and "other" up the plane the line stands in.
                var across = cross(new float[] {0f, 0f, 1f}, along);
                var up = cross(along, across);
                yield new float[] {speed * across[0] + other * up[0], speed * across[1] + other * up[1],
                    speed * across[2] + other * up[2]};
            }
            case POINT -> scaled(pointOnUnitSphere(), speed);
        };
    }

    /**
     * A direction: a point in the cube round the middle, pushed out to the sphere. Not even over the sphere —
     * the corners of the cube crowd it — which is how the reference draws it, and so how its bursts look.
     */
    private float[] pointOnUnitSphere() {
        float[] point;
        do {
            point = new float[] {world.between(-1f, 1f), world.between(-1f, 1f), world.between(-1f, 1f)};
        } while (point[0] == 0f && point[1] == 0f && point[2] == 0f);
        return normalised(point);
    }

    private static float[] normalised(float[] vector) {
        float length = (float) Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length == 0f) {
            return vector;
        }
        return new float[] {vector[0] / length, vector[1] / length, vector[2] / length};
    }

    private static float[] scaled(float[] vector, float by) {
        return new float[] {vector[0] * by, vector[1] * by, vector[2] * by};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** One number of a vector written in data, 0 where it was not written. */
    static float at(List<Float> vector, int index) {
        return vector == null || index >= vector.size() || vector.get(index) == null ? 0f : vector.get(index);
    }
}
