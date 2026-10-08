package uz.dukeengine.rpg.item;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "Go and get that": a player's hero sent for a thing lying on the floor — a click on it, as on anything else he
 * is sent at. See {@link ItemErrand}.
 *
 * @param item the thing lying there
 */
public record PickUp(int playerIndex, ObjectId item) implements Command {
}
