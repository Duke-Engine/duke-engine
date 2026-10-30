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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The ropes things let down — {@link Visuals.UnitVisual#rope}: one line from a bone of a thing's model straight down
 * to the ground under it, so it is as long as the bone is high. Drawn from every frame's lines, so a rope follows its
 * bone, and one no longer given is gone. A rope {@link Visuals.Lowered} lengthens, and one {@link Visuals.Wobble}
 * bows, by the logic frames since it was first given.
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
    /** The logic frame each rope drawn was first given, by the same key. */
    private final Map<Long, Integer> since = new HashMap<>();

    Ropes(AssetManager assets, Node parent) {
        this.assets = assets;
        parent.attachChild(root);
    }

    /** {@link #update(List, WorldMoments.Floor, int)} at frame 0, for ropes that neither lengthen nor wobble. */
    void update(List<Line> lines, WorldMoments.Floor floor) {
        update(lines, floor, 0);
    }

    /**
     * Every rope as these lines say at logic frame {@code frame}: new ones let down, the rest moved, any not in them
     * hauled in.
     */
    void update(List<Line> lines, WorldMoments.Floor floor, int frame) {
        var kept = new HashSet<Long>();
        for (var line : lines) {
            long key = key(line.id(), line.index());
            kept.add(key);
            var from = line.from();
            var rope = line.rope();
            int frames = frame - since.computeIfAbsent(key, _ -> frame);
            float height = from.y - floor.at(from.x, from.z);
            var lowered = rope.lowered();
            float most = lowered == null ? height : Math.max(1f, height - lowered.stopShort());
            float length = lowered == null ? height : lowered(lowered, frames, most);
            var geometry = drawn.computeIfAbsent(key, _ -> made());
            var mesh = geometry.getMesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(points(key, from, length,
                    most, frames, rope.wobble())));
            mesh.updateBound();
            mesh.updateCounts();
            geometry.updateModelBound();
            var material = geometry.getMaterial();
            material.setColor("Color", rgb(rope.colour()));
            material.getAdditionalRenderState().setLineWidth(rope.width());
        }
        drawn.entrySet().removeIf(one -> {
            boolean gone = !kept.contains(one.getKey());
            if (gone) {
                one.getValue().removeFromParent();
                since.remove(one.getKey());
            }
            return gone;
        });
    }

    /** {@code ChinookCombatDropState::update}: from 1 long, faster by {@code accel} a frame up to {@code topSpeed}. */
    private static float lowered(Visuals.Lowered lowered, int frames, float most) {
        float at = 1f;
        float speed = 0f;
        for (int frame = 0; frame < frames && at < most; frame++) {
            speed = Math.min(speed + lowered.accel(), lowered.topSpeed());
            at += speed;
        }
        return Math.min(at, most);
    }

    /**
     * The points of the line down from {@code from} {@code length} long: one piece, or — wobbling — as many as cut
     * {@code most} into lengths of the wobble ({@code W3DRopeDraw::buildSegments}), each ending {@code sin(phase) *
     * amplitude} off the line along an axis of its own (the same axes every frame, from the rope's key).
     */
    private static float[] points(long key, Vector3f from, float length, float most, int frames,
            Visuals.Wobble wobble) {
        int pieces = wobble == null ? 1 : Math.max(1, (int) Math.ceil(most / Math.min(most, wobble.len())));
        float bow = wobble == null ? 0f : (float) (Math.sin(wobble.rate() * (double) frames % (2 * Math.PI))
                * wobble.amplitude());
        var axes = new Random(key);
        var out = new float[3 * (pieces + 1)];
        out[0] = from.x;
        out[1] = from.y;
        out[2] = from.z;
        for (int piece = 1; piece <= pieces; piece++) {
            double axis = wobble == null ? 0 : axes.nextDouble(2 * Math.PI);
            out[3 * piece] = from.x + bow * (float) Math.cos(axis);
            out[3 * piece + 1] = from.y - length * piece / pieces;
            out[3 * piece + 2] = from.z + bow * (float) Math.sin(axis);
        }
        return out;
    }

    /** How long a thing's first rope is now, or -1 for none, for a test. */
    float lengthOf(int id) {
        return lengthOf(id, 0);
    }

    /** How far down the {@code index}th of a thing's ropes reaches now, or -1 for none, for a test. */
    float lengthOf(int id, int index) {
        var points = pointsOf(id, index);
        return points.isEmpty() ? -1f : points.getFirst().y - points.getLast().y;
    }

    /** Where the {@code index}th of a thing's ropes bends, top to foot; empty for none, for a test. */
    List<Vector3f> pointsOf(int id, int index) {
        var geometry = drawn.get(key(id, index));
        if (geometry == null) {
            return List.of();
        }
        var at = (java.nio.FloatBuffer) geometry.getMesh().getBuffer(VertexBuffer.Type.Position).getData();
        var points = new ArrayList<Vector3f>();
        for (int i = 0; i + 2 < at.limit(); i += 3) {
            points.add(new Vector3f(at.get(i), at.get(i + 1), at.get(i + 2)));
        }
        return points;
    }

    /** Haul them all in — a new world has nothing hanging in it. */
    void clear() {
        drawn.values().forEach(Geometry::removeFromParent);
        drawn.clear();
        since.clear();
    }

    private static long key(int id, int index) {
        return (long) id << 32 | index;
    }

    private Geometry made() {
        var mesh = new Mesh();
        mesh.setMode(Mesh.Mode.LineStrip);
        var geometry = new Geometry("rope", mesh);
        geometry.setMaterial(new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md"));
        root.attachChild(geometry);
        return geometry;
    }

    private static ColorRGBA rgb(int packed) {
        return new ColorRGBA((packed >> 16 & 0xFF) / 255f, (packed >> 8 & 0xFF) / 255f, (packed & 0xFF) / 255f, 1f);
    }
}
