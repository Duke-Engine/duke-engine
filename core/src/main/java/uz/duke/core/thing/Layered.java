package uz.duke.core.thing;

/**
 * A world built in storeys: how tall one is, in world units. Every map laid in it is laid at
 * that height — see {@code GameLogic.setPathGrid} — so a floor swapped in mid-game stands as
 * tall as the first.
 */
public interface Layered extends WorldTemplate {

    float levelHeight();
}
