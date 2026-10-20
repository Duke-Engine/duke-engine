package uz.dukeengine.client3d;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.TreeMap;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * One node per open cell, holding its floor and whatever walls and posts stand on its edges. A node rather than a list
 * of pieces because fog is decided per cell: hiding a cell is one call, not one per piece it happens to own.
 */
final class Cells {

    private final Node root;
    private Node[] nodes = new Node[0];
    /** How many cells the map laid is wide, which a cell's index ({@code cy * wide + cx}) is under. */
    private int wide;
    /** A chunk's cells while the chunk's pieces are laid, each a node, in order; null when none is. */
    private TreeMap<Integer, Node> building;
    private Node buildingRoot;

    Cells(Node root) {
        this.root = root;
    }

    void clear() {
        nodes = new Node[0];
    }

    void widen(int wide) {
        this.wide = wide;
    }

    /** A node a cell of {@code grid}, none yet. */
    void open(PathGrid grid) {
        nodes = new Node[grid.getWidth() * grid.getHeight()];
        wide = grid.getWidth();
    }

    /** {@link #open} where nothing has been. */
    void openIfNone(PathGrid grid) {
        if (nodes.length == 0) {
            open(grid);
        }
    }

    int wide() {
        return wide;
    }

    /** The node of cell ({@code cx}, {@code cy}), made and attached on the first piece laid in it. */
    Node at(int cx, int cy) {
        int index = cy * wide + cx;
        if (building != null) {
            return building.computeIfAbsent(index, cell -> {
                var node = new Node("cell");
                buildingRoot.attachChild(node);
                return node;
            });
        }
        if (nodes[index] == null) {
            nodes[index] = new Node("cell");
            root.attachChild(nodes[index]);
        }
        return nodes[index];
    }

    /** Run {@code work}, every cell it lays into made in {@code into} and attached to {@code under} instead. */
    void layingInto(TreeMap<Integer, Node> into, Node under, Runnable work) {
        building = into;
        buildingRoot = under;
        try {
            work.run();
        } finally {
            building = null;
            buildingRoot = null;
        }
    }

    /** Every cell's node, null where nothing was built. */
    Node[] nodes() {
        return nodes;
    }

    /**
     * Anything nobody could see even if it were drawn is left out of the picture.
     *
     * <p>All this does is cull. How bright a piece is drawn belongs to its own
     * material, which samples the fog at the place the fragment stands — see
     * {@link FogMap}.
     *
     * <p>Per <b>piece</b>, and asked about where the piece stands rather than
     * which cell it was filed under. Those are not the same thing: a wall sits on
     * the line between two cells and a roof lies over rock that can be diagonal to
     * the room it was built with. Culling by the cell a piece belonged to dropped
     * walls and roofs that stood beside a fully lit room — the light ran out, and
     * instead of the wall going dim it went out, which reads as a bug in the
     * scene rather than as fog.
     */
    void applyDiscovery(Discovery seen) {
        for (var node : nodes) {
            if (node == null) {
                continue; // stone; nothing was built here
            }
            var pieces = node.getChildren();
            for (int i = 0; i < pieces.size(); i++) {
                var piece = pieces.get(i);
                var at = piece.getLocalTranslation();
                piece.setCullHint(seen.hiddenAt(at.x, at.z)
                        ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
            }
        }
    }
}
