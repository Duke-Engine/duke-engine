package uz.dukeengine.game.script;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * The base class for user-written unit behaviour — duke-engine's
 * {@code MonoBehaviour}. Extend it, override {@link #onUpdate()}, attach it to
 * a unit in the Studio, and the engine calls it once per logic frame
 * (30×/second):
 *
 * <pre>{@code
 * public class Berserker extends UnitScript {
 *     @Override
 *     public void onUpdate() {
 *         if (!isAttacking()) {
 *             var enemy = findNearestEnemy(200);
 *             if (enemy != null) {
 *                 attack(enemy);
 *             }
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Scripts run on the simulation thread inside the deterministic logic step.
 * The rules of that world apply: <b>never</b> read the wall clock, use
 * {@code Math.random()}/unseeded {@code Random}, spawn threads, or touch UI —
 * or multiplayer and replays will desync. Everything reachable from the
 * protected helpers below is safe.
 */
public abstract class UnitScript {

    GameObject unit;
    World world;

    /** Called once, the frame the script's unit first updates. */
    public void onStart() {
    }

    /** Called every logic frame (30×/second of game time). */
    public abstract void onUpdate();

    // ---- state queries ----

    /** The unit this script is attached to. */
    protected final GameObject unit() {
        return unit;
    }

    /** The simulation the unit lives in. */
    protected final World world() {
        return world;
    }

    protected final Coord3D position() {
        return unit.getPosition();
    }

    protected final float health() {
        return unit.getBody() == null ? 0f : unit.getBody().getHealth();
    }

    protected final int frame() {
        return world.getFrame();
    }

    protected final boolean isMoving() {
        var ai = unit.getLocomotor();
        return ai != null && ai.isMoving();
    }

    protected final boolean isAttacking() {
        var weapon = unit.findModule(WeaponUpdate.class);
        return weapon != null && weapon.isAttacking();
    }

    protected final float distanceTo(GameObject other) {
        return position().distance(other.getPosition());
    }

    /** The closest living enemy within {@code range}, or {@code null}. */
    protected final GameObject findNearestEnemy(float range) {
        return world.findClosest(position(), range, candidate ->
                candidate != unit
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(unit.getPlayerIndex(), candidate.getPlayerIndex())
                                == Relationship.ENEMIES);
    }

    // ---- orders ----

    /** Walk toward a point (pathfinds around obstacles if the unit can move). */
    protected final void moveTo(float x, float y) {
        var ai = unit.getLocomotor();
        if (ai != null) {
            ai.moveTo(new Coord3D(x, y, 0f));
        }
    }

    /** Engage a target (if the unit has a weapon), an order of the game's own. */
    protected final void attack(GameObject target) {
        var weapon = unit.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.attack(target.getId(), false, uz.dukeengine.combat.message.OrderSource.GAME);
        }
    }

    /** Stop moving and hold fire. */
    protected final void stop() {
        var ai = unit.getLocomotor();
        if (ai != null) {
            ai.stop();
        }
        var weapon = unit.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.holdFire();
        }
    }
}
