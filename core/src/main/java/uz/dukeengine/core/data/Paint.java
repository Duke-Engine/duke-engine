package uz.dukeengine.core.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A component that is what a map's cells are painted with: a row of characters for every row of cells, one
 * character a cell, each standing for a name in the map's palette — {@code Paint = ["nnnbbb", "nnnbbb", …]},
 * beside the {@link Grid} it is laid over and read the same way, by
 * {@code uz.dukeengine.core.map.MapTerrain#rows}.
 *
 * <p>One character, so one texture a cell. SAGE kept four 16-bit indices for every cell — the texture and
 * three baked blend and cliff seams — and across 34 million cells of Zero Hour's skirmish maps the three were
 * zero on 88%, 99.8% and 99.8% of them. A renderer works a seam out where two names meet; carrying the baked
 * ones would triple the file to say what can be derived.
 *
 * <p>The simulation must never read this. What a cell is painted with is how it looks, not what it is: what
 * may be walked on is the {@link Grid}, how high it stands is the {@link Relief}, and a machine that renders
 * nothing has to reach the same frame as one that renders everything.
 */
@Documented
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface Paint {
}
