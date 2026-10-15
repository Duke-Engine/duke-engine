package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;

/** A rider hung on a mount's bone, and heard by the look he is drawn as (E88). */
class RiderTest {

    @Test
    void theRidersNodeHangsUnderTheMountsBone() {
        var root = new Joint("root");
        var back = new Joint("Spine_Back");
        root.addChild(back);
        var horse = new Node("horse");
        horse.addControl(new SkinningControl(new Armature(new Joint[] {root, back})));
        var rider = new Node("knight");

        assertTrue(Riders.seat(rider, horse, "spine_back", new Vector3f(0f, 1.5f, 0f)), "the bone, in either case");
        assertSame(Riders.boneNode(horse, "Spine_Back"), rider.getParent(), "hung under the mount's bone");
        assertEquals(1.5f, rider.getLocalTranslation().y, 1e-6f);
        assertFalse(Riders.seat(new Node("other"), horse, "no_such_bone", Vector3f.ZERO), "a bone it has not");
    }

    @Test
    void aRidingLooksFootstepsArePlayedOverHisOwn() {
        var heard = new java.util.ArrayList<String>();
        var bank = SoundBank.create()
                .cue("walking.KnightRiding", SoundBank.Channel.EFFECTS, false, 1f, 0f, List.of("audio/sfx/hooves.ogg"))
                .cue("walking.Knight", SoundBank.Channel.EFFECTS, false, 1f, 0f, List.of("audio/sfx/steps.ogg"))
                .build();
        var noises = new GameSounds(new Sounds(bank, new SoundSink() {
            @Override
            public void play(String assetPath, float gain, Vector3f at) {
                heard.add(assetPath);
            }

            @Override
            public Playing loop(String assetPath, float gain, Vector3f at) {
                return Playing.NONE;
            }

            @Override
            public Playing music(String assetPath, float gain) {
                return Playing.NONE;
            }
        }), 5f);
        noises.frame(view(1, 10f, false), 1, 0f);
        noises.frame(view(2, 20f, true), 1, 1f);
        assertEquals("audio/sfx/hooves.ogg", heard.getLast(), "hooves, not his own steps");
    }

    private static WorldSnapshot view(int frame, float x, boolean moving) {
        var knight = new UnitView(3, "Knight", 1, x, 20f, 0f, 100f, 100f, false, true, moving, false, -1);
        var riding = new UnitView(knight.id(), knight.templateName(), knight.playerIndex(), knight.x(), knight.y(),
                knight.orientation(), knight.health(), knight.maxHealth(), knight.structure(), knight.selectable(),
                knight.moving(), knight.attacking(), knight.productionQueue(), knight.z(), knight.pitch(),
                knight.roll(), knight.ownHeight(), knight.statuses(), knight.passengers(), knight.conditions(),
                knight.built(), knight.ridesOn(), knight.allied(), knight.span(), knight.mobile(), "KnightRiding",
                knight.wears(), knight.opacity(), knight.hostile(), knight.speed(), knight.remembered(),
                knight.turrets(), knight.lift(), knight.yaw(), knight.corners());
        return new WorldSnapshot(frame, 0f, false, 0, 0, List.of(riding), List.of(), "", "", List.of(), true);
    }
}
