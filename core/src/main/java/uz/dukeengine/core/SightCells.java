package uz.dukeengine.core;

import java.util.Arrays;
import uz.dukeengine.core.math.Coord3D;

/**
 * What each player has seen of the map, kept by cells — the reference's partition cells' shroud
 * ({@code PartitionCell::getShroudStatusForPlayer}): never seen until a looker first covers a cell; in sight while one
 * covers it and for a while after — {@code UnlookPersistDuration}, a scout's trail staying open behind him — and seen,
 * not in sight, once that while is out.
 *
 * <p>A looker covers the disc of cells round its own cell as far as its reach in cells, rounded up ({@code
 * doShroudReveal}, {@code DiscreteCircle}): cells whose middles lie within that many cells of its own cell's middle.
 * Who looks for whom is the world's ({@code GameLogic.canSee}'s lookers). Looked at once a frame, as the frame ends, in
 * the order the world keeps its things: the same on every machine. Where the game says what hides the ground ({@link
 * Ground}), a line from the looker's cell passes no stone, and a floor above the looker's is seen and not in sight.
 *
 * <p>Two things besides the lookers, each the reference's: a player's whole map marked seen once ({@link #markSeen}),
 * and the whole map in sight to a player for good ({@link #reveal}).
 *
 * <p>Kept as small as what was seen: a player's cells in square chunks of {@link #CHUNK} a side, each made the first
 * time a looker of his covers one of its cells, and the cells in sight now with the frame each was last covered — a
 * world a thousand cells a side costs what its players have walked, and a frame what their lookers cover. What a client
 * is shown ({@link #view}) shares the chunks, and a chunk is copied before it is written again, so a view is kept as it
 * was, and a chunk the client already has is the one it had.
 */
public final class SightCells {

    /** What a player has of a cell. */
    public enum Sight {
        /** No looker of his has ever covered it. */
        NEVER_SEEN,
        /** Covered once, and by nothing now nor for the while after. */
        SEEN,
        /** Covered now, or within the while after. */
        IN_SIGHT
    }

    /** How many cells a side a chunk of a player's cells is. */
    public static final int CHUNK = 64;

    private static final byte NEVER = (byte) Sight.NEVER_SEEN.ordinal();
    private static final byte SEEN = (byte) Sight.SEEN.ordinal();
    private static final byte IN_SIGHT = (byte) Sight.IN_SIGHT.ordinal();
    private static final Sight[] SIGHTS = Sight.values();

    /**
     * What hides the ground from a looker, where the game says anything does: a line of sight passes no stone, and a
     * floor standing above the looker's is seen — its side is in plain view — but nothing on it is in sight. Asked by
     * cell, on the simulation thread.
     */
    public interface Ground {
        /** Whether the cell is stone, which a line of sight stops at: the stone itself is seen, what is behind it not. */
        boolean stone(int cx, int cy);

        /** Which storey the cell stands on. */
        int storey(int cx, int cy);
    }

    private final float cellSize;
    private final int linger;
    private final int width;
    private final int height;
    private final int chunksAcross;
    private final int chunksDown;
    /** Each player's cells. */
    private final java.util.Map<Integer, Eyes> players = new java.util.TreeMap<>();
    /** The players every cell is in sight to for good. */
    private final java.util.Set<Integer> revealed = new java.util.TreeSet<>();
    /** The last frame looked at. */
    private int lookedAt = Integer.MIN_VALUE;
    /** What hides the ground, or null for none: a looker covers its disc. */
    private Ground ground;

    /**
     * @param cellSize  how wide a cell is, world units — the reference's {@code PartitionCellSize}, 40
     * @param linger    how many frames a cell stays in sight once nothing covers it — its 150
     * @param mapWidth  how wide the map is, world units
     * @param mapHeight how deep
     */
    public SightCells(float cellSize, int linger, float mapWidth, float mapHeight) {
        this.cellSize = Math.max(1f, cellSize);
        this.linger = Math.max(1, linger);
        this.width = Math.max(1, (int) Math.ceil(mapWidth / this.cellSize));
        this.height = Math.max(1, (int) Math.ceil(mapHeight / this.cellSize));
        this.chunksAcross = (width + CHUNK - 1) / CHUNK;
        this.chunksDown = (height + CHUNK - 1) / CHUNK;
    }

