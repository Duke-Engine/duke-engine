package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.UnitView;

/**
 * The camera opens on the player and then belongs to him.
 *
 * <p>Two moments have to put it somewhere — the start of a game and the start of a
 * new run — and every other moment has to leave it alone, because panning around a
 * standing hero is most of how the game is played.
 */
class CameraFocusTest {

    private static final int HERO_PLAYER = 1;
    private static final int DUNGEON_PLAYER = 2;

    private static UnitView unit(int id, int player, float x, float y) {
        return new UnitView(id, "Rogue", player, x, y, 0f, 100f, 100f,
                false, true, false, false, -1);
    }

    @Test
    void aStartingGameOpensOnThePlayersOwnUnit() {
        var camera = new CameraFocus();
        camera.lookAt(250f, 180f); // the middle of the map, where it starts out

        camera.requestOwnUnit();
        boolean moved = camera.focusOnOwnUnit(List.of(
                unit(1, DUNGEON_PLAYER, 400f, 300f),
                unit(2, HERO_PLAYER, 75f, 95f)), HERO_PLAYER);

        assertTrue(moved);
        assertEquals(75f, camera.targetX(), 0.001f, "the camera should be on the hero");
        assertEquals(95f, camera.targetZ(), 0.001f);
        assertFalse(camera.isAwaitingOwnUnit(), "and the request is spent");
    }

    /** A new dungeon puts the hero somewhere else; the camera goes there too. */
    @Test
    void aNewRunOpensOnTheNewHero() {
        var camera = new CameraFocus();
        camera.requestOwnUnit();
        camera.focusOnOwnUnit(List.of(unit(1, HERO_PLAYER, 75f, 95f)), HERO_PLAYER);

        camera.requestOwnUnit(); // the hero died; here is the next dungeon
        camera.focusOnOwnUnit(List.of(unit(9, HERO_PLAYER, 410f, 60f)), HERO_PLAYER);

        assertEquals(410f, camera.targetX(), 0.001f);
        assertEquals(60f, camera.targetZ(), 0.001f);
    }

    /**
     * The important half: it is a one-shot request, not a leash. Once the camera
     * has been placed, the hero can walk the whole dungeon without dragging the
     * view along behind him.
     */
    @Test
    void theCameraDoesNotFollowTheHeroAround() {
        var camera = new CameraFocus();
        camera.requestOwnUnit();
        camera.focusOnOwnUnit(List.of(unit(1, HERO_PLAYER, 100f, 100f)), HERO_PLAYER);

        // The hero walks away, several snapshots later.
        camera.focusOnOwnUnit(List.of(unit(1, HERO_PLAYER, 300f, 250f)), HERO_PLAYER);

        assertEquals(100f, camera.targetX(), 0.001f, "the camera should have stayed put");
        assertEquals(100f, camera.targetZ(), 0.001f);
    }

    /** Nor does it snatch the view back from a player who has panned somewhere. */
    @Test
    void panningIsNeverOverridden() {
        var camera = new CameraFocus();
        camera.requestOwnUnit();
        camera.focusOnOwnUnit(List.of(unit(1, HERO_PLAYER, 100f, 100f)), HERO_PLAYER);

        camera.panBy(60f, -40f);
        camera.focusOnOwnUnit(List.of(unit(1, HERO_PLAYER, 100f, 100f)), HERO_PLAYER);

        assertEquals(160f, camera.targetX(), 0.001f, "the player's panning should stand");
        assertEquals(60f, camera.targetZ(), 0.001f);
    }

    /**
     * A request made before the world exists survives until it can be honoured —
     * the game starts a frame or two before the first units show up in a snapshot.
     */
    @Test
    void aRequestWaitsUntilThereIsSomethingToLookAt() {
        var camera = new CameraFocus();
        camera.lookAt(250f, 180f);
        camera.requestOwnUnit();

        assertFalse(camera.focusOnOwnUnit(List.of(), HERO_PLAYER), "no units yet");
        assertFalse(camera.focusOnOwnUnit(
                List.of(unit(1, DUNGEON_PLAYER, 400f, 300f)), HERO_PLAYER),
                "and someone else's unit is not his");
        assertTrue(camera.isAwaitingOwnUnit(), "so the request is still standing");
        assertEquals(250f, camera.targetX(), 0.001f, "and the camera has not moved meanwhile");

        assertTrue(camera.focusOnOwnUnit(List.of(unit(2, HERO_PLAYER, 80f, 90f)), HERO_PLAYER));
        assertEquals(80f, camera.targetX(), 0.001f);
    }

