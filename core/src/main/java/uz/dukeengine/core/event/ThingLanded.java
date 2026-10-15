package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A thing the story moved has come down where it was sent — the hero landed from the sky, a commander thrown back has
 * hit the ground — for the picture to answer: the dust, the shake. See {@code ScriptedMove}; the story itself hears it
 * on the simulation's thread, through the move's own word.
 */
public record ThingLanded(int frame, ObjectId object, Coord3D where) implements WorldEvent {
}
