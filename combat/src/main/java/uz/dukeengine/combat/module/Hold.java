package uz.dukeengine.combat.module;

import uz.dukeengine.core.thing.GameObject;

/**
 * A module whose thing holds others inside it, answering whether one it holds fires from in there — a transport's
 * firing ports, a garrisoned building, riders on top. A weapon of a thing that is contained asks every hold in the
 * world, in the order the world keeps its things, and holds its fire unless one says yes.
 */
public interface Hold {

    /** Whether {@code passenger} is held in this hold and fires from inside it; false for a thing not held here. */
    boolean passengerFires(GameObject passenger);
}
