package uz.duke.client3d;

import java.util.BitSet;
import java.util.List;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.game.view.UnitView;

/**
 * What the player has seen of the map, and what they can see right now.
 *
 * <p>The engine's fog answers one question — can this player see that thing, this
 * instant — and answers it about <em>things</em>. It is the right question for an
 * RTS, where the ground is a given and only the units on it are hidden. A dungeon
 * asks a second question the engine has no answer for: has the player ever been
 * here? Without it there is no discovery, because the map is laid out in full
 * before the hero takes a step.
 *
 * <p>So there are three states, and they need two facts per cell rather than one:
 *
 * <ul>
 *   <li>{@link State#UNSEEN} — never visited. Black: no ground, no walls, nothing.
 *   <li>{@link State#REMEMBERED} — visited, out of sight now. The walls are drawn
 *       so the player can find their way back; the engine's own fog takes care of
 *       hiding whatever is moving about in there.
 *   <li>{@link State#VISIBLE} — within sight this instant.
 * </ul>
 *
 * <p>{@code explored} is never cleared while a world lasts — that is the memory.
 * {@code visible} is rewritten every frame — that is the eyes.
 *
 * <p>This is a client-side view of the world and nothing else. It reads a
 * snapshot, it is read by the renderer, and the simulation neither produces nor
 * consumes it — which is what makes "fog cannot affect the game" structural rather
 * than a promise. Nothing here is part of the deterministic state; two players
 * watching the same replay may have explored quite different amounts of it.
 */
final class Discovery {

    enum State { UNSEEN, REMEMBERED, VISIBLE }

    private int width;
    private int height;
    private float cellSize;
    private final BitSet explored = new BitSet();
    private final BitSet visible = new BitSet();

    /**
     * Which cells are stone.
     *
     * <p>Only the softening uses it, and only to leave stone out of the average.
     * Rock is not unlit ground -- it is the thing the walls are made of -- so a
     * room beside it must not be dimmed by it. Without this a corridor two cells
     * wide could never be drawn at full light, because most of what surrounds it
     * is stone.
     */
    private final BitSet solid = new BitSet();

    Discovery(PathGrid grid) {
        reset(grid);
    }

