package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * What grows on the ground round the camera — see {@link GroundCover}: the chunks within the cover's reach grown, each
 * a node of instanced models hung on the chunk, and those beyond let go.
 */
final class CoverGrowth {

    private final TileSource tiles;
    private final Chunks chunks;
    private GroundCover cover = GroundCover.NONE;
    /** Each chunk's cover, grown, by chunk. */
    private final Map<Integer, Node> byChunk = new HashMap<>();
    /** Each kind's model as cover draws it: its materials its own, instanced. */
    private final Map<GroundCover.Kind, Spatial> models = new HashMap<>();
    /** The kinds whose every material takes {@code UseInstancing}: those alone are drawn instanced. */
    private final Set<GroundCover.Kind> instanceable = new HashSet<>();
    /**
     * Told each material cover makes of its own — a copy of a piece's, instanced — so whoever keeps the materials that
     * read the fog up to date (its window, its haze, its lights) keeps these too.
     */
    private Consumer<Material> madeMaterial = _ -> { };

    CoverGrowth(TileSource tiles, Chunks chunks) {
        this.tiles = tiles;
        this.chunks = chunks;
    }

    void onMadeMaterial(Consumer<Material> told) {
        this.madeMaterial = told == null ? _ -> { } : told;
    }

    /** Grow {@code grows} round the camera from now, what grew before let go. */
    void set(GroundCover grows) {
        byChunk.values().forEach(Spatial::removeFromParent);
        byChunk.clear();
        this.cover = grows == null ? GroundCover.NONE : grows;
    }

    /**
     * The chunks within the cover's reach of chunk ({@code centreX}, {@code centreY}) grown, and those beyond let go;
     * nothing on a map not painted.
     */
    void grow(PathGrid grid, GroundPaint paint, int centreX, int centreY) {
        if (cover.reach() <= 0f || cover.density() <= 0f || paint == null) {
            return;
        }
        int rings = Math.max(0, Math.ceilDiv((int) Math.ceil(cover.reach()), Chunks.CELLS) - 1);
        for (var it = byChunk.entrySet().iterator(); it.hasNext();) {
            var grown = it.next();
            if (chunks.far(grown.getKey(), centreX, centreY, rings + 1) || chunks.at(grown.getKey()) == null) {
                grown.getValue().removeFromParent();
                it.remove();
            }
        }
        for (int cy = Math.max(0, centreY - rings); cy <= Math.min(chunks.deep() - 1, centreY + rings); cy++) {
            for (int cx = Math.max(0, centreX - rings); cx <= Math.min(chunks.wide() - 1, centreX + rings); cx++) {
                int chunk = cy * chunks.wide() + cx;
                if (chunks.at(chunk) != null && !byChunk.containsKey(chunk)) {
                    var grown = coverOf(grid, paint, chunk);
                    chunks.at(chunk).attachChild(grown);
                    byChunk.put(chunk, grown);
                }
            }
        }
    }

    /** What grows in chunk {@code chunk}: every piece of a model drawn as one instanced geometry, about its corner. */
    private Node coverOf(PathGrid grid, GroundPaint paint, int chunk) {
        int x0 = chunks.originX(chunk);
        int y0 = chunks.originY(chunk);
        int x1 = chunks.lastX(chunk, grid);
        int y1 = chunks.lastY(chunk, grid);
        float cell = grid.getCellSize();
        var grown = new Node("cover");
        var instances = new java.util.LinkedHashMap<Geometry, java.util.List<Geometry>>();
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                for (var piece : cover.growIn(cx, cy, paint.pictureAt(cx, cy))) {
                    var model = coverModel(piece.kind());
                    if (model == null) {
                        continue;
                    }
                    var one = model.clone(false); // its mesh and its material shared: one instanced draw a model
                    float x = piece.x() * cell;
                    float z = piece.y() * cell;
                    one.setLocalScale(piece.scale());
                    KitPieces.turn(one, FastMath.DEG_TO_RAD * piece.facing());
                    float y = PaintedGround.groundAt(grid, x, z);
                    // One whose material cannot be instanced is drawn as it is: never the window's death (E100).
                    if (instanceable.contains(piece.kind())) {
                        one.setLocalTranslation(x, y, z); // an instance stands in the world itself, not under its chunk
                        one.updateGeometricState();
                        for (var geometry : geometriesOf(one)) {
                            instances.computeIfAbsent(masterOf(piece.kind(), geometry), _ -> new java.util.ArrayList<>())
                                    .add(geometry);
                        }
                    } else {
                        one.setLocalTranslation(x - x0 * cell, y, z - y0 * cell);
                        grown.attachChild(one);
                    }
                }
            }
        }
        instances.forEach((master, pieces) ->
                grown.attachChild(Scenery.instanced("cover instanced", master.getMesh(), master.getMaterial(), pieces)));
        grown.setShadowMode(RenderQueue.ShadowMode.Off);
        return grown;
    }

    /** A kind's model as cover draws it — loaded once, its materials its own and instanced; null where it will not load. */
    private Spatial coverModel(GroundCover.Kind kind) {
        if (models.containsKey(kind)) {
            return models.get(kind);
        }
        var loaded = tiles == null ? null : tiles.piece(Scenery.DRESSING, kind.model(), kind.tint(), null);
        Spatial model = null;
        if (loaded != null) {
            // Its own materials: scenery sharing the model is drawn as ever, not as an instance it is not.
            model = loaded.clone(true);
            var geometries = geometriesOf(model);
            boolean every = true;
            for (var geometry : geometries) {
                every &= geometry.getMaterial().getMaterialDef().getMaterialParam("UseInstancing") != null;
            }
            if (every) {
                for (var geometry : geometries) {
                    geometry.getMaterial().setBoolean("UseInstancing", true);
                }
                instanceable.add(kind);
            }
            // A copy is a material of its own: told about as one, or its fog's window and haze stand still (E100).
            for (var geometry : geometries) {
                madeMaterial.accept(geometry.getMaterial());
            }
        }
        models.put(kind, model);
        return model;
    }

    /** The geometry of a kind's model that {@code geometry}, a copy of it, was cut from: what its instances are drawn as. */
    private Geometry masterOf(GroundCover.Kind kind, Geometry geometry) {
        for (var master : geometriesOf(models.get(kind))) {
            if (master.getMesh() == geometry.getMesh()) {
                return master;
            }
        }
        throw new IllegalStateException("a copy of a model with a mesh the model has not");
    }

    private static List<Geometry> geometriesOf(Spatial model) {
        return model instanceof Node node ? node.descendantMatches(Geometry.class) : List.of((Geometry) model);
    }
}
