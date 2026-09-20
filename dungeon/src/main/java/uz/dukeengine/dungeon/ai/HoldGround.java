package uz.dukeengine.dungeon.ai;

import uz.dukeengine.core.message.Command;

/**
 * "Stand where you are and pick no fights" — this game's own order.
 *
 * <p>Three of the four orders the panel offers are the engine's: walk there,
 * attack that, stop. This is the fourth, and it is a dungeon's rather than an
 * RTS's, which is why it is declared here beside the brain that obeys it.
 *
 * <p>What it is <em>for</em> is the thing the other three cannot say. The hero
 * shoots whatever he can see without being told — see
 * {@link HeroBrain#standAndShoot} — and that is right nearly always and wrong at
 * exactly the moments that matter: creeping up on a boss, waiting at a doorway
 * for the room to come to him, standing in the dark while something walks past.
 * Stop cancels a walk and says nothing about his bow, so before this there was no
 * way to tell him to leave something alone.
 *
 * <p><b>Set rather than toggled</b>, which it was to begin with. A toggle needs
 * the sender to know the current state, and it cannot be shown: a button that
 * lights while the state is on has to know <em>which</em> state, and "the other
 * one" is not a state. Two buttons say it plainly instead — Stop means stop and
 * Guard means guard — and each one is also the lamp for the state it sets. See
 * {@link Doing}.
 *
 * @param stand whether to stand still and start nothing, or to go back to
 *              guarding the ground he is on
 */
public record HoldGround(int playerIndex, boolean stand) implements Command {
}
