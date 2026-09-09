package uz.duke.dungeon;

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
 * @param skeletons where the skeletons wait (world units), spread across the
 *                  rooms the hero does not start in
 * @param rooms     the carved rooms, in placement order — {@code rooms[0]} is the
 *                  start room
 */
public record GeneratedDungeon(
        String asciiMap,
        Placement hero,
        List<Placement> skeletons,
        List<Room> rooms) {

    /** A spot in the world, in world units (not cells). */
    public record Placement(float x, float y) {
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
}
