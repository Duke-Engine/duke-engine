package uz.dukeengine.rts.module;

import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * A module of a factory that takes part in queueing — the reference's airfield, four parking spaces, refusing a fifth
 * jet before charging for it, each space with its own hangar door: asked for a token as a unit is queued, told when
 * the job is called off or the factory sold, and handed the unit its job made.
 */
public interface ProductionReservation {

    /** A token for {@code unit}, about to be queued, or null to refuse the job — nothing is then charged or queued. */
    Object reserve(ThingTemplate unit);

    /** The job holding {@code token} was called off, or its factory sold: whatever the token held is free again. */
    void release(Object token);

    /** The job holding {@code token} is done and {@code unit} made — the token saying, where it does, whose door opens. */
    void place(GameObject unit, Object token);

    /**
     * The door of its factory the job holding {@code token} goes out by, the first 0 — the reference's {@code
     * reserveDoorForExit}, a parking space's own hangar door — or -1 where it names none, and the first is used. Its
     * door, and no other, opens for it, and it is made once that door is open.
     */
    default int door(Object token) {
        return -1;
    }
}
