package uz.dukeengine.core;

import java.util.Objects;
import java.util.function.Predicate;
import uz.dukeengine.core.thing.GameObject;

/**
 * Which of a world's things sleep: those farther than {@code cells} from every waker — the world's cells, counted the
 * longer way across — decided every {@code everyFrames} frames from the world as it stands, in the order its things
 * came into it, so it is decided alike on every machine. A sleeping thing's modules do not run — no brain, no step, no
 * weapon, no timer — and it looks at nothing; every part of it stays as it was, and it wakes when a waker comes near.
 * What a frame costs then follows what is awake, not the size of the world.
 *
 * @param wakers      what keeps the world awake round it — a game's heroes. Asked of every thing on the simulation
 *                    thread, so it must be a function of the thing alone
 * @param cells       how far round a waker things stay awake, in the world's cells
 * @param everyFrames how often who sleeps is decided: on each frame that is a multiple of it, and on the first frame
 *                    after the rule is given or the world cleared
 */
public record Sleep(Predicate<GameObject> wakers, int cells, int everyFrames) {

    public Sleep {
        Objects.requireNonNull(wakers, "wakers");
        if (cells < 0 || everyFrames < 1) {
            throw new IllegalArgumentException("sleep needs a reach of 0 cells or more, decided every frame or less often");
        }
    }
}
