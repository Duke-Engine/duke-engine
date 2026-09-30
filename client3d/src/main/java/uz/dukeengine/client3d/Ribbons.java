package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.StreamView;

/**
 * Streams drawn as ribbons — the reference's {@code W3DProjectileStreamDraw}: one strip of the stream's picture
 * through the places of what rides it, in order, its look's width across and turned to face the eye, cut again each
 * frame as things start and end, and broken, not drawn across, where the stream breaks.
 */
final class Ribbons {

    private final AssetManager assets;
    private final Node parent;
    private final Map<String, Visuals.StreamLook> looks;
    private final Map<String, Geometry> drawn = new HashMap<>();

    Ribbons(AssetManager assets, Node parent, Map<String, Visuals.StreamLook> looks) {
        this.assets = assets;
        this.parent = parent;
        this.looks = looks;
    }

    /** This frame's streams, seen from {@code eye}: each with a look drawn, the rest taken away. */
    void show(List<StreamView> streams, Vector3f eye) {
        var seen = new java.util.HashSet<String>();
        for (var stream : streams) {
            var look = looks.get(stream.name());
            if (look == null) {
                continue;
            }
            seen.add(stream.name());
            var ribbon = drawn.computeIfAbsent(stream.name(), name -> made(look));
            ribbon.setMesh(mesh(stream.pieces(), look.width(), eye));
        }
        drawn.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().removeFromParent();
            return true;
        });
    }

    private Geometry made(Visuals.StreamLook look) {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", assets.loadTexture(look.picture()));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        var ribbon = new Geometry("stream", new Mesh());
        ribbon.setMaterial(material);
        ribbon.setQueueBucket(RenderQueue.Bucket.Transparent);
        ribbon.setShadowMode(RenderQueue.ShadowMode.Off);
        parent.attachChild(ribbon);
        return ribbon;
    }

    /**
     * The strips through each piece of places — the map's y the scene's z — {@code width} across and turned to face
     * {@code eye}: two corners a place, the picture's u along each piece from 0 to 1, and no triangle from one piece
     * to the next.
     */
    static Mesh mesh(List<List<Coord3D>> pieces, float width, Vector3f eye) {
        int corners = 0;
        int triangles = 0;
        for (var piece : pieces) {
            if (piece.size() >= 2) {
                corners += piece.size() * 2;
                triangles += (piece.size() - 1) * 2;
            }
        }
        var positions = new float[corners * 3];
        var uvs = new float[corners * 2];
        var indices = new int[triangles * 3];
        int corner = 0;
        int index = 0;
        for (var piece : pieces) {
            if (piece.size() < 2) {
                continue;
            }
            int first = corner;
            for (int at = 0; at < piece.size(); at++) {
                var here = scene(piece.get(at));
                var along = scene(piece.get(Math.min(at + 1, piece.size() - 1)))
                        .subtract(scene(piece.get(Math.max(at - 1, 0))));
                var side = along.cross(eye.subtract(here)).normalizeLocal().multLocal(width / 2f);
                float u = (float) at / (piece.size() - 1);
                put(positions, uvs, corner++, here.add(side), u, 0f);
                put(positions, uvs, corner++, here.subtract(side), u, 1f);
            }
            for (int at = 0; at + 1 < piece.size(); at++) {
                int a = first + at * 2;
                indices[index++] = a;
                indices[index++] = a + 1;
                indices[index++] = a + 2;
                indices[index++] = a + 1;
                indices[index++] = a + 3;
                indices[index++] = a + 2;
            }
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        return mesh;
    }

    private static Vector3f scene(Coord3D place) {
        return new Vector3f(place.x(), place.z(), place.y());
    }

    private static void put(float[] positions, float[] uvs, int corner, Vector3f at, float u, float v) {
        positions[corner * 3] = at.x;
        positions[corner * 3 + 1] = at.y;
        positions[corner * 3 + 2] = at.z;
        uvs[corner * 2] = u;
        uvs[corner * 2 + 1] = v;
    }
}
