package uz.dukeengine.client3d;

/**
 * A heavy blow felt as a catch in the motion: the struck creature and the one that struck it slowed for a moment, in
 * the window's animation alone — the simulation's frames run on untouched.
 *
 * @param seconds   how long the catch lasts; a second blow within it does not lengthen it
 * @param slow      the share of normal speed they move at meanwhile; 0 freezes them
 * @param majorOnly only a blow at or past {@link HitNumbers#bigAt()}, or a critical one, catches
 * @param critOnly  only a critical blow catches
 */
public record HitStop(float seconds, float slow, boolean majorOnly, boolean critOnly) {

    public HitStop {
        seconds = Math.max(0f, seconds);
        slow = Math.clamp(slow, 0f, 1f);
    }

    /** Whether a blow of {@code amount}, critical or not, catches — {@code bigAt} the numbers' big blow, 0 for none. */
    boolean catches(float amount, boolean critical, float bigAt) {
        if (seconds <= 0f || critOnly && !critical) {
            return false;
        }
        return !majorOnly || critical || bigAt > 0f && amount >= bigAt;
    }
}
