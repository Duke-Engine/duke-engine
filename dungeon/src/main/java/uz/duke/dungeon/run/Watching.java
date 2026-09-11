package uz.duke.dungeon.run;

import uz.duke.core.message.Command;
import uz.duke.core.thing.ObjectId;

/**
 * "I have picked that one out" — this game's own command, and the only one that
 * asks for nothing to be done.
 *
 * <p>It exists because the panel describes <em>a creature</em> and the two halves
 * of that sentence live on opposite sides of the thread. Which creature is the
 * client's: selection is a fact about a screen, and a simulation that had one
 * would be a simulation with a camera in it. What the creature is worth is the
 * simulation's: a skeleton's damage is its template's times whatever this floor
 * multiplies by, and the client has never seen a template.
 *
 * <p>So the client says which and the game says what, and the saying travels the
 * only road that crosses safely — the command queue, on a frame boundary. The
 * alternative is the render thread reading objects while the simulation moves
 * them, which is not slower or uglier but simply wrong.
 *
 * <p>Nothing in the world changes when this arrives. It moves one number that the
 * status line is written from, and the status line is the one part of the
 * snapshot the engine carries without reading. No frame differs because of it,
 * which is exactly why a selection may be a command at all.
 *
 * @param unit the one thing he has picked out, or {@code null} for nothing and
 *             for a whole box of them at once — either way the panel goes back to
 *             describing his own hero
 */
public record Watching(int playerIndex, ObjectId unit) implements Command {
}
