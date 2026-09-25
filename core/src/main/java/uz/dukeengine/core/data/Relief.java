package uz.dukeengine.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component that is a map's relief: a row of whole numbers for every row of cell corners, each the height of
 * that corner in steps — {@code Relief = ["0 0 1 2", "0 1 2 3", …]}, one more row and one more number a row than
 * the map has cells, as {@code uz.dukeengine.core.pathfind.HeightMap} reads it.
 *
 * <p>The engine reads it as the rows it is -- see {@code uz.dukeengine.core.map.MapTerrain} -- and so does an editor,
 * which draws the ground rising and falling
 * over the {@link Grid} beside it, and raises and lowers it by hand.
 *
 * <p>{@code diagonal} is which corners of a cell its split joins — the main diagonal unless said; a game that writes
 * its maps north first, with the reference's rows turned over, says the other.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface Relief {

    /** Which diagonal the relief's cells are split along. */
    uz.dukeengine.core.pathfind.HeightMap.Diagonal diagonal() default uz.dukeengine.core.pathfind.HeightMap.Diagonal.MAIN;
}
