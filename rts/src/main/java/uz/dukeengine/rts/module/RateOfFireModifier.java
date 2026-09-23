package uz.dukeengine.rts.module;

/**
 * A module that changes how fast its owner fires: the delay between shots and a clip's reload are both divided
 * by it, and floored to whole frames — the reference game's rule ({@code WeaponTemplate::getDelayBetweenShots},
 * {@code getClipReloadTime}), where a horde's or a rank's rate-of-fire bonus is a number like 1.5.
 *
 * <p>The same bargain as {@link DamageModifier}: any module may implement it, the engine's or a game's, and
 * {@link WeaponUpdate} multiplies every one on the unit together, in module order, which is fixed when the
 * object is built — so the product, and every wait it shortens, is the same on every machine.
 */
public interface RateOfFireModifier {

    /**
     * How much faster this unit fires. 1.0 changes nothing; 2.0 halves every wait. Read at every shot, so it
     * may vary with the unit's state — but only with simulation state, never the clock.
     */
    float rateOfFireMultiplier();
}
