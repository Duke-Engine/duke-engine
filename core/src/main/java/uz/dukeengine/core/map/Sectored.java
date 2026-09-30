package uz.dukeengine.core.map;

/**
 * A map whose routes are looked for by sectors of {@link #routeSectors} of its cells a side — a wide world: a route
 * across it is found through the sectors its way crosses, examining the ground along its way rather than every cell it
 * could reach, and what reaches what is kept sector by sector as the ground changes — see {@code
 * GameLogic.setRouteSectors}. A map that is not searches its whole grid for each route, as every map always did.
 *
 * <p>Read by the simulation, and every machine reads it from the same map.
 */
public interface Sectored extends MapTemplate {

    /** How many of its cells a side a sector is: 32 for a world of a thousand cells a side. */
    int routeSectors();
}
