package uz.dukeengine.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component that lays the picture of some of a map's cells by their corners instead of straight down: one entry a
 * cell, {@code "cx cy u0 v0 u1 v1 u2 v2 u3 v3"}, the corners at ({@code cx}, {@code cy}), ({@code cx+1}, {@code cy}),
 * ({@code cx+1}, {@code cy+1}) and ({@code cx}, {@code cy+1}), each {@code u, v} in the picture's own units — one copy
 * of it is 1 — read by {@code uz.dukeengine.core.map.MapTerrain#rows}.
 *
 * <p>A picture laid straight down is stretched up a cliff by the rise across the cell, about 1.8 times at 24 steps
 * and 2.7 and more past 40. The reference lays it up its cliffs as its map's editor laid it, four corner coordinates
 * a cliff cell ({@code WorldHeightMap::getUVForTileIndex} with {@code AdjustCliffTextures}); the game works them out
 * from its map, and the engine draws what it is told.
 *
 * <p>How the ground looks, not what it is: the simulation must never read it.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface PictureCorners {
}
