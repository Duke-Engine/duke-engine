package uz.duke.dungeon.skill;

import uz.duke.core.message.Command;

/**
 * "Cast the skill on this key" — this game's own command.
 *
 * <p>Not an RTS order and never will be, which is exactly why it is declared
 * here: {@code core}'s {@link Command} says a game brings its own set, and
 * {@code rts} now lets a game built on it do so. Going through the command
 * pipeline rather than calling the skill directly is what makes a keypress land
 * on a frame boundary, on the simulation thread, and in the replay log.
 *
 * <p>It names the key rather than the hero, and finding whose hero that is happens
 * in the simulation. A command carrying an object id would have meant the input
 * layer reading simulation state to fill it in, from the render thread, one frame
 * out of date.
 */
public record CastSkill(int playerIndex, char key) implements Command {
}
