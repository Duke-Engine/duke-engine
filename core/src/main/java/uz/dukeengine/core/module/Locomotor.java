package uz.dukeengine.core.module;

/**
 * Marks a module that can move its object under its own power, ported in name
 * from SAGE's {@code Locomotor}.
 *
 * <p>The engine needs to answer one question it cannot otherwise ask: <em>is this
 * object part of the terrain?</em> Something that never moves can be baked into
 * the navigation grid so paths route around it; something that walks must not be,
 * or it would wall itself in.
 *
 * <p>Asking "is it a building?" would mean {@code core} knowing a game's
 * vocabulary. Asking "can anything move it?" is genre-neutral and simply true:
 * an object with no locomotor cannot move, whatever the game calls it. A game
 * that writes its own movement module implements this interface and the engine
 * classifies it correctly with no further wiring.
 */
public interface Locomotor {
}
