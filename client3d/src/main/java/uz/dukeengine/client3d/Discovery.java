package uz.dukeengine.client3d;

import java.util.BitSet;
import java.util.List;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.UnitView;

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
 *
 * <p>The two facts are kept for every cell of the world, two bits each. How brightly each cell is drawn is kept for the
 * whole map, or, on a world too wide to draw whole, for a {@link #window} of cells round the point the camera looks at
 * ({@link #follow}): the dark is drawn nowhere else, so it is worked out nowhere else, and what changed is counted
 * where it changed rather than found by comparing the whole world with itself every frame.
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
     *
     * <p>Two bytes a cell, as the grid keeps them.
     */
    private short[] storey = new short[0];

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
        storey = new short[width * height];
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                if (grid.isTerrainBlocked(cx, cy)) {
                    solid.set(cy * width + cx);
                }
                storey[cy * width + cx] = (short) grid.level(cx, cy);
            }
        }
        lastVisible.clear();
        lastExplored.clear();
        moved.clear();
        changed.clear();
        touched.clear();
        shown.clear();
        shownBefore.clear();
        everythingChanged = false;
        everythingTouched = false;
        visibleElsewhere = false;
        allOpen = false;
        lastSight = null;
        layWindow();
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    // ---- the window of soft light ----

    /** How many cells a side the soft light is kept for round the point followed; 0 for the whole map. */
    private int windowCells;
    /** Whether the soft light is kept for a window that moves, not for the whole map. */
    private boolean windowed;
    /** The window's first cell, and how many cells across and deep it is: the map's own where it is kept whole. */
    private int windowX;
    private int windowY;
    private int wide;
    private int deep;

    /**
     * Keep how brightly each cell is drawn for {@code cells} a side round the point {@link #follow followed} — a world
     * whose ground is built only round the camera, darkened only where it is built — or, at 0 or at least the map's own
     * size, for the whole map, as it always was. What the player has seen and sees is kept for every cell either way.
     */
    void window(int cells) {
        windowCells = Math.max(0, cells);
        layWindow();
    }

    private void layWindow() {
        windowed = windowCells > 0 && (windowCells < width || windowCells < height);
        wide = windowed ? windowCells : width;
        deep = windowed ? windowCells : height;
        windowX = 0;
        windowY = 0;
        // Made at the first soften, the size of the window then: a world laid and given a window at once is never
        // given the whole map's first. Until then every cell is black, as a light never softened is.
        light = new float[0];
        target = new float[0];
        easing.clear();
        dirty.clear();
        everyTarget = true;
    }

    /**
     * Keep the window's middle on the point ({@code worldX}, {@code worldY}) of the ground: each cell that comes into it
     * is drawn at once as bright as it has settled — it was out of sight of the camera while it eased — and is among
     * the {@link #movedCells}. Nothing where the whole map is kept.
     */
    void follow(float worldX, float worldY) {
        if (!windowed || cellSize <= 0f) {
            return;
        }
        int toX = (int) Math.floor(worldX / cellSize) - wide / 2;
        int toY = (int) Math.floor(worldY / cellSize) - deep / 2;
        if (toX == windowX && toY == windowY) {
            return;
        }
        int fromX = windowX;
        int fromY = windowY;
        windowX = toX;
        windowY = toY;
        if (everyTarget) {
            return; // the next soften lays every cell of the window where it now stands
        }
        for (int cy = toY; cy < toY + deep; cy++) {
            if (cy < fromY || cy >= fromY + deep) {
                for (int cx = toX; cx < toX + wide; cx++) {
                    settle(cx, cy);
                }
                continue;
            }
            for (int cx = toX; cx < Math.min(toX + wide, fromX); cx++) {
                settle(cx, cy);
            }
            for (int cx = Math.max(toX, fromX + wide); cx < toX + wide; cx++) {
                settle(cx, cy);
            }
        }
    }

    /** A cell come into the window, drawn as bright as it has settled. */
    private void settle(int cx, int cy) {
        int slot = slotOf(cx, cy);
        easing.clear(slot);
        if (cx < 0 || cy < 0 || cx >= width || cy >= height) {
            light[slot] = 0f;
            target[slot] = 0f;
            return;
        }
        float settled = softenedAt(cx, cy);
        target[slot] = settled;
        light[slot] = settled;
        moved.add(cy * width + cx);
    }

    /** Whether the soft light is kept for a window that moves. */
    boolean isWindowed() {
        return windowed;
    }

    /** The window's first cell across. */
    int windowX() {
        return windowX;
    }

    /** The window's first cell down. */
    int windowY() {
        return windowY;
    }

    /** How many cells across the window is — the map's width where it is kept whole. */
    int windowWide() {
        return wide;
    }

    /** How many cells deep the window is. */
    int windowDeep() {
        return deep;
    }

    private boolean inWindow(int cx, int cy) {
        return cx >= windowX && cy >= windowY && cx < windowX + wide && cy < windowY + deep;
    }

    /** Where a cell of the window keeps its light: each cell of the window a slot, a cell leaving giving its to one coming in. */
    private int slotOf(int cx, int cy) {
        return Math.floorMod(cy, deep) * wide + Math.floorMod(cx, wide);
    }

    /** The cell of the window at {@code slot}, across. */
    private int cellXOf(int slot) {
        return windowX + Math.floorMod(slot % wide - windowX, wide);
    }

    /** The cell of the window at {@code slot}, down. */
    private int cellYOf(int slot) {
        return windowY + Math.floorMod(slot / wide - windowY, deep);
    }

    // ---- what is open ----

    /** The cells whose state may have changed since the last soften, counted where each was written. */
    private final CellSet touched = new CellSet();
    /** The cells the last {@link #reveal} put in sight, and so the ones the next takes out of it. */
    private CellSet shown = new CellSet();
    /** The cells a reveal found in sight before it, while it puts the ones in sight now. */
    private CellSet shownBefore = new CellSet();
    /** A change too wide to count — the map opened, the simulation's cells gone — every cell asked at the next soften. */
    private boolean everythingTouched;
    /** Whether cells are in sight that no reveal put there: a reveal after them takes the whole map out of sight. */
    private boolean visibleElsewhere;
    /** Whether the whole map is open and in sight, so opening it again changes nothing. */
    private boolean allOpen;

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
        lastSight = null;
        allOpen = false;
        if (visibleElsewhere) {
            // In sight by the simulation's cells, or the map opened: none of it a reveal's to take back cell by cell.
            visible.clear();
            everythingTouched = true;
            visibleElsewhere = false;
            shown.clear();
        }
        var before = shown;
        shown = shownBefore;
        shownBefore = before;
        if (radius > 0f && width > 0) {
            for (var unit : units) {
                if (unit.playerIndex() != localPlayer) {
                    continue;
                }
                if (eyesOf != null && !eyesOf.equals(unit.templateName())) {
                    continue; // his, but not his eyes
                }
                revealAround(unit.x(), unit.y(), radius);
            }
        }
        // Out of sight, what was in it and is no longer: put after what is in sight now, so the cells in sight are
        // never all taken out at once, which would have the set of them look through the whole map for its last one.
        for (int i = 0; i < before.size(); i++) {
            int cell = before.get(i);
            if (!shown.contains(cell)) {
                visible.clear(cell);
                touched.add(cell);
            }
        }
        before.clear();
    }

    /** Whether past the map's edges is never seen, not the outermost cell repeated — see {@link #fromSight}. */
    private boolean darkPastTheEdge;

    /**
     * Take what is open from the simulation's own cells of what the local player has seen, in place of any looking of
     * its own: each of its cells as the sight cell its middle stands in says — in sight, seen, or never seen — and past
     * the map's edges never seen.
     */
    void fromSight(uz.dukeengine.core.SightCells.View sight) {
        darkPastTheEdge = true;
        allOpen = false;
        var before = lastSight;
        lastSight = sight;
        if (sight == null) {
            visible.clear();
            explored.clear();
            shown.clear();
            shownBefore.clear();
            everythingTouched = true;
            visibleElsewhere = false;
            return;
        }
        if (before == null || before.width() != sight.width() || before.height() != sight.height()
                || before.cellSize() != sight.cellSize() || before.everywhere() != sight.everywhere()) {
            takeCells(sight, 0, 0, width - 1, height - 1);
            return;
        }
        // Only the chunks written since: a chunk the simulation has not written since is the same array.
        var chunks = sight.chunks();
        var was = before.chunks();
        float span = uz.dukeengine.core.SightCells.CHUNK * sight.cellSize();
        for (int chunk = 0; chunk < chunks.length; chunk++) {
            if (chunks[chunk] == was[chunk]) {
                continue;
            }
            float x0 = (chunk % sight.chunksAcross()) * span;
            float y0 = (chunk / sight.chunksAcross()) * span;
            takeCells(sight, (int) Math.floor(x0 / cellSize) - 1, (int) Math.floor(y0 / cellSize) - 1,
                    (int) Math.ceil((x0 + span) / cellSize) + 1, (int) Math.ceil((y0 + span) / cellSize) + 1);
        }
    }

    /** The simulation's cells as last taken, whose chunks the next are told apart from; null for none. */
    private uz.dukeengine.core.SightCells.View lastSight;

    /** The cells {@code fromX..toX} by {@code fromY..toY} as the sight cell under each one's middle has it. */
    private void takeCells(uz.dukeengine.core.SightCells.View sight, int fromX, int fromY, int toX, int toY) {
        for (int cy = Math.max(0, fromY); cy <= Math.min(height - 1, toY); cy++) {
            float y = (cy + 0.5f) * cellSize;
            for (int cx = Math.max(0, fromX); cx <= Math.min(width - 1, toX); cx++) {
                int cell = cy * width + cx;
                boolean wasInSight = visible.get(cell);
                boolean wasSeen = explored.get(cell);
                switch (sight.at((cx + 0.5f) * cellSize, y)) {
                    case IN_SIGHT -> {
                        visible.set(cell);
                        explored.set(cell);
                        visibleElsewhere = true;
                    }
                    case SEEN -> {
                        visible.clear(cell);
                        explored.set(cell);
                    }
                    case NEVER_SEEN -> {
                        visible.clear(cell);
                        explored.clear(cell);
                    }
                }
                if (visible.get(cell) != wasInSight || explored.get(cell) != wasSeen) {
                    touched.add(cell);
                }
            }
        }
    }

    /** The whole map open and in sight: for a player it was revealed to, and for a watcher. */
    void openEverything() {
        lastSight = null;
        if (allOpen) {
            return; // open already: asked every frame, it changes nothing after the first
        }
        visible.set(0, width * height);
        explored.set(0, width * height);
        shown.clear();
        shownBefore.clear();
        visibleElsewhere = true;
        everythingTouched = true;
        allOpen = true;
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
                int cell = cy * width + cx;
                // Anything standing higher than the eyes is behind its own floor:
                // not in sight, so nothing on it is drawn and the room keeps its
                // secret until it is climbed to.
                //
                // But it is remembered rather than unseen. A raised floor is a
                // thing in plain view — you are looking at the side of it — and
                // leaving it out of the picture altogether cuts a black rectangle
                // out of the middle of a lit room, which reads as a hole rather
                // than as a storey. Remembered draws the stone and hides whoever
                // is standing on it, which is what "you cannot see up there"
                // actually looks like.
                if (storey[cell] > eyes) {
                    explored.set(cell);
                    touched.add(cell);
                    continue;
                }
                if (fog.lineOfSight() && !inSight(fromX, fromY, cx, cy, eyes)) {
                    continue;
                }
                // In sight, and so seen: what the eyes open, the memory keeps.
                visible.set(cell);
                explored.set(cell);
                shown.add(cell);
                touched.add(cell);
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
     * The eased, softened brightness of each cell of the window. Presentation only, like the
     * rest of this class — it is a number about drawing, and no two players
     * watching the same replay need agree on it.
     */
    private float[] light = new float[0];

    /**
     * How bright each cell ought to be — its own state blurred with its neighbours' — worked out again only where
     * what is open around it changed, rather than for the whole map every frame: a floor of twenty thousand cells
     * and a kernel of twenty-five is half a million samples a frame spent mostly on ground nobody's sight touched.
     */
    private float[] target = new float[0];
    /** What was in sight, and seen, the last time the light moved: what changed since is where targets move. */
    private final BitSet lastVisible = new BitSet();
    private final BitSet lastExplored = new BitSet();
    /** The cells of the window still easing toward their target; a cell that has arrived costs nothing until it is dirtied. */
    private final BitSet easing = new BitSet();
    /** The cells whose light moved in the last soften, for whatever redraws only what moved. */
    private final CellSet moved = new CellSet();
    /** Every target to be worked out at the next soften, as for a new map. */
    private boolean everyTarget = true;
    /** The cells whose state the last soften found changed. */
    private final CellSet changed = new CellSet();
    /** Whether the last soften took every cell's state as changed: a new map, or the whole of it opened. */
    private boolean everythingChanged;
    /** The cells of the window whose target is to be worked out again. */
    private final BitSet dirty = new BitSet();

    /**
     * Closer than this to its target, a cell is at it: well under the 256th of the light a texel of the fog holds, so
     * the step to it is never seen, and a cell that has arrived is left alone.
     */
    private static final float SETTLED = 1f / 1024f;

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
     *
     * <p>Only where something moved: a cell's target is worked out again where what is open within the kernel's reach
     * of it changed since the last time, and a cell eases only until it has arrived. A hero standing still costs
     * nothing once the light has settled, and one walking costs the ground his sight crosses. What changed is counted
     * where it was written — the cells a reveal opened and closed, the sight cells taken — so a frame never compares
     * the whole world with itself.
     */
    void soften(float seconds) {
        int slots = wide * deep;
        if (light.length != slots) {
            light = new float[slots];
            target = new float[slots];
            everyTarget = true;
        }
        moved.clear();
        changed.clear();
        everythingChanged = false;
        if (slots == 0) {
            return;
        }
        dirty.clear();
        if (everyTarget || everythingTouched) {
            // Every cell of the window worked out again, and every cell's state taken as changed: a target worked out
            // again where nothing changed is the target it was, so this is what the cells that changed would give.
            dirtyTheWindow();
            everythingChanged = true;
            everyTarget = false;
            everythingTouched = false;
            touched.clear();
            lastVisible.clear();
            lastVisible.or(visible);
            lastExplored.clear();
            lastExplored.or(explored);
        } else {
            for (int i = 0; i < touched.size(); i++) {
                int cell = touched.get(i);
                boolean inSight = visible.get(cell);
                boolean seen = explored.get(cell);
                if (inSight != lastVisible.get(cell) || seen != lastExplored.get(cell)) {
                    changed.add(cell);
                    lastVisible.set(cell, inSight);
                    lastExplored.set(cell, seen);
                }
            }
            touched.clear();
            int reach = fog.softenCells();
            for (int i = 0; i < changed.size(); i++) {
                int at = changed.get(i);
                dirtyAround(at % width, at / width, reach);
            }
        }
        for (int slot = dirty.nextSetBit(0); slot >= 0; slot = dirty.nextSetBit(slot + 1)) {
            target[slot] = softenedAt(cellXOf(slot), cellYOf(slot));
        }
        easing.or(dirty);
        float step = Math.min(1f, fog.openPerSecond() * Math.max(0f, seconds));
        for (int slot = easing.nextSetBit(0); slot >= 0; slot = easing.nextSetBit(slot + 1)) {
            float before = light[slot];
            float after = before + (target[slot] - before) * step;
            if (Math.abs(target[slot] - after) < SETTLED) {
                after = target[slot];
            }
            if (after != before) {
                light[slot] = after;
                moved.add(cellYOf(slot) * width + cellXOf(slot));
            }
            if (after == target[slot]) {
                easing.clear(slot);
            }
        }
    }

    /** Every cell of the window that stands on the map, to be worked out again. */
    private void dirtyTheWindow() {
        int fromX = Math.max(0, windowX);
        int toX = Math.min(width, windowX + wide) - 1;
        for (int cy = Math.max(0, windowY); cy < Math.min(height, windowY + deep); cy++) {
            dirtyRow(cy, fromX, toX);
        }
    }

    /** The cells of the window within {@code reach} of cell ({@code cx}, {@code cy}), to be worked out again. */
    private void dirtyAround(int cx, int cy, int reach) {
        int fromX = Math.max(Math.max(0, cx - reach), windowX);
        int toX = Math.min(Math.min(width - 1, cx + reach), windowX + wide - 1);
        for (int y = Math.max(Math.max(0, cy - reach), windowY);
                y <= Math.min(Math.min(height - 1, cy + reach), windowY + deep - 1); y++) {
            dirtyRow(y, fromX, toX);
        }
    }

    /** Cells {@code fromX..toX} of row {@code cy}, all of them in the window: one run of slots, or two where it wraps. */
    private void dirtyRow(int cy, int fromX, int toX) {
        if (fromX > toX) {
            return;
        }
        int row = Math.floorMod(cy, deep) * wide;
        int first = Math.floorMod(fromX, wide);
        int last = Math.floorMod(toX, wide);
        if (first <= last) {
            dirty.set(row + first, row + last + 1);
        } else {
            dirty.set(row + first, row + wide);
            dirty.set(row, row + last + 1);
        }
    }

    /** The cells whose light moved in the last {@link #soften}, indexed {@code cy * width + cx}. Read, not kept. */
    BitSet movedCells() {
        return moved.bits();
    }

    /**
     * The same cells, in the order they moved, to be walked at the cost of how many moved: the ones that came into the
     * window as it {@link #follow followed} among them.
     */
    CellSet moved() {
        return moved;
    }

    /**
     * The cells whose state — unseen, remembered, in sight — the last {@link #soften} found changed, indexed {@code cy *
     * width + cx}; every cell's is to be taken as changed where {@link #everythingChanged}. Read, not kept.
     */
    BitSet changedCells() {
        return changed.bits();
    }

    /** The same cells, in the order they changed. */
    CellSet changed() {
        return changed;
    }

    /**
     * Whether the last {@link #soften} took every cell's state as changed — the first after a new map, and after a
     * change too wide to count, the map opened whole — so whatever draws the states draws them all again.
     */
    boolean everythingChanged() {
        return everythingChanged;
    }

    /** How wide a cell of the map this was laid out for is, in world units. */
    float getCellSize() {
        return cellSize;
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
     * truth with the corners taken off. Outside the window it is kept for, as bright
     * as the cell has settled.
     */
    float lightAt(int cellX, int cellY) {
        if (cellX < 0 || cellY < 0 || cellX >= width || cellY >= height
                || light.length != wide * deep) {
            return 0f;
        }
        return keptLight(cellX, cellY);
    }

    /** A cell of the map's light: as eased where the window keeps it, as settled where it does not. */
    private float keptLight(int cellX, int cellY) {
        return inWindow(cellX, cellY) ? light[slotOf(cellX, cellY)] : softenedAt(cellX, cellY);
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
        if (width == 0 || height == 0 || cellSize <= 0f || light.length != wide * deep) {
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

    /**
     * The map's edge is a wall, not a cliff: past it, the outermost cell repeats — or, drawn by the simulation's cells,
     * it is never seen.
     */
    private float clampedLight(int cellX, int cellY) {
        if (darkPastTheEdge && (cellX < 0 || cellY < 0 || cellX >= width || cellY >= height)) {
            return fog.unseenLight();
        }
        return keptLight(Math.clamp(cellX, 0, width - 1), Math.clamp(cellY, 0, height - 1));
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

    /** Whether a point in the world has ever been seen: in sight now, or remembered. */
    boolean everSeen(float worldX, float worldY) {
        if (cellSize <= 0f) {
            return true;
        }
        return stateAt((int) (worldX / cellSize), (int) (worldY / cellSize)) != State.UNSEEN;
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
