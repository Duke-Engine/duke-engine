package uz.dukeengine.core.thing;

import uz.dukeengine.core.math.Coord3D;

/**
 * The line a thing is drawn along — a bridge from bank to bank — its two ends, heights and all: see {@link
 * GameObject#setSpan}. The simulation's to set, the client's to draw.
 */
public record Span(Coord3D from, Coord3D to) {
}
