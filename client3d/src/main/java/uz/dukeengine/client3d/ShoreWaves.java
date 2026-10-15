package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import uz.dukeengine.core.math.Coord3D;

/**
 * Waves sliding in along a shore — the reference's {@code W3DWaterTracks}: each a picture whose front runs from one pair
 * of points to another over its frames, its body {@code width} behind toward deep water, fading over its last frames
 * and starting again every {@code repeatFrames}, at the water's height. Drawing only.
 */
final class ShoreWaves {

    private record Wave(Visuals.WaveLook look, Geometry geometry) {
    }

    private final Node node;
    private final Function<String, Material> pictures;
    private final List<Wave> waves = new ArrayList<>();

    ShoreWaves(Node node, Function<String, Material> pictures) {
        this.node = node;
        this.pictures = pictures;
    }

    /** The waves of {@code looks}, laid again. */
    void lay(List<Visuals.WaveLook> looks) {
        clear();
        for (var look : looks) {
            var material = pictures.apply(look.picture());
            if (material == null) {
                continue;
            }
            material.setBoolean("VertexColor", true);
            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            material.getAdditionalRenderState().setDepthWrite(false);
            material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            var mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(12));
            mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(16));
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, 
                    BufferUtils.createFloatBuffer(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f));
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(0, 1, 2, 0, 2, 3));
            var geometry = new Geometry("shore wave", mesh);
            geometry.setMaterial(material);
            geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
            node.attachChild(geometry);
            waves.add(new Wave(look, geometry));
        }
    }

    /** Each wave where it is at the game's frame {@code frame}, at the height {@code height} gives a point. */
    void see(int frame, BiFunction<Float, Float, Float> height) {
        for (var wave : waves) {
            var at = at(wave.look(), frame);
            var mesh = wave.geometry().getMesh();
            var positions = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();
            var colours = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Color).getData();
            positions.clear();
            colours.clear();
            float y = height.apply((at[0] + at[2]) / 2f, (at[1] + at[3]) / 2f) + 0.1f;
            // The front A to B, the body behind it toward deep water: back along the way it came.
            var look = wave.look();
            float backX = look.startA().x() - look.endA().x();
            float backY = look.startA().y() - look.endA().y();
            float length = (float) Math.sqrt(backX * backX + backY * backY);
            float bx = length == 0f ? 0f : backX / length * look.width();
            float by = length == 0f ? 0f : backY / length * look.width();
            positions.put(at[0] + bx).put(y).put(at[1] + by);
            positions.put(at[2] + bx).put(y).put(at[3] + by);
            positions.put(at[2]).put(y).put(at[3]);
            positions.put(at[0]).put(y).put(at[1]);
            for (int corner = 0; corner < 4; corner++) {
                colours.put(1f).put(1f).put(1f).put(at[4]);
            }
            positions.flip();
            colours.flip();
            mesh.getBuffer(VertexBuffer.Type.Position).updateData(positions);
            mesh.getBuffer(VertexBuffer.Type.Color).updateData(colours);
            mesh.updateBound();
        }
    }

    /** Where a wave's front is at {@code frame} — {@code ax, ay, bx, by} — and how clear it is, the fifth. */
    static float[] at(Visuals.WaveLook look, int frame) {
        int cycle = Math.floorMod(frame, Math.max(1, Math.max(look.frames(), look.repeatFrames())));
        float t = Math.min(1f, cycle / (float) Math.max(1, look.frames()));
        float alpha = cycle >= look.frames() ? 0f
                : cycle < look.frames() - look.fadeFrames() ? 1f
                : 1f - (cycle - (look.frames() - look.fadeFrames())) / (float) Math.max(1, look.fadeFrames());
        return new float[] {lerp(look.startA(), look.endA(), t, true), lerp(look.startA(), look.endA(), t, false),
            lerp(look.startB(), look.endB(), t, true), lerp(look.startB(), look.endB(), t, false), alpha};
    }

    private static float lerp(Coord3D from, Coord3D to, float t, boolean x) {
        return x ? from.x() + (to.x() - from.x()) * t : from.y() + (to.y() - from.y()) * t;
    }

    void clear() {
        waves.forEach(wave -> wave.geometry().removeFromParent());
        waves.clear();
    }
}
