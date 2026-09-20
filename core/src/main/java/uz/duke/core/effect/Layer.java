package uz.duke.core.effect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One layer of an {@link Effect}, as its {@code Layer} block writes it — only the fields it
 * said. Every one may be left out, and what one left out is is the client's to say: a layer
 * that does not mention its drag gets whatever the client gives a layer that does not mention
 * its drag, and there is one place that says what that is instead of two that can disagree.
 *
 * @param blend  {@code Additive} for light — fire, magic, sparks — and {@code Alpha} for stuff:
 *               smoke and dust drawn additively brighten the floor they are meant to hide
 * @param cover  how much of the floor it hides, for fire that has to read on pale ground: 0 is
 *               Additive's, 1 is Alpha's
 * @param follows whether a layer on somebody goes where he goes; an AURA always does
 * @param life   the least and the most a particle lives, {@code [0.2, 0.5]}; so too
 *               {@code size}, {@code alpha} (start and end), {@code speed} and {@code colour}
 * @param measure UNITS, or REACH for a shape as wide as the skill's own radius
 */
public record Layer(String name, String type, String texture, String blend, Float cover, Integer count,
        Float rate, Float delay, Float seconds, Float sizeEase, Float sizeJitter, Float colourEase, Float fadeIn,
        Float fadeOut, Float spread, Float radius, Float height, Float gravity, Float drag, Float stretch,
        Float spin, Float turn, Float turnJitter, Float pulseRate, Float pulseDepth, Float lightPower,
        Float lightRadius, Float fall, Float rise, Float riseEase, Boolean follows, List<Float> life,
        List<Float> size, List<Float> alpha, List<Float> speed, List<Integer> colour, Integer lightColour,
        String direction, String at, String measure) {

    public Layer {
        for (var pair : List.of(life, size, alpha, speed, colour)) {
            if (!pair.isEmpty() && pair.size() != 2) {
                throw new IllegalArgumentException("a range is two numbers, [from, to]");
            }
        }
    }

    /** What it said, by the client's name for each and in the client's form. */
    public Map<String, String> fields() {
        var said = new LinkedHashMap<String, String>();
        upper(said, "type", type);
        text(said, "texture", texture);
        if (blend != null) {
            said.put("additive", String.valueOf(!"Alpha".equalsIgnoreCase(blend)));
        }
        text(said, "cover", cover);
        text(said, "count", count);
        text(said, "rate", rate);
        text(said, "delay", delay);
        text(said, "seconds", seconds);
        text(said, "sizeEase", sizeEase);
        text(said, "sizeJitter", sizeJitter);
        text(said, "colourEase", colourEase);
        text(said, "fadeIn", fadeIn);
        text(said, "fadeOut", fadeOut);
        text(said, "spread", spread);
        text(said, "radius", radius);
        text(said, "height", height);
        text(said, "gravity", gravity);
        text(said, "drag", drag);
        text(said, "stretch", stretch);
        text(said, "spin", spin);
        text(said, "turn", turn);
        text(said, "turnJitter", turnJitter);
        text(said, "pulseRate", pulseRate);
        text(said, "pulseDepth", pulseDepth);
        text(said, "lightPower", lightPower);
        text(said, "lightRadius", lightRadius);
        text(said, "fall", fall);
        text(said, "rise", rise);
        text(said, "riseEase", riseEase);
        text(said, "follows", follows);
        pair(said, "lifeMin", "lifeMax", life);
        pair(said, "sizeStart", "sizeEnd", size);
        pair(said, "alphaStart", "alphaEnd", alpha);
        pair(said, "speedMin", "speedMax", speed);
        pair(said, "colourStart", "colourEnd", colour);
        text(said, "lightColour", lightColour);
        upper(said, "direction", direction);
        upper(said, "at", at);
        upper(said, "measure", measure);
        return said;
    }

    private static void text(Map<String, String> said, String key, Object value) {
        if (value != null) {
            said.put(key, String.valueOf(value));
        }
    }

    private static void upper(Map<String, String> said, String key, String value) {
        if (value != null) {
            said.put(key, value.toUpperCase(Locale.ROOT));
        }
    }

    private static void pair(Map<String, String> said, String first, String second, List<?> values) {
        if (!values.isEmpty()) {
            said.put(first, String.valueOf(values.get(0)));
            said.put(second, String.valueOf(values.get(1)));
        }
    }
}
