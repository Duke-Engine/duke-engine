package uz.dukeengine.rts.module;

import uz.dukeengine.core.thing.GameObject;

/**
 * A module on a factory told what came out of it, the frame it came out — the reference's {@code
 * AIPlayer::onUnitProduced}, which assigns a new unit to the team whose order its factory was working on.
 *
 * <p>Told on the simulation's thread, before game code that watches every factory ({@link
 * uz.dukeengine.rts.RtsSimulation#onProduced}), and in the order the factory's modules stand — the same on every
 * machine. Without it, a computer player had to compare the object list and every factory's queue frame to
 * frame, and guess which of two factories that finished the same unit together made which.
 */
public interface ProductionListener {

    /** {@code unit} has just left this module's factory, standing where it was released. */
    void onProduced(GameObject unit);
}
