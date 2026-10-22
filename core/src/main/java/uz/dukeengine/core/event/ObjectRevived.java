package uz.dukeengine.core.event;

import uz.dukeengine.core.thing.ObjectId;

/**
 * A thing kept in the world dead brought back to life ({@code GameObject.revive}) — a fallen bridge raised again — for
 * the picture to draw it whole and alive once more rather than as the body it laid out at its {@link ObjectDied}. It
 * has no place, so it is told through fog as well: a bridge is drawn wherever the ground under it is, and a picture
 * holding no body of it has nothing to raise.
 */
public record ObjectRevived(int frame, ObjectId object) implements WorldEvent {
}
