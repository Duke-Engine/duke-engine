package uz.dukeengine.client3d;

import com.jme3.anim.SkinningControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;

/** A rider hung on a mount's bone — see {@code GameObject.rideOn}: its look moves with the bone, the mount's clips its. */
final class Riders {

    private Riders() {
    }

    /** The node a bone of {@code body}'s skeleton carries what hangs on it by, its name matched in either case; or null. */
    static Node boneNode(Spatial body, String bone) {
        if (body == null || bone == null) {
            return null;
        }
        var skinned = new ArrayList<SkinningControl>();
        body.depthFirstTraversal(spatial -> {
            var control = spatial.getControl(SkinningControl.class);
            if (control != null) {
                skinned.add(control);
            }
        });
        for (var skinning : skinned) {
            for (var joint : skinning.getArmature().getJointList()) {
                if (bone.equalsIgnoreCase(joint.getName())) {
                    return skinning.getAttachmentsNode(joint.getName());
                }
            }
        }
        return null;
    }

    /**
     * {@code rider} seated on {@code bone} of {@code mountBody}, {@code offset} from it (the world's x, height, y):
     * moved under the bone's node where it is not there yet. False — and nothing moved — where the mount has no such
     * bone.
     */
    static boolean seat(Node rider, Spatial mountBody, String bone, Vector3f offset) {
        var on = boneNode(mountBody, bone);
        if (on == null) {
            return false;
        }
        if (rider.getParent() != on) {
            on.attachChild(rider);
        }
        rider.setLocalTranslation(offset);
        rider.setLocalRotation(Quaternion.IDENTITY);
        return true;
    }
}
