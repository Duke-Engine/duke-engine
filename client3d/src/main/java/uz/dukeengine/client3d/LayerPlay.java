package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingBox;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

/**
 * One layer burning: its particles and light, its clock, where it is, and what it does each
 * frame — follow whoever it follows, flare its light, breathe as it runs out — until it is over
 * and {@link LayeredEffects} puts it away. Letting particles out is {@link LayerLaying}'s.
 */
final class LayerPlay {

    /** How long homing particles take to fade once what they fly into has gone. */
    private static final float HOME_FADE_SECONDS = 0.4f;

    final EffectLayer layer;
    final ParticleLayer drawn;
    final PointLight light;
    /** For an aura: what it is, on whom, so a second cast does not stack a second. */
    final String key;
    /** What one of its sizes is: 1 for units, or the skill's reach. */
    final float unit;
    final int projectile;

    float clock;
    /** How long it lasts once started: an aura's run, a mark's stay, a path's laying. */
    float span;
    /** When, after starting, it is finished and can be put away. */
    float until;
    int follows = LayeredEffects.NOBODY;
    Spatial rides;
    Vector3f offset = Vector3f.ZERO;
    Vector3f anchor;
    Vector3f lastAnchor;
    float facingX;
    float facingZ = 1f;
    boolean emitting;
    float owed;
    int next;
    float fall;
    /** Whether its particles are laid round the origin and the geometry carried to where it is. */
    boolean attached;
    /** The creature its homing particles fly into, or none; and, once it has gone, when they began to fade. */
    int homeTo = LayeredEffects.NOBODY;
    float homeLostAt = -1f;
    /** Everywhere a fed layer has let particles out, for the camera to test. */
    BoundingBox bound;

    LayerPlay(EffectLayer layer, ParticleLayer drawn, PointLight light, String key, float unit,
            int projectile, Vector3f at) {
        this.layer = layer;
        this.drawn = drawn;
        this.light = light;
        this.key = key;
        this.unit = unit;
        this.projectile = projectile;
        this.anchor = at.clone();
        this.lastAnchor = anchor.clone();
    }

    /**
     * How far anything a layer lets out can get from where it was born.
     *
     * @param unit what its sizes are counted in -- see {@link LayeredEffects}
     */
    static float reachOf(EffectLayer layer, float unit) {
        float life = layer.lifeMax();
        // Either way: a speed drawn inwards reaches as far as one thrown out.
        float thrown = Math.max(Math.abs(layer.speedMin()), Math.abs(layer.speedMax())) * life;
        float fell = Math.abs(layer.gravity()) * life * life * 0.5f;
        float size = Math.max(layer.sizeStart(), layer.sizeEnd()) * (1f + layer.sizeJitter())
                * unit;
        // A pillar's height is one of its sizes, and is counted as they are.
        float high = Math.abs(layer.height())
                * (EffectLayer.PILLAR.equals(layer.type()) ? unit : 1f);
        return thrown + fell + size + layer.radius() * unit + high + layer.fall() + 1f;
    }

    float reach() {
        return reachOf(layer, unit);
    }

    /** Seconds since its delay was up; negative until then. */
    float age() {
        return clock - layer.delay();
    }

    /** Whether it is still burning under this key. */
    boolean burning(String under) {
        return under.equals(key) && age() < until;
    }

    /** Where its particles are laid from: the origin if it carries its geometry, else where it began. */
    Vector3f origin() {
        return attached ? Vector3f.ZERO : anchor;
    }

    /** A burning aura cast again where it renews, carried on to {@code more} seconds from now, and fed again. */
    void renew(float more) {
        span = Math.max(0f, age()) + more;
        emitting = layer.continuous();
        until = untilFor();
    }

    /** How long after starting it is over, as far as can be known now. */
    float untilFor() {
        if (rides != null) {
            return Float.POSITIVE_INFINITY; // for as long as the thing flies
        }
        if (layer.pathTrail() || layer.continuous()) {
            return span + layer.lifeMax();
        }
        return layer.lasting() ? span : layer.lifeMax();
    }

    void face(float x, float z) {
        float length = (float) Math.sqrt(x * x + z * z);
        if (length < 0.0001f) {
            facingX = 0f;
            facingZ = 1f;
        } else {
            facingX = x / length;
            facingZ = z / length;
        }
    }

