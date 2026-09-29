package uz.dukeengine.core.map;

/**
 * One piece of scenery on a map: a model placed to be looked at, and nothing else — no thing of the simulation's stands
 * for it, so it costs a frame nothing, is on no minimap and in no snapshot. Drawn with the ground it stands on and hidden
 * by the fog as the ground is.
 *
 * <p>Across the ground, cells, as a {@link MapThing}'s are: {@link #x} and {@link #y} from the left edge of the first
 * column and the top edge of the first row, and {@link #footprint} too, so a map drawn at its own scale has nothing to
 * be told.
 *
 * <p>With a footprint it stands in the way: the simulation closes the cells of the ground it covers, as a still thing's
 * outline does, and a walker is kept out of it by its shape. That is the one part of scenery the simulation reads, and
 * every machine reads it from the same map.
 */
public interface MapScenery {

    /** The model it is drawn with: a path, whole from the root of the game's art, as it is loaded. */
    String model();

    /** Cells across. */
    float x();

    /** Cells down. */
    float y();

    /** Which way it faces, in degrees about the vertical, as a kit's pieces are turned. */
    default float facing() {
        return 0f;
    }

    /** How large it is drawn against the model as it was made. */
    default float scale() {
        return 1f;
    }

    /** A colour multiplied over it, packed {@code 0xRRGGBB}; white leaves it as it is. */
    default int tint() {
        return 0xFFFFFF;
    }

    /** How far round its middle it stands in the way, in cells; 0, the default, stands in nobody's way. */
    default float footprint() {
        return 0f;
    }
}
