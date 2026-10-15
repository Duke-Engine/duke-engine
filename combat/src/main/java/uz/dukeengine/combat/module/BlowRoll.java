package uz.dukeengine.combat.module;

import uz.dukeengine.core.thing.World;

/**
 * A module that may make one blow of its owner's land harder by chance — a critical strike. Asked exactly once for
 * each shot its weapon fires, beside its {@link DamageModifier}s, and never to estimate one, so what it draws from the
 * world's own random numbers is drawn alike on every machine.
 */
public interface BlowRoll {

    /** What this blow is multiplied by: 1 for an ordinary one. May draw from {@code world.random()}. */
    float roll(World world);

    /** Every roll on {@code owner} for one blow, multiplied together in module order: 1 for none. */
    static float of(uz.dukeengine.core.thing.GameObject owner, World world) {
        float multiplier = 1f;
        for (var module : owner.getModules()) {
            if (module instanceof BlowRoll roll) {
                multiplier *= roll.roll(world);
            }
        }
        return multiplier;
    }
}