    /**
     * Forget everything and take the shape of a new world.
     *
     * <p>Called when the game lays out a different map — a new run, a deeper
     * floor. A dungeon the player has never been down has to start black, and
     * carrying the old floor's memory into it would open rooms nobody has walked.
     */
    void reset(PathGrid grid) {
        this.width = grid == null ? 0 : grid.getWidth();
        this.height = grid == null ? 0 : grid.getHeight();
        this.cellSize = grid == null ? PathGrid.DEFAULT_CELL_SIZE : grid.getCellSize();
        explored.clear();
        visible.clear();
        solid.clear();
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                if (grid.isBlocked(cx, cy)) {
                    solid.set(cy * width + cx);
                }
            }
        }
        light = new float[width * height];
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    /**
     * Open up everything within {@code radius} of the local player's own units,
     * and note what is in sight this instant.
     *
     * <p>Only the viewer's units open the map. An enemy's eyes are its own
     * business — this is the player's view of the world, not a shared one — and
     * a monster that wandered somewhere must not light it up.
     */
    void reveal(List<UnitView> units, int localPlayer, float radius) {
        visible.clear();
        if (radius <= 0f || width == 0) {
            return;
        }
        for (var unit : units) {
            if (unit.playerIndex() != localPlayer) {
                continue;
            }
            revealAround(unit.x(), unit.y(), radius);
        }
        explored.or(visible);
    }

    /** Mark every cell whose centre lies within {@code radius} of a point. */
    private void revealAround(float x, float y, float radius) {
        int minX = Math.max(0, (int) ((x - radius) / cellSize));
        int maxX = Math.min(width - 1, (int) ((x + radius) / cellSize));
        int minY = Math.max(0, (int) ((y - radius) / cellSize));
        int maxY = Math.min(height - 1, (int) ((y + radius) / cellSize));
        float radiusSquared = radius * radius;
        for (int cy = minY; cy <= maxY; cy++) {
            float dy = (cy + 0.5f) * cellSize - y;
            for (int cx = minX; cx <= maxX; cx++) {
                float dx = (cx + 0.5f) * cellSize - x;
                // A circle, not the bounding box: squared distance keeps the
                // corners out without a square root per cell.
                if (dx * dx + dy * dy <= radiusSquared) {
                    visible.set(cy * width + cx);
                }
            }
        }
    }

    // ---- softening ----

    /**
     * How bright remembered ground is drawn, against 1 for ground in sight.
     *
     * <p>Low enough to read as memory rather than as light, high enough that the
     * player can still find the way back through it.
     */
    private static final float REMEMBERED_LIGHT = 0.34f;

    /** Below this a cell is not drawn at all — it is the edge of the black. */
    static final float DARK = 0.02f;

    /**
     * How fast the fog opens and closes, as a share of the remaining gap each
     * second. Fast enough not to lag behind a walking hero, slow enough that the
     * edge is a sweep rather than a switch.
     */
    private static final float EASE_PER_SECOND = 7f;

    /**
     * The eased, softened brightness of each cell. Presentation only, like the
     * rest of this class — it is a number about drawing, and no two players
     * watching the same replay need agree on it.
     */
    private float[] light = new float[0];

    /**
     * Move every cell a little closer to how bright it ought to be.
     *
     * <p>Three states drawn as three shades give a floor of hard-edged squares:
     * the lit circle around the hero has a staircase for a boundary, and every
     * step he takes flips a row of cells at once. Two things fix that and neither
     * touches what the player is allowed to see.
     *
     * <p>The first is spatial. A cell's target is the average of its own state and
     * its neighbours', so the boundary between lit and remembered is spread over a
     * couple of cells instead of falling on one line. The kernel is the usual
     * 4-2-1: itself, its sides, its corners.
     *
     * <p>The second is time. Nothing jumps to its target; it eases toward it, so
     * ground opens as the hero arrives rather than the instant a cell centre
     * crosses his sight. Per second rather than per frame, or the fog would be
     * quicker on a faster machine.
     *
     * <p>What none of this changes is <em>what</em> is revealed: the softening
     * reads {@code visible} and {@code explored} and never writes them, so a cell
     * that is dim is a cell that was already open.
     */
    void soften(float seconds) {
        int cells = width * height;
        if (light.length != cells) {
            light = new float[cells];
        }
        if (cells == 0) {
            return;
        }
        float step = Math.min(1f, EASE_PER_SECOND * Math.max(0f, seconds));
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                int index = cy * width + cx;
                light[index] += (softenedAt(cx, cy) - light[index]) * step;
            }
        }
    }

    /** A cell's own brightness blurred together with its neighbours'. */
    private float softenedAt(int cellX, int cellY) {
        float total = 0f;
        float weight = 0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = cellX + dx;
                int ny = cellY + dy;
                if (!open(nx, ny)) {
                    continue; // stone, or off the map: neither of them is dark ground
                }
                float share = dx == 0 && dy == 0 ? 4f : dx == 0 || dy == 0 ? 2f : 1f;
                total += rawLightAt(nx, ny) * share;
                weight += share;
            }
        }
        return weight == 0f ? 0f : total / weight;
    }

    private boolean open(int cellX, int cellY) {
        return cellX >= 0 && cellY >= 0 && cellX < width && cellY < height
                && !solid.get(cellY * width + cellX);
    }

    private float rawLightAt(int cellX, int cellY) {
        if (cellX < 0 || cellY < 0 || cellX >= width || cellY >= height) {
            return 0f;
        }
        int index = cellY * width + cellX;
        if (visible.get(index)) {
            return 1f;
        }
        return explored.get(index) ? REMEMBERED_LIGHT : 0f;
    }

    /**
     * How brightly to draw a cell, from 0 for black to 1 for full daylight.
     *
     * <p>The smooth counterpart of {@link #stateAt}, and what the floor is drawn
     * from. The three states are still the truth underneath; this is that truth
     * with the corners taken off.
     */
    float lightAt(int cellX, int cellY) {
        if (cellX < 0 || cellY < 0 || cellX >= width || cellY >= height
                || light.length != width * height) {
            return 0f;
        }
        return light[cellY * width + cellX];
    }

    State stateAt(int cellX, int cellY) {
        if (cellX < 0 || cellY < 0 || cellX >= width || cellY >= height) {
            return State.UNSEEN;
        }
        int index = cellY * width + cellX;
        if (visible.get(index)) {
            return State.VISIBLE;
        }
        return explored.get(index) ? State.REMEMBERED : State.UNSEEN;
    }

    /** How much of the map has been opened — the measure a test can hold on to. */
    int exploredCells() {
        return explored.cardinality();
    }

    int visibleCells() {
        return visible.cardinality();
    }
}
