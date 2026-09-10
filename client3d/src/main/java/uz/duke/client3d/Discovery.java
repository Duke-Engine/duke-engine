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

    /**
     * Which storey each cell stands on.
     *
     * <p>Height hides things the way stone does, and more completely. A room a
     * storey above you is behind its own floor: standing in the corridor beneath
     * it there is nothing of it to see, however open the map looks from above.
     * Ground <em>below</em> is another matter — you are looking down on it from
     * the edge — so the rule is one-sided, and what is hidden is whatever is
     * higher than the eyes looking.
     *
     * <p>Zero everywhere on a flat map, and then every test below is {@code 0 > 0}
     * and the fog behaves exactly as it did before there was any height.
     */
    private int[] storey = new int[0];

    /** What the game asked the dark to be worth — see {@link Fog}. */
    private final Fog fog;

    Discovery(PathGrid grid) {
        this(grid, Fog.DEFAULT);
    }

    Discovery(PathGrid grid, Fog fog) {
        this.fog = fog == null ? Fog.DEFAULT : fog;
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
        storey = new int[width * height];
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                if (grid.isBlocked(cx, cy)) {
                    solid.set(cy * width + cx);
                }
                storey[cy * width + cx] = grid.level(cx, cy);
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
     * business -- this is the player's view of the world, not a shared one -- and
     * a monster that wandered somewhere must not light it up.
     *
     * <p>And only the ones named by {@code eyesOf}, because owning a thing is not
     * the same as seeing through it. An arrow is a unit like any other and it is
     * the player's, so a map opened around everything he owns is a map opened
     * along the flight of every shot he takes -- which turns a bow into a flare
     * gun and the dark into something you can simply shoot away. Null for a game
     * where anything of his that has a position also has eyes.
     */
    void reveal(List<UnitView> units, int localPlayer, float radius, String eyesOf) {
        visible.clear();
        if (radius <= 0f || width == 0) {
            return;
        }
        for (var unit : units) {
            if (unit.playerIndex() != localPlayer) {
                continue;
            }
            if (eyesOf != null && !eyesOf.equals(unit.templateName())) {
                continue; // his, but not his eyes
            }
            revealAround(unit.x(), unit.y(), radius);
        }
        explored.or(visible);
    }

    /**
     * Mark every cell whose centre lies within {@code radius} of a point — and,
     * if the game asked for it, only the ones he could actually see from there.
     */
    private void revealAround(float x, float y, float radius) {
        int minX = Math.max(0, (int) ((x - radius) / cellSize));
        int maxX = Math.min(width - 1, (int) ((x + radius) / cellSize));
        int minY = Math.max(0, (int) ((y - radius) / cellSize));
        int maxY = Math.min(height - 1, (int) ((y + radius) / cellSize));
        float radiusSquared = radius * radius;
        int fromX = Math.clamp((int) (x / cellSize), 0, Math.max(0, width - 1));
        int fromY = Math.clamp((int) (y / cellSize), 0, Math.max(0, height - 1));
        int eyes = storey[fromY * width + fromX];
        for (int cy = minY; cy <= maxY; cy++) {
            float dy = (cy + 0.5f) * cellSize - y;
            for (int cx = minX; cx <= maxX; cx++) {
                float dx = (cx + 0.5f) * cellSize - x;
                // A circle, not the bounding box: squared distance keeps the
                // corners out without a square root per cell.
                if (dx * dx + dy * dy > radiusSquared) {
                    continue;
                }
                // Anything standing higher than the eyes is behind its own floor.
                // Climb to it and it opens; until then a raised room is as good as
                // rock, which is the whole point of building the dungeon upward.
                if (storey[cy * width + cx] > eyes) {
                    continue;
                }
                if (fog.lineOfSight() && !inSight(fromX, fromY, cx, cy, eyes)) {
                    continue;
                }
                visible.set(cy * width + cx);
            }
        }
    }

    /**
     * Whether a straight line from one cell to another passes through no stone.
     *
     * <p>Without it the light is a circle that does not care what it is shining
     * through: standing in a corridor lit the rooms on both sides of it, walls and
     * all, and the whole point of a dungeon — not knowing what is round the corner
     * — went with it.
     *
     * <p>The wall itself is seen; what is behind it is not. So only the cells
     * <em>between</em> the two are asked, which is what makes a room's own walls
     * appear as the player steps into it rather than a frame later.
     *
     * <p>Bresenham, on integers, walking the cells the line actually crosses. No
     * trigonometry and no square roots — and no shader either: this is arithmetic
     * over a grid the pathfinder already keeps.
     */
    private boolean inSight(int fromX, int fromY, int toX, int toY, int eyes) {
        int dx = Math.abs(toX - fromX);
        int dy = -Math.abs(toY - fromY);
        int stepX = fromX < toX ? 1 : -1;
        int stepY = fromY < toY ? 1 : -1;
        int error = dx + dy;
        int x = fromX;
        int y = fromY;
        while (x != toX || y != toY) {
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x += stepX;
            }
            if (doubled <= dx) {
                error += dx;
                y += stepY;
            }
            if (x == toX && y == toY) {
                return true; // arrived; the far cell is allowed to be stone
            }
            // Stone stops the line, and so does a floor standing above the eyes:
            // a raised room between here and there is a wall with a room on top
            // of it, and what is behind it is behind it.
            if (solid.get(y * width + x) || storey[y * width + x] > eyes) {
                return false;
            }
        }
        return true;
    }

    // ---- softening ----

    /** Below this a cell is not drawn at all — it is the edge of the black. */
    static final float DARK = 0.02f;

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
     * its neighbours', so the boundary between lit and remembered is spread over
     * several cells instead of falling on one line. The kernel is a pyramid over
     * however many cells the game asked for: at one cell it is the familiar 4-2-1,
     * and a wider one simply takes longer to fall away.
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
        float step = Math.min(1f, fog.openPerSecond() * Math.max(0f, seconds));
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                int index = cy * width + cx;
                light[index] += (softenedAt(cx, cy) - light[index]) * step;
            }
        }
    }

    /** A cell's own brightness blurred together with its neighbours'. */
    private float softenedAt(int cellX, int cellY) {
        int reach = fog.softenCells();
        if (reach <= 0) {
            return rawLightAt(cellX, cellY);
        }
        float total = 0f;
        float weight = 0f;
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                int nx = cellX + dx;
                int ny = cellY + dy;
                if (!open(nx, ny)) {
                    continue; // stone, or off the map: neither of them is dark ground
                }
                // A pyramid: full weight at the centre, nothing past the reach.
                // Separable, so it stays the 4-2-1 kernel when the reach is one.
                float share = (reach + 1 - Math.abs(dx)) * (reach + 1f - Math.abs(dy));
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
            return fog.visibleLight();
        }
        return explored.get(index) ? fog.rememberedLight() : fog.unseenLight();
    }

    /**
     * How brightly to draw a cell, from 0 for black to 1 for full daylight.
     *
     * <p>The smooth counterpart of {@link #stateAt}, and what the fog layer is
     * drawn from. The three states are still the truth underneath; this is that
     * truth with the corners taken off.
     */
    float lightAt(int cellX, int cellY) {
        if (cellX < 0 || cellY < 0 || cellX >= width || cellY >= height
                || light.length != width * height) {
            return 0f;
        }
        return light[cellY * width + cellX];
    }

    /**
     * The brightness at a <em>point</em> rather than at a cell, taken smoothly
     * between the cell centres around it.
     *
     * <p>What this is for is that a cell is ten units of ground and the eye can
     * see every one of them. Reading one value per cell and painting it over the
     * whole cell is what drew the fog as a field of squares, however carefully the
     * cells themselves had been blurred beforehand — the softening was real, it
     * was simply happening at the wrong size.
     *
     * <p>The weights are eased rather than straight, so the slope is flat as it
     * passes through each cell centre. Straight weights are continuous but their
     * slope is not, and a change of slope on every cell boundary is a crease the
     * eye picks out as readily as the squares did.
     */
    float lightAtPoint(float worldX, float worldY) {
        if (width == 0 || height == 0 || cellSize <= 0f || light.length != width * height) {
            return 0f;
        }
        float atX = worldX / cellSize - 0.5f;
        float atY = worldY / cellSize - 0.5f;
        int leftX = (int) Math.floor(atX);
        int topY = (int) Math.floor(atY);
        float alongX = ease(atX - leftX);
        float alongY = ease(atY - topY);
        float top = between(clampedLight(leftX, topY), clampedLight(leftX + 1, topY), alongX);
        float bottom = between(clampedLight(leftX, topY + 1),
                clampedLight(leftX + 1, topY + 1), alongX);
        return between(top, bottom, alongY);
    }

    /** Smoothstep: 0 and 1 where it started, and flat at both ends. */
    private static float ease(float along) {
        return along * along * (3f - 2f * along);
    }

    private static float between(float from, float to, float along) {
        return from + (to - from) * along;
    }

    /** The map's edge is a wall, not a cliff: past it, the outermost cell repeats. */
    private float clampedLight(int cellX, int cellY) {
        return light[Math.clamp(cellY, 0, height - 1) * width + Math.clamp(cellX, 0, width - 1)];
    }

    /**
     * How far around a cell the dark has to reach before the cell may be dropped.
     *
     * <p>One cell, because the fog is drawn between cell centres: a black cell
     * beside a lit one is only black at its own centre, and half way to its
     * neighbour the dark has already begun to clear. Cull on the cell alone and
     * that half is a hole with the void showing through it, which is the one way a
     * softer fog can look worse than a hard one. Culling too little only draws
     * something nobody can see.
     */
    private static final int CULL_MARGIN = 1;

    /**
     * Whether anything standing in this cell would be entirely behind the fog.
     *
     * <p>The cull the renderer wants: not "is this cell dark" but "is the dark
     * thick enough, right across and a little way around, that drawing here would
     * change no pixel".
     */
    boolean hidden(int cellX, int cellY) {
        for (int dy = -CULL_MARGIN; dy <= CULL_MARGIN; dy++) {
            for (int dx = -CULL_MARGIN; dx <= CULL_MARGIN; dx++) {
                if (lightAt(cellX + dx, cellY + dy) > DARK) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The same question asked about a <em>place</em> rather than a cell.
     *
     * <p>Which is what the renderer actually has. A wall stands on the line
     * between two cells and a roof lies over a piece of rock that may be diagonal
     * to the room it was built with — so asking about the cell a piece was
     * <em>filed under</em> can drop a wall that stands beside a lit room, and did.
     */
    boolean hiddenAt(float worldX, float worldY) {
        if (cellSize <= 0f) {
            return false;
        }
        return hidden((int) Math.floor(worldX / cellSize), (int) Math.floor(worldY / cellSize));
    }

    /**
     * Whether a point in the world is in sight this instant.
     *
     * <p>The crisp truth rather than the eased brightness: what is drawn fades,
     * but whether a monster is visible must not depend on how long the light has
     * had to arrive.
     */
    boolean canSee(float worldX, float worldY) {
        if (cellSize <= 0f) {
            return true;
        }
        return stateAt((int) (worldX / cellSize), (int) (worldY / cellSize)) == State.VISIBLE;
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
