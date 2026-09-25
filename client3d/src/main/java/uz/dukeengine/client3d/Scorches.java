package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;
import java.util.function.Supplier;
import uz.dukeengine.core.pathfind.HeightMap;

/**
 * Marks burnt into the ground — an effect list's scorches, and the ones a map lays as it loads — drawn as the reference
 * draws every one of them ({@code BaseHeightMapRenderObjClass::addScorch}, {@code updateScorches}): a vertex at every
 * corner of the ground's cells round the mark, at the ground's height there and a little over it, the cells cut along
 * the diagonal the ground is drawn cut along, so a mark lies on the ground's rise and fall however large it is. Its
 * picture is laid by each corner's offset from its middle; how it is shaded and shrouded is the material's.
 */
final class Scorches {

    /** The reference's {@code MAX_SCORCH_MARKS}: past this many, the oldest mark is let go of. */
    static final int MOST = 500;

    /** How far over the ground a mark lies: the reference's {@code MAP_HEIGHT_SCALE / 10}, a tenth of its step. */
    static final float LIFT = 0.0625f;

    /** The ground a mark lies on, as it is drawn. */
    interface Ground {

        /** How wide a cell is, or 0 where there are no cells: a mark then lies on the four corners of its square. */
        float cellSize();

        /** How many cells across and down, a mark laid past its edges cut at them. */
        int columns();

        int rows();

        /** How high the ground stands at a place, in the client's frame: {@code z} is the map's y. */
        float heightAt(float x, float z);

        /** The diagonal a cell is drawn cut along. */
        HeightMap.Diagonal diagonal(int cx, int cy);

        /** Ground with no cells, flat at nothing. */
        Ground NONE = new Ground() {
            @Override
            public float cellSize() {
                return 0f;
            }

            @Override
            public int columns() {
                return 0;
            }

            @Override
            public int rows() {
                return 0;
            }

            @Override
            public float heightAt(float x, float z) {
                return 0f;
            }

            @Override
            public HeightMap.Diagonal diagonal(int cx, int cy) {
                return HeightMap.Diagonal.MAIN;
            }
        };
    }

    private record Mark(Geometry drawn, float x, float z, float radius, String picture) {
    }

    private final Node node;
    private final Supplier<Ground> ground;
    private final Function<String, Material> looks;
    private final Deque<Mark> marks = new ArrayDeque<>();

    /**
     * @param node   where the marks are hung
     * @param ground the ground as it is drawn now
     * @param looks  the material a mark's picture is drawn with, or null where the picture will not load
     */
    Scorches(Node node, Supplier<Ground> ground, Function<String, Material> looks) {
        this.node = node;
        this.ground = ground;
        this.looks = looks;
    }

    /** A mark of {@code picture} laid at ({@code x}, {@code z}), {@code radius} each way. */
    void lay(float x, float z, float radius, String picture) {
        if (radius <= 0f || picture == null) {
            return;
        }
        // addScorch: one next to one just like it is the same mark.
        float near = radius / 4f;
        for (var mark : marks) {
            if (Math.abs(x - mark.x()) < near && Math.abs(z - mark.z()) < near
                    && Math.abs(radius - mark.radius()) < near && picture.equals(mark.picture())) {
                return;
            }
        }
        var look = looks.apply(picture);
        if (look == null) {
            return;
        }
        if (marks.size() >= MOST) {
            marks.pollFirst().drawn().removeFromParent();
        }
        var drawn = new Geometry("scorch", mesh(ground.get(), x, z, radius));
        drawn.setMaterial(look);
        drawn.setQueueBucket(RenderQueue.Bucket.Transparent);
        node.attachChild(drawn);
        marks.addLast(new Mark(drawn, x, z, radius, picture));
    }

    /**
     * {@code updateScorches}: the corners of the cells from the one under the mark's near edge to one past its far
     * edge, each at the ground's height and {@link #LIFT} over it, its picture laid by its offset from the middle — the
     * whole picture across the mark's diameter, {@code v} growing with the map's y as the reference's does with its.
     */
    static Mesh mesh(Ground ground, float x, float z, float radius) {
        float cell = ground.cellSize();
        int fromX;
        int toX;
        int fromZ;
        int toZ;
        float step;
        if (cell > 0f) {
            fromX = Math.max(0, (int) Math.floor((x - radius) / cell));
            fromZ = Math.max(0, (int) Math.floor((z - radius) / cell));
            toX = Math.min(ground.columns() + 1, (int) Math.ceil((x + radius) / cell) + 1); // corners, one past cells
            toZ = Math.min(ground.rows() + 1, (int) Math.ceil((z + radius) / cell) + 1);
            step = cell;
        } else {
            fromX = 0;
            fromZ = 0;
            toX = 2;
            toZ = 2;
            step = 2f * radius;
        }
        float originX = cell > 0f ? 0f : x - radius;
        float originZ = cell > 0f ? 0f : z - radius;
        int across = Math.max(0, toX - fromX);
        int down = Math.max(0, toZ - fromZ);
        var positions = BufferUtils.createFloatBuffer(across * down * 3);
        var normals = BufferUtils.createFloatBuffer(across * down * 3);
        var uvs = BufferUtils.createFloatBuffer(across * down * 2);
        for (int j = fromZ; j < toZ; j++) {
            for (int i = fromX; i < toX; i++) {
                float cornerX = originX + i * step;
                float cornerZ = originZ + j * step;
                positions.put(cornerX).put(ground.heightAt(cornerX, cornerZ) + LIFT).put(cornerZ);
                normals.put(0f).put(1f).put(0f);
                uvs.put(0.5f + (cornerX - x) / (2f * radius)).put(0.5f + (cornerZ - z) / (2f * radius));
            }
        }
        int cellsAcross = Math.max(0, across - 1);
        int cellsDown = Math.max(0, down - 1);
        var indices = BufferUtils.createIntBuffer(cellsAcross * cellsDown * 6);
        for (int j = 0; j < cellsDown; j++) {
            for (int i = 0; i < cellsAcross; i++) {
                int first = j * across + i;
                int[] corner = {first, first + 1, first + 1 + across, first + across};
                var cut = cell > 0f ? ground.diagonal(fromX + i, fromZ + j) : HeightMap.Diagonal.MAIN;
                for (int k : TerrainScene.cellTriangles(cut)) {
                    indices.put(corner[k]);
                }
            }
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        return mesh;
    }

    int count() {
        return marks.size();
    }

    /** The mark laid last, for a test. */
    Geometry newest() {
        return marks.isEmpty() ? null : marks.peekLast().drawn();
    }

    /** Every mark gone at once, for a new world. */
    void clear() {
        marks.forEach(mark -> mark.drawn().removeFromParent());
        marks.clear();
    }
}
