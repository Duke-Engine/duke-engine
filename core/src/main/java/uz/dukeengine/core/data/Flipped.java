package uz.dukeengine.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component that says which of a map's cells are drawn cut along the other diagonal from the one its {@link Relief}
 * splits them along: a row of characters for every row of cells, one a cell, any but {@code .} and {@code 0} turning
 * that cell — {@code Flipped = ["..1.", "....", …]}, read by {@code uz.dukeengine.core.map.MapTerrain#rows}.
 *
 * <p>The reference keeps its heights and the clicks on its ground on one diagonal and draws some cells cut along the
 * other ({@code HeightMap.h}'s {@code FLIP_TRIANGLES}, {@code HeightMapRenderObjClass::updateVB}): a cell whose blend
 * fades along a diagonal, and a cliff whose picture is stretched up it, cut along whichever rises less. Which cells
 * is the game's to work out; the engine draws what it is told.
 *
 * <p>Only the drawn ground takes it, and the marks laid on it. The simulation must never read it: heights, slopes,
 * clicks and the pathfinder keep the relief's diagonal, and a unit on a turned cell stands where the logic says, a
 * little off the drawn ground, as in the reference.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flipped {
}
