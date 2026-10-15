package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.view.UnitView;

/**
 * The bones a game drives, each drawn at the pose its {@link BoneDrive} gives, eased toward it over the frames the game
 * names; a model naming no such bone drawn as the file has it. Nothing of it reaches the simulation.
 */
final class DrivenBones {

    /** A driven bone laid on a model: its pose in the file, and where its ease has got to. */
    private static final class Laid {
        final Visuals.DrivenBone drive;
        final Spatial bone;
        final Quaternion rest;
        final Vector3f restAt;
        final Quaternion intoBone;
        float degrees;
        float dx;
        float dy;
        float dz;

        Laid(Visuals.DrivenBone drive, Spatial bone, Quaternion intoBone) {
            this.drive = drive;
            this.bone = bone;
            this.rest = bone.getLocalRotation().clone();
            this.restAt = bone.getLocalTranslation().clone();
            this.intoBone = intoBone;
        }
    }

    private record Thing(Spatial model, List<Laid> bones, float facing, float time) {
    }

    private final Map<Integer, Thing> things = new HashMap<>();

    /** Each of its driven bones posed for {@code view}, {@code now} seconds into the game's picture. */
    void drive(int id, Spatial model, List<Visuals.DrivenBone> drives, UnitView view, float now) {
        if (model == null || drives.isEmpty()) {
            return;
        }
        var was = things.get(id);
        if (was == null || was.model() != model) {
            forget(id);
            was = new Thing(model, lay(model, drives), view.orientation(), now);
        }
        float seconds = Math.max(1e-3f, now - was.time());
        float turned = FastMath.normalize(view.orientation() - was.facing(), -FastMath.PI, FastMath.PI);
        var input = new BoneDrive.Input(view, now == was.time() ? 0f : turned * FastMath.RAD_TO_DEG / seconds);
        for (var laid : was.bones()) {
            var pose = laid.drive.drive().pose(input);
            pose = pose == null ? BoneDrive.Pose.REST : pose;
            float share = Math.min(1f, seconds * 30f / Math.max(1, laid.drive.easeFrames()));
            laid.degrees += (pose.degrees() - laid.degrees) * share;
            laid.dx += (pose.dx() - laid.dx) * share;
            laid.dy += (pose.dy() - laid.dy) * share;
            laid.dz += (pose.dz() - laid.dz) * share;
            var axis = laid.intoBone.mult(new Vector3f(pose.axisX(), pose.axisY(), pose.axisZ()));
            if (axis.lengthSquared() > 0f) {
                laid.bone.setLocalRotation(laid.rest.mult(new Quaternion()
                        .fromAngleNormalAxis(laid.degrees * FastMath.DEG_TO_RAD, axis.normalizeLocal())));
            }
            laid.bone.setLocalTranslation(laid.restAt.add(laid.dx, laid.dy, laid.dz));
            laid.bone.setCullHint(pose.visible() ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
        things.put(id, new Thing(model, was.bones(), view.orientation(), now));
    }

    /** A thing gone: what it drove stands as the file has it. */
    void forget(int id) {
        var was = things.remove(id);
        if (was == null) {
            return;
        }
        for (var laid : was.bones()) {
            laid.bone.setLocalRotation(laid.rest);
            laid.bone.setLocalTranslation(laid.restAt);
            laid.bone.setCullHint(Spatial.CullHint.Inherit);
        }
    }

    private static List<Laid> lay(Spatial model, List<Visuals.DrivenBone> drives) {
        var laid = new ArrayList<Laid>();
        for (var drive : drives) {
            var bone = Bones.named(model, drive.bone());
            if (bone != null) {
                laid.add(new Laid(drive, bone, turnInModel(bone, model).inverse()));
            }
        }
        return laid;
    }

    private static Quaternion turnInModel(Spatial bone, Spatial model) {
        var turn = new Quaternion();
        for (var at = bone; at != null; at = at == model ? null : at.getParent()) {
            turn = at.getLocalRotation().mult(turn);
        }
        return turn;
    }
}
