package uz.dukeengine.core.content;

import java.util.List;
import uz.dukeengine.core.data.Link;

/**
 * A particle system as the reference game describes one: every field of its {@code ParticleSystem} block, with
 * the meaning its source gives them ({@code ParticleSys.cpp} — {@code Particle::update},
 * {@code ParticleSystem::update}, {@code generateParticleInfo}, {@code computeParticlePosition},
 * {@code computeParticleVelocity}). The client draws it; nothing in the simulation reads it.
 *
 * <p><b>Why a second kind of effect.</b> An {@link Effect} is a list of layers — a colour at birth and at death,
 * a size along one curve, a handful of named directions — which is what a dungeon's spells needed. An RTS's
 * explosions, smoke, fire, dust, muzzle flashes and tracers are written in a different, fully specified model,
 * and none of them can be put in a layer's terms: the RTS this was measured in has 1087 of them, every one
 * giving every field. So this is that model, field for field, and an effect keeps its own beside it. Wherever a
 * game names an effect it may name one of these instead.
 *
 * <p><b>Time is frames</b> of the game's own 30 a second, as in the reference's data, and every rate below is
 * per frame; the client steps these at that rate however fast it draws, so no curve changes. <b>Space is
 * Z-up</b> — x and y across the ground, z up, the frame the game's models were made in — and the client turns
 * it by the same quarter turn it turns those models.
 *
 * <p><b>A range</b> is written {@code [min, max]}, and each particle draws its own value between the two from
 * the client's random numbers, never the simulation's; one number is that number, and none is 0. A vector is
 * {@code [x, y, z]}.
 *
 * @param priority         how much it matters when there are too many particles: past the client's budget the
 *                         oldest particles of a lower priority go first, and one at or above the budget's
 *                         highest is never refused
 * @param blend            how it is drawn over what is behind it
 * @param type             what each particle is drawn as
 * @param texture          the picture each particle is drawn with, a path as the game's art is laid out
 * @param angle            its turn about the view axis at birth, in radians
 * @param angularRate      how much that turn changes each frame
 * @param angularDamping   what the rate is multiplied by each frame; 1 keeps it
 * @param velocityDamping  what its velocity is multiplied by each frame; 1 keeps it
 * @param gravity          added to its upward velocity each frame; up is positive, so falling is negative
 * @param lifetime         frames it lives; 0 is until it can no longer be seen
 * @param systemLifetime   frames the system emits for; 0 is until it is stopped. A stopped or finished system
 *                         lives on until its last particle dies
 * @param size             its size at birth: the side of its square, in world units
 * @param startSizeRate    added to a running bonus with every particle the system makes, the bonus added to
 *                         each new particle's size and capped at 50
 * @param sizeRate         added to its size each frame
 * @param sizeRateDamping  what that rate is multiplied by each frame; 1 keeps it
 * @param alphaKeys        up to eight, each a value the particle draws for itself between two, and the frame
 *                         it reaches it on; the first is its alpha at birth. None is fully opaque
 * @param colourKeys       up to eight, each a colour and the frame it is reached on; the first is its colour
 *                         at birth. None is white
 * @param colourScale      added to all three of its colour's channels every frame, in 0-255 like the colours
 * @param burstDelay       frames between one burst and the next
 * @param burstCount       particles a burst makes at once
 * @param initialDelay     frames before anything happens at all
 * @param driftVelocity    added to every particle's position each frame
 * @param slaveSystem      a system every particle born here also makes one particle in: at the same place
 *                         plus {@code slavePosOffset}, moving with it, drawn with the slave's own look
 * @param perParticleAttachedSystem a whole system riding each particle — smoke behind a flying spark — gone
 *                         when its particle dies
 * @param velocityType     how a particle's velocity at birth is decided; see the {@code vel} ranges
 * @param volumeType       where in the system a particle is born; see the {@code vol} fields
 * @param isHollow         for a sphere, born on its surface; for a cylinder, on its rim; for a box, on a face
 * @param isGroundAligned  drawn lying flat rather than facing the camera
 * @param isEmitAboveGroundOnly a particle that would be born under the ground is not born
 * @param isParticleUpTowardsEmitter turned each frame so its top points back at where the system emits from,
 *                         instead of turning by its angular rate
 */
