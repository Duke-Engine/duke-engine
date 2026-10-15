package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.Armature;
import com.jme3.anim.ArmatureMask;

/** A clip played from one joint up on a layer of its own, whatever plays below it going on: a swing on the move. */
final class UpperBody {

    /** The composer layer the upper body plays on. */
    static final String LAYER = "upper-body";

    private UpperBody() {
    }

    /**
     * Play {@code clip} once from {@code joint} up, on a layer masked to it made the first time; false — and nothing
     * played — where the armature has no such joint or the composer no such clip.
     */
    /** Whatever the upper body plays, ended: the legs' layer has the whole body again. */
    static void end(AnimComposer composer) {
        if (composer != null && composer.getLayerNames().contains(LAYER)) {
            composer.removeCurrentAction(LAYER);
        }
    }

    static boolean play(AnimComposer composer, Armature armature, String joint, String clip) {
        if (armature == null || armature.getJoint(joint) == null || composer.getAnimClip(clip) == null) {
            return false;
        }
        if (!composer.getLayerNames().contains(LAYER)) {
            composer.makeLayer(LAYER, ArmatureMask.createMask(armature, joint));
        }
        composer.setCurrentAction(clip, LAYER, false);
        return true;
    }
}
