package uz.duke.dungeon.gen;

import java.util.List;
import uz.duke.core.pathfind.PathGrid;

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
 * @param levelMap  the same grid again, saying how high each cell stands: a digit
 *                  is a storey and {@code /} is a stair. Read by
 *                  {@code MapLoader.levels}, so the rule for what may be walked
 *                  between two of them belongs to the engine rather than here
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
 * @param props       what stands about in the rooms — solid, and not alive
 * @param roomStoreys how high each room ended up standing, in the same order as
 *                  {@code rooms} — what the generator decided, after any climb it
 *                  had to give up to keep the dungeon walkable
 */
public record GeneratedDungeon(
        String asciiMap,
        String levelMap,
        Placement hero,
        List<Monster> monsters,
        Monster boss,
        int bossRoom,
        List<Room> rooms,
        List<Link> links,
        List<Integer> roomStoreys,
        List<Prop> props) {

    /** A spot in the world, in world units (not cells). */
    public record Placement(float x, float y) {

        /**
         * The centre of a cell, which is where a dungeon stands everything it
         * places — hero, monster, boss and prop alike.
         *
         * <p>Here rather than inside the generator because a stage file writes
         * cells and reads them back. Two copies of this arithmetic is a frozen
         * dungeon that plays half a cell away from the one it was cut from, and
         * half a cell is the difference between a doorway and a wall.
         */
        public static Placement atCell(int cx, int cy) {
            float cell = PathGrid.DEFAULT_CELL_SIZE;
            return new Placement((cx + 0.5f) * cell, (cy + 0.5f) * cell);
        }

        public int cellX() {
            return (int) Math.floor(x / PathGrid.DEFAULT_CELL_SIZE);
        }

        public int cellY() {
            return (int) Math.floor(y / PathGrid.DEFAULT_CELL_SIZE);
        }
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

    /**
     * Something standing in a room: a pillar, a statue, a barrel.
     *
     * <p>A kind and a place, like a monster — and for the same reason. What a
     * Pillar is lives in data/props/ and how it is drawn lives in the theme; the
     * generator only decides that one goes here.
     */
    public record Prop(String kind, Placement at) {
    }

    /** A carved rectangle of floor, in cell coordinates. */
    public record Room(int x, int y, int w, int h) {

        /**
         * The cell the room is walked to and from.
         *
         * <p>Public because the reachability walk is not only the generator's any
         * more: a hand-edited stage is checked by the same rule, and a check that
         * measured rooms from a different corner would pass floors the generator
         * would have rejected.
         */
        public int centerCellX() {
            return x + w / 2;
        }

        public int centerCellY() {
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
