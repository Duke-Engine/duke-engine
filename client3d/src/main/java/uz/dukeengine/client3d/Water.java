package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.Vector4f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import uz.dukeengine.core.map.MapArea;
import uz.dukeengine.core.pathfind.HeightMap;

/**
 * A map's water, drawn as the reference draws its translucent water ({@code W3DWater.cpp}): standing water a surface
 * at its height over the cells inside its outline, its picture laid across the world drifting, a second picture over
 * it, and as clear at the shore as the ground under it is shallow; a river a strip from its start along its banks, its
 * picture running down it and its banks faded. How it looks — its pictures, their sizes, drifts and flow, its tint and
 * its fade — is the game's ({@link Visuals.WaterLook}). Drawing only.
 */
final class Water {

    private record Laid(Geometry drawn, boolean river) {
    }

    private final Node node;
    private final BiFunction<Visuals.WaterLook, Boolean, Material> looks;
    private final List<Laid> laid = new ArrayList<>();
    private Visuals.WaterLook look;

    /**
     * @param node  where the water is hung
     * @param looks the material standing water ({@code false}) or a river ({@code true}) is drawn with
     */
    Water(Node node, BiFunction<Visuals.WaterLook, Boolean, Material> looks) {
        this.node = node;
        this.looks = looks;
    }

    /** The water of a map laid again, as {@code look} draws it: every area its map says is water. */
    void rebuild(List<? extends MapArea> areas, DrawnGround ground, Visuals.WaterLook with) {
        clear();
        this.look = with;
        if (with == null || ground.cellSize() <= 0f) {
            return;
        }
        for (var area : areas) {
            if (!area.water() || area.points().size() < 6) {
                continue;
            }
            var mesh = area.river() ? river(area, ground) : standing(area, ground, with.fadeDepth());
            if (mesh.getVertexCount() == 0) {
                continue;
            }
            var material = looks.apply(with, area.river());
            if (material == null) {
                continue;
            }
            var water = new Geometry(area.river() ? "river" : "water", mesh);
            water.setMaterial(material);
            water.setQueueBucket(RenderQueue.Bucket.Transparent);
            node.attachChild(water);
            laid.add(new Laid(water, area.river()));
        }
        update(0f);
    }

    /** The game's time passing: every picture drifted and every river run on to where it is {@code seconds} in. */
    void update(float seconds) {
        if (look == null) {
            return;
        }
        var still = placeOf(look.size(), look.driftU(), look.driftV(), seconds);
        var over = placeOf(look.overlaySize(), look.overlayDriftU(), look.overlayDriftV(), seconds);
        float run = riverOffset(look, seconds);
        for (var one : laid) {
            var material = one.drawn().getMaterial();
            if (one.river()) {
                material.setVector4("ColorPlace", new Vector4f(1f, 1f, 0f, run));
            } else {
                material.setVector4("ColorPlace", still);
            }
            material.setVector4("OverlayPlace", over);
        }
    }

    /** A picture laid by world position {@code size} a copy, drifted where it is {@code seconds} in. */
    static Vector4f placeOf(float size, float driftU, float driftV, float seconds) {
        float across = Math.max(0.01f, size);
        float u = driftU * seconds;
        float v = driftV * seconds;
        return new Vector4f(1f / across, 1f / across, u - (float) Math.floor(u), v - (float) Math.floor(v));
    }

    /**
     * How far a river's picture has run down it {@code seconds} in, in its own lengths — back from its start, so what
     * the picture shows moves on down the river, as the reference's {@code m_riverVOrigin} does.
     */
    static float riverOffset(Visuals.WaterLook look, float seconds) {
        float run = -look.riverFlow() * seconds;
        return run - (float) Math.floor(run);
    }

    /** The water's surface height, from the steps its area stands at. */
    private static float surfaceOf(MapArea area, DrawnGround ground) {
        return area.height() * ground.cellSize() / HeightMap.STEPS_PER_CELL;
    }

