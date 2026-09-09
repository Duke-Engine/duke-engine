package uz.duke.dungeon.gen;

import java.util.List;

/**
 * The result of generating a dungeon: everything the game needs to lay one out,
 * and nothing about how it is drawn.
 *
 * <p>This is plain data on purpose. Generation lives entirely below the client —
 * it hands back an ASCII map and world positions, and the 3D client decides how
 * to render them. That separation is what lets the whole generator be tested
 * headlessly, and the connectivity guarantee checked as a property of the map
 * rather than of a picture.
 *
 * @param asciiMap  the map in the same {@code #}=stone / {@code .}=floor form the
 *                  engine's {@code MapLoader} already reads; row 0 is {@code cy=0}
 * @param hero      where the hero starts (world units)
 * @param monsters  what fills the rooms the hero does not start in, each with
 *                  the kind that was drawn for it
 * @param boss      the one in the furthest room — killing it opens the way down
 * @param bossRoom  which room that is, so the client can point at it
 * @param rooms     the carved rooms, in placement order — {@code rooms[0]} is the
 *                  start room
 * @param links     which rooms were joined by a corridor. Exposed because "the
 *                  corridors are short" is a property worth testing directly, and
 *                  an L-shaped corridor's length is exactly the Manhattan distance
 *                  between the two room centres
 */
public record GeneratedDungeon(
        String asciiMap,
        Placement hero,
        List<Monster> monsters,
        Monster boss,
        int bossRoom,
        List<Room> rooms,
        List<Link> links) {

    /** A spot in the world, in world units (not cells). */
    public record Placement(float x, float y) {
    }

    /**
     * Something to fight, and what kind of thing it is.
     *
     * <p>The kind is a name rather than a type because the list of kinds lives in
     * a data file: the generator picks from what the file describes and never
     * learns what a Runner is.
     */
    public record Monster(String kind, Placement at) {
    }

    /** A carved rectangle of floor, in cell coordinates. */
    public record Room(int x, int y, int w, int h) {

        int centerCellX() {
            return x + w / 2;
        }

        int centerCellY() {
            return y + h / 2;
        }
    }

    /**
     * A corridor between two rooms, by index. {@code from} was already part of the
     * connected set when the corridor was carved and {@code to} was joined to it by
     * this corridor — which is what makes the links a spanning tree and so the
     * dungeon one reachable space.
     */
    public record Link(int from, int to) {
    }
}
