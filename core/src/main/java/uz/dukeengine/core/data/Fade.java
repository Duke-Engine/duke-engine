package uz.dukeengine.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component that says how each cell's {@link Overlay} fades in: a row of characters for every row of
 * cells, one character a cell — {@code .} for a cell with no overlay, or one hexadecimal digit naming the
 * shape.
 *
 * <p>The shape is a name, and the mask behind it is the engine's. A game says which of sixteen it wants;
 * it never supplies a picture of a fade, because the fades are few and fixed and every game's are the
 * same ones.
 *
 * <pre>
 *   0 from the left       1 from the right      2 from the top        3 from the bottom
 *   4 the top-left corner 5 the top-right       6 the bottom-right    7 the bottom-left
 *   8 – F                 the same eight, reversed
 * </pre>
 *
 * <p>A side fades straight across the cell: all overlay at that edge, none at the opposite one. A corner
 * fills the triangle between that corner and the diagonal that does not touch it, and fades to nothing
 * on that diagonal. Reversed is the overlay where the shape was not and none where it was — which for a
 * side is simply the opposite side, and for a corner is everything but that corner's triangle.
 *
 * <p>Top is the first row and left the first column, as the rows are written.
 *
 * <p>A map with more than one {@link Overlay} has one of these a layer: the n-th {@code @Fade}, in the order
 * the record declares its components, says how the n-th overlay fades in.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface Fade {
}
