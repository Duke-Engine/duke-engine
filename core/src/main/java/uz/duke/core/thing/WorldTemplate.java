package uz.duke.core.thing;

/**
 * What every game's world is, as its INI {@code World} block writes it: a name. The block is
 * the world's settings, one block for all of them, the way a {@code Monster} block is all of
 * a monster.
 *
 * <p>Everything else a world may have is a capability of its own, as a thing's is — see
 * {@link Layered} — and the engine reads only the capabilities it has a use for. A game's
 * world is a record that implements what its world has; the game's own sections of the
 * block are the game's to read.
 */
public interface WorldTemplate {

    String name();
}
