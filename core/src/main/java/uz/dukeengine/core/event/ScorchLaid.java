package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;

/**
 * A mark burnt into the ground where the simulation says — the reference's {@code TheGameClient->addScorch} from a
 * nuke's blast or a firestorm ({@code ScorchMarkSize}): laid by a client in the first of its game's scorch pictures,
 * as the reference lays its first kind, on the ground's rise and fall. Posted from the simulation, it reaches a client
 * only where that client's player can see the point, as every event with a place does.
 *
 * @param where  the point it is laid round: x and y the ground
 * @param radius how far it reaches from there
 */
public record ScorchLaid(int frame, Coord3D where, float radius) implements WorldEvent {
}
