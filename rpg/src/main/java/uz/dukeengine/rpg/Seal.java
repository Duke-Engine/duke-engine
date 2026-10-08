package uz.dukeengine.rpg;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.World;

/**
 * What parts two places so that nothing hurting or mending passes between them: a shut gate, a ward, a wall of
 * force. Every blow, shot, burst, falling blast and mending here asks it rather than saying the rule again, and so
 * does every dash, blink and rift — refused where it would come down on the other side — and every haste and aura,
 * which is not lent across.
 *
 * <p>The game's to answer, and to answer cheaply and deterministically: it is asked on every blow.
 */
@FunctionalInterface
public interface Seal {

    /** Nothing parted, ever: a game with no seals. */
    Seal NONE = (world, a, b) -> false;

    /** Whether {@code a} and {@code b} lie on two sides of a seal that stands. */
    boolean parts(World world, Coord3D a, Coord3D b);
}
