package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uz.dukeengine.core.map.MapScenery;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The map's scenery, laid with its ground: each piece in the cell it stands in, at the cell's floor on a kit's map,
 * where the relief is then laid over it as over the floor; on the relief itself on a painted one, upright.
 */
final class Scenery {

    /** How scenery is dressed: in the materials its models came with, each kept and made to read the fog. */
    static final Tileset DRESSING = Tileset.create().ownMaterials(true);

    private final TileSource tiles;
    private final Cells cells;
    private List<? extends MapScenery> pieces = List.of();
    /** Each piece's index in the map's scenery, by the piece itself. */
    private Map<MapScenery, Integer> index = new IdentityHashMap<>();
    /** The pieces the world has taken away, by their index in the map's scenery: not drawn. */
    private final Set<Integer> hidden = new HashSet<>();

    Scenery(TileSource tiles, Cells cells) {
        this.tiles = tiles;
        this.cells = cells;
    }

    void set(List<? extends MapScenery> pieces) {
        this.pieces = pieces == null ? List.of() : pieces;
        this.index = new IdentityHashMap<>();
        for (int at = 0; at < this.pieces.size(); at++) {
            index.put(this.pieces.get(at), at);
        }
    }

    /** The pieces taken away when the ground is next built — a save's felled trees. */
    void hide(Collection<Integer> indices) {
        hidden.clear();
        if (indices != null) {
            hidden.addAll(indices);
        }
    }

    /** A piece taken away or put back: the piece where that changed anything, else null. */
    MapScenery show(int at, boolean shown) {
        boolean changed = shown ? hidden.remove(at) : hidden.add(at);
        return changed && at >= 0 && at < pieces.size() ? pieces.get(at) : null;
    }

    /** Every piece laid, none at all where the game has no tile source to load models from. */
    void layAll(PathGrid grid, boolean onKitFloor) {
        if (pieces.isEmpty() || tiles == null) {
            return;
        }
        cells.openIfNone(grid);
        for (var piece : pieces) {
            lay(grid, piece, onKitFloor);
        }
    }

    /** One piece of scenery in the cell it stands in. */
    void lay(PathGrid grid, MapScenery piece, boolean onKitFloor) {
        var model = model(grid, piece, onKitFloor);
        if (model == null) {
            return;
        }
        cells.at(cellX(grid, piece), cellY(grid, piece)).attachChild(model);
    }

    /**
     * One piece of scenery, made and placed where it stands, turned and sized; null where it cannot be made. On the
     * floor of its cell on a kit's map, on the relief on a painted one.
     */
    Spatial model(PathGrid grid, MapScenery piece, boolean onKitFloor) {
        var at = index.get(piece);
        if (tiles == null || at != null && hidden.contains(at)) {
            return null; // taken away: a felled tree is not drawn
        }
        var model = tiles.piece(DRESSING, piece.model(), piece.tint(), piece.picture());
        if (model == null) {
            return null;
        }
        float cell = grid.getCellSize();
        float x = piece.x() * cell;
        float z = piece.y() * cell;
        model.setLocalScale(piece.scale());
        KitPieces.turn(model, FastMath.DEG_TO_RAD * piece.facing());
        model.setLocalTranslation(x, onKitFloor ? grid.storeyHeight(cellX(grid, piece), cellY(grid, piece))
                : PaintedGround.groundAt(grid, x, z), z);
        return model;
    }

    /** The pieces standing in each chunk of {@code grid}, by the chunk's index. */
    Map<Integer, List<MapScenery>> byChunk(PathGrid grid, Chunks chunks) {
        var byChunk = new HashMap<Integer, List<MapScenery>>();
        for (var piece : pieces) {
            byChunk.computeIfAbsent(chunkOf(grid, piece, chunks), _ -> new ArrayList<>()).add(piece);
        }
        return byChunk;
    }

    /** The chunk a piece stands in. */
    static int chunkOf(PathGrid grid, MapScenery piece, Chunks chunks) {
        return chunks.of(cellX(grid, piece), cellY(grid, piece));
    }

    private static int cellX(PathGrid grid, MapScenery piece) {
        return Math.clamp((int) Math.floor(piece.x()), 0, grid.getWidth() - 1);
    }

    private static int cellY(PathGrid grid, MapScenery piece) {
        return Math.clamp((int) Math.floor(piece.y()), 0, grid.getHeight() - 1);
    }
}
