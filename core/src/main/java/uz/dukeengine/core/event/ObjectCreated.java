package uz.dukeengine.core.event;

import uz.dukeengine.core.thing.ObjectId;

/**
 * A thing come into the world, and how: made by a factory, let out of a hold, spawned by the game's script, or handed
 * over — so a voice that greets what was made greets only that. A moment, not a state.
 */
public record ObjectCreated(int frame, ObjectId object, How how) implements WorldEvent {

    /** How a thing came into the world. */
    public enum How { PRODUCED, UNLOADED, SPAWNED, RELEASED }
}
