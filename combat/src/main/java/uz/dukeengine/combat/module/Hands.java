package uz.dukeengine.combat.module;

import uz.dukeengine.combat.ArmedSide;
import uz.dukeengine.combat.ArmedWorld;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;

/**
 * A weapon as it is in one unit's hands: what it deals, how far it reaches, how fast it fires, once the unit's
 * modules, its side and the game's bonuses have had their say. Nothing here keeps state.
 */
final class Hands {

    private Hands() {
    }

    /**
     * What {@code weapon} deals before the target's armour: its damage, this unit's and its side's bonuses —
     * multiplied in that order, as they always were, so a shot deals the same bits it did.
     */
    static float dealt(GameObject owner, Weapon weapon, float damage) {
        float dealt = damage * damageModifiers(owner) * bonus(owner, weapon, WeaponBonus.Kind.DAMAGE);
        var world = owner.getWorld();
        if (world != null && world.getPlayer(owner.getPlayerIndex()) instanceof ArmedSide side) {
            dealt *= side.getWeaponDamageBonus(); // player-wide upgrade bonus
        }
        return dealt;
    }

    /**
     * Everything attached to this unit that changes how hard it hits, multiplied
     * together.
     *
     * <p>Walked in module order, which is fixed when the object is built, so the
     * product is the same on every machine and in every replay.
     */
    private static float damageModifiers(GameObject owner) {
        float multiplier = 1f;
        for (var module : owner.getModules()) {
            if (module instanceof DamageModifier modifier) {
                multiplier *= modifier.damageMultiplier();
            }
        }
        return multiplier;
    }

    /**
     * The game's weapon bonuses and this weapon's own for what this unit holds, of one kind, added up — see
     * {@link WeaponBonus}.
     */
    static float bonus(GameObject owner, Weapon weapon, WeaponBonus.Kind kind) {
        return owner.getWorld() instanceof ArmedWorld armed
                ? armed.armoury().weaponBonus(owner, kind, weapon.bonuses()) : 1f;
    }

    /** How far a weapon reaches in this unit's hands: its range, and the bonuses for what it holds. */
    static float range(GameObject owner, Weapon weapon) {
        return weapon.attackRange() * bonus(owner, weapon, WeaponBonus.Kind.RANGE);
    }

    /** Everything that changes how fast this weapon fires in this unit's hands, multiplied together in module order. */
    static float rateOfFire(GameObject owner, Weapon weapon) {
        float multiplier = bonus(owner, weapon, WeaponBonus.Kind.RATE_OF_FIRE);
        for (var module : owner.getModules()) {
            if (module instanceof RateOfFireModifier modifier) {
                multiplier *= modifier.rateOfFireMultiplier();
            }
        }
        return multiplier;
    }

    /** How near a weapon fires in this unit's world: see {@link Weapon#leastRange}. */
    static float least(GameObject owner, Weapon weapon) {
        var world = owner.getWorld();
        return weapon.leastRange(world == null ? PathGrid.DEFAULT_CELL_SIZE : world.cellSize());
    }

    /**
     * Whether a target {@code gap} off, outline to outline, is inside a weapon's least range. Outlines that overlap
     * are no distance apart, never less, as the reference's are ({@code distCalcProc_BoundaryAndBoundary_2D}: "sorry,
     * distances can't be negative"): inside any least range, and inside none where the weapon has none.
     */
    static boolean insideLeast(GameObject owner, Weapon weapon, float gap) {
        return Math.max(0f, gap) < least(owner, weapon);
    }

    /**
     * How far the target is, measured wall to wall. A tank parked against a
     * barracks is at range 0 from it, not half a building away — the same rule
     * SAGE uses, and the reason a short-ranged unit can hit a big structure.
     */
    static float rangeTo(GameObject owner, GameObject victim) {
        return World.reachBetween(owner, victim);
    }

    /** Whether it cannot move off from a target too near: nothing to move it, or held where it stands. */
    static boolean cannotBackAway(GameObject owner) {
        return owner.getLocomotor() == null || owner.hasStatus(ObjectStatus.HELD);
    }

    /** Whether the owner is under way — nothing to say if it cannot move at all. */
    static boolean isWalking(GameObject owner) {
        var locomotor = owner.getLocomotor();
        return locomotor != null && locomotor.isMoving();
    }

    /** The rank its experience has reached, 0 for none — told with each shot. */
    static int rankOf(GameObject owner) {
        var experience = owner.findModule(ExperienceModule.class);
        return experience == null ? 0 : experience.getLevel();
    }

    /**
     * The shot {@code armed} takes now, its two rings and their damage worked out for this unit: one roll a shot, so a
     * critical blow is critical in both its rings.
     */
    static Shot shotOf(World world, GameObject owner, Armed armed) {
        var weapon = armed.weapon();
        float wider = bonus(owner, weapon, WeaponBonus.Kind.RADIUS);
        float rolled = BlowRoll.of(owner, world);
        return new Shot(owner.getId(), owner.getPlayerIndex(), weapon, armed.index(),
                dealt(owner, weapon, weapon.damage()) * rolled, weapon.splashRadius() * wider,
                dealt(owner, weapon, weapon.secondaryDamage()) * rolled, weapon.secondaryRadius() * wider,
                null, rolled > 1f);
    }
}
