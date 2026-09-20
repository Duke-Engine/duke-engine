package uz.duke.core.thing;

/**
 * Built in storeys: how tall one is, in world units. Every map laid in a layered world is laid at that
 * height — see {@code GameLogic.setPathGrid} — so a floor swapped in mid-game stands as tall as the first.
 *
 * <p>A capability of a world and of a map alike, which is why it is nobody's child: a game whose storeys are
 * one height everywhere says so on its world, and a map that stands at its own says so on itself.
 */
public interface Layered {

    float levelHeight();
}
