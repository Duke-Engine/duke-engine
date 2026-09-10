package uz.duke.dungeon.power;

import uz.duke.core.message.Command;

/**
 * "I'll take that one" — this game's second command, beside
 * {@link uz.duke.dungeon.skill.CastSkill}.
 *
 * <p>A click on a card goes through the command queue rather than reaching into
 * the simulation, for the same reason a keypress does: it lands on a frame
 * boundary, on the simulation thread, and in the replay log. The client's job
 * ends at posting it.
 *
 * <p>It names the offer as well as the card. The offer is on screen while the
 * world is held still, and the client goes on drawing the last snapshot it was
 * given — so without saying which offer he was answering, a click arriving late
 * could spend the next one's card on the last one's picture.
 *
 * <p>A serial rather than the level it was earned at, and that is not a detail:
 * a run that ends puts the hero back at level one, so the next run's second level
 * would answer to the same name as the last one's — and a client remembering
 * which offer it had already dealt with would refuse to show it.
 *
 * @param index    which of the offered cards, in the order they were drawn
 * @param offerId  which offer this answers, counted up through the session
 */
public record ChoosePower(int playerIndex, int index, int offerId) implements Command {
}
