package uz.dukeengine.dungeon.skill;

import uz.dukeengine.core.message.Command;

/**
 * "Put my next level into that one" — this game's fourth command.
 *
 * <p>A click on the little button beside a slot goes through the command queue
 * rather than reaching into the simulation, for the same reason a keypress and a
 * card do: it lands on a frame boundary, on the simulation thread, and in the
 * replay log. The client's job ends at posting it.
 *
 * <p>It names only the key, and everything else is settled on the far side —
 * whether he has a point, whether that slot is full, whether an ultimate is still
 * waiting for him, whether raising it would leave his other skills too far
 * behind. The panel draws the button only when {@link SkillRanks} says it may,
 * but the panel is drawing a snapshot that is already a frame old, so the rule
 * is asked again here. A click that arrives too late does nothing rather than
 * something nearly right.
 *
 * @param key which slot — Q, W, E or R by convention
 */
public record UpgradeSkill(int playerIndex, char key) implements Command {
}
