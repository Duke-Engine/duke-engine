package uz.dukeengine.rpg.item;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "Use that on this": a player's hero sent to take what is in one slot of his bag to a thing, and use it there — the
 * key on the keep's gate. See {@link ItemErrand}.
 *
 * @param slot   which of his bag's slots, from 0
 * @param target what he takes it to
 */
public record UseItem(int playerIndex, int slot, ObjectId target) implements Command {
}
