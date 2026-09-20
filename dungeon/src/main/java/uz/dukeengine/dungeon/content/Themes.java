package uz.dukeengine.dungeon.content;

import java.util.List;
import uz.dukeengine.dungeon.gen.DeterministicRng;
import uz.dukeengine.dungeon.world.Theme;

/**
 * Which floor looks like what, and how it varies.
 *
 * <p>Two decisions, both written in the data and neither in Java. The
 * first is the order: depth one wears the first theme named, depth two the second,
 * and so on. The second is what happens when the list runs out, which is a real
 * question in a game with no bottom — either it comes round again or the deepest
 * look is the look from then on.
 *
 * <p>The variation inside a theme is drawn from the seed and the depth, which
 * makes it a fact about the floor rather than about the moment it was drawn: the
 * same seed shows the same floor to every player, on every machine, and again
 * tomorrow. It is drawn from a generator of its own rather than the world's, so
 * asking what a floor looks like cannot move the world's own dice by a single
 * step — see the checksum test.
 */
public record Themes(List<String> order, WhenExhausted whenExhausted, List<Theme> all) {

    /** What to do below the last depth the order names. */
    public enum WhenExhausted {
        /** Begin the list again — stone, ice, lava, stone, ice, lava. */
        REPEAT,
        /** Stay in the deepest one for ever. */
        LAST
    }

    /** No themes at all: the game is drawn from whatever single kit it named. */
    public static final Themes NONE =
            new Themes(List.of(), WhenExhausted.REPEAT, List.of());

    public Themes {
        order = List.copyOf(order);
        all = List.copyOf(all);
    }

    /** A floor's whole look: which theme, and which of its variations. */
    public record Chosen(Theme theme, Theme.Tone tone) {

        /** What the client is told, and all it is told: two names. */
        public String asStatus() {
            return theme.name() + "," + tone.name();
        }
    }

    public boolean isEmpty() {
        return order.isEmpty() || all.isEmpty();
    }

    /**
     * What the floor at {@code depth} of the run that began with {@code seed}
     * looks like, or {@code null} if the file described no themes.
     *
     * <p>Pure: the same two numbers always give the same answer, and asking costs
     * the world nothing.
     */
    public Chosen pick(long seed, int depth) {
        if (isEmpty()) {
            return null;
        }
        var theme = themeNamed(nameFor(depth));
        if (theme == null || theme.tones().isEmpty()) {
            return null;
        }
        var rng = new DeterministicRng(mix(seed, depth));
        return new Chosen(theme, theme.tones().get(rng.nextInt(theme.tones().size())));
    }

    /**
     * The seed and the depth stirred into one number.
     *
     * <p>Stirred rather than combined, and that is not fussiness. The generator is
     * a single xorshift step, and choosing one of two tones reads a single bit of
     * its first output — so two seeds that differ only in bits that step does not
     * reach come out identical. Exclusive-or was the first attempt and it gave
     * every depth of a run the same tone, every time: the depth was in the seed
     * and made no difference to the one bit anybody looked at.
     *
     * <p>This is splitmix64's finaliser, which is three multiplies and three
     * shifts and spreads every input bit across every output bit. No dependency,
     * no clock, and the same answer everywhere — which is all this has to be.
     */
    private static long mix(long seed, int depth) {
        long z = seed + depth * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Which theme depth wears, following the order and then the policy. */
    public String nameFor(int depth) {
        if (order.isEmpty()) {
            return null;
        }
        int step = Math.max(1, depth) - 1;
        if (step < order.size()) {
            return order.get(step);
        }
        return whenExhausted == WhenExhausted.LAST
                ? order.get(order.size() - 1)
                : order.get(step % order.size());
    }

    public Theme themeNamed(String name) {
        for (var theme : all) {
            if (theme.name().equals(name)) {
                return theme;
            }
        }
        return null;
    }
}
