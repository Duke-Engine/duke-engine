package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.game.view.UnitView;

/**
 * A vehicle's running gear as it moves: treads whose picture runs and wheels that roll and steer — the reference's
 * {@code W3DTankDraw} and {@code W3DTruckDraw}. Worked out from where the thing stands in each new frame of the game,
 * so it is the same in every client, stands still while the game does, and nothing of it reaches the simulation.
 *
 * <p><b>Treads</b> ({@code doDrawModule}, {@code updateTreadPositions}): turning while slower than {@code
 * pivotFraction} of its speed, the two sides run opposite ways; faster than {@code driveFraction} of it, every tread
 * runs with it at {@code rate} — a rate and not the speed, as the reference found a tread tied to the speed looked
 * odd; slower, or standing, they stand. The picture runs along u, as the reference's {@code customUVOffset.X}. The
 * speed it is a share of is the fastest it has been seen to go, the client not being told its locomotor's.
 *
 * <p><b>Wheels</b>: each rolls {@code multiplier} radians a unit it travels, backwards when it goes backwards; the
 * front ones turn toward a turn, a tenth of the way each frame ({@code WHEEL_SMOOTHNESS}, {@code
 * Drawable::calcPhysicsXformWheels}). A wheel rolls about the vehicle's own side and steers about its up, found in
 * each bone's own frame, so it rolls true however the model's bones were drawn — the reference's bone y is the
 * converted model's z.
 */
final class RunningGear {

    /** The reference's test for a turn: the facing's cosine moved by more than this. */
    private static final float TURNING = 0.00001f;
    /** {@code WHEEL_SMOOTHNESS}: a front wheel turns a tenth of the way to its angle a frame. */
    private static final float WHEEL_SMOOTHNESS = 10f;
    /** The vehicle's left as the client draws it — its forward x, its up y — about which a wheel rolls forward. */
    private static final Vector3f LEFT = new Vector3f(0f, 0f, -1f);

    private enum Side { LEFT, RIGHT, MIDDLE }

    private record Tread(Geometry piece, float[] uvs, int stride, Side side) {
    }

    private record Wheel(Spatial bone, Quaternion rest, Vector3f axle, Vector3f up, boolean front) {
    }

    private record Dressed(List<Tread> treads, List<Wheel> wheels, Visuals.Treads treadsLook,
            Visuals.Wheels wheelsLook) {
    }

    /** Where a thing was last seen and how its gear stands: the thing's, kept through a swapped model. */
    private static final class Motion {
        int frame;
        float x;
        float y;
        float orientation;
        float fastest;
        float left;
        float right;
        float middle;
        float roll;
        float steer;

        Motion(int frame, UnitView view) {
            this.frame = frame;
            this.x = view.x();
            this.y = view.y();
            this.orientation = view.orientation();
        }
    }

    private final Map<Integer, Dressed> dressed = new HashMap<>();
    private final Map<Integer, Motion> motions = new HashMap<>();

    /**
     * A thing's model is new — first drawn, or swapped for another: find its treads and wheels, and lay on them the
     * gear as it stood. A tread's mesh is made its own first, a model's meshes being shared by every copy of it.
     */
    void dress(int thing, Spatial model, Visuals.UnitVisual visual) {
        if (model == null || visual == null || visual.treads == null && visual.wheels == null) {
            dressed.remove(thing);
            return;
        }
        var parts = new Dressed(visual.treads == null ? List.of() : treads(model, visual.treads),
                visual.wheels == null ? List.of() : wheels(model, visual.wheels), visual.treads, visual.wheels);
        dressed.put(thing, parts);
        var motion = motions.get(thing);
        if (motion != null) {
            lay(parts, motion);
        }
    }

    /** It is seen in the game's frame {@code frame}: how far it went and turned since it was last seen, worn. */
    void see(int thing, UnitView view, int frame) {
        var parts = dressed.get(thing);
        if (parts == null) {
            return;
        }
        var motion = motions.get(thing);
        if (motion == null) {
            motions.put(thing, new Motion(frame, view)); // first seen: where it goes from
            return;
        }
        int frames = frame - motion.frame;
        if (frames <= 0) {
            return; // no new frame of the game: the gear stands as it stood
        }
        float dx = view.x() - motion.x;
        float dy = view.y() - motion.y;
        float turn = FastMath.normalize(view.orientation() - motion.orientation, -FastMath.PI, FastMath.PI);
        motion.frame = frame;
        motion.x = view.x();
        motion.y = view.y();
        motion.orientation = view.orientation();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        boolean backwards = dx * Math.cos(view.orientation()) + dy * Math.sin(view.orientation()) < 0;
        step(motion, parts.treadsLook(), parts.wheelsLook(), backwards ? -distance : distance, turn, frames);
        lay(parts, motion);
    }

