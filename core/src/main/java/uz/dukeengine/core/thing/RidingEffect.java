package uz.dukeengine.core.thing;

import uz.dukeengine.core.math.Coord3D;

/**
 * An effect the simulation plays riding a thing until it ends it — see {@link World#effect(String, GameObject, String,
 * Coord3D)}: the particle system of its name, at a bone of the thing's model, or a point in the thing's own frame, or
 * its middle.
 *
 * @param bone   the bone of the thing's model it rides, or null
 * @param offset the point of the thing's own frame it rides where it names no bone — x its forward, y the ground's
 *               other way, z up — or null for its place
 */
public record RidingEffect(int id, String name, ObjectId thing, String bone, Coord3D offset) {
}
