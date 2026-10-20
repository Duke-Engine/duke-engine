package uz.dukeengine.action;

import uz.dukeengine.combat.module.PointAttack;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * An attack toward a point, no target named — a click on open ground: turned to it, one attack at the weapon's own
 * timing, the enemy nearest its front within reach and the swing's arc struck, or a projectile loosed along the line;
 * held, again each time the weapon is ready toward the latest point, until it is let go. Let go between blows it is
 * over; during one's wind-up, over once that blow has gone off — and a single click, with no blow yet, makes its one.
 */
public final class Swing implements PointAttack {

    /** The arc a swing strikes in: a third of a turn. */
    private static final float ARC_DEGREES = 120f;

    /** How far past its reach a swing looks for middles, the bodies' own sizes beyond them. */
    private static final float LOOK = 40f;

    private Coord3D point;
    private boolean held;
    /** Whether it has swung toward the point since it was begun. */
    private boolean swung;

    private Swing() {
    }

    /**
     * Attack toward {@code point} with {@code thing}'s weapon, again while {@code held}: the attack it is already on,
     * if it is on one, turned to the new point and held or let go.
     */
    public static void toward(GameObject thing, Coord3D point, boolean held) {
        var weapon = thing.findModule(WeaponUpdate.class);
        if (weapon == null || point == null) {
            return;
        }
        var swing = weapon.getPointAttack() instanceof Swing going ? going : new Swing();
        swing.point = point;
        swing.held = held;
        weapon.attackToward(swing);
    }

    @Override
    public Coord3D point(boolean windingUp) {
        return !held && swung && !windingUp ? null : point;
    }

    @Override
    public boolean attacked() {
        swung = true;
        return held;
    }

    /** The nearest enemy — by reach, then the lowest id — within {@code reach} and inside the arc before it. */
    @Override
    public GameObject struck(WeaponUpdate weapon, float reach, float facing) {
        var owner = weapon.getOwner();
        World world = owner.getWorld();
        float half = (float) Math.toRadians(ARC_DEGREES) / 2f;
        var here = owner.getPosition();
        GameObject best = null;
        float bestGap = Float.MAX_VALUE;
        for (var other : world.objectsInRange(here, reach + LOOK, thing -> thing != owner)) {
            if (other.isEffectivelyDead() || other.getBody() == null
                    || world.getRelationship(owner.getPlayerIndex(), other.getPlayerIndex()) != Relationship.ENEMIES
                    || !weapon.canFireAt(other)) {
                continue;
            }
            float gap = World.reachBetween(owner, other);
            if (gap > reach) {
                continue;
            }
            var at = other.getPosition();
            float off = (float) StrictMath.atan2(at.y() - here.y(), at.x() - here.x()) - facing;
            off = (float) StrictMath.atan2(StrictMath.sin(off), StrictMath.cos(off));
            if (Math.abs(off) > half) {
                continue;
            }
            if (gap < bestGap || gap == bestGap && best != null && other.getId().value() < best.getId().value()) {
                best = other;
                bestGap = gap;
            }
        }
        return best;
    }
}
