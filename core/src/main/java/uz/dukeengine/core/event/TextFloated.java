package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;

/**
 * A short text floated up from a point of the world — money earned there, a bounty, a word the game wants seen where it
 * happened: the reference's {@code InGameUI::addFloatingText}. Posted from the simulation, it reaches a client only where
 * that client's player can see the point, as every event with a place does; the client draws it rising and fading.
 *
 * @param where the point it rises from: x and y the ground, z the height
 * @param argb  its colour, alpha included — the reference's are 230 or 255 opaque
 */
public record TextFloated(int frame, Coord3D where, String text, int argb) implements WorldEvent {
}