public record ParticleSystem(String name, int priority, Blend blend, Type type, String texture,
        List<Float> angle, List<Float> angularRate, List<Float> angularDamping, List<Float> velocityDamping,
        float gravity, List<Float> lifetime, int systemLifetime, List<Float> size, List<Float> startSizeRate,
        List<Float> sizeRate, List<Float> sizeRateDamping, List<AlphaKey> alphaKeys, List<ColourKey> colourKeys,
        List<Float> colourScale, List<Float> burstDelay, List<Float> burstCount, List<Float> initialDelay,
        List<Float> driftVelocity, @Link(ParticleSystem.class) String slaveSystem, List<Float> slavePosOffset,
        @Link(ParticleSystem.class) String perParticleAttachedSystem, VelocityType velocityType,
        List<Float> velOrthoX, List<Float> velOrthoY, List<Float> velOrthoZ, List<Float> velSpherical,
        List<Float> velHemispherical, List<Float> velCylindricalRadial, List<Float> velCylindricalNormal,
        List<Float> velOutward, List<Float> velOutwardOther, VolumeType volumeType, List<Float> volLineStart,
        List<Float> volLineEnd, List<Float> volBoxHalfSize, float volSphereRadius, float volCylinderRadius,
        float volCylinderLength, boolean isHollow, boolean isGroundAligned, boolean isEmitAboveGroundOnly,
        boolean isParticleUpTowardsEmitter) {

    /** How it is drawn over what is behind it — the reference's four shaders. */
    public enum Blend {
        /** Added to what is behind: fire, sparks, glows. Its alpha never changes, and it dies once black. */
        ADDITIVE,
        /** Blended by its alpha: smoke, dust. It dies once its alpha is under 0.02. */
        ALPHA,
        /** Drawn where its picture's alpha passes a test, and not blended; never dies of being unseen. */
        ALPHA_TEST,
        /** Multiplied with what is behind: a scorch. It dies once all but white. */
        MULTIPLY
    }

    /** What each particle is drawn as. */
    public enum Type {
        /** A square facing the camera, or lying flat where the system is ground-aligned. */
        PARTICLE,
        /** The system's particles joined, in order, into one strip — a tracer. */
        STREAK,
        /** Drawn as a {@code PARTICLE}; the reference layers it in depth, which this client does not. */
        VOLUME_PARTICLE,
        /** A model a particle — not drawn by this client, and said so once. */
        DRAWABLE
    }

    /** How a particle's velocity at birth is decided. */
    public enum VelocityType {
        /** {@code velOrthoX}, {@code velOrthoY}, {@code velOrthoZ}, each its own range. */
        ORTHO,
        /** {@code velSpherical} times a random direction. */
        SPHERICAL,
        /** {@code velHemispherical} times a random direction that is never downward. */
        HEMISPHERICAL,
        /** {@code velCylindricalRadial} in a random direction across the ground, plus {@code velCylindricalNormal} up. */
        CYLINDRICAL,
        /**
         * {@code velOutward} along the volume's outward normal where it is born, from its centre: across the
         * ground for a cylinder, plus {@code velOutwardOther} up; across a line, plus that up; in a random
         * direction from a point.
         */
        OUTWARD
    }

    /** Where in the system a particle is born, relative to the system's place and turn. */
    public enum VolumeType {
        /** At the system's place. */
        POINT,
        /** Anywhere between {@code volLineStart} and {@code volLineEnd}. */
        LINE,
        /** Anywhere within {@code volBoxHalfSize} of the middle. */
        BOX,
        /** Within {@code volSphereRadius}. */
        SPHERE,
        /** Within {@code volCylinderRadius} across and {@code volCylinderLength} along up. */
        CYLINDER
    }

    /**
     * One alpha key: the particle draws its own value between {@code min} and {@code max}, and has it on
     * {@code frame}.
     */
    public record AlphaKey(float min, float max, int frame) {
    }

    /** One colour key: {@code colour} packed {@code 0xRRGGBB}, reached on {@code frame}. */
    public record ColourKey(int colour, int frame) {
    }

    /** What a block leaves out: nothing damped, fully opaque, white, a point, fired straight up at nothing. */
    static final ParticleSystem DEFAULTS = new ParticleSystem(null, 0, Blend.ALPHA, Type.PARTICLE, null,
            List.of(), List.of(), List.of(1f), List.of(1f), 0f, List.of(), 0, List.of(), List.of(), List.of(),
            List.of(1f), List.of(), List.of(), List.of(), List.of(), List.of(1f), List.of(), List.of(), null,
            List.of(), null, VelocityType.ORTHO, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), VolumeType.POINT, List.of(), List.of(), List.of(), 0f, 0f, 0f, false,
            false, false, false);

    public ParticleSystem {
        blend = blend == null ? Blend.ALPHA : blend;
        type = type == null ? Type.PARTICLE : type;
        velocityType = velocityType == null ? VelocityType.ORTHO : velocityType;
        volumeType = volumeType == null ? VolumeType.POINT : volumeType;
        alphaKeys = alphaKeys == null ? List.of() : List.copyOf(alphaKeys);
        colourKeys = colourKeys == null ? List.of() : List.copyOf(colourKeys);
        if (alphaKeys.size() > 8 || colourKeys.size() > 8) {
            throw new IllegalArgumentException("a particle system has up to eight alpha keys and eight colour keys");
        }
    }
}
