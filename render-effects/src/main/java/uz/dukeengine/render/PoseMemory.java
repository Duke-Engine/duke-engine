package uz.dukeengine.render;

import com.jme3.math.Matrix4f;
import java.util.IdentityHashMap;
import java.util.Map;

/** What each thing's matrices were last frame. What is not seen for a frame is forgotten, so a game's dead things are not kept. */
final class PoseMemory {

    private static final class Pose {
        Matrix4f[] previous;
        Matrix4f[] current;
        long seen;

        Pose(Matrix4f[] now) {
            previous = copies(now);
            current = copies(now);
        }
    }

    private final Map<Object, Pose> poses = new IdentityHashMap<>();
    private long frame = 1;

    /**
     * Keeps {@code now} for the next frame and answers what {@code owner}'s matrices were last frame: {@code now} itself
     * when it was not seen then. Asked again in a frame, by another piece of one skeleton, it answers the same.
     */
    Matrix4f[] previous(Object owner, Matrix4f[] now) {
        var pose = poses.get(owner);
        if (pose == null || pose.current.length != now.length) {
            pose = new Pose(now);
            poses.put(owner, pose);
            pose.seen = frame;
            return pose.previous;
        }
        if (pose.seen == frame) {
            return pose.previous;
        }
        var spare = pose.previous;
        pose.previous = pose.current;
        pose.current = spare;
        for (int i = 0; i < now.length; i++) {
            spare[i].set(now[i]);
        }
        pose.seen = frame;
        return pose.previous;
    }

    void endFrame() {
        poses.values().removeIf(pose -> pose.seen != frame);
        frame++;
    }

    private static Matrix4f[] copies(Matrix4f[] matrices) {
        var copies = new Matrix4f[matrices.length];
        for (int i = 0; i < matrices.length; i++) {
            copies[i] = matrices[i].clone();
        }
        return copies;
    }
}
