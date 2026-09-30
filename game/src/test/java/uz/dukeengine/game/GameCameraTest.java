package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** The camera as the game drives it: stepped with the logic frames, linear, the same flight every time it is flown. */
class GameCameraTest {

    private static DukeGame watched() {
        var game = DukeGame.create("camera-test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(60, 40);
        game.addPlayer("One", Color.BLUE);
        return game.observe();
    }

    @Test
    void aMoveOver300FramesIsHalfWayAt150AndThereAt300WithPitchAndZoomUnchanged() {
        var game = watched();
        game.onStart(started -> {
            var camera = started.camera();
            camera.moveTo(100f, 100f, 0);
            camera.pitch(0.8f);
            camera.zoom(200f);
            camera.moveTo(400f, 250f, 300);
        });

        game.runHeadless(150);
        var half = game.camera().current();
        game.runHeadless(150);
        var there = game.camera().current();

        assertEquals(250f, half.x(), 1e-3f);
        assertEquals(175f, half.y(), 1e-3f);
        assertEquals(400f, there.x(), 1e-3f);
        assertEquals(250f, there.y(), 1e-3f);
        assertEquals(0.8f, there.pitch(), 0f, "pitch kept");
        assertEquals(200f, there.zoom(), 0f, "zoom kept");
        assertFalse(game.camera().isMoving());
        assertNotNull(game.getSnapshot().camera(), "and the client is shown the game's camera");
    }

    @Test
    void turningTowardAPointOnTheWayEndsTheMoveFacingIt() {
        var game = watched();
        game.onStart(started -> {
            var camera = started.camera();
            camera.moveTo(100f, 100f, 0);
            camera.angle(0f);
            camera.moveTo(300f, 100f, 60);
            camera.lookToward(300f, 300f);
        });

        game.runHeadless(30);
        float halfway = game.camera().current().angle();
        game.runHeadless(30);
        float angle = game.camera().current().angle();

        // Facing is (-sin, -cos) of the angle: toward smaller y at 0, so toward the point here is +y.
        assertEquals(0f, (float) -Math.sin(angle), 1e-5f);
        assertEquals(1f, (float) -Math.cos(angle), 1e-5f);
        assertEquals(angle / 2f, halfway, 1e-5f, "turned at an even pace");
    }

    @Test
    void aCameraTheGameHasNotTakenIsThePlayersAndAMoveStartsFromWhereHeLooks() {
        var game = watched();
        game.runHeadless(0);
        game.setCameraSeen(120f, 40f, 0.5f);

        game.runHeadless(1);
        assertEquals(120f, game.camera().current().x(), 0f, "it follows what the player looks at");
        assertNull(game.getSnapshot().camera(), "and the player keeps his camera");

        game.camera().moveTo(220f, 40f, 10);
        game.runHeadless(5);
        assertEquals(170f, game.camera().current().x(), 1e-3f, "from where he was looking");
        assertTrue(game.camera().isDriven());

        game.camera().release();
        game.runHeadless(1);
        assertNull(game.getSnapshot().camera(), "given back");
    }

    @Test
    void theViewMovedTwiceBeforeTheClientsFrameLandsOnTheSecondAndIsTakenOnce() throws Exception {
        var game = watched();
        var from = new Thread(() -> game.moveView(100f, 200f)); // from any thread
        from.start();
        from.join();
        game.moveView(300f, 400f);

        var moved = game.takeViewMove();

        assertEquals(300f, moved.x(), 0f);
        assertEquals(400f, moved.y(), 0f);
        assertNull(game.takeViewMove(), "taken: nothing more to move to");
        assertFalse(game.camera().isDriven(), "and the camera never the game's");
    }
}