    @Test
    void zoomStaysBetweenUsefulDistances() {
        var camera = new CameraFocus();
        assertEquals(CameraFocus.START_DISTANCE, camera.distance(), 0.001f);

        for (int i = 0; i < 200; i++) {
            camera.zoomBy(0.92f);
        }
        assertTrue(camera.distance() >= 40f, "should not zoom inside the ground");

        for (int i = 0; i < 200; i++) {
            camera.zoomBy(1.09f);
        }
        assertTrue(camera.distance() <= 400f, "nor out past any useful view");
    }

    /** Panning covers the screen in about the same time however far back you are. */
    @Test
    void panningIsFasterWhenFurtherBack() {
        var close = new CameraFocus();
        var far = new CameraFocus();
        far.zoomBy(2f);

        assertTrue(far.panSpeed() > close.panSpeed());
    }

    // ---- the edge of the map ----

    /**
     * Held down, a pan key stops at the edge rather than going on into nothing.
     *
     * <p>Off the map there is no ground, no landmark and no minimap mark to say
     * which way home is — a player who leans on a key for a second is lost, and
     * the only way back is to guess.
     */
    @Test
    void panningStopsAtTheEdgeOfTheMap() {
        var camera = new CameraFocus();
        camera.keepInside(700f, 450f);
        camera.lookAt(350f, 225f);

        for (int i = 0; i < 100; i++) {
            camera.panBy(40f, 40f);
        }
        assertEquals(700f, camera.targetX(), 0.001f, "the far corner of the map, and no further");
        assertEquals(450f, camera.targetZ(), 0.001f);

        for (int i = 0; i < 100; i++) {
            camera.panBy(-40f, -40f);
        }
        assertEquals(0f, camera.targetX(), 0.001f, "and the near corner going back");
        assertEquals(0f, camera.targetZ(), 0.001f);
    }

    /** Whoever does the looking — the minimap, a new run, the hero key — is inside it too. */
    @Test
    void nothingCanPutTheCameraOffTheMap() {
        var camera = new CameraFocus();
        camera.keepInside(700f, 450f);

        camera.lookAt(-500f, 9000f); // a minimap click at the very corner
        assertEquals(0f, camera.targetX(), 0.001f);
        assertEquals(450f, camera.targetZ(), 0.001f);

        camera.requestOwnUnit();
        camera.focusOnOwnUnit(List.of(unit(1, HERO_PLAYER, 1200f, 90f)), HERO_PLAYER);
        assertEquals(700f, camera.targetX(), 0.001f, "even a unit outside the map");
    }

    /**
     * A smaller map moves the camera in, rather than leaving it outside.
     *
     * <p>Every new floor is laid out afresh and may be smaller than the last;
     * the camera was on the old one when it was.
     */
    @Test
    void aNewSmallerMapPullsTheCameraOntoIt() {
        var camera = new CameraFocus();
        camera.keepInside(700f, 450f);
        camera.lookAt(690f, 440f);

        camera.keepInside(200f, 200f);

        assertEquals(200f, camera.targetX(), 0.001f);
        assertEquals(200f, camera.targetZ(), 0.001f);
    }

    /** A game that never says how big its world is keeps the old free camera. */
    @Test
    void withNoMapSaidThereIsNoFence() {
        var camera = new CameraFocus();

        camera.panBy(-5000f, 9000f);

        assertEquals(-5000f, camera.targetX(), 0.001f);
        assertEquals(9000f, camera.targetZ(), 0.001f);
    }

    // ---- back to the hero ----

    /**
     * The key that fetches the camera back is the same one-shot request a new run
     * makes, and it may be asked for again and again.
     *
     * <p>It has to be a request rather than a jump: the hero the player wants to
     * see is in the next snapshot, not in the keypress.
     */
    @Test
    void theCameraCanBeCalledBackToTheHeroWheneverHeAsks() {
        var camera = new CameraFocus();
        var hero = List.of(unit(1, HERO_PLAYER, 120f, 260f));
        camera.requestOwnUnit();
        camera.focusOnOwnUnit(hero, HERO_PLAYER);

        camera.panBy(300f, -200f);
        assertEquals(420f, camera.targetX(), 0.001f, "panned away, as it should be");

        camera.requestOwnUnit();
        assertTrue(camera.focusOnOwnUnit(hero, HERO_PLAYER));
        assertEquals(120f, camera.targetX(), 0.001f, "and called straight back");
        assertEquals(260f, camera.targetZ(), 0.001f);
        assertFalse(camera.isAwaitingOwnUnit(), "the request is spent, so it stays his");
    }
}
