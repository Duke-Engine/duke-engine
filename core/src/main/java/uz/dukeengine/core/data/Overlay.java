package uz.dukeengine.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component that is a painted map's second layer: a row of characters for every row of cells, one
 * character a cell, each a key of the same palette as the {@link Paint} beside it — the picture laid
 * <em>over</em> the cell's own and faded in by the shape its {@link Fade} names.
 *
 * <p>Read only where the {@code @Fade} row says the cell has an overlay; anywhere else the character is
 * a placeholder and nothing reads it.
 *
 * <p>This is where ground stops being a patchwork. With one picture a cell, grass stops and sand starts on
 * a cell's edge, and every border on a map is a staircase. Across the 65 skirmish maps of one RTS measured,
 * 12.9% of the ground is blended — every edge between one picture and the next — so a map without this
 * layer is a map whose edges are all drawn wrong. The simulation never reads it.
 *
 * <p><b>More than one layer.</b> A record may mark several components, and the n-th {@code @Overlay} in the
 * order the record declares them goes with the n-th {@code @Fade}; each layer is drawn over everything
 * before it. One is enough where two kinds of ground meet. Where three meet a cell needs two pictures over
 * its own, and with one the third stops hard — 0.22% of the cells of that same RTS's maps, 29,241 of 13.3
 * million, always a small patch at a junction of three textures. A record whose counts differ is told so
 * once and drawn with the pairs that are whole.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface Overlay {
}
