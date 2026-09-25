package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import uz.dukeengine.game.DukeGame;

/**
 * Pieces of pictures laid along the ground — a map's roads, pavements, tracks and painted marks — laid as the reference
 * lays each piece of its roads ({@code W3DRoadBuffer::loadFloat4PtSection}): a column every cell's width or less along
 * it, each flat across at the highest corner of the ground's cells under it and a little over it, a middle column left
 * out where the line between its neighbours passes just over it. One mesh a picture a layer, drawn after the ground
 * and its overlays and a later layer over an earlier one; how the pieces of a road meet is the game's.
 */
final class Strips {

    /** How far over the ground a piece floats: the reference's {@code MAP_HEIGHT_SCALE / 8}. */
    static final float FLOAT = 0.078f;
    /** How far the line between two columns may pass over the one between them for it to be left out. */
    static final float MOST_ERROR = 0.6875f;
    /** The reference's {@code MAP_XY_FACTOR}: a column at least this often along a piece, and a sample across. */
    static final float STEP = 10f;
    /** Where the pieces' layers start in the ground's drawing order, past any overlay's ({@link OverlayOrder}). */
    static final int ABOVE_OVERLAYS = 1 << 16;

    private record Group(int layer, String picture) {
    }

    private final Node node;
    private final Supplier<DrawnGround> ground;
    private final Function<String, Material> looks;
    private final Map<Group, List<DukeGame.StripPiece>> groups = new LinkedHashMap<>();
    private final Map<Group, Geometry> drawn = new LinkedHashMap<>();

    /**
     * @param node   where the pieces are hung
     * @param ground the ground as it is drawn now
     * @param looks  the material a picture is drawn with, or null where it will not load
     */
    Strips(Node node, Supplier<DrawnGround> ground, Function<String, Material> looks) {
        this.node = node;
        this.ground = ground;
        this.looks = looks;
    }

    /** More pieces laid: the meshes of the pictures and layers they add to built again. */
    void lay(List<DukeGame.StripPiece> pieces) {
        var touched = new java.util.LinkedHashSet<Group>();
        for (var piece : pieces) {
            if (piece.picture() == null) {
                continue;
            }
            var group = new Group(piece.layer(), piece.picture());
            groups.computeIfAbsent(group, key -> new ArrayList<>()).add(piece);
            touched.add(group);
        }
        for (var group : touched) {
            var was = drawn.remove(group);
            if (was != null) {
                was.removeFromParent();
            }
            var look = looks.apply(group.picture());
            if (look == null) {
                continue;
            }
            var laid = new Geometry("strip", mesh(ground.get(), groups.get(group)));
            laid.setMaterial(look);
            laid.setQueueBucket(RenderQueue.Bucket.Transparent);
            laid.setUserData(OverlayOrder.LAYER, ABOVE_OVERLAYS + group.layer());
            node.attachChild(laid);
            drawn.put(group, laid);
        }
    }

