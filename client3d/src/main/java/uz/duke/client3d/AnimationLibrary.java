package uz.duke.client3d;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.TransformTrack;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Puts animations from one file onto a model in another.
 *
 * <p>Model kits and animation libraries are usually sold apart: the creature has
 * a skeleton and no movement, the library has movement and no creature. As long
 * as both were built on the same skeleton — and whole families of kits are, on
 * the standard humanoid rig — one library animates every creature in the kit.
 *
 * <p><b>A clip cannot simply be handed over.</b> Its tracks hold direct references
 * to the joints they drive, so a clip taken from the library and added to a
 * creature goes on animating <em>the library's</em> invisible skeleton. The
 * creature stands still, nothing is logged, and nothing looks wrong except that
 * it does not move. Each track therefore has to be rebuilt against the joint of
 * the same name in the target — which is all "retargeting" means here.
 *
 * <p>Joints the target does not have are dropped: a library rig often carries a
 * finger or a toe tip a particular creature was not modelled with, and a track
 * for a joint that is not there is simply nothing to apply.
 *
 * <p>The sample arrays are shared, not copied. A hundred monsters on a floor then
 * cost a hundred small track objects rather than a hundred copies of every
 * keyframe in the clip.
 */
public final class AnimationLibrary {

    private AnimationLibrary() {
    }

    /**
     * Copy the named clips from {@code library} onto {@code target}, retargeting
     * each to the target's own joints.
     *
     * @return how many clips arrived — 0 means the two skeletons share no joints,
     *         which is the failure worth noticing
     */
    public static int copy(Spatial library, Spatial target, Collection<String> clipNames) {
        var source = findControl(library, AnimComposer.class);
        var destination = findControl(target, AnimComposer.class);
        var skin = findControl(target, SkinningControl.class);
        if (source == null || destination == null || skin == null) {
            return 0;
        }
        var armature = skin.getArmature();
        int copied = 0;
        for (var name : clipNames) {
            if (name == null || destination.getAnimClip(name) != null) {
                continue;
            }
            var clip = source.getAnimClip(name);
            if (clip == null) {
                continue;
            }
            var retargeted = retarget(clip, armature);
            if (retargeted != null) {
                destination.addAnimClip(retargeted);
                copied++;
            }
        }
        return copied;
    }

    /** The same clip, driving {@code armature}'s joints instead of its own. */
    private static AnimClip retarget(AnimClip clip, Armature armature) {
        var tracks = new ArrayList<AnimTrack<?>>();
        for (AnimTrack<?> track : clip.getTracks()) {
            if (!(track instanceof TransformTrack transform)
                    || !(transform.getTarget() instanceof Joint joint)) {
                continue; // morph and other tracks have no joint to match
            }
            var mine = armature.getJoint(joint.getName());
            if (mine == null) {
                continue; // a joint this creature was not modelled with
            }
            tracks.add(new TransformTrack(mine, transform.getTimes(),
                    transform.getTranslations(), transform.getRotations(),
                    transform.getScales()));
        }
        if (tracks.isEmpty()) {
            return null; // nothing in common: a different skeleton altogether
        }
        var copy = new AnimClip(clip.getName());
        copy.setTracks(tracks.toArray(new AnimTrack<?>[0]));
        return copy;
    }

    /** The first control of this type anywhere in the model. */
    static <T extends Control> T findControl(Spatial spatial, Class<T> type) {
        if (spatial == null) {
            return null;
        }
        var control = spatial.getControl(type);
        if (control != null) {
            return control;
        }
        if (spatial instanceof Node node) {
            for (var child : node.getChildren()) {
                var found = findControl(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