    public float cellSize() {
        return cellSize;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Let {@code ground} hide what it hides from every looker from now on; null for none, a disc. */
    void setGround(Ground ground) {
        this.ground = ground;
    }

    /** One player's cells: the chunks of what he has of each, and the cells in sight now. */
    private final class Eyes {
        /** Each chunk's cells, a {@link Sight} ordinal each; null for a chunk no looker of his has covered. */
        final byte[][] chunks = new byte[chunksAcross * chunksDown][];
        /** Each chunk handed out in a view since it was last written: copied before it is written again. */
        final boolean[] shared = new boolean[chunksAcross * chunksDown];
        /** The cells in sight now, and the frame each was last covered, side by side. */
        int[] inSight = new int[64];
        int[] coveredAt = new int[64];
        int inSightCount;
        /** Where each cell in sight stands in {@link #inSight}. */
        final java.util.HashMap<Integer, Integer> slotOf = new java.util.HashMap<>();

        byte state(int cell) {
            var chunk = chunks[chunkOf(cell)];
            return chunk == null ? NEVER : chunk[inChunk(cell)];
        }

        void setState(int cell, byte state) {
            int index = chunkOf(cell);
            var chunk = chunks[index];
            if (chunk == null) {
                if (state == NEVER) {
                    return;
                }
                chunk = new byte[CHUNK * CHUNK];
                chunks[index] = chunk;
            } else if (shared[index]) {
                chunk = chunk.clone(); // what a view holds stays as it was
                chunks[index] = chunk;
                shared[index] = false;
            }
            chunk[inChunk(cell)] = state;
        }

        /** The cell covered this frame: in sight, and its while after starting again. */
        void cover(int cell) {
            var slot = slotOf.get(cell);
            if (slot != null) {
                coveredAt[slot] = lookedAt;
                return;
            }
            if (inSightCount == inSight.length) {
                inSight = Arrays.copyOf(inSight, inSightCount * 2);
                coveredAt = Arrays.copyOf(coveredAt, inSightCount * 2);
            }
            inSight[inSightCount] = cell;
            coveredAt[inSightCount] = lookedAt;
            slotOf.put(cell, inSightCount++);
            setState(cell, IN_SIGHT);
        }

        /** The cell seen, where it was never seen: a floor above a looker's, in plain view. */
        void glimpse(int cell) {
            if (state(cell) == NEVER) {
                setState(cell, SEEN);
            }
        }

        /** The cells whose while after is out by frame {@code frame}: seen, and no longer in sight. */
        void expire(int frame) {
            for (int i = 0; i < inSightCount; i++) {
                if (frame - coveredAt[i] < linger) {
                    continue;
                }
                int cell = inSight[i];
                setState(cell, SEEN);
                slotOf.remove(cell);
                int last = --inSightCount;
                if (i != last) {
                    inSight[i] = inSight[last];
                    coveredAt[i] = coveredAt[last];
                    slotOf.put(inSight[i], i);
                    i--; // the one moved into this place is looked at too
                }
            }
        }
    }

    private int chunkOf(int cell) {
        return ((cell / width) / CHUNK) * chunksAcross + (cell % width) / CHUNK;
    }

    private int inChunk(int cell) {
        return ((cell / width) % CHUNK) * CHUNK + (cell % width) % CHUNK;
    }

    private Eyes eyes(int player) {
        return players.computeIfAbsent(player, p -> new Eyes());
    }

    /** A new frame's look begins: {@code frame} is the frame just run. */
    void begin(int frame) {
        lookedAt = frame;
        for (var eyes : players.values()) {
            eyes.expire(frame);
        }
    }

    /** {@code player}'s looker at {@code at} covers the disc of its reach, this frame. */
    void look(int player, Coord3D at, float reach) {
        var eyes = eyes(player);
        int cx = (int) Math.floor(at.x() / cellSize);
        int cy = (int) Math.floor(at.y() / cellSize);
        int round = (int) Math.ceil(reach / cellSize);
        long most = (long) round * round;
        var hiding = ground;
        int fromX = Math.clamp(cx, 0, width - 1);
        int fromY = Math.clamp(cy, 0, height - 1);
        int eyesAt = hiding == null ? 0 : hiding.storey(fromX, fromY);
        for (int dy = -round; dy <= round; dy++) {
            int y = cy + dy;
            if (y < 0 || y >= height) {
                continue;
            }
            for (int dx = -round; dx <= round; dx++) {
                int x = cx + dx;
                if (x < 0 || x >= width || (long) dx * dx + (long) dy * dy > most) {
                    continue;
                }
                int cell = y * width + x;
                if (hiding != null) {
                    if (hiding.storey(x, y) > eyesAt) {
                        eyes.glimpse(cell);
                        continue;
                    }
                    if (!inLine(hiding, fromX, fromY, x, y, eyesAt)) {
                        continue;
                    }
                }
                eyes.cover(cell);
            }
        }
    }

    /**
     * Whether a straight line from one cell to another passes no stone and no floor above the eyes: Bresenham, on whole
     * numbers, over the cells between — the far cell may be stone, and is seen.
     */
    private static boolean inLine(Ground ground, int fromX, int fromY, int toX, int toY, int eyes) {
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
                return true;
            }
            if (ground.stone(x, y) || ground.storey(x, y) > eyes) {
                return false;
            }
        }
        return true;
    }

    /** What {@code player} has of the cell under {@code at}; past the map's edges, never seen. */
    public Sight sight(int player, Coord3D at) {
        return sight(player, (int) Math.floor(at.x() / cellSize), (int) Math.floor(at.y() / cellSize));
    }

    /**
     * Every cell {@code player} has never seen marked seen, as though a looker had covered the whole map and left long
     * enough ago for its while after to have run out — the reference's {@code PartitionManager::revealMapForPlayer},
     * an {@code addLooker} and at once its {@code removeLooker} over every cell, which leaves each one fogged: nothing
     * put in sight by it, a cell in sight now left as it is, and his lookers opening cells to in sight as ever.
     */
    void markSeen(int player) {
        var eyes = eyes(player);
        for (int cell = 0; cell < width * height; cell++) {
            eyes.glimpse(cell);
        }
    }

    /**
     * Every cell in sight to {@code player} for good — the reference's {@code revealMapForPlayerPermanently}, a looker
     * over every cell that is never removed. Whatever else was done to his cells, this is more.
     */
    void reveal(int player) {
        revealed.add(player);
    }

    /** What {@code player} has of cell ({@code cx}, {@code cy}); past the map's edges, never seen. */
    public Sight sight(int player, int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= width || cy >= height) {
            return Sight.NEVER_SEEN;
        }
        if (revealed.contains(player)) {
            return Sight.IN_SIGHT;
        }
        var eyes = players.get(player);
        return eyes == null ? Sight.NEVER_SEEN : SIGHTS[eyes.state(cy * width + cx)];
    }

    /** Whether {@code player} has the cell under {@code at} in sight. */
    public boolean inSight(int player, Coord3D at) {
        return sight(player, at) == Sight.IN_SIGHT;
    }

    /** Whether {@code player} has ever seen the cell under {@code at}. */
    public boolean everSeen(int player, Coord3D at) {
        return sight(player, at) != Sight.NEVER_SEEN;
    }

    /**
     * {@code player}'s cells as they stand, for a client to keep and draw from any thread: the chunks themselves, each
     * copied before it is written again — so a chunk that is the same array as in an earlier view has not changed since.
     */
    public View view(int player) {
        if (revealed.contains(player)) {
            return new View(cellSize, width, height, new byte[chunksAcross * chunksDown][], Sight.IN_SIGHT);
        }
        var eyes = players.get(player);
        if (eyes == null) {
            return new View(cellSize, width, height, new byte[chunksAcross * chunksDown][], null);
        }
        Arrays.fill(eyes.shared, true);
        return new View(cellSize, width, height, eyes.chunks.clone(), null);
    }

    /**
     * What {@code player} has seen, to be kept with a saved game: every cell he has seen, a bit each, and each cell in
     * sight with the frame it was last covered — enough that {@link #recall} gives back every answer as it stands.
     */
    public Memory remember(int player) {
        var seen = new java.util.BitSet(width * height);
        var eyes = players.get(player);
        if (eyes == null) {
            return new Memory(width, height, seen.toLongArray(), new int[0]);
        }
        for (int chunk = 0; chunk < eyes.chunks.length; chunk++) {
            var cells = eyes.chunks[chunk];
            if (cells == null) {
                continue;
            }
            int x0 = (chunk % chunksAcross) * CHUNK;
            int y0 = (chunk / chunksAcross) * CHUNK;
            for (int y = y0; y < Math.min(y0 + CHUNK, height); y++) {
                for (int x = x0; x < Math.min(x0 + CHUNK, width); x++) {
                    if (cells[(y - y0) * CHUNK + x - x0] != NEVER) {
                        seen.set(y * width + x);
                    }
                }
            }
        }
        var inSight = new int[eyes.inSightCount * 2];
        var order = new Integer[eyes.inSightCount];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, java.util.Comparator.comparingInt(i -> eyes.inSight[i]));
        for (int i = 0; i < order.length; i++) {
            inSight[2 * i] = eyes.inSight[order[i]];
            inSight[2 * i + 1] = eyes.coveredAt[order[i]];
        }
        return new Memory(width, height, seen.toLongArray(), inSight);
    }

    /** {@code player}'s cells as {@code memory} kept them — see {@link #remember}; a memory of other cells is refused. */
    void recall(int player, Memory memory) {
        if (memory.width() != width || memory.height() != height) {
            throw new IllegalArgumentException("a memory of " + memory.width() + "x" + memory.height()
                    + " cells is not of these " + width + "x" + height);
        }
        var eyes = new Eyes();
        players.put(player, eyes);
        var seen = java.util.BitSet.valueOf(memory.seen());
        for (int cell = seen.nextSetBit(0); cell >= 0 && cell < width * height; cell = seen.nextSetBit(cell + 1)) {
            eyes.setState(cell, SEEN);
        }
        int was = lookedAt;
        for (int i = 0; i + 1 < memory.inSight().length; i += 2) {
            lookedAt = memory.inSight()[i + 1];
            eyes.cover(memory.inSight()[i]);
        }
        lookedAt = was;
    }

    /**
     * What a player has seen, as {@link #remember} keeps it: his map's size in cells, a bit for each cell he has seen
     * ({@link java.util.BitSet#toLongArray}), and each cell in sight beside the frame it was last covered, cell by cell.
     */
    public record Memory(int width, int height, long[] seen, int[] inSight) {
    }

    /**
     * One player's cells at one frame, to keep and read from any thread: each chunk of {@link #CHUNK} cells a side a
     * {@link Sight} ordinal a cell, row by row, or null for a chunk nothing has seen; and, for a map in sight everywhere,
     * that. A chunk is never written once a view holds it: the same array in two views is the same cells.
     */
    public record View(float cellSize, int width, int height, byte[][] chunks, Sight everywhere) {

        /** A view of cells given one by one, row by row: what a test or a tool builds. */
        public View(float cellSize, int width, int height, byte[] states) {
            this(cellSize, width, height, chunked(width, height, states), null);
        }

        private static byte[][] chunked(int width, int height, byte[] states) {
            int across = (width + CHUNK - 1) / CHUNK;
            int down = (height + CHUNK - 1) / CHUNK;
            var chunks = new byte[across * down][];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    byte state = states[y * width + x];
                    if (state == NEVER) {
                        continue;
                    }
                    int chunk = (y / CHUNK) * across + x / CHUNK;
                    if (chunks[chunk] == null) {
                        chunks[chunk] = new byte[CHUNK * CHUNK];
                    }
                    chunks[chunk][(y % CHUNK) * CHUNK + x % CHUNK] = state;
                }
            }
            return chunks;
        }

        /** What the view has of the cell under world point ({@code x}, {@code y}); past the edges, never seen. */
        public Sight at(float x, float y) {
            return at((int) Math.floor(x / cellSize), (int) Math.floor(y / cellSize));
        }

        /** What the view has of cell ({@code cx}, {@code cy}); past the edges, never seen. */
        public Sight at(int cx, int cy) {
            if (cx < 0 || cy < 0 || cx >= width || cy >= height) {
                return Sight.NEVER_SEEN;
            }
            if (everywhere != null) {
                return everywhere;
            }
            var chunk = chunks[(cy / CHUNK) * chunksAcross() + cx / CHUNK];
            return chunk == null ? Sight.NEVER_SEEN : SIGHTS[chunk[(cy % CHUNK) * CHUNK + cx % CHUNK]];
        }

        /** How many chunks there are across the map, and down it. */
        public int chunksAcross() {
            return (width + CHUNK - 1) / CHUNK;
        }

        public int chunksDown() {
            return (height + CHUNK - 1) / CHUNK;
        }

        /** Every cell's {@link Sight} ordinal, row by row: a copy, the size of the map. */
        public byte[] states() {
            var states = new byte[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    states[y * width + x] = (byte) at(x, y).ordinal();
                }
            }
            return states;
        }
    }
}
