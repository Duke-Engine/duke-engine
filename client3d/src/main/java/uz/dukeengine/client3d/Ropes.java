package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * The ropes things let down — {@link Visuals.UnitVisual#rope}: one line from a bone of a thing's model straight down
 * to the ground under it, so it is as long as the bone is high. Drawn from every frame's lines, so a rope follows its
 * bone, and one no longer given is gone.
 */
final class Ropes {

    /** One of a thing's ropes — the {@code index}th it lets down — and the point of the world its bone is at. */
    record Line(int id, int index, Visuals.Rope rope, Vector3f from) {

        /** A thing's only rope. */
        Line(int id, Visuals.Rope rope, Vector3f from) {
            this(id, 0, rope, from);
        }
    }

    private final AssetManager assets;
    private final Node root = new Node("ropes");
    /** Each rope drawn, by its thing and its place among the thing's ropes — see {@link #key}. */
    private final Map<Long, Geometry> drawn = new HashMap<>();

    Ropes(AssetManager assets, Node parent) {
        this.assets = assets;
        parent.attachChild(root);
    }

    /** Every rope as these lines say: new ones let down, the rest moved, any not in them hauled in. */
    void update(List<Line> lines, WorldMoments.Floor floor) {
        var kept = new HashSet<Long>();
        for (var line : lines) {
            kept.add(key(line.id(), line.index()));
            var from = line.from();
            float ground = floor.at(from.x, from.z);
            var geometry = drawn.computeIfAbsent(key(line.id(), line.index()), _ -> made());
            var mesh = geometry.getMesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3,
                    BufferUtils.createFloatBuffer(from.x, from.y, from.z, from.x, ground, from.z));
            mesh.updateBound();
            mesh.updateCounts();
            geometry.updateModelBound();
            var material = geometry.getMaterial();
            material.setColor("Color", rgb(line.rope().colour()));
            material.getAdditionalRenderState().setLineWidth(line.rope().width());
        }
        drawn.entrySet().removeIf(one -> {
            boolean gone = !kept.contains(one.getKey());
            if (gone) {
                one.getValue().removeFromParent();
            }
            return gone;
        });
    }

    /** How long a thing's first rope is now, or -1 for none, for a test. */
    float lengthOf(int id) {
        return lengthOf(id, 0);
    }

    /** How long the {@code index}th of a thing's ropes is now, or -1 for none, for a test. */
    float lengthOf(int id, int index) {
        var geometry = drawn.get(key(id, index));
        if (geometry == null) {
            return -1f;
        }
        var at = (java.nio.FloatBuffer) geometry.getMesh().getBuffer(VertexBuffer.Type.Position).getData();
        return new Vector3f(at.get(0), at.get(1), at.get(2)).distance(new Vector3f(at.get(3), at.get(4), at.get(5)));
    }

    /** Haul them all in — a new world has nothing hanging in it. */
    void clear() {
        drawn.values().forEach(Geometry::removeFromParent);
        drawn.clear();
    }

    private static long key(int id, int index) {
        return (long) id << 32 | index;
    }

    private Geometry made() {
        var mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        var geometry = new Geometry("rope", mesh);
        geometry.setMaterial(new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md"));
        root.attachChild(geometry);
        return geometry;
    }

    private static ColorRGBA rgb(int packed) {
        return new ColorRGBA((packed >> 16 & 0xFF) / 255f, (packed >> 8 & 0xFF) / 255f, (packed & 0xFF) / 255f, 1f);
    }
}
