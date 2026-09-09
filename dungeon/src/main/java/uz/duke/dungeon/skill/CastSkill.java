package uz.duke.dungeon.skill;

import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;
import uz.duke.core.thing.ObjectId;

/**
 * "Cast the skill on this key, at that" — this game's own command.
 *
 * <p>Not an RTS order and never will be, which is exactly why it is declared
 * here: {@code core}'s {@link Command} says a game brings its own set, and
 * {@code rts} now lets a game built on it do so. Going through the command
 * pipeline rather than calling the skill directly is what makes a keypress land
 * on a frame boundary, on the simulation thread, and in the replay log.
 *
 * <p>It names the key rather than the hero, and finding whose hero that is happens
 * in the simulation. A command carrying the caster's id would have meant the input
 * layer reading simulation state to fill it in, from the render thread, one frame
 * out of date.
 *
 * <p>What it does carry is what the player pointed at, because that is his and
 * nothing else knows it: a strike is aimed at the creature he clicked, not at
 * whatever happens to be nearest when the frame comes round. Both are empty for a
 * skill that goes off where he stands.
 *
 * @param target  the creature a {@code UNIT} skill was aimed at, or null
 * @param point   where a {@code GROUND} skill was aimed, or null
 */
public record CastSkill(int playerIndex, char key, ObjectId target, Coord3D point)
        implements Command {

    /** Cast with nothing pointed at — the self-cast, and what a test usually wants. */
    public CastSkill(int playerIndex, char key) {
        this(playerIndex, key, null, null);
    }
}
