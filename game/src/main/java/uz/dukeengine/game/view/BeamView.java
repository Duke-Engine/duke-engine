package uz.dukeengine.game.view;

import uz.dukeengine.core.math.Coord3D;

/**
 * A beam the simulation owns, as a client is shown it — see {@code World.beam}: the look it is drawn with, its two
 * ends in the simulation's frame, and the share of its full width it has, 0 to 1.
 */
public record BeamView(int id, String look, Coord3D from, Coord3D to, float width) {
}
