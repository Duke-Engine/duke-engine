package uz.dukeengine.game.view;

import uz.dukeengine.core.math.Coord3D;

/**
 * An effect riding a thing until the simulation ends it, as a client is shown it — see {@code World.effect}: the
 * particle system of its name, on the thing of that id, at its bone or a point in its own frame.
 */
public record EffectView(int id, String name, int thing, String bone, Coord3D offset) {
}
