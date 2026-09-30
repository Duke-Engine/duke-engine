package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

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

    // ---- framed by the game ----

    /**
     * The reference's camera: 37.5 degrees down, 310 above the ground at its start and furthest, 120 at its nearest,
     * a wheel notch 10 up or down — along the line of sight, 509, 197 and 16.4.
     */
    private static final float STEP = 10f / (float) Math.sin(Math.toRadians(37.5));
    private static final CameraFrame REFERENCE = new CameraFrame(37.5f, 50f, 197f, 509f, 509f, STEP, 0.01f,
            699f, 532f);

    private static CameraFocus framed() {
        var camera = new CameraFocus();
        camera.frame(REFERENCE);
        return camera;
    }

    private static float degreesDown(com.jme3.math.Vector3f eye) {
        return (float) Math.toDegrees(Math.atan2(eye.y, Math.hypot(eye.x, eye.z)));
    }

    @Test
    void framedAt37AndAHalfDegreesTheEyeLooksDownSoWhileThePlayerPans() {
        var camera = framed();
        assertEquals(37.5f, degreesDown(camera.eyeOffset(camera.pitch())), 1e-3f);
        assertEquals(509f, camera.eyeOffset(camera.pitch()).length(), 1e-2f, "its whole distance back");

        camera.panBy(250f, -80f);
        camera.turnBy(0.7f);

        assertEquals(37.5f, degreesDown(camera.eyeOffset(camera.pitch())), 1e-3f, "panned and turned, still 37.5");
        assertEquals((float) Math.toDegrees(CameraFocus.DEFAULT_PITCH), degreesDown(new CameraFocus().eyeOffset(
                new CameraFocus().pitch())), 1e-3f, "and unframed, the client's own slope");
    }

    @Test
    void theWheelMovesTheEyeByItsStepAndStopsAtTheSetLimits() {
        var camera = framed();
        assertEquals(509f, camera.distance(), 1e-3f, "it starts at its start");
        float high = camera.eyeOffset(camera.pitch()).y;

        camera.wheel(true);
        assertEquals(509f - STEP, camera.distance(), 1e-3f, "a notch in");
        assertEquals(high - 10f, camera.eyeOffset(camera.pitch()).y, 1e-3f, "ten nearer the ground");

        for (int notch = 0; notch < 40; notch++) {
            camera.wheel(true);
        }
        assertEquals(197f, camera.distance(), 1e-3f, "no nearer than its nearest");
        for (int notch = 0; notch < 40; notch++) {
            camera.wheel(false);
        }
        assertEquals(509f, camera.distance(), 1e-3f, "no further than its furthest");
    }

    @Test
    void aResetReturnsTheSetPitchNoTurnAndTheStartingDistance() {
        var camera = framed();
        camera.turnBy(1.2f);
        camera.wheel(true);
        camera.wheel(true);

        camera.resetView();

        assertEquals(0f, camera.yaw(), 0f);
        assertEquals(509f, camera.distance(), 1e-3f);
        assertEquals((float) Math.toRadians(37.5), camera.pitch(), 1e-6f);
    }

    /** What a new match does with the camera a backdrop's flight left turned and pulled back. */
    @Test
    void aViewTheBackdropLeftTurnedIsPutBackUnturnedAtItsStart() {
        var camera = framed();
        camera.restore(new CameraFocus.View(300f, 200f, 1.57f, 250f));

        camera.resetView();

        assertEquals(0f, camera.yaw(), 0f, "unturned");
        assertEquals(509f, camera.distance(), 1e-3f, "at its start");
        assertEquals(300f, camera.targetX(), 0f, "looking where it looked");
    }

    /** What the client does with a view the game moved: looks there, and the player's pan that frame goes on from it. */
    @Test
    void aViewTheGameMovedIsPannedFromInTheSameFrameTurnedAndAsFarBackAsItWas() {
        var camera = framed();
        camera.turnBy(0.4f);
        camera.wheel(true);
        float distance = camera.distance();

        camera.lookAt(300f, 400f);
        camera.panBy(12f, -5f);

        assertEquals(312f, camera.targetX(), 1e-4f);
        assertEquals(395f, camera.targetZ(), 1e-4f);
        assertEquals(0.4f, camera.yaw(), 1e-6f, "turned as it was");
        assertEquals(distance, camera.distance(), 0f, "as far back");
    }

    // ---- the keys at the game's speeds, and the eye easing ----

    private static CameraFocus keyed(float turnSpeed, float zoomSpeed, float zoomEase) {
        var camera = new CameraFocus();
        camera.frame(new CameraFrame(37.5f, 50f, 197f, 509f, 509f, STEP, 0.01f, 699f, 532f, turnSpeed, zoomSpeed,
                zoomEase));
        return camera;
    }

    @Test
    void withThreeRadiansASecondTheTurnKeyHeldForASecondTurnsTheViewByThree() {
        var camera = keyed(3f, Float.NaN, Float.NaN);
        for (int frame = 0; frame < 60; frame++) {
            camera.heldTurn(1, 1f / 60f);
        }
        assertEquals(3f, camera.yaw(), 1e-4f);
        var own = new CameraFocus();
        own.heldTurn(1, 1f);
        assertEquals((float) Math.PI / 2f, own.yaw(), 1e-6f, "left alone, a quarter turn a second, as always");
    }

    @Test
    void withAZoomSpeedTheZoomKeyHeldHalfASecondBringsTheEyeThatMuchNearerAndNoNearerThanTheNearest() {
        var camera = keyed(Float.NaN, 492.8f, Float.NaN);
        for (int frame = 0; frame < 15; frame++) {
            camera.heldZoom(true, 1f / 30f);
            camera.approach(1f / 30f);
        }
        assertEquals(509f - 246.4f, camera.distance(), 1e-2f);
        camera.heldZoom(true, 5f);
        assertEquals(197f, camera.distance(), "no nearer than the nearest");
    }

    @Test
    void easingAShareEachThirtiethAWheelNotchHasMovedTheEyeAFifthThenNearlyAll() {
        var camera = keyed(Float.NaN, Float.NaN, 0.3f);
        camera.wheel(true);
        assertEquals(509f, camera.distance(), "not at once");
        camera.approach(1f / 30f);
        assertEquals(STEP * 0.3f, 509f - camera.distance(), 1e-3f, "0.3 of the notch, 4.9, after a thirtieth");
        for (int frame = 1; frame < 10; frame++) {
            camera.approach(1f / 30f);
        }
        assertEquals(STEP * (1f - (float) Math.pow(0.7, 10)), 509f - camera.distance(), 1e-3f,
                "97% of it, 16.0, after ten");
    }

    // ---- kept back from the edges ----

    /** The reference's camera: 37.5 down, 50 across a world region of 800 by 480, kept in by 0.95 of its height. */
    private static CameraFocus heldInBy(float back, float zoomEase, float wheelStep) {
        var camera = new CameraFocus();
        camera.frame(new CameraFrame(37.5f, 50f, 100f, 600f, back, wheelStep, Float.NaN, Float.NaN, Float.NaN,
                Float.NaN, Float.NaN, zoomEase, 0.95f));
        camera.viewShape((float) Math.tan(Math.toRadians(25.0)) * 480f / 800f);
        camera.keepInside(2390f, 2590f);
        return camera;
    }

    @Test
    void atItsHighestThePointLookedAtIsHeld158InFromEachEdge() {
        var camera = heldInBy(509.2f, Float.NaN, Float.NaN);
        camera.lookAt(0f, 0f);
        assertEquals(158.6f, camera.targetX(), 0.05f);
        assertEquals(158.6f, camera.targetZ(), 0.05f);
        camera.lookAt(5000f, 5000f);
        assertEquals(2231.4f, camera.targetX(), 0.05f);
        assertEquals(2431.4f, camera.targetZ(), 0.05f);
    }

    @Test
    void atItsLowestBy61AndTurnedTheSame() {
        assertEquals(61.4f, heldInBy(197.1f, Float.NaN, Float.NaN).inset(), 0.05f);
        var turned = heldInBy(509.2f, Float.NaN, Float.NaN);
        turned.turnBy((float) Math.PI / 2f);
        turned.lookAt(0f, 0f);
        assertEquals(158.6f, turned.targetX(), 0.05f, "the same on every side, whichever way it is turned");
        var free = new CameraFocus();
        free.keepInside(2390f, 2590f);
        free.lookAt(0f, 0f);
        assertEquals(0f, free.targetX(), "unset: kept on the map, as ever");
    }

    @Test
    void aNotchKeepsTheInsetWhileTheEyeEasesAndTheNextWorksItOutFromWhereTheEyeIs() {
        var camera = heldInBy(509.2f, 0.3f, 100f);
        camera.lookAt(0f, 0f);
        camera.wheel(true);
        for (int frame = 0; frame < 5; frame++) {
            camera.approach(1f / 30f);
            assertEquals(158.6f, camera.inset(), 0.05f, "as it eases in");
        }
        float eye = camera.distance();
        camera.wheel(true);
        assertEquals(CameraFocus.inset((float) Math.toRadians(37.5), eye,
                (float) Math.tan(Math.toRadians(25.0)) * 0.6f, 0.95f), camera.inset(), 1e-3f, "from where it then is");
        assertTrue(camera.inset() < 158f);
    }
}
