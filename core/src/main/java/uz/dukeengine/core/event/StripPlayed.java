package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;

/**
 * A strip of pictures played at a point of the world, rising and fading — the reference's {@code
 * InGameUI::addWorldAnimation} with {@code WORLD_ANIM_FADE_ON_EXPIRE}: a unit's new rank over it, a heal pulse, a
 * crate's money where it was taken. The strip is the game's, by name, its pictures and its time a picture in its
 * client's looks.
 * Posted from the simulation, it reaches a client only where that client's player can see the point, as every event
 * with a place does.
 *
 * @param where   the point it is drawn at first: x and y the ground, z the height
 * @param seconds how long it is up, the last second of it fading out
 * @param rise    how far it rises over those seconds; the reference's rise is a second's, so its level-gain strip,
 *                15 a second for 4 seconds, rises 60
 */
public record StripPlayed(int frame, String strip, Coord3D where, float seconds, float rise) implements WorldEvent {
}
