package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.TransformTrack;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;

/** A blow on the move drawn from the spine up: the legs keep walking, the arms swing (E70). */
class UpperBodyTest {

    private static AnimClip clip(String name, Joint arm, Joint leg, float armX, float legX) {
        var clip = new AnimClip(name);
        clip.setTracks(new AnimTrack[] {track(arm, armX), track(leg, legX)});
        return clip;
    }

    private static TransformTrack track(Joint joint, float x) {
        var at = new Vector3f(x, 0f, 0f);
        return new TransformTrack(joint, new float[] {0f, 1f}, new Vector3f[] {at, at},
                new Quaternion[] {Quaternion.IDENTITY, Quaternion.IDENTITY},
                new Vector3f[] {Vector3f.UNIT_XYZ, Vector3f.UNIT_XYZ});
    }

    @Test
    void anAttackOnTheMoveMovesTheArmsAndLeavesTheLegsToTheWalk() {
        var root = new Joint("root");
        var spine = new Joint("spine");
        var arm = new Joint("arm");
        var leg = new Joint("leg");
        root.addChild(spine);
        spine.addChild(arm);
        root.addChild(leg);
        var armature = new Armature(new Joint[] {root, spine, arm, leg});
        var composer = new AnimComposer();
        composer.addAnimClip(clip("walk", arm, leg, 1f, 1f));
        composer.addAnimClip(clip("attack", arm, leg, 5f, 9f));
        new Node("knight").addControl(composer);

        composer.setCurrentAction("walk");
        assertTrue(UpperBody.play(composer, armature, "spine", "attack"));
        for (int step = 0; step < 6; step++) {
            composer.update(0.1f); // eased in, as every action a composer is handed is
        }

        assertEquals(5f, arm.getLocalTranslation().x, 1e-4f, "the arm swings");
        assertEquals(1f, leg.getLocalTranslation().x, 1e-4f, "the leg walks on");
        assertFalse(UpperBody.play(composer, armature, "no_such_joint", "attack"), "a joint it has not: nothing");
    }

    /** A busy clip that begins cuts off a swing under way, its upper-body layer too; one going on does not (E72). */
    @Test
    void aFlinchBegunMidSwingTakesTheBody() {
        assertTrue(DukeRtsApp.cutsIn(null, "Hurt"), "a stagger begins");
        assertFalse(DukeRtsApp.cutsIn("Hurt", "Hurt"), "the same one going on");
        assertFalse(DukeRtsApp.cutsIn("Hurt", null), "none");

        var root = new Joint("root");
        var spine = new Joint("spine");
        var arm = new Joint("arm");
        var leg = new Joint("leg");
        root.addChild(spine);
        spine.addChild(arm);
        root.addChild(leg);
        var armature = new Armature(new Joint[] {root, spine, arm, leg});
        var composer = new AnimComposer();
        composer.addAnimClip(clip("walk", arm, leg, 1f, 1f));
        composer.addAnimClip(clip("attack", arm, leg, 5f, 9f));
        new Node("knight").addControl(composer);
        composer.setCurrentAction("walk");
        UpperBody.play(composer, armature, "spine", "attack");
        UpperBody.end(composer);
        assertEquals(null, composer.getCurrentAction(UpperBody.LAYER), "the swing above is gone");
    }
}
