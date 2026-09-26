package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A sound cue the simulation plays by name at a thing or a place — the reference's {@code TheAudio->addAudioEvent}
 * from its logic: a unit's voice as its gattling reaches full speed, a vehicle's crew sniped, a building switched off.
 * Every machine's client plays it the same frame, heard where it is as any positional sound, following the thing it
 * rides. Nothing the simulation decides reads it.
 *
 * @param where  the point — for one riding a thing, where the thing stood
 * @param riding the thing it follows, or null for a point of the world
 * @param hold   frames it plays on for: asked again within them it goes on, not asked it stops — a weapon's fire loop
 *               ({@code FireSoundLoopTime}), kept going while the next shot comes within its time. 0 plays it once
 */
public record SoundPlayed(int frame, String cue, Coord3D where, ObjectId riding, int hold) implements WorldEvent {
}
