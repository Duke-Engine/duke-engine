package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.LogicRandom;

/**
 * One weapon's rounds and how long until it may fire again — the reference game's rule, from its source
 * ({@code Weapon::privateFireWeapon}, {@code Weapon::reloadWithBonus}):
 *
 * <ul>
 *   <li>A clip of 0 is no clip: after every shot it waits its delay, which is what every weapon did before
 *       clips existed. A weapon starts full and ready.
 *   <li>A shot that leaves rounds waits the delay between shots — drawn from its least to its most, both
 *       included, from the simulation's own random numbers, and not drawn at all where the two are one.
 *   <li>The shot that empties it: a weapon that reloads by itself starts the reload at that shot — the clip is
 *       full again at once and it waits the reload out; one that does not is {@link WeaponStatus#OUT} until
 *       something {@linkplain #refill refills} it.
 *   <li>Both waits are divided by the unit's rate of fire and floored to whole frames.
 * </ul>
 *
 * <p>A countdown rather than the frame it may fire again, and ticked only where the weapon itself runs: so a
 * weapon that is left out — no clip, one delay — keeps exactly the timing it always had, including standing
 * still while its unit is disabled or carried.
 */
final class Clip {

    private final int size;
    private final int leastDelay;
    private final int mostDelay;
    private final int reloadFrames;
    private final boolean reloadsItself;

    private int rounds;
    private int wait;
    private boolean reloading;
    private boolean out;

    Clip(int size, int leastDelay, int mostDelay, int reloadFrames, boolean reloadsItself) {
        this.size = Math.max(0, size);
        this.leastDelay = Math.max(0, leastDelay);
        this.mostDelay = Math.max(this.leastDelay, mostDelay);
        this.reloadFrames = Math.max(0, reloadFrames);
        this.reloadsItself = reloadsItself;
        this.rounds = this.size;
    }

    /** A frame has passed where this weapon runs. */
    void tick() {
        if (wait > 0) {
            wait--;
        }
    }

    WeaponStatus status() {
        if (out) {
            return WeaponStatus.OUT;
        }
        if (wait > 0) {
            return reloading ? WeaponStatus.RELOADING : WeaponStatus.BETWEEN_SHOTS;
        }
        return WeaponStatus.READY;
    }

    /** Rounds left in the clip; for a weapon with no clip, always 0, since nothing is counted. */
    int rounds() {
        return rounds;
    }

    /** Rounds it holds full; 0 for no clip. */
    int size() {
        return size;
    }

    /**
     * A shot has been taken: count it, and start whichever wait follows it.
     *
     * @return whether it emptied the clip
     */
    boolean fired(LogicRandom random, float rateOfFire) {
        if (size > 0 && --rounds <= 0) {
            if (reloadsItself) {
                rounds = size;
                reloading = true;
                wait = shortened(reloadFrames, rateOfFire);
            } else {
                out = true;
                wait = 0;
            }
            return true;
        }
        reloading = false;
        wait = shortened(random.nextInt(leastDelay, mostDelay), rateOfFire);
        return false;
    }

    /**
     * At least {@code share} of it full, rounded down — a jet on its pad for part of its reload — and ready now if
     * that is any rounds at all. A clip with more already keeps them.
     */
    void refill(float share) {
        rounds = Math.max(rounds, (int) Math.floor(size * share));
        if (rounds > 0) {
            out = false;
            reloading = false;
            wait = 0;
        }
    }

    /** Full, and ready now — a landing at base, a crate. */
    void refill() {
        rounds = size;
        out = false;
        reloading = false;
        wait = 0;
    }

    /** A wait divided by the rate of fire, floored — a bonus of 1 leaves it exactly as written. */
    private static int shortened(int frames, float rateOfFire) {
        return rateOfFire > 0f && rateOfFire != 1f ? (int) (frames / rateOfFire) : frames;
    }
}
