package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.EffectList;

/** A thrown piece heard striking the ground, each time where it strikes, and its list played where it first lands. */
class ThrownPieceTest {

    private record Played(String name, Vector3f at) {
    }

    @Test
    void aPieceWithABounceSoundPlaysItAtEachBounceAtTheBounce() {
        var sounds = new ArrayList<Played>();
        var lists = new ArrayList<Played>();
        var show = new ListShow(null, new Node(), null, (cue, at) -> sounds.add(new Played(cue, at)),
                () -> Vector3f.ZERO, (x, z) -> 0f, (model, piece) -> new Node("piece"),
                (name, at) -> lists.add(new Played(name, at)), null);
        var debris = new EffectList.Debris("models/plank.glb", null, 1, List.of(), List.of(), List.of(), 1f, 0.5f,
                List.of(200f), 0, 0f, false, "PlankBounce", null, List.of(), "PlankLanded", false);
        var thrown = new Thrown(new Vector3f(10f, 0f, 20f), new Vector3f(1f, 10f, 0f), Vector3f.UNIT_Y, 0f, 1f, 0.5f,
                200, 0);
        show.debris(debris, null, null, thrown, null);

        var strikes = new ArrayList<Played>();
        for (int frame = 0; frame < 100; frame++) {
            show.frame();
            if (thrown.struck()) {
                strikes.add(new Played("PlankBounce", thrown.at().clone()));
            }
        }

        assertTrue(strikes.size() >= 2, "it bounced: " + strikes.size());
        assertEquals(strikes, sounds, "heard at every strike, where it struck");
        for (var sound : sounds) {
            assertEquals(0f, sound.at().y, "on the ground");
        }
        assertEquals(List.of(new Played("PlankLanded", strikes.getFirst().at())), lists,
                "and its list played once, where it first came down");
    }
}
