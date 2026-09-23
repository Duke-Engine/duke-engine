package uz.dukeengine.client3d;

import uz.dukeengine.core.content.ParticleSystem;

/**
 * One particle of a particle system, as the reference game moves it a frame at a time ({@code Particle::update}).
 * Kept in the system's own Z-up frame, where every rule below is written; the drawing turns it into the client's.
 *
 * <p>Every value here was decided at its birth — drawn between its system's least and most from the client's
 * own random numbers — and from then on it only follows its rules, frame by frame.
 */
final class Particle {

    /** Everything a particle is born with — the reference's {@code ParticleInfo}. */
    record Born(float x, float y, float z, float vx, float vy, float vz, float velocityDamping,
            float angle, float angularRate, float angularDamping, int lifetime, float size, float sizeRate,
            float sizeRateDamping, float[] alphas, float colourScale, float emitterX, float emitterY) {
    }

    final Emitter system;
    /** Its place in the order every particle was made in, for the budget to let the oldest go first. */
    final long made;
    private final int born;

    float x;
    float y;
    float z;
    private float vx;
    private float vy;
    private float vz;
    private float ax;
    private float ay;
    private float az;
    private final float velocityDamping;

    float angle;
    private float angularRate;
    private final float angularDamping;

    float size;
    private float sizeRate;
    private final float sizeRateDamping;

    float alpha;
    private float alphaRate;
    private int alphaTarget = 1;
    private final float[] alphas;

    float red;
    float green;
    float blue;
    private float redRate;
    private float greenRate;
    private float blueRate;
    private int colourTarget = 1;
    private final float colourScale;

    private int lifetimeLeft;
    private final float emitterX;
    private final float emitterY;

    /** A whole system riding this particle, gone when it dies — see {@link ParticleSystem#perParticleAttachedSystem}. */
    Emitter riding;
    boolean gone;

    Particle(Emitter system, long made, int frame, Born info) {
        this.system = system;
        this.made = made;
        this.born = frame;
        this.x = info.x();
        this.y = info.y();
        this.z = info.z();
        this.vx = info.vx();
        this.vy = info.vy();
        this.vz = info.vz();
        this.velocityDamping = info.velocityDamping();
        this.angle = info.angle();
        this.angularRate = info.angularRate();
        this.angularDamping = info.angularDamping();
        this.lifetimeLeft = info.lifetime();
        this.size = info.size();
        this.sizeRate = info.sizeRate();
        this.sizeRateDamping = info.sizeRateDamping();
        this.alphas = info.alphas();
        this.colourScale = info.colourScale();
        this.emitterX = info.emitterX();
        this.emitterY = info.emitterY();
        this.alpha = alphas[0];
        alphaRate();
        var colours = system.colours();
        this.red = colours[0][0];
        this.green = colours[0][1];
        this.blue = colours[0][2];
        colourRate();
    }

    /** Add an acceleration for this frame — the system's gravity, which is up. */
    void push(float x, float y, float z) {
        ax += x;
        ay += y;
        az += z;
    }

    /**
     * One frame of its life; false once it is over. In the reference's order: velocity, place, turn, size,
     * alpha, colour, then whether it has lived its time or can no longer be seen.
     *
     * @param frame the system's frame counter, which its keys are counted against from its birth
     */
    boolean update(int frame, float driftX, float driftY, float driftZ) {
        vx = (vx + ax) * velocityDamping;
        vy = (vy + ay) * velocityDamping;
        vz = (vz + az) * velocityDamping;
        x += vx + driftX;
        y += vy + driftY;
        z += vz + driftZ;

        angle += angularRate;
        angularRate *= angularDamping;
        if (system.data().isParticleUpTowardsEmitter()) {
            angle = angleFromUp(x - emitterX, y - emitterY) + (float) Math.PI;
        }

        size += sizeRate;
        sizeRate *= sizeRateDamping;

        int age = frame - born;
        var keys = system.alphaFrames();
        // An additive particle is as bright as its colour, so its alpha is never moved.
        if (system.data().blend() != ParticleSystem.Blend.ADDITIVE) {
            alpha += alphaRate;
            if (alphaTarget < keys.length) {
                if (age >= keys[alphaTarget]) {
                    alpha = alphas[alphaTarget]; // set exactly on reaching it
                    alphaTarget++;
                    alphaRate();
                }
            } else {
                alphaRate = 0f;
            }
            alpha = clamp(alpha);
        }

        red += redRate;
        green += greenRate;
        blue += blueRate;
        var colourKeys = system.colourFrames();
        if (colourTarget < colourKeys.length) {
            if (age >= colourKeys[colourTarget]) {
                // Not set on reaching it, because the scale below has been added all along.
                colourTarget++;
                colourRate();
            }
        } else {
            redRate = 0f;
            greenRate = 0f;
            blueRate = 0f;
        }
        red = clamp(red + colourScale);
        green = clamp(green + colourScale);
        blue = clamp(blue + colourScale);

        ax = 0f;
        ay = 0f;
        az = 0f;

        if (lifetimeLeft != 0 && --lifetimeLeft == 0) {
            return false;
        }
        return !unseen();
    }

    /**
     * Whether it can no longer be seen, and so is over whatever its lifetime says: an alpha one under 0.02; an
     * additive one black — r + g + b at most 0.06 — once past its last colour key; a multiplying one all but
     * white — r × g × b over 0.95 — once past its last.
     */
    private boolean unseen() {
        boolean pastItsKeys = colourTarget >= system.colourFrames().length;
        return switch (system.data().blend()) {
            case ADDITIVE -> pastItsKeys && red + green + blue <= 0.06f;
            case ALPHA -> alpha < 0.02f;
            case ALPHA_TEST -> false;
            case MULTIPLY -> pastItsKeys && red * green * blue > 0.95f;
        };
    }

    private void alphaRate() {
        var frames = system.alphaFrames();
        if (alphaTarget >= frames.length) {
            alphaRate = 0f;
            return;
        }
        int time = frames[alphaTarget] - frames[alphaTarget - 1];
        alphaRate = time <= 0 ? 0f : (alphas[alphaTarget] - alphas[alphaTarget - 1]) / time;
    }

    private void colourRate() {
        var frames = system.colourFrames();
        if (colourTarget >= frames.length) {
            redRate = 0f;
            greenRate = 0f;
            blueRate = 0f;
            return;
        }
        int time = frames[colourTarget] - frames[colourTarget - 1];
        var colours = system.colours();
        var to = colours[colourTarget];
        var from = colours[colourTarget - 1];
        redRate = time <= 0 ? 0f : (to[0] - from[0]) / time;
        greenRate = time <= 0 ? 0f : (to[1] - from[1]) / time;
        blueRate = time <= 0 ? 0f : (to[2] - from[2]) / time;
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    /**
     * The turn from straight up the ground's y to a direction across it, signed by which side of it the
     * direction is on — the reference's {@code angleBetween} of {@code (0, 1)} and the direction.
     */
    static float angleFromUp(float dx, float dy) {
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0f) {
            return 0f;
        }
        if (dy == 0f) {
            return dx > 0f ? (float) Math.PI : 0f;
        }
        float theta = (float) Math.acos(Math.clamp(dy / length, -1f, 1f));
        return dx > 0f ? theta : -theta;
    }
}
