package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

/**
 * The sun the world is drawn under, eased from one hour to the next over a while: where it shines from, its colour and
 * the ambient's — see {@link Duke3D#sunlight}. Drawing only.
 */
final class HourOfDay {

    /** One hour's sun: its direction, its colour and the ambient's. */
    record Sun(Vector3f direction, ColorRGBA colour, ColorRGBA ambient) {

        static Sun of(Sunlight light) {
            return new Sun(light.direction(), light.sunColour(), light.ambientColour());
        }

        /** The sun {@code t} of the way from this to {@code to}: its direction turned, its colours mixed. */
        Sun toward(Sun to, float t) {
            var turned = direction.clone().interpolateLocal(to.direction, t);
            if (turned.lengthSquared() < 1e-8f) {
                turned = to.direction.clone();
            }
            return new Sun(turned.normalizeLocal(), colour.clone().interpolateLocal(to.colour, t),
                    ambient.clone().interpolateLocal(to.ambient, t));
        }
    }

    private Sun from;
    private Sun to;
    private float seconds;
    private float gone;

    HourOfDay(Sun now) {
        this.from = now;
        this.to = now;
    }

    /** Ease to {@code next} over {@code seconds}, from wherever it is now; at once for none. */
    void toward(Sun next, float seconds) {
        this.from = now();
        this.to = next;
        this.seconds = Math.max(0f, seconds);
        this.gone = 0f;
    }

    /** The sun now. */
    Sun now() {
        return seconds <= 0f || gone >= seconds ? to : from.toward(to, gone / seconds);
    }

    /** {@code tpf} seconds on; whether it is still on its way. */
    boolean step(float tpf) {
        if (seconds <= 0f || gone >= seconds) {
            return false;
        }
        gone = Math.min(seconds, gone + tpf);
        return true;
    }

    /** {@code now}'s share of what a material was built with, a channel at a time; 1 where it was built dark. */
    static ColorRGBA shift(ColorRGBA now, ColorRGBA built) {
        return new ColorRGBA(share(now.r, built.r), share(now.g, built.g), share(now.b, built.b), 1f);
    }

    private static float share(float now, float built) {
        return built <= 1e-4f ? 1f : now / built;
    }
}
