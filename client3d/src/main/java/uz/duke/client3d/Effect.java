package uz.duke.client3d;

import java.util.List;

/**
 * What something burning or glowing looks like, by name: its {@code Effect} block, and the
 * {@code Layer} blocks it is drawn from, in its {@code Layers = [ … ]} in draw order.
 *
 * <p>Shared rather than written on each projectile or skill: an arrow and the drawn shot the
 * hero looses are the same fire at two sizes. The client owns the layer <em>types</em> — a trail,
 * an aura, a burst where it lands — and every number in them is here, so a new burning thing is a
 * block and not a class.
 *
 * @param glow        named pieces of the wearer's model lit from inside, or none: the one thing an
 *     effect draws that is not a layer, because it is a material on a model rather than something
 *     let out into the air
 * @param shakeSeconds how long the camera is knocked when it goes off; with {@code shakePower} the
 *     one part of it that is not drawn, because there is one camera and so one knock
 */
public record Effect(String name, Glow glow, float shakeSeconds, float shakePower, List<Layer> layers) {

    /** What a block leaves out. */
    static final Effect DEFAULTS = new Effect(null, null, 0f, 0f, List.of());

    public Effect {
        layers = layers == null ? List.of() : List.copyOf(layers);
    }

    /**
     * Pieces of a model lit from inside — a skeleton's eye sockets, a rune, the coals in a brazier.
     *
     * @param parts  words that name the pieces: {@code Eyes} lights {@code Skeleton_Mage_Eyes} and
     *     every other piece with the word in its name
     * @param colour what they burn, packed {@code 0xRRGGBB}
     */
    public record Glow(List<String> parts, int colour) {

        static final Glow DEFAULTS = new Glow(List.of(), 0xFFFFFF);

        public Glow {
            parts = parts == null ? List.of() : List.copyOf(parts);
        }
    }
}
