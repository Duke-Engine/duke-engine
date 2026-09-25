package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A named effect played where and when the simulation says — a strafing run's bursts, a slow death's stages, a tower's
 * pulse: the reference's {@code FXList::doFXPos} and {@code doFXObj}, called from object creation lists and behaviours.
 * The client plays the moment of that name, or else the effect list, effect or particle system of that name; a name it
 * has none of draws nothing. Seen where the point is, as every event with a place is. Nothing the simulation decides
 * reads it.
 *
 * @param where  the point, x and y the ground and z the height — for one riding a thing, where the thing stood
 * @param facing the way it is turned, in radians, as a thing's orientation
 * @param riding the thing it rides, its particles that attach to a thing following it; null for a point of the world
 */
public record EffectPlayed(int frame, String name, Coord3D where, float facing, ObjectId riding) implements WorldEvent {
}
