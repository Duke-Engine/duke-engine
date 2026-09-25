package uz.dukeengine.core.thing;

import uz.dukeengine.core.math.Coord3D;

/**
 * A beam the simulation owns — see {@link World#beam}: the look it is drawn with, its two ends, and the share of its
 * full width it has, 0 to 1.
 */
public record Beam(int id, String look, Coord3D from, Coord3D to, float width) {

    public Beam {
        width = Math.clamp(width, 0f, 1f);
    }
}
