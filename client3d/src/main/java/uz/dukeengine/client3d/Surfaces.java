package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;

/**
 * How the ground's materials are made.
 *
 * <p>A function rather than an {@code AssetManager}, for the reason {@link TerrainScene} has always taken
 * one: the shape of a scene can then be checked without a window, a GPU or a single file on disk, and the
 * tests in this module do exactly that.
 *
 * <p>Two things rather than one, because the ground is now two things. It was a colour — the flat green a
 * map with nothing to say is drawn in — and a map that says what its cells are painted with also hands over
 * a picture for each. The picture is the path the map itself wrote, passed through untouched; whoever
 * implements this loads it, and says so and carries on where it will not load.
 */
@FunctionalInterface
interface Surfaces {

    /**
     * A material for a surface.
     *
     * @param colour  what it is painted, which a picture is laid over — white where the picture is the
     *                whole of it
     * @param texture the map's own word for the picture, or {@code null} for a plain colour. It repeats:
     *                the ground's texture coordinates run across the world rather than 0..1 inside a cell,
     *                so whatever is loaded has to be set to wrap or one cell shows the whole picture and
     *                the rest is its edge pixel smeared across the map
     */
    Material of(ColorRGBA colour, String texture);
}
