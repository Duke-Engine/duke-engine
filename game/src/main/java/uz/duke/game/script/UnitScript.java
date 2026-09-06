package uz.duke.game.script;

import uz.duke.core.math.Coord3D;
import uz.duke.core.module.MoveUpdate;
import uz.duke.rts.module.WeaponUpdate;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;

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
        var ai = unit.findModule(MoveUpdate.class);
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
        var ai = unit.findModule(MoveUpdate.class);
        if (ai != null) {
            ai.moveTo(new Coord3D(x, y, 0f));
        }
    }

    /** Engage a target (if the unit has a weapon). */
    protected final void attack(GameObject target) {
        var weapon = unit.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.attack(target.getId());
        }
    }

    /** Stop moving and hold fire. */
    protected final void stop() {
        var ai = unit.findModule(MoveUpdate.class);
        if (ai != null) {
            ai.stop();
        }
        var weapon = unit.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.holdFire();
        }
    }

    // ---- production (for scripts on factory structures) ----

    /** This unit's owner's current money. */
    protected final int money() {
        var player = world.getPlayer(unit.getPlayerIndex());
        return player == null ? 0 : player.getMoney();
    }

    /** How many units this structure has queued (0 if it can't produce). */
    protected final int productionQueue() {
        var production = unit.findModule(uz.duke.rts.module.ProductionUpdate.class);
        return production == null ? 0 : production.getQueueSize();
    }

    /**
     * Queue one unit of {@code templateName} in this structure's production
     * (must be on its build menu). Returns false if this unit is not a factory,
     * the unit is off-menu, or the owner cannot afford it.
     */
    protected final boolean trainUnit(String templateName) {
        var production = unit.findModule(uz.duke.rts.module.ProductionUpdate.class);
        var template = world.findTemplate(templateName);
        if (production == null || template == null || !production.canBuild(templateName)) {
            return false;
        }
        return production.queue(template);
    }

    /** Send this structure's finished units to a rally position. */
    protected final void setRallyPoint(float x, float y) {
        var production = unit.findModule(uz.duke.rts.module.ProductionUpdate.class);
        if (production != null) {
            production.setRallyPoint(new Coord3D(x, y, 0f));
        }
    }
}
