package uz.dukeengine.client3d;

import com.jme3.anim.SkinningControl;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The named points of a model an effect is played at — a gun's muzzle, a barrel that recoils, the piece a
 * muzzle flash is drawn with. A piece of the model's own tree by that name, or a joint of its skeleton, whose
 * attachment node follows it.
 *
 * <p>Names are compared ignoring case, as the reference compares its bones': its data writes {@code
 * WeaponFireFXBone = [PRIMARY Muzzle]} and its models call the bone {@code MUZZLE01}.
 */
final class Bones {

    /** How far the reference numbers a model's barrels: {@code NAME01} to {@code NAME99}. */
    static final int MOST_NUMBERED = 99;

    private Bones() {
    }

    /**
     * The model's bones by this name, numbered — {@code NAME01}, {@code NAME02} … to the first missing — or,
     * where none is numbered, {@code NAME} itself: {@code W3DModelDraw::validateWeaponBarrelInfo}'s rule. Empty
     * where there is neither.
     */
    static List<Spatial> numbered(Spatial model, String name) {
        var found = new ArrayList<Spatial>();
        if (model == null || name == null || name.isBlank()) {
            return found;
        }
        for (int number = 1; number <= MOST_NUMBERED; number++) {
            var bone = named(model, numbered(name, number));
            if (bone == null) {
                break;
            }
            found.add(bone);
        }
        if (found.isEmpty()) {
            var bare = named(model, name);
            if (bare != null) {
                found.add(bare);
            }
        }
        return found;
    }

    /** {@code NAME07}: a barrel's bone by its number, as the reference spells one. */
    static String numbered(String name, int number) {
        return String.format(Locale.ROOT, "%s%02d", name, number);
    }

    /** The piece or joint of the model called this, ignoring case, or null. */
    static Spatial named(Spatial model, String name) {
        if (model == null || name == null) {
            return null;
        }
        var piece = piece(model, name);
        return piece != null ? piece : joint(model, name);
    }

    private static Spatial piece(Spatial at, String name) {
        if (name.equalsIgnoreCase(at.getName())) {
            return at;
        }
        if (at instanceof Node node) {
            for (var child : node.getChildren()) {
                var found = piece(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Spatial joint(Spatial model, String name) {
        var skinned = new ArrayList<SkinningControl>();
        model.depthFirstTraversal(spatial -> {
            var control = spatial.getControl(SkinningControl.class);
            if (control != null) {
                skinned.add(control);
            }
        });
        for (var skinning : skinned) {
            for (var joint : skinning.getArmature().getJointList()) {
                if (name.equalsIgnoreCase(joint.getName())) {
                    return skinning.getAttachmentsNode(joint.getName());
                }
            }
        }
        return null;
    }
}
