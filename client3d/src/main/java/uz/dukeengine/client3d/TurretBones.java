package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.game.view.Turrets;

/**
 * Turrets drawn turned and pitched as the simulation has them — the reference's {@code
 * W3DModelDraw::handleClientTurretPositioning}: each frame a turret's bone turned about the thing's up by the turret's
 * turn and its fixed angle, and its pitch bone about the thing's side by the pitch and its own, each from the pose the
 * file gives it. The bones the words it holds choose, found again for a new model or a new choice; nothing of it
 * reaches the simulation.
 */
final class TurretBones {

    /** The thing's up and its right, as the client draws a model: its forward x, its up y. */
    private static final Vector3f UP = Vector3f.UNIT_Y;
    private static final Vector3f RIGHT = Vector3f.UNIT_Z;

    /** A bone laid: the turret it follows, whether it pitches, its pose in the file, and its axis in its own frame. */
    private record Laid(int slot, boolean pitches, Spatial bone, Quaternion rest, Vector3f axis, float art) {
    }

    private final Map<Integer, List<Laid>> laid = new HashMap<>();

    /**
     * A thing's model, or the turrets its words choose, is new: whatever it turned stands as the file has it again, and
     * the bones {@code looks} names — by turret — are found on {@code model}.
     */
    void dress(int thing, Spatial model, Map<Integer, Visuals.TurretLook> looks) {
        forget(thing);
        if (model == null || looks == null || looks.isEmpty()) {
            return;
        }
        var bones = new ArrayList<Laid>();
        looks.forEach((slot, look) -> {
            add(bones, model, slot, false, look.turn(), look.artAngle());
            add(bones, model, slot, true, look.pitch(), look.artPitch());
        });
        if (!bones.isEmpty()) {
            laid.put(thing, bones);
        }
    }

    private static void add(List<Laid> bones, Spatial model, int slot, boolean pitches, String name,
            float artDegrees) {
        var bone = name == null ? null : Bones.named(model, name);
        if (bone == null) {
            return;
        }
        var intoBone = turnInModel(bone, model).inverse();
        bones.add(new Laid(slot, pitches, bone, bone.getLocalRotation().clone(),
                intoBone.mult(pitches ? RIGHT : UP).normalizeLocal(), artDegrees * FastMath.DEG_TO_RAD));
    }

    /**
     * Its turrets as they stand: each turning bone turned the way a thing turns — its growing facing toward the
     * client's z, a turn about the up the other way round — and each pitch bone raised toward the up.
     */
    void turn(int thing, Turrets turrets) {
        var bones = laid.get(thing);
        if (bones == null) {
            return;
        }
        var by = turrets == null ? Turrets.NONE : turrets;
        for (var bone : bones) {
            float angle = bone.pitches() ? by.pitch(bone.slot()) + bone.art() : -(by.turn(bone.slot()) + bone.art());
            bone.bone().setLocalRotation(bone.rest().mult(new Quaternion().fromAngleNormalAxis(angle, bone.axis())));
        }
    }

    /** A thing gone, or dressed again: what it turned stands as the file has it. */
    void forget(int thing) {
        var bones = laid.remove(thing);
        if (bones != null) {
            for (var bone : bones) {
                bone.bone().setLocalRotation(bone.rest());
            }
        }
    }

    /** A bone's turn in the frame the model hangs in: every turn from the model down to it. */
    private static Quaternion turnInModel(Spatial bone, Spatial model) {
        var turn = new Quaternion();
        for (var at = bone; at != null; at = at == model ? null : at.getParent()) {
            turn = at.getLocalRotation().mult(turn);
        }
        return turn;
    }
}
