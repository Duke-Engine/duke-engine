package uz.duke.dungeon.ai;

import uz.duke.core.message.Command;

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
 * <p>A toggle rather than a state to be set, because the panel's button is a
 * toggle and a command that carried "on" or "off" would need the client to read
 * the simulation to know which to send — from the render thread, a frame late,
 * and wrong the moment two of them arrived together.
 */
public record HoldGround(int playerIndex) implements Command {
}
