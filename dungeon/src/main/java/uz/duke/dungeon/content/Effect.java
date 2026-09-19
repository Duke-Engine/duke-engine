package uz.duke.dungeon.content;

import java.util.List;

/**
 * What something burning or glowing looks like, by name: its {@code Effect} block, and the
 * {@code Layer} blocks it is drawn from, inside it in draw order.
 *
 * <p>Shared rather than written on each projectile or skill: an arrow and the drawn shot the
 * hero looses are the same fire at two sizes. The client owns the <em>kinds</em> — a trail, a
 * glowing body, a burst where it lands — and every number in them is here, so a new burning
 * thing is a block and not a class.
 *
 * @param kinds what the client draws it as: {@code [TRAIL, GLOW]}; one thing can do several
 * @param parts which parts of a creature it burns on: {@code [eyes, jaw]}
 */
public record Effect(String name, List<String> kinds, List<String> parts, int colour, int fadeColour,
        int lightColour, float lightPower, float lightRadius,
        int particles, float particleSize, float particleLife, float spread,
        float orbSize, int burstParticles, float burstSize, float burstSeconds,
        float waveFrom, float waveTo, float waveSeconds, float waveEase,
        float waveEdge, float waveWash, float markRadius, float markSeconds,
        float shakeSeconds, float shakePower, List<Layer> layers) {

    /** What a block leaves out. */
    static final Effect DEFAULTS = new Effect(null, List.of(), List.of(), 0xFFFFFF, 0x000000,
            0xFFFFFF, 0f, 0f,
            0, 1f, 0.4f, 0f,
            0f, 0, 1f, 0.3f,
            0f, 0f, 0.45f, 2.4f,
            1f, 0.25f, 0f, 0f,
            0f, 0f, List.of());

    public Effect {
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
        parts = parts == null ? List.of() : List.copyOf(parts);
        layers = layers == null ? List.of() : List.copyOf(layers);
    }

    public java.awt.Color awtColour() {
        return new java.awt.Color(colour);
    }

    public java.awt.Color awtFade() {
        return new java.awt.Color(fadeColour);
    }

    public java.awt.Color awtLight() {
        return new java.awt.Color(lightColour);
    }
}
