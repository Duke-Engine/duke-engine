package uz.dukeengine.rts.module;

import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.player.Upgrade;

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

    /**
     * Whether {@code research}, about to be queued, may be — false refuses it before anything is charged, as a full
     * queue refuses an upgrade ({@code ProductionUpdate::canQueueUpgrade}, {@code CANMAKE_QUEUE_FULL}). Every one
     * takes it unless it says otherwise.
     */
    default boolean admits(Upgrade research) {
        return true;
    }

    /**
     * Write {@code token}, which this module gave a job, into a save: the factory writes it with the job and hands it
     * to {@link #loadToken} on a load. What the module holds itself is for its own save; this is the value it handed
     * out. A module that gives out tokens and does not say how to write them refuses the save.
     */
    default void saveToken(Object token, uz.dukeengine.core.save.SaveOut out) {
        throw new uz.dukeengine.core.save.UnsavedModuleException(getClass().getName(), getClass().getName()
                + " gave a job a token and cannot write it: it overrides saveToken and loadToken, or a save is refused");
    }

    /** Read back the token {@link #saveToken} wrote, an equal one; the module's own state is loaded apart from it. */
    default Object loadToken(uz.dukeengine.core.save.SaveIn in) {
        throw new uz.dukeengine.core.save.UnsavedModuleException(getClass().getName(), getClass().getName()
                + " cannot read a token back: it overrides saveToken and loadToken, or a save is refused");
    }
}
