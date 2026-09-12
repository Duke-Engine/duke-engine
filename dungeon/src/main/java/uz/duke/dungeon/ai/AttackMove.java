package uz.duke.dungeon.ai;

import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;

/**
 * "Go there, and kill what you meet on the way" — the attack order pointed at a
 * piece of floor rather than at a creature.
 *
 * <p>The engine's {@code AttackObject} needs something to name, and naming
 * something is the one thing a player cannot do about a room he has not walked
 * into yet. Which is exactly when he most wants to say this: advance, and do not
 * make me watch for every doorway.
 *
 * <p><b>It is not a walk and it is not an attack.</b> A walk passes what it goes
 * by — {@link HeroBrain#standAndShoot} says so in as many words, "walking
 * somewhere; what he passes is not his business" — and an attack is about one
 * creature and ends with it. This is a walk whose business IS what it passes: he
 * stops for whatever comes into reach, finishes it, and carries on to the spot he
 * was sent to. Arriving, he has no order left and is guarding the ground he is
 * on, which is where every order in this game ends.
 *
 * <p>By player rather than by unit, as {@link HoldGround} is and for the same
 * reason: it is a standing order, it lives in {@link Orders} beside the brain
 * that obeys it, and a dungeon holds one hero.
 *
 * @param spot where he is going, or {@code null} to call the whole thing off
 */
public record AttackMove(int playerIndex, Coord3D spot) implements Command {
}
