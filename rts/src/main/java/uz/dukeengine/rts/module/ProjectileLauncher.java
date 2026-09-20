package uz.dukeengine.rts.module;

import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.thing.GameObject;

/**
 * A module that takes its owner's shots instead of letting the weapon land them.
 *
 * <p>{@link WeaponUpdate} hits the instant it fires. For most of an RTS that is
 * right and is what players expect — a rifle's bullet does not need modelling.
 * But it left no way to express the other kind at all: an arrow, a shell, a
 * missile, anything that leaves the barrel and arrives later. A game wanting one
 * had to give up the weapon entirely, and with it the targeting, the reload, the
 * command routing and the fired event that everything else is built on.
 *
 * <p>So the weapon still does all of that. What it hands over is the last step:
 * if its owner carries a launcher, the shot goes there instead of into the
 * victim, and when the damage lands — if it lands — is the game's business.
 *
 * <p>The mirror of {@link DamageModifier}, which changes <em>how hard</em> a shot
 * lands; this changes <em>whether and when</em> it does. Neither is a rule the
 * library imposes: a game that installs no launcher is hitscan exactly as before.
 */
public interface ProjectileLauncher {

    /**
     * Take this shot, or decline it.
     *
     * <p>The damage is final — every modifier and player bonus has already been
     * applied — so a launcher carries a number rather than having to work one out
     * again on arrival.
     *
     * <p>Declining is not an error: a launcher with nowhere to put a projectile
     * says so, and the weapon lands the shot itself rather than the unit quietly
     * becoming harmless.
     *
     * <p>This runs inside the simulation frame, so whatever it does must be
     * deterministic — the same rule every module update follows.
     *
     * @return whether the shot was taken; false leaves it to the weapon
     */
    boolean launch(GameObject shooter, GameObject victim, float damage, DamageType type);
}
