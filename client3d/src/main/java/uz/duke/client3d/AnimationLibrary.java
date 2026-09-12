package uz.duke.client3d;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.TransformTrack;
import com.jme3.math.Vector3f;
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

    /**
     * Take the one animation in {@code library} and put it on {@code target} under
     * a name of our choosing.
     *
     * <p>For libraries that are a single animation per file, which is how most
     * animation sites hand them out — and they all arrive carrying the same
     * exporter-generated name, so the file is the only thing that says which is
     * the walk and which is the punch. Naming it here is what lets three
     * identically-named clips live on one character.
     *
     * @return whether it arrived; false if the file holds no animation, or more
     *         than one, in which case there is nothing for a single name to mean
     */
    public static boolean copySingle(Spatial library, Spatial target, String nameToGive) {
        var source = findControl(library, AnimComposer.class);
        var destination = findControl(target, AnimComposer.class);
        var skin = findControl(target, SkinningControl.class);
        if (source == null || destination == null || skin == null || nameToGive == null) {
            return false;
        }
        var names = source.getAnimClipsNames();
        if (names.size() != 1) {
            return false;
        }
        var retargeted = retarget(source.getAnimClip(names.iterator().next()), skin.getArmature());
        if (retargeted == null) {
            return false;
        }
        var named = new AnimClip(nameToGive);
        named.setTracks(retargeted.getTracks());
        var taken = destination.getAnimClip(nameToGive);
        if (taken != null) {
            destination.removeAnimClip(taken); // asked for twice; the last one wins
        }
        destination.addAnimClip(named);
        return true;
    }

    /** The same clip, driving {@code armature}'s joints instead of its own. */
    private static AnimClip retarget(AnimClip clip, Armature armature) {
        var tracks = new ArrayList<AnimTrack<?>>();
        for (AnimTrack<?> track : clip.getTracks()) {
            if (!(track instanceof TransformTrack transform)) {
                continue; // morph and other tracks have no bone to match
            }
            var name = nameOfTarget(transform.getTarget());
            if (name == null) {
                continue;
            }
            var mine = armature.getJoint(name);
            if (mine == null) {
                continue; // a joint this creature was not modelled with
            }
            tracks.add(new TransformTrack(mine, transform.getTimes(),
                    heldInPlace(mine, transform.getTranslations()),
                    facingIsTheGames(mine, transform.getRotations()),
                    transform.getScales()));
        }
        if (tracks.isEmpty()) {
            return null; // nothing in common: a different skeleton altogether
        }
        var copy = new AnimClip(clip.getName());
        copy.setTracks(tracks.toArray(new AnimTrack<?>[0]));
        return copy;
    }

    /**
     * The same movement with the walking taken out of it.
     *
     * <p>Animations come in two kinds, and the difference is invisible in a file
     * listing. "In place" ones run on the spot; the other kind carries <b>root
     * motion</b> — the whole skeleton is authored travelling forward, because in
     * some engines the animation is what moves the character.
     *
     * <p>Here it is not: the simulation owns every position, and a clip that also
     * moves the model makes it drift out of its own unit. What that looks like is
     * a hero who walks away from his own selection ring and health bar, since
     * those sit on the unit while the mesh wanders off — and it is not obviously
     * an animation problem at all.
     *
     * <p>So the root joint's horizontal travel is dropped and its vertical kept:
     * a run still rises and falls on each stride, it simply does it here. Only the
     * root is touched, since every other joint moves relative to it.
     */
    private static Vector3f[] heldInPlace(Joint joint, Vector3f[] translations) {
        if (translations == null || joint.getParent() != null) {
            return translations;
        }
        var held = new Vector3f[translations.length];
        for (int at = 0; at < translations.length; at++) {
            held[at] = new Vector3f(0f, translations[at].y, 0f);
        }
        return held;
    }

    /**
     * The same movement with the turning taken off the root.
     *
     * <p>The other half of {@link #heldInPlace}, and the same rule: the simulation
     * owns where a creature is <em>and which way it faces</em>, so a clip may move
     * the body and may not move the unit. Only the root is touched, and the root
     * in these rigs is a placement bone — every clip that means to turn a
     * character turns its hips, which is why taking this off changes nothing in
     * any of the libraries the game already ships. Theirs is the identity.
     *
     * <p><b>What it is actually for is imported animation.</b> FBX is authored
     * Z-up and glTF is Y-up, and the conversion has to land somewhere: Blender's
     * exporter writes it onto the root as a quarter turn about X, constant across
     * every key, whatever the armature itself says. Retargeted onto a model whose
     * own root is the identity, that quarter turn is not a conversion any more —
     * it is an instruction, and the instruction is <em>lie down</em>.
     *
     * <p>Measured rather than guessed at: the imported clip's root carried three
     * keys of −90° about X and never varied, while every KayKit clip carries two
     * keys of nothing. A constant is a placement, and placements are the game's.
     */
    private static com.jme3.math.Quaternion[] facingIsTheGames(Joint joint,
            com.jme3.math.Quaternion[] rotations) {
        if (rotations == null || joint.getParent() != null) {
            return rotations;
        }
        var still = new com.jme3.math.Quaternion[rotations.length];
        java.util.Arrays.fill(still, new com.jme3.math.Quaternion());
        return still;
    }

    /**
     * What a track calls the thing it drives, whether that is a joint or a node.
     *
     * <p>Both shapes turn up and the difference is not a detail. A library that
     * ships a creature <em>with</em> its animations has a skinned mesh, so a
     * loader builds a skeleton and the tracks drive {@code Joint}s. A library that
     * ships animation <em>alone</em> — which is the small, cheap kind, and the
     * kind most sites hand out — carries no mesh, so there is no skin, so there is
     * no skeleton, and the very same bones arrive as ordinary nodes.
     *
     * <p>Matching only joints therefore finds nothing in half the files anyone
     * would use, without saying so: the clip is copied, it holds no tracks, and
     * the character stands still.
     */
    private static String nameOfTarget(Object target) {
        return switch (target) {
            case Joint joint -> joint.getName();
            case Spatial spatial -> spatial.getName();
            default -> null;
        };
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