    /**
     * {@code frames} of the game in which it went {@code travelled} along its facing — less than none backwards —
     * and turned {@code turn}: a growing facing turns it to its right, as the client draws the simulation's y as
     * its z.
     */
    private static void step(Motion motion, Visuals.Treads treads, Visuals.Wheels wheels, float travelled, float turn,
            int frames) {
        float speed = Math.abs(travelled) / frames;
        motion.fastest = Math.max(motion.fastest, speed);
        boolean turning = 1f - (float) Math.cos(turn) > TURNING;
        if (treads != null) {
            float share = motion.fastest > 0f ? speed / motion.fastest : 0f;
            float run = treads.rate() * GameConstants.SECONDS_PER_LOGICFRAME * frames;
            if (turning && share < treads.pivotFraction()) {
                float left = turn > 0f ? run : -run; // turning right, the left runs forward and the right back
                motion.left = wrap(motion.left - left);
                motion.right = wrap(motion.right + left);
            } else if (speed > 0f && share >= treads.driveFraction()) {
                float all = travelled < 0f ? -run : run;
                motion.left = wrap(motion.left - all);
                motion.right = wrap(motion.right - all);
                motion.middle = wrap(motion.middle - all);
            }
        }
        if (wheels != null) {
            motion.roll = (motion.roll + wheels.multiplier() * travelled) % FastMath.TWO_PI;
            float toward = !turning ? 0f : turn > 0f ? -wheels.steer() : wheels.steer();
            if (travelled < 0f) {
                toward = -toward; // backing, the front wheels turn the other way for the same turn
            }
            float kept = (float) Math.pow(1f - 1f / WHEEL_SMOOTHNESS, frames);
            motion.steer = toward + (motion.steer - toward) * kept;
        }
    }

    private static float wrap(float offset) {
        return offset - (float) Math.floor(offset);
    }

    /** The gear as it stands, on the model: each tread's picture run on, each wheel rolled and steered. */
    private static void lay(Dressed parts, Motion motion) {
        for (var tread : parts.treads()) {
            float offset = switch (tread.side()) {
                case LEFT -> motion.left;
                case RIGHT -> motion.right;
                case MIDDLE -> motion.middle;
            };
            var mesh = tread.piece().getMesh();
            var buffer = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord);
            for (int at = 0; at < tread.uvs().length; at += tread.stride()) {
                buffer.put(at, tread.uvs()[at] + offset);
            }
            mesh.getBuffer(VertexBuffer.Type.TexCoord).setUpdateNeeded();
        }
        for (var wheel : parts.wheels()) {
            var turned = wheel.rest().clone();
            if (wheel.front()) {
                turned.multLocal(new Quaternion().fromAngleNormalAxis(motion.steer, wheel.up()));
            }
            turned.multLocal(new Quaternion().fromAngleNormalAxis(motion.roll, wheel.axle()));
            wheel.bone().setLocalRotation(turned);
        }
    }

    /**
     * Every piece of the model a tread: named — or under a piece named — with a name beginning the left's or the
     * right's, ignoring case and the container; one both begin is neither side's.
     */
    private static List<Tread> treads(Spatial model, Visuals.Treads look) {
        var found = new ArrayList<Tread>();
        String left = look.left() == null ? null : Pieces.bare(look.left());
        String right = look.right() == null ? null : Pieces.bare(look.right());
        model.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry piece) || piece.getMesh() == null) {
                return;
            }
            var side = sideOf(piece, model, left, right);
            var uvs = piece.getMesh().getBuffer(VertexBuffer.Type.TexCoord);
            if (side == null || uvs == null) {
                return;
            }
            piece.setMesh(piece.getMesh().deepClone());
            var buffer = piece.getMesh().getFloatBuffer(VertexBuffer.Type.TexCoord);
            var original = new float[buffer.limit()];
            buffer.get(0, original);
            found.add(new Tread(piece, original, uvs.getNumComponents(), side));
        });
        return found;
    }

    private static Side sideOf(Spatial piece, Spatial model, String left, String right) {
        for (var at = piece; at != null; at = at == model ? null : at.getParent()) {
            if (at.getName() == null) {
                continue;
            }
            var name = Pieces.bare(at.getName());
            boolean isLeft = left != null && name.startsWith(left);
            boolean isRight = right != null && name.startsWith(right);
            if (isLeft || isRight) {
                return isLeft && isRight ? Side.MIDDLE : isLeft ? Side.LEFT : Side.RIGHT;
            }
        }
        return null;
    }

    /** Its wheel bones, each with the vehicle's side and up in the bone's own frame. */
    private static List<Wheel> wheels(Spatial model, Visuals.Wheels look) {
        var front = new LinkedHashMap<String, Boolean>();
        look.bones().forEach(bone -> front.putIfAbsent(bone, false));
        look.front().forEach(bone -> front.put(bone, true));
        var found = new ArrayList<Wheel>();
        front.forEach((name, steers) -> {
            var bone = Bones.named(model, name);
            if (bone == null) {
                return;
            }
            var intoBone = turnInModel(bone, model).inverse();
            found.add(new Wheel(bone, bone.getLocalRotation().clone(), intoBone.mult(LEFT).normalizeLocal(),
                    intoBone.mult(Vector3f.UNIT_Y).normalizeLocal(), steers));
        });
        return found;
    }

    /** A bone's turn in the frame the model hangs in: every turn from the model down to it, the model's facing too. */
    private static Quaternion turnInModel(Spatial bone, Spatial model) {
        var turn = new Quaternion();
        for (var at = bone; at != null; at = at == model ? null : at.getParent()) {
            turn = at.getLocalRotation().mult(turn);
        }
        return turn;
    }

    /** A thing gone: its gear is forgotten. */
    void forget(int thing) {
        dressed.remove(thing);
        motions.remove(thing);
    }

    /** Every thing forgotten, for a new world. */
    void clear() {
        dressed.clear();
        motions.clear();
    }
}
