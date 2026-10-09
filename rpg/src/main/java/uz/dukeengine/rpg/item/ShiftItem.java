package uz.dukeengine.rpg.item;

import uz.dukeengine.core.message.Command;

/**
 * "Put that on" or "take that off": a thing moved between a player's hero's backpack and what he wears, at once and
 * where he stands. See {@link LootBag#shift}.
 *
 * @param place which place in his bag, from 0: the backpack's, then the worn slots' ({@link LootBag#wornPlace})
 */
public record ShiftItem(int playerIndex, int place) implements Command {
}