    /** It stops being fed; what it let out lives out its lives. */
    void stopFeeding() {
        if (!emitting && rides == null) {
            return;
        }
        emitting = false;
        until = Math.min(until, Math.max(0f, age()) + layer.lifeMax());
    }

    /** Where it is this frame: on the thing it rides, on the man it follows, or where it began. */
    void place(LayerSpots spots, float local) {
        Vector3f now = null;
        if (rides != null) {
            if (rides.getParent() == null) {
                stopFeeding();
                rides = null;
            } else {
                now = rides.localToWorld(offset, null);
            }
        } else if (follows != LayeredEffects.NOBODY) {
            now = spots.whereIs(follows);
            if (now == null) {
                // He has gone -- died, or walked into the dark. What was round him
                // does not stay standing in the air where he was.
                stopFeeding();
                follows = LayeredEffects.NOBODY;
                until = Math.min(until, local + layer.lifeMax());
            }
        }
        if (now != null) {
            lastAnchor = anchor;
            anchor = now;
        }
        var at = raised(local);
        if (drawn != null && attached) {
            drawn.geometry().setLocalTranslation(at);
        }
        if (light != null) {
            light.setPosition(at.add(0f, Math.max(1f, layer.height()), 0f));
        }
    }

    /** Its place this frame with the height it is still falling from taken off. */
    Vector3f raised(float local) {
        if (fall <= 0f) {
            return anchor;
        }
        float through = span <= 0f ? 1f : Math.clamp(local / span, 0f, 1f);
        // Slow and then sudden, as anything dropped from a height is.
        return anchor.add(0f, fall * (1f - through * through), 0f);
    }

    /** A homing layer told where its creature stands this frame; once it is gone, faded out where they are. */
    void homeIn(LayerSpots spots, float local) {
        if (homeTo == LayeredEffects.NOBODY || drawn == null) {
            return;
        }
        var at = spots.whereIs(homeTo);
        if (at != null && homeLostAt < 0f) {
            drawn.home(at.add(0f, Math.max(1f, layer.height()), 0f));
            return;
        }
        if (homeLostAt < 0f) {
            homeLostAt = local;
        }
        drawn.opacity(Math.max(0f, 1f - (local - homeLostAt) / HOME_FADE_SECONDS));
    }

    /**
     * An aura that is running out says so.
     *
     * <p>For the last part of its run — as much of it as the layer's fade-out says
     * — it breathes, at the layer's own pulse. A shield that simply vanished would
     * be a shield the player was still standing behind when it went.
     */
    void fadeOutAnAura(float local) {
        if (drawn == null || !EffectLayer.AURA.equals(layer.type()) || layer.pulseRate() <= 0f) {
            return;
        }
        float warning = span * layer.fadeOut();
        boolean ending = span - local <= warning;
        drawn.pulse(ending ? layer.pulseRate() : 0f, layer.pulseDepth());
    }

    /** A light flares in, holds, and fades, along the layer's own fade curve. */
    void lightUp(float local, float scale) {
        if (light == null) {
            return;
        }
        float length = rides != null || emitting ? Float.POSITIVE_INFINITY : Math.max(0.01f, until);
        float through = Float.isInfinite(length) ? 0.5f : Math.clamp(local / length, 0f, 1f);
        float in = layer.fadeIn() <= 0f ? 1f : smooth(0f, layer.fadeIn(), through);
        float out = layer.fadeOut() <= 0f || Float.isInfinite(length) ? 1f
                : 1f - smooth(1f - layer.fadeOut(), 1f, through);
        float strength = layer.lightPower() * in * out * scale;
        int packed = layer.lightColour();
        light.setColor(new ColorRGBA(((packed >> 16) & 0xFF) / 255f * strength,
                ((packed >> 8) & 0xFF) / 255f * strength, (packed & 0xFF) / 255f * strength,
                1f));
        light.setRadius(Math.max(0.001f, layer.lightRadius() * scale));
    }

    private static float smooth(float edge0, float edge1, float x) {
        if (edge1 <= edge0) {
            return x >= edge1 ? 1f : 0f;
        }
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
