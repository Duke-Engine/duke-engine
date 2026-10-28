package uz.dukeengine.client3d;

import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * The lights things carry — {@link Visuals.UnitVisual#light}: a point light on each, lent from the effects' pool,
 * moved with its thing and put back the frame the thing no longer has it.
 *
 * <p>A point light has a radius, so it lights only what lies within it — a police flasher the road beside the car and
 * nothing beyond, where {@link Visuals#thingSuns} lights every thing at once. When every light in the pool is burning, a
 * thing goes without.
 */
final class UnitLights {

    /** One thing's light, and the point of the world it shines from. */
    record Line(int id, Visuals.ThingLight light, Vector3f at) {
    }

    private final LightPool pool;
    private final Map<Integer, PointLight> burning = new HashMap<>();

    UnitLights(LightPool pool) {
        this.pool = pool;
    }

    /** Every light as these lines say: new ones lit, the rest moved and recoloured, any not in them put out. */
    void update(List<Line> lines, float seconds) {
        var kept = new HashSet<Integer>();
        for (var line : lines) {
            var look = line.light();
            if (look.radius() <= 0f) {
                continue; // jME reads a radius of 0 as reaching everything
            }
            var lamp = burning.get(line.id());
            if (lamp == null) {
                // shortcut: first come first lit, whatever the camera sees; sort by distance to it if a scene runs dry
                lamp = pool.take();
                if (lamp == null) {
                    continue;
                }
                burning.put(line.id(), lamp);
            }
            kept.add(line.id());
            lamp.setPosition(line.at().add(0f, look.height(), 0f));
            lamp.setRadius(look.radius());
            lamp.setColor(rgb(look.colour()).mult(brightness(look, line.id(), seconds)));
        }
        burning.entrySet().removeIf(one -> {
            boolean out = !kept.contains(one.getKey());
            if (out) {
                pool.give(one.getValue());
            }
            return out;
        });
    }

    /** How strong a thing's light is {@code seconds} in: whole when steady, swinging down by its flicker otherwise. */
    static float brightness(Visuals.ThingLight look, int id, float seconds) {
        if (look.flicker() <= 0f || look.hertz() <= 0f) {
            return 1f;
        }
        // Each thing at a phase of its own, or every flasher in a street would blink as one.
        float swing = FastMath.sin(FastMath.TWO_PI * (look.hertz() * seconds + id * 0.37f));
        return 1f - look.flicker() * 0.5f * (1f + swing);
    }

    /** The light a thing burns, or null for none, for a test. */
    PointLight of(int id) {
        return burning.get(id);
    }

    /** Put them all out — a new world has nothing burning in it. */
    void clear() {
        burning.values().forEach(pool::give);
        burning.clear();
    }

    private static ColorRGBA rgb(int packed) {
        return new ColorRGBA((packed >> 16 & 0xFF) / 255f, (packed >> 8 & 0xFF) / 255f, (packed & 0xFF) / 255f, 1f);
    }
}
