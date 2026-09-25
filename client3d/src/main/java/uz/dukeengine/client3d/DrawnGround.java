package uz.dukeengine.client3d;

import uz.dukeengine.core.pathfind.HeightMap;

/** The ground as it is drawn, for what is laid over it: its cells, its heights, and the cut each is drawn along. */
interface DrawnGround {

    /** How wide a cell is, or 0 where there are no cells. */
    float cellSize();

    /** How many cells across and down. */
    int columns();

    int rows();

    /** How high the ground stands at a place, in the client's frame: {@code z} is the map's y. */
    float heightAt(float x, float z);

    /** The diagonal a cell is drawn cut along. */
    HeightMap.Diagonal diagonal(int cx, int cy);

    /**
     * The highest of the four corners of the cell a place lies in — the reference's {@code getMaxCellHeight} — or the
     * height there where there are no cells.
     */
    default float highestCorner(float x, float z) {
        float cell = cellSize();
        if (cell <= 0f) {
            return heightAt(x, z);
        }
        int cx = Math.clamp((int) Math.floor(x / cell), 0, Math.max(0, columns() - 1));
        int cz = Math.clamp((int) Math.floor(z / cell), 0, Math.max(0, rows() - 1));
        float x0 = cx * cell;
        float z0 = cz * cell;
        return Math.max(Math.max(heightAt(x0, z0), heightAt(x0 + cell, z0)),
                Math.max(heightAt(x0, z0 + cell), heightAt(x0 + cell, z0 + cell)));
    }

    /** Ground with no cells, flat at nothing. */
    DrawnGround NONE = new DrawnGround() {
        @Override
        public float cellSize() {
            return 0f;
        }

        @Override
        public int columns() {
            return 0;
        }

        @Override
        public int rows() {
            return 0;
        }

        @Override
        public float heightAt(float x, float z) {
            return 0f;
        }

        @Override
        public HeightMap.Diagonal diagonal(int cx, int cy) {
            return HeightMap.Diagonal.MAIN;
        }
    };
}
