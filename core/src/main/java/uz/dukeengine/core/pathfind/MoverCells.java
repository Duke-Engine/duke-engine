package uz.dukeengine.core.pathfind;

import java.util.HashMap;
import java.util.Map;

/**
 * The ground movers on a grid's cells, as the reference's pathfinder keeps them ({@code PathfindCell::setGoalUnit},
 * {@code setPosUnit}): in every cell the mover standing on it, moving or still, and the mover going to it — each
 * covering its {@link Block}. A mover whose goal is where it stands is still there; others passing over the cells it
 * holds that way do not take them from it. Ids are the movers' object ids; none is 0.
 */
public final class MoverCells {

    private final int width;
    private final int height;
    private final int[] going;
    private final int[] standing;
    // Looked up by id, never walked, so the order a hash map keeps is nothing any machine reads.
    private final Map<Integer, Block> goals = new HashMap<>();
    private final Map<Integer, Block> stands = new HashMap<>();

    MoverCells(int width, int height) {
        this.width = width;
        this.height = height;
        this.going = new int[width * height];
        this.standing = new int[width * height];
    }

    /** The mover going to cell {@code (cx, cy)}, or 0. */
    public int goalAt(int cx, int cy) {
        return inside(cx, cy) ? going[cy * width + cx] : 0;
    }

    /** The mover standing on cell {@code (cx, cy)}, or 0. */
    public int standingAt(int cx, int cy) {
        return inside(cx, cy) ? standing[cy * width + cx] : 0;
    }

    /** The block mover {@code id} is going to, or null. */
    public Block goalOf(int id) {
        return goals.get(id);
    }

    /** The block mover {@code id} stands on, or null. */
    public Block standOf(int id) {
        return stands.get(id);
    }

    /** Mover {@code id} going to {@code block} from now on: the block it went to before let go. */
    public void claimGoal(int id, Block block) {
        if (block.equals(goals.get(id))) {
            return;
        }
        releaseGoal(id);
        goals.put(id, block);
        for (int cy = block.minY(); cy <= block.maxY(); cy++) {
            for (int cx = block.minX(); cx <= block.maxX(); cx++) {
                if (inside(cx, cy)) {
                    going[cy * width + cx] = id;
                }
            }
        }
    }

    /** Mover {@code id} going nowhere: the cells it was going to, where still its, let go. */
    public void releaseGoal(int id) {
        var block = goals.remove(id);
        if (block == null) {
            return;
        }
        for (int cy = block.minY(); cy <= block.maxY(); cy++) {
            for (int cx = block.minX(); cx <= block.maxX(); cx++) {
                if (inside(cx, cy) && going[cy * width + cx] == id) {
                    going[cy * width + cx] = 0;
                }
            }
        }
    }

    /**
     * Mover {@code id} standing on {@code block}: the cells it stood on let go, and the new ones marked — all but those
     * another stands on as its own goal, which it only passes over.
     */
    public void stand(int id, Block block) {
        if (block.equals(stands.get(id))) {
            return;
        }
        leave(id);
        stands.put(id, block);
        for (int cy = block.minY(); cy <= block.maxY(); cy++) {
            for (int cx = block.minX(); cx <= block.maxX(); cx++) {
                if (!inside(cx, cy)) {
                    continue;
                }
                int at = cy * width + cx;
                int parked = standing[at];
                if (parked != 0 && parked != id && going[at] == parked) {
                    continue; // another is still here, holding it: passed over, not taken
                }
                standing[at] = id;
            }
        }
    }

    /** Mover {@code id} off the ground: the cells it stood on, where still its, let go. */
    public void leave(int id) {
        var block = stands.remove(id);
        if (block == null) {
            return;
        }
        for (int cy = block.minY(); cy <= block.maxY(); cy++) {
            for (int cx = block.minX(); cx <= block.maxX(); cx++) {
                if (inside(cx, cy) && standing[cy * width + cx] == id) {
                    standing[cy * width + cx] = 0;
                }
            }
        }
    }

    /** Mover {@code id} gone from the ground for good: where it stood and where it was going. */
    public void forget(int id) {
        leave(id);
        releaseGoal(id);
    }

    /** Each mover's blocks, and every cell's movers as they are: which one a cell names depends on the claims' order. */
    public void save(uz.dukeengine.core.save.SaveOut out) {
        saveBlocks(out, goals);
        saveBlocks(out, stands);
        saveCells(out, going);
        saveCells(out, standing);
    }

    /** Read back what {@link #save} wrote, in place of what it held. */
    public void load(uz.dukeengine.core.save.SaveIn in) {
        loadBlocks(in, goals);
        loadBlocks(in, stands);
        loadCells(in, going);
        loadCells(in, standing);
    }

    private static void saveBlocks(uz.dukeengine.core.save.SaveOut out, Map<Integer, Block> blocks) {
        var ids = blocks.keySet().stream().sorted().toList();
        out.writeInt(ids.size());
        for (int id : ids) {
            var block = blocks.get(id);
            out.writeInt(id).writeInt(block.x()).writeInt(block.y()).writeInt(block.half())
                    .writeBoolean(block.centred());
        }
    }

    private static void loadBlocks(uz.dukeengine.core.save.SaveIn in, Map<Integer, Block> blocks) {
        blocks.clear();
        for (int count = in.readInt(); count > 0; count--) {
            int id = in.readInt();
            blocks.put(id, new Block(in.readInt(), in.readInt(), in.readInt(), in.readBoolean()));
        }
    }

    /** The cells naming a mover, as runs: where each starts, how long it is, and the mover. */
    private static void saveCells(uz.dukeengine.core.save.SaveOut out, int[] cells) {
        var runs = new java.util.ArrayList<int[]>();
        for (int at = 0; at < cells.length; at++) {
            if (cells[at] == 0) {
                continue;
            }
            int from = at;
            while (at + 1 < cells.length && cells[at + 1] == cells[from]) {
                at++;
            }
            runs.add(new int[] {from, at - from + 1, cells[from]});
        }
        out.writeInt(runs.size());
        for (var run : runs) {
            out.writeInt(run[0]).writeInt(run[1]).writeInt(run[2]);
        }
    }

    private static void loadCells(uz.dukeengine.core.save.SaveIn in, int[] cells) {
        java.util.Arrays.fill(cells, 0);
        for (int count = in.readInt(); count > 0; count--) {
            int from = in.readInt();
            int length = in.readInt();
            java.util.Arrays.fill(cells, from, from + length, in.readInt());
        }
    }

    private boolean inside(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < width && cy < height;
    }
}
