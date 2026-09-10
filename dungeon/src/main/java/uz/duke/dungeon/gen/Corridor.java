package uz.duke.dungeon.gen;

import java.util.List;

/**
 * One carved corridor: which rooms it joins, and the line it walks between them.
 *
 * <p>Carving needs no order to it — a floor is a floor whichever end it was cut
 * from — which is why this did not exist while dungeons were flat. Height needs
 * one: a stair is somewhere <em>along</em> a corridor, with one storey behind it
 * and another ahead of it, and that is a question about a journey.
 *
 * @param from  the room that was already connected when this was carved
 * @param to    the room it joined to the dungeon
 * @param spine the centre line, cell by cell, in walking order. Each entry is
 *              {@code {x, y, alongX}}, the flag saying which way the corridor's
 *              width runs at that cell
 * @param width how many cells across the corridor is
 */
record Corridor(int from, int to, List<int[]> spine, int width) {
}
