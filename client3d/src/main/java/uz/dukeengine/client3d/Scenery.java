package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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

    /** A model's far mesh, by the model's path: see {@link Visuals#instancedScenery}; null where scenery is not instanced. */
    private java.util.function.UnaryOperator<String> farOf;
    private java.util.function.Predicate<String> casts = _ -> false;
    /** Each material as its instances are drawn with it: a copy of a piece's, instanced; null for one that cannot be. */
    private final Map<Material, Material> instancedLook = new IdentityHashMap<>();
    private java.util.function.Consumer<Material> madeMaterial = _ -> { };

    Scenery(TileSource tiles, Cells cells) {
        this.tiles = tiles;
        this.cells = cells;
    }

    /** Draw it instanced from now, by chunk, with these far meshes; null as it was. */
    void instanced(java.util.function.UnaryOperator<String> farOf, java.util.function.Predicate<String> casts) {
        this.farOf = farOf;
        this.casts = casts == null ? _ -> false : casts;
    }

    boolean isInstanced() {
        return farOf != null;
    }

    /** Told each material made for instances: whoever keeps what reads the fog up to date keeps these too. */
    void onMadeMaterial(java.util.function.Consumer<Material> told) {
        this.madeMaterial = told == null ? _ -> { } : told;
    }

    /**
     * The chunk's scenery under {@code chunk}: every piece of a model drawn by one instanced draw whose instances are
     * written once, since scenery does not move -- a chunk's worth of a model is one thing to cull and draw and nothing
     * to do from frame to frame, its pieces not in the scene at all. Those with a far mesh in two draws, one shown at a
     * time ({@link Lod#show}); what cannot be instanced is drawn as it is.
     */
    Lod layInstanced(PathGrid grid, List<MapScenery> here, float ox, float oz, Node chunk) {
        var near = new LinkedHashMap<Instances, List<Geometry>>();
        var far = new LinkedHashMap<Instances, List<Geometry>>();
        var always = new LinkedHashMap<Instances, List<Geometry>>();
        for (var piece : here) {
            var placed = model(grid, piece, false);
            if (placed == null) {
                continue;
            }
            var farModel = farOf.apply(piece.model());
            var farPlaced = farModel == null ? null : farMesh(piece, farModel, placed);
            if (!file(placed, piece.model(), farPlaced == null ? always : near)) {
                placed.move(-ox, 0f, -oz);
                chunk.attachChild(placed);
                continue;
            }
            if (farPlaced != null) {
                file(farPlaced, farModel, far);
            }
        }
        var nearNode = drawn(near, "scenery near", chunk);
        var farNode = drawn(far, "scenery far", chunk);
        drawn(always, "scenery", chunk);
        return far.isEmpty() ? null : new Lod(nearNode, farNode);
    }

    private Spatial farMesh(MapScenery piece, String model, Spatial placed) {
        var far = tiles.piece(DRESSING, model, piece.tint(), piece.picture());
        if (far != null) {
            far.setLocalTransform(placed.getLocalTransform());
        }
        return far;
    }

    /** What instances of a piece share: a mesh, a look, and whether they cast the sun's shadow. */
    private record Instances(com.jme3.scene.Mesh mesh, Material look, boolean casts) {
    }

    /** Every geometry of a placed piece filed by what its instances share; false, nothing filed, where one cannot be instanced. */
    private boolean file(Spatial placed, String model, Map<Instances, List<Geometry>> into) {
        placed.updateGeometricState();
        var geometries = placed instanceof Node node ? node.descendantMatches(Geometry.class)
                : List.of((Geometry) placed);
        for (var geometry : geometries) {
            if (lookOf(geometry.getMaterial()) == null) {
                return false;
            }
        }
        boolean cast = casts.test(model);
        for (var geometry : geometries) {
            into.computeIfAbsent(new Instances(geometry.getMesh(), lookOf(geometry.getMaterial()), cast),
                    _ -> new ArrayList<>()).add(geometry);
        }
        return true;
    }

    private Material lookOf(Material material) {
        if (!instancedLook.containsKey(material)) {
            Material look = null;
            if (material.getMaterialDef().getMaterialParam("UseInstancing") != null) {
                look = material.clone();
                look.setBoolean("UseInstancing", true);
                madeMaterial.accept(look);
            }
            instancedLook.put(material, look);
        }
        return instancedLook.get(material);
    }

    /**
     * One instanced draw of {@code pieces} (standing in the world, their transforms up to date), its instances' data
     * written here, once: what never moves costs nothing from frame to frame, as jME's InstancedNode's rewriting of it does.
     */
    static com.jme3.scene.instancing.InstancedGeometry instanced(String name, com.jme3.scene.Mesh mesh, Material look,
            List<Geometry> pieces) {
        var draw = new com.jme3.scene.instancing.InstancedGeometry(name);
        draw.setMesh(mesh);
        draw.setMaterial(look);
        draw.setMaxNumInstances(pieces.size() + 1);
        pieces.forEach(draw::addInstance);
        draw.updateInstances(null);
        return draw;
    }

    /** A node of one instanced draw for each thing filed, under {@code chunk}. */
    private static Node drawn(Map<Instances, List<Geometry>> filed, String name, Node chunk) {
        var node = new Node(name);
        filed.forEach((what, pieces) -> {
            var draw = instanced(name, what.mesh(), what.look(), pieces);
            if (what.casts()) {
                draw.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive);
            }
            node.attachChild(draw);
        });
        chunk.attachChild(node);
        return node;
    }

    /** A chunk's two sets of instances: the models themselves near the camera, their far meshes past that. */
    record Lod(Spatial near, Spatial far) {

        void show(boolean nearOnes) {
            near.setCullHint(nearOnes ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            far.setCullHint(nearOnes ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        }
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
