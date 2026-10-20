package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jme3tools.optimize.GeometryBatchFactory;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;

/** What is done to the geometry of a chunk of a kit's floor: gathered into a geometry a material, bent, shifted. */
final class ChunkGeometry {

    private ChunkGeometry() {
    }

    /** What pieces must share to be gathered into one geometry: a material, a bucket, shadows, a vertex layout. */
    record Gather(Material material, RenderQueue.Bucket bucket, RenderQueue.ShadowMode shadows, String layout) {

        static Gather of(Geometry geometry) {
            var layout = new StringBuilder();
            for (var buffer : geometry.getMesh().getBufferList()) {
                if (buffer.getBufferType() != VertexBuffer.Type.Index) {
                    layout.append(buffer.getBufferType()).append(buffer.getNumComponents()).append(' ');
                }
            }
            return new Gather(geometry.getMaterial(), geometry.getQueueBucket(), geometry.getShadowMode(),
                    layout.toString());
        }
    }

    /** Files the geometries under {@code under} by what they must share to be gathered, in the order each is first met. */
    static void file(Node under, Map<Gather, List<Geometry>> byLook) {
        for (var geometry : under.descendantMatches(Geometry.class)) {
            byLook.computeIfAbsent(Gather.of(geometry), _ -> new ArrayList<>()).add(geometry);
        }
    }

    /** A chunk: a geometry for each look filed. */
    static Node merged(Map<Gather, List<Geometry>> byLook) {
        var node = new Node("chunk");
        byLook.forEach((key, geometries) -> node.attachChild(merged(key, geometries)));
        return node;
    }

    /** The geometries sharing {@code key}, one geometry. */
    static Geometry merged(Gather key, List<Geometry> geometries) {
        var mesh = new Mesh();
        GeometryBatchFactory.mergeGeometries(geometries, mesh);
        mesh.updateCounts();
        mesh.updateBound();
        var one = new Geometry("chunk", mesh);
        one.setMaterial(key.material());
        one.setQueueBucket(key.bucket());
        one.setShadowMode(key.shadows());
        return one;
    }

    /**
     * Every piece bent over the map's relief: each corner of its mesh raised by the relief under it, so a floor
     * rises and falls with the ground its walkers stand on and a wall follows the ground along its foot. The layout
     * is the storeys', and this the one place the relief enters the picture.
     *
     * <p>Only where a map has relief. A mesh is shared by every piece cut from one model, so a bent piece bends a
     * copy of its own; a chunk's gathered mesh is its own already, and is bent where it is — {@code ownMeshes}.
     */
    static void drape(PathGrid grid, Spatial under, boolean ownMeshes) {
        if (grid.getRelief() == null) {
            return;
        }
        under.updateGeometricState();
        var local = new Vector3f();
        var world = new Vector3f();
        under.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry)) {
                return;
            }
            var mesh = ownMeshes ? geometry.getMesh() : geometry.getMesh().deepClone();
            var positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
            var transform = geometry.getWorldTransform();
            for (int i = 0; i + 2 < positions.limit(); i += 3) {
                local.set(positions.get(i), positions.get(i + 1), positions.get(i + 2));
                transform.transformVector(local, world);
                world.y += grid.reliefHeight(new Coord3D(world.x, world.z, 0f));
                transform.transformInverseVector(world, local);
                positions.put(i, local.x).put(i + 1, local.y).put(i + 2, local.z);
            }
            mesh.getBuffer(VertexBuffer.Type.Position).setUpdateNeeded();
            mesh.updateBound();
            geometry.setMesh(mesh);
        });
    }

    /** Every geometry under {@code under} moved back by {@code corner} in x and z, so it is kept about a corner of its own. */
    static void moveBack(Node under, Vector3f corner) {
        for (var geometry : under.descendantMatches(Geometry.class)) {
            var positions = geometry.getMesh().getFloatBuffer(VertexBuffer.Type.Position);
            for (int i = 0; i + 2 < positions.limit(); i += 3) {
                positions.put(i, positions.get(i) - corner.x).put(i + 2, positions.get(i + 2) - corner.z);
            }
            geometry.getMesh().getBuffer(VertexBuffer.Type.Position).setUpdateNeeded();
            geometry.getMesh().updateBound();
        }
    }
}
