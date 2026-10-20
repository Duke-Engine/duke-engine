package uz.dukeengine.client3d;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The gathered floor: a node a chunk of {@link #CELLS} cells a side (null where nothing stands), indexed
 * {@code chunkY * wide + chunkX}, and which of them the fog hides.
 */
final class Chunks {

    /** The side of a chunk of a kit's floor, in cells: gathered into one geometry a material, and culled as one. */
    static final int CELLS = 16;

    private final Node root;
    private int wide;
    private Node[] nodes = new Node[0];
    /** Every chunk to be asked whether the fog hides it at the next discovery, as for a new map. */
    private boolean every;
    private final BitSet dirty = new BitSet();

    Chunks(Node root) {
        this.root = root;
    }

    void clear() {
        nodes = new Node[0];
    }

    /** No chunk built yet, for the chunks of {@code grid}; every one to be asked about the fog. */
    void over(PathGrid grid) {
        wide = Math.ceilDiv(grid.getWidth(), CELLS);
        nodes = new Node[wide * Math.ceilDiv(grid.getHeight(), CELLS)];
        every = true;
    }

    /** Whether the map is laid in chunks. */
    boolean any() {
        return nodes.length > 0;
    }

    int wide() {
        return wide;
    }

    int deep() {
        return nodes.length / wide;
    }

    /** The chunk the cell ({@code cx}, {@code cy}) is in. */
    int of(int cx, int cy) {
        return cy / CELLS * wide + cx / CELLS;
    }

    /** The first cell column of a chunk. */
    int originX(int chunk) {
        return chunk % wide * CELLS;
    }

    /** The first cell row of a chunk. */
    int originY(int chunk) {
        return chunk / wide * CELLS;
    }

    /** The last cell column of a chunk on {@code grid}. */
    int lastX(int chunk, PathGrid grid) {
        return Math.min(originX(chunk) + CELLS, grid.getWidth()) - 1;
    }

    /** The last cell row of a chunk on {@code grid}. */
    int lastY(int chunk, PathGrid grid) {
        return Math.min(originY(chunk) + CELLS, grid.getHeight()) - 1;
    }

    /** The chunk's node; null where nothing stands. */
    Node at(int chunk) {
        return nodes[chunk];
    }

    /** Whether chunk {@code chunk} is more than {@code rings} chunks from chunk ({@code centreX}, {@code centreY}). */
    boolean far(int chunk, int centreX, int centreY, int rings) {
        return Math.max(Math.abs(chunk % wide - centreX), Math.abs(chunk / wide - centreY)) > rings;
    }

    /** Lay {@code node} as the chunk. */
    void put(int chunk, Node node) {
        root.attachChild(node);
        nodes[chunk] = node;
    }

    void drop(int chunk) {
        if (chunk < nodes.length && nodes[chunk] != null) {
            nodes[chunk].removeFromParent();
            nodes[chunk] = null;
        }
    }

    void ask(int chunk) {
        dirty.set(chunk);
    }

    /**
     * Every laid piece gathered into the chunk its cell is in: one geometry for each material in each chunk of {@link
     * #CELLS} cells a side, its vertices where the pieces stood — the reference's static terrain drawn as a few
     * hundred things rather than a geometry a piece, tens of thousands on a large floor, each walked by the scene every
     * frame. What is gathered keeps its material, its bucket and its shadows, and pieces of different vertex layouts
     * are gathered apart so none borrows another's.
     */
    void gather(PathGrid grid, Cells cells) {
        over(grid);
        root.updateGeometricState();
        var gathered = new ArrayList<LinkedHashMap<ChunkGeometry.Gather, List<Geometry>>>();
        for (int chunk = 0; chunk < nodes.length; chunk++) {
            gathered.add(new LinkedHashMap<>());
        }
        var laid = cells.nodes();
        for (int index = 0; index < laid.length; index++) {
            if (laid[index] != null) {
                ChunkGeometry.file(laid[index], gathered.get(of(index % cells.wide(), index / cells.wide())));
            }
        }
        for (var node : laid) {
            if (node != null) {
                node.removeFromParent(); // a painted map's ground stays where it is
            }
        }
        cells.clear();
        for (int chunk = 0; chunk < nodes.length; chunk++) {
            if (!gathered.get(chunk).isEmpty()) {
                put(chunk, ChunkGeometry.merged(gathered.get(chunk)));
            }
        }
    }

    /**
     * A chunk is left out of the picture only when all of it, and a cell round it, is behind the dark: a wall on the
     * chunk's edge faces the cell beside it, as a piece was asked about where it stood with a cell round it. Asked of
     * the chunks near a cell whose light moved, and of every chunk the first time after a new map.
     */
    void applyDiscovery(Discovery seen, int cellsWide) {
        if (every) {
            dirty.set(0, nodes.length);
            every = false;
        } else {
            var moved = seen.moved();
            int deep = deep();
            for (int i = 0; i < moved.size(); i++) {
                int at = moved.get(i);
                int cx = at % cellsWide;
                int cy = at / cellsWide;
                int fromX = Math.max(0, Math.floorDiv(cx - 1, CELLS));
                int toX = Math.min(wide - 1, (cx + 1) / CELLS);
                for (int y = Math.max(0, Math.floorDiv(cy - 1, CELLS)); y <= Math.min(deep - 1, (cy + 1) / CELLS); y++) {
                    dirty.set(y * wide + fromX, y * wide + toX + 1);
                }
            }
        }
        for (int chunk = dirty.nextSetBit(0); chunk >= 0; chunk = dirty.nextSetBit(chunk + 1)) {
            if (nodes[chunk] != null) {
                nodes[chunk].setCullHint(hidden(seen, chunk)
                        ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
            }
        }
        dirty.clear();
    }

    private boolean hidden(Discovery seen, int chunk) {
        int x0 = originX(chunk);
        int y0 = originY(chunk);
        for (int cy = y0 - 1; cy <= y0 + CELLS; cy++) {
            for (int cx = x0 - 1; cx <= x0 + CELLS; cx++) {
                if (seen.lightAt(cx, cy) > Discovery.DARK) {
                    return false;
                }
            }
        }
        return true;
    }
}
