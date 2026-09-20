package uz.dukeengine.rts.module;

/**
 * A module that can stop its unit's weapon firing for a while.
 *
 * <p>The seam for everything a unit does that occupies it: drawing a heavy shot,
 * channelling something, being briefly stunned. While any module on the unit says
 * it is holding, {@link WeaponUpdate} acquires nothing and fires nothing — but
 * keeps whatever it was already pointed at, and keeps counting its reload down,
 * so the moment the hold lifts it is ready rather than starting to wait.
 *
 * <p>It has to live in the weapon rather than in whatever is steering the unit,
 * and that is worth saying because the alternative looks like it should work. A
 * weapon finds its own target and fires in the same call: a script that disarms
 * it runs <em>after</em> it, so the shot is already gone, and by the time the
 * script speaks again the weapon has re-acquired. Holding fire from outside is a
 * frame too late, always.
 *
 * <p>The mirror of {@link DamageModifier}, which changes how hard a shot lands,
 * and of {@link ProjectileLauncher}, which changes when. This one decides whether
 * there is a shot at all. Like both of them it is walked in module order, fixed
 * when the object is built, so every peer reaches the same answer.
 */
public interface WeaponHold {

    /** Whether this unit's weapon should keep quiet this frame. */
    boolean holdingFire();
}
