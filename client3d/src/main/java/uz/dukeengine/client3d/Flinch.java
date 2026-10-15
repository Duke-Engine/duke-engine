package uz.dukeengine.client3d;

import java.util.HashMap;
import java.util.Map;

/**
 * When a creature that is hurt flinches: only while it is doing nothing else — never over a swing, a cast or its work —
 * at most once in {@code seconds}, and, where the game says, only at a heavy blow: a critical one, or one of at least
 * {@link HitNumbers#bigAt()}.
 *
 * @param seconds   the least time from one flinch to the next on one creature; 0 for no limit
 * @param heavyOnly whether only a heavy blow flinches
 */
public record Flinch(float seconds, boolean heavyOnly) {

    /** As every creature flinched before a game could say: at every blow it is free for. */
    public static final Flinch DEFAULTS = new Flinch(0f, false);

    public Flinch {
        seconds = Math.max(0f, seconds);
    }

    /** Each creature's last flinch, by id, in the window's seconds. */
    static final class Clock {
        private final Map<Integer, Float> last = new HashMap<>();

        /**
         * Whether a blow of {@code amount} on {@code unit} at {@code now} flinches it, and if it does, noted.
         *
         * @param busy whether it is swinging, casting, at work or has a target
         */
        boolean flinches(Flinch look, int unit, float now, boolean busy, float amount, boolean critical,
                float bigAt) {
            if (busy || look.heavyOnly() && !critical && !(bigAt > 0f && amount >= bigAt)) {
                return false;
            }
            var before = last.get(unit);
            if (before != null && now - before < look.seconds()) {
                return false;
            }
            last.put(unit, now);
            return true;
        }

        void forget(int unit) {
            last.remove(unit);
        }
    }
}