    /** Every piece laid, one mesh. */
    static Mesh mesh(DrawnGround ground, List<DukeGame.StripPiece> pieces) {
        var positions = new ArrayList<Vector3f>();
        var normals = new ArrayList<Vector3f>();
        var uvs = new ArrayList<float[]>();
        var indices = new ArrayList<Integer>();
        for (var piece : pieces) {
            lay(ground, piece, positions, normals, uvs, indices);
        }
        var positionBuffer = BufferUtils.createFloatBuffer(positions.size() * 3);
        var normalBuffer = BufferUtils.createFloatBuffer(normals.size() * 3);
        var uvBuffer = BufferUtils.createFloatBuffer(uvs.size() * 2);
        for (int i = 0; i < positions.size(); i++) {
            positionBuffer.put(positions.get(i).x).put(positions.get(i).y).put(positions.get(i).z);
            normalBuffer.put(normals.get(i).x).put(normals.get(i).y).put(normals.get(i).z);
            uvBuffer.put(uvs.get(i)[0]).put(uvs.get(i)[1]);
        }
        var indexBuffer = BufferUtils.createIntBuffer(indices.size());
        indices.forEach(indexBuffer::put);
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positionBuffer);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normalBuffer);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvBuffer);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indexBuffer);
        mesh.updateBound();
        return mesh;
    }

    /** One column of a piece: where along it, and how high it stands flat across. */
    private record Column(float along, float height) {
    }

    /** {@code loadFloat4PtSection}: one piece, its columns worked out, the ones left out left out, and laid. */
    private static void lay(DrawnGround ground, DukeGame.StripPiece piece, List<Vector3f> positions,
            List<Vector3f> normals, List<float[]> uvs, List<Integer> indices) {
        var a = new float[] {piece.a().x(), piece.a().y()};
        var b = new float[] {piece.b().x(), piece.b().y()};
        var c = new float[] {piece.c().x(), piece.c().y()};
        var d = new float[] {piece.d().x(), piece.d().y()};
        float length = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
        float width = (float) Math.hypot(d[0] - a[0], d[1] - a[1]);
        int columns = Math.max(2, (int) (length / STEP) + 1);
        int samples = Math.max(2, (int) (width / STEP) + 1);
        var all = new ArrayList<Column>(columns);
        for (int i = 0; i < columns; i++) {
            float along = (float) i / (columns - 1);
            float highest = -Float.MAX_VALUE;
            for (int j = 0; j < samples; j++) {
                var at = bilinear(a, b, c, d, along, (float) j / (samples - 1));
                highest = Math.max(highest, ground.highestCorner(at[0], at[1]));
            }
            all.add(new Column(along, highest));
        }
        var kept = new ArrayList<Column>();
        kept.add(all.getFirst());
        for (int i = 1; i < columns - 1; i++) {
            var previous = kept.getLast();
            var current = all.get(i);
            var next = all.get(i + 1);
            float from = previous.along() * (columns - 1);
            float here = i;
            float to = i + 1;
            // As the reference weighs them — each neighbour by the other's gap, swapped from a straight line's — so the
            // same columns are left out as there.
            float line = (previous.height() * (here - from) + next.height() * (to - here)) / (to - from);
            boolean passesJustOver = line >= current.height() && line < current.height() + MOST_ERROR;
            if (!passesJustOver) {
                kept.add(current);
            }
        }
        kept.add(all.getLast());
        int first = positions.size();
        for (int k = 0; k < kept.size(); k++) {
            var column = kept.get(k);
            var near = bilinear(a, b, c, d, column.along(), 0f);
            var far = bilinear(a, b, c, d, column.along(), 1f);
            float y = column.height() + FLOAT;
            var left = new Vector3f(near[0], y, near[1]);
            var right = new Vector3f(far[0], y, far[1]);
            var normal = normalOf(a, b, c, d, kept, k, left, right);
            positions.add(left);
            positions.add(right);
            normals.add(normal);
            normals.add(normal);
            float u = piece.u0() + (piece.u1() - piece.u0()) * column.along();
            uvs.add(new float[] {u, piece.v0()});
            uvs.add(new float[] {u, piece.v1()});
            if (k > 0) {
                int was = first + (k - 1) * 2;
                int now = first + k * 2;
                // Both windings, whichever way round the piece's corners go, face up.
                if (facesUp(positions.get(was), positions.get(was + 1), positions.get(now))) {
                    indices.addAll(List.of(was, was + 1, now, now, was + 1, now + 1));
                } else {
                    indices.addAll(List.of(was, now, was + 1, now, now + 1, was + 1));
                }
            }
        }
    }

    private static boolean facesUp(Vector3f first, Vector3f second, Vector3f third) {
        return second.subtract(first).crossLocal(third.subtract(first)).y > 0f;
    }

    /** Which way a column faces: up, tipped along the piece by the heights of the columns either side of it. */
    private static Vector3f normalOf(float[] a, float[] b, float[] c, float[] d, List<Column> kept, int k,
            Vector3f left, Vector3f right) {
        var before = kept.get(Math.max(0, k - 1));
        var after = kept.get(Math.min(kept.size() - 1, k + 1));
        var from = bilinear(a, b, c, d, before.along(), 0.5f);
        var to = bilinear(a, b, c, d, after.along(), 0.5f);
        var along = new Vector3f(to[0] - from[0], after.height() - before.height(), to[1] - from[1]);
        var across = right.subtract(left);
        var normal = along.cross(across);
        if (normal.lengthSquared() < 1e-12f) {
            return Vector3f.UNIT_Y.clone();
        }
        normal.normalizeLocal();
        return normal.y < 0f ? normal.negateLocal() : normal;
    }

    /** The point {@code along} a piece from {@code a} to {@code b}, and {@code across} it from {@code a} to d. */
    static float[] bilinear(float[] a, float[] b, float[] c, float[] d, float along, float across) {
        float nearX = a[0] + (b[0] - a[0]) * along;
        float nearZ = a[1] + (b[1] - a[1]) * along;
        float farX = d[0] + (c[0] - d[0]) * along;
        float farZ = d[1] + (c[1] - d[1]) * along;
        return new float[] {nearX + (farX - nearX) * across, nearZ + (farZ - nearZ) * across};
    }

    /** The meshes laid, for a test. */
    List<Geometry> laid() {
        return List.copyOf(drawn.values());
    }

    /** Every piece gone at once, for a new world. */
    void clear() {
        drawn.values().forEach(Geometry::removeFromParent);
        drawn.clear();
        groups.clear();
    }
}
