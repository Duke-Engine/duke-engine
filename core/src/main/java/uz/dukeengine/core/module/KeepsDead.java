package uz.dukeengine.core.module;

/**
 * A module that keeps its thing in the world after it dies, while its death plays out — the reference's
 * SlowDeathBehavior: a tank's hulk made a moment after, a demo trap going off a second after it is killed, a soldier's
 * body lying and sinking. Asked the frame the thing dies; where any module says yes, its death is told at once — the
 * {@code ObjectDied}, its {@link DieModule}s — with the thing still in the world, and it stays: dead to everything as
 * the engine reads death (no target, not hurt, in nobody's way, not counted alive), still drawn, and its modules
 * still running, until one of them destroys it ({@code GameLogic.destroyObject}).
 */
public interface KeepsDead {

    /** Whether it keeps its thing, dead, in the world. */
    boolean keepsDead();
}