    /**
     * Standing water: a quad at its surface over every cell whose middle lies inside its outline, each corner as clear
     * as the ground under it is shallow — whole {@code fadeDepth} under the surface and deeper ({@code
     * TransparentWaterDepth}).
     */
    static Mesh standing(MapArea area, DrawnGround ground, float fadeDepth) {
        float cell = ground.cellSize();
        var outline = outlineOf(area, cell);
        float surface = surfaceOf(area, ground);
        float leastX = Float.MAX_VALUE;
        float mostX = -Float.MAX_VALUE;
        float leastZ = Float.MAX_VALUE;
        float mostZ = -Float.MAX_VALUE;
        for (int i = 0; i < outline.length; i += 2) {
            leastX = Math.min(leastX, outline[i]);
            mostX = Math.max(mostX, outline[i]);
            leastZ = Math.min(leastZ, outline[i + 1]);
            mostZ = Math.max(mostZ, outline[i + 1]);
        }
        int fromX = Math.max(0, (int) Math.floor(leastX / cell));
        int toX = Math.min(ground.columns(), (int) Math.ceil(mostX / cell));
        int fromZ = Math.max(0, (int) Math.floor(leastZ / cell));
        int toZ = Math.min(ground.rows(), (int) Math.ceil(mostZ / cell));
        var cells = new ArrayList<int[]>();
        for (int cz = fromZ; cz < toZ; cz++) {
            for (int cx = fromX; cx < toX; cx++) {
                if (inside(outline, (cx + 0.5f) * cell, (cz + 0.5f) * cell)) {
                    cells.add(new int[] {cx, cz});
                }
            }
        }
        var positions = BufferUtils.createFloatBuffer(cells.size() * 12);
        var colours = BufferUtils.createFloatBuffer(cells.size() * 16);
        var uvs = BufferUtils.createFloatBuffer(cells.size() * 8);
        var indices = BufferUtils.createIntBuffer(cells.size() * 6);
        int vertex = 0;
        for (var at : cells) {
            float x0 = at[0] * cell;
            float z0 = at[1] * cell;
            for (var corner : new float[][] {{x0, z0}, {x0 + cell, z0}, {x0 + cell, z0 + cell}, {x0, z0 + cell}}) {
                positions.put(corner[0]).put(surface).put(corner[1]);
                float depth = surface - ground.heightAt(corner[0], corner[1]);
                float clear = fadeDepth <= 0f ? 1f : Math.clamp(depth / fadeDepth, 0f, 1f);
                colours.put(1f).put(1f).put(1f).put(clear);
                uvs.put(0f).put(0f);
            }
            indices.put(vertex).put(vertex + 3).put(vertex + 2).put(vertex).put(vertex + 2).put(vertex + 1);
            vertex += 4;
        }
        return mesh(positions, colours, uvs, indices);
    }

    /**
     * A river, as {@code drawRiverWater} lays one: its corners paired off from its start — one bank on from the corner
     * after it, the other back from it — a quad between each pair and the next; its picture across it from the second
     * bank to the first and down it from its start, repeating once a width of its mouth.
     */
    static Mesh river(MapArea area, DrawnGround ground) {
        var outline = outlineOf(area, ground.cellSize());
        int corners = outline.length / 2;
        int pairs = corners / 2;
        int start = Math.floorMod(area.riverStart(), corners);
        float surface = surfaceOf(area, ground);
        float total = 0f;
        float mouth = 0f;
        for (int i = 0; i < corners - 1; i++) {
            float edge = (float) Math.hypot(outline[i * 2 + 2] - outline[i * 2],
                    outline[i * 2 + 3] - outline[i * 2 + 1]);
            total += edge;
            if (i == start) {
                mouth = edge;
            }
        }
        float lengths = mouth > 0f ? (total / 2f - mouth) / mouth : 1f;
        float step = pairs > 1 ? lengths / (pairs - 1) : 0f;
        var positions = BufferUtils.createFloatBuffer(pairs * 6);
        var colours = BufferUtils.createFloatBuffer(pairs * 8);
        var uvs = BufferUtils.createFloatBuffer(pairs * 4);
        var indices = BufferUtils.createIntBuffer(Math.max(0, pairs - 1) * 6);
        for (int i = 0; i < pairs; i++) {
            int first = Math.floorMod(start + 1 + i, corners);
            int second = Math.floorMod(start - i, corners);
            positions.put(outline[first * 2]).put(surface).put(outline[first * 2 + 1]);
            positions.put(outline[second * 2]).put(surface).put(outline[second * 2 + 1]);
            colours.put(1f).put(1f).put(1f).put(1f).put(1f).put(1f).put(1f).put(1f);
            uvs.put(1f).put(step * i).put(0f).put(step * i);
            if (i > 0) {
                int was = (i - 1) * 2;
                indices.put(was).put(was + 1).put(was + 3).put(was).put(was + 3).put(was + 2);
            }
        }
        return mesh(positions, colours, uvs, indices);
    }

    /** Its outline in the client's frame: the map's cells as world units, {@code x, z, x, z, …}. */
    private static float[] outlineOf(MapArea area, float cell) {
        var points = area.points();
        var outline = new float[points.size() / 2 * 2];
        for (int i = 0; i < outline.length; i++) {
            outline[i] = points.get(i) * cell;
        }
        return outline;
    }

    /** Whether a place lies inside an outline: the even-odd rule. */
    static boolean inside(float[] outline, float x, float z) {
        boolean in = false;
        int corners = outline.length / 2;
        for (int i = 0, j = corners - 1; i < corners; j = i++) {
            float xi = outline[i * 2];
            float zi = outline[i * 2 + 1];
            float xj = outline[j * 2];
            float zj = outline[j * 2 + 1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    private static Mesh mesh(java.nio.FloatBuffer positions, java.nio.FloatBuffer colours, java.nio.FloatBuffer uvs,
            java.nio.IntBuffer indices) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Color, 4, colours);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        return mesh;
    }

    /** The water laid, for a test. */
    List<Geometry> laid() {
        return laid.stream().map(Laid::drawn).toList();
    }

    /** Every piece of water gone, for a new world. */
    void clear() {
        laid.forEach(one -> one.drawn().removeFromParent());
        laid.clear();
    }
}
