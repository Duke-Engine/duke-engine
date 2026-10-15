package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Round 21: a box picks things where they are drawn; the force-attack key's pointers; sharper slanted pictures. */
class ForcedAndSharperTest {

    @Test
    void aBoxTakesAThingWhereItIsDrawnNotAtTheFootOfTheWorld() {
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(45f, 1600f / 900f, 1f, 2000f);
        camera.setLocation(new Vector3f(100f, 260f, 320f));
        camera.lookAt(new Vector3f(100f, 40f, 100f), Vector3f.UNIT_Y);
        camera.update();
        // A tank on a hill: its body drawn round (100, 50, 100), on ground 40 high.
        var body = new BoundingBox(new Vector3f(100f, 50f, 100f), 6f, 10f, 6f);
        var drawn = camera.getScreenCoordinates(DukeRtsApp.drawnCentre(body, 100f, 100f, 40f));
        var foot = camera.getScreenCoordinates(new Vector3f(100f, 0f, 100f));
        var candidate = List.of(new SelectionBox.Candidate(1, drawn.x, drawn.y, true, true, false));

        assertEquals(1, SelectionBox.inside(drawn.x - 15f, drawn.y - 15f, drawn.x + 15f, drawn.y + 15f, candidate)
                .size(), "a box round where it is seen takes it");
        assertEquals(0, SelectionBox.inside(foot.x - 15f, foot.y - 15f, foot.x + 15f, foot.y + 15f, candidate)
                .size(), "a box round the ground under the world's foot does not");
        assertEquals(new Vector3f(10f, 7f, 20f), DukeRtsApp.drawnCentre(null, 10f, 20f, 7f),
                "nothing drawn: the ground under it");
    }

    @Test
    void theForceAttackKeyShowsItsTwoPointers() {
        var overThing = new Cursors.Over(true, null, true, false, true, true, true, true, null, null, true, true);
        var overGround = new Cursors.Over(true, null, true, false, false, false, true, true, null, null, true, true);
        var notHeld = new Cursors.Over(true, null, true, false, true, true, true, true, null, null, true, false);

        assertEquals(Cursors.FORCE_ATTACK_OBJ, Cursors.situationFor(overThing), "over his own tank, key held");
        assertEquals(Cursors.FORCE_ATTACK_GROUND, Cursors.situationFor(overGround));
        assertEquals(Cursors.FRIEND, Cursors.situationFor(notHeld), "without the key nothing changes");
    }

    @Test
    void anisotropyIsAPowerOfTwoTheCardCanTake() {
        assertEquals(8, DukeRtsApp.anisotropyFor(8, 16));
        assertEquals(4, DukeRtsApp.anisotropyFor(6, 16));
        assertEquals(4, DukeRtsApp.anisotropyFor(16, 4), "no more than the card takes");
        assertEquals(1, DukeRtsApp.anisotropyFor(1, 16), "1: plain mipmapping, as before");
        assertEquals(16, DukeRtsApp.anisotropyFor(32, null));
    }
}
