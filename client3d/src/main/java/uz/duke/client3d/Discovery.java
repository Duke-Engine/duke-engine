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
