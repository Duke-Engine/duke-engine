package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Where a click lands, on a map with more than one storey to it, and on hills.
 *
 * <p>This is arithmetic dressed as a rendering problem, and it goes wrong in a
 * way that looks like nothing at all: the order marker appears a pace beyond the
 * cursor, the hero walks there, and everything about it is consistent — the
 * marker really is where the game thinks the click was.
 *
 * <p>No camera and no window: a ray is a point and a direction, and how high the
 * floor is at a place is a function this hands in.
 */
class GroundPickTest {

    private static final float STOREY = 10f;

    /**
     * A camera above and behind, looking down the way this client's does — about
     * 55 degrees, so a ray drops roughly one unit for every unit it travels north.
     */
    private static final Vector3f DOWNWARD = new Vector3f(0f, -0.8f, -0.6f).normalizeLocal();

    /** Two storeys, and a stair up from the upper one. */
    private static final DukeRtsApp.GroundSpan STOREYS = new DukeRtsApp.GroundSpan(0f, 2 * STOREY);
    private static final DukeRtsApp.GroundSpan FLAT = new DukeRtsApp.GroundSpan(0f, 0f);
    /** Half a cell of 10: how far across the map the ray is walked at a time. */
    private static final float STEP = 5f;

    /** Everything west of x = 100 is the ground floor; east of it stands a storey up. */
    private static float terraced(float x, float z) {
        return x >= 100f ? STOREY : 0f;
    }

    private static Vector3f pick(Vector3f from) {
        return DukeRtsApp.groundHit(from, DOWNWARD, STOREYS, STEP, GroundPickTest::terraced);
    }

    /** On a hill the click lands on the hill, where the ground under the cursor is — not on the level plane under it. */
    @Test
    void aClickOnASlopeLandsOnTheSlope() {
        var hit = DukeRtsApp.groundHit(new Vector3f(50f, 40f, 100f), DOWNWARD, STOREYS, STEP, (x, z) -> x * 0.08f);

        assertEquals(hit.x * 0.08f, hit.y, 0.02f, "standing on the ground at " + hit);
    }

    /** On flat ground the answer is the plain one: where the ray meets zero. */
    @Test
    void aClickOnTheGroundFloorLandsWhereTheRayMeetsIt() {
        var hit = pick(new Vector3f(50f, 40f, 100f));

        assertEquals(0f, hit.y, 0.001f, "it should be standing on the ground floor");
        assertTrue(hit.x < 100f, "and west of the step up: " + hit.x);
    }

    /**
     * A click on the raised floor lands on the raised floor — not on the ground
     * behind it.
     *
     * <p>The ray passes over the raised room and, carried on to the plane at zero,
     * comes down somewhere past it. That point's floor is at zero too, so the old
     * answer looked settled: the marker sat a pace beyond the cursor, every time,
     * and only upstairs.
     */
    @Test
    void aClickUpstairsLandsUpstairsRatherThanBehindIt() {
        var from = new Vector3f(150f, 40f, 100f);

        var hit = pick(from);
        var throughTheFloor = from.add(DOWNWARD.mult(-from.y / DOWNWARD.y));

        assertEquals(STOREY, hit.y, 0.001f, "it is the upper floor being pointed at");
        assertEquals(STOREY, terraced(hit.x, hit.z), 0.001f,
                "and the point really is standing on it");
        assertTrue(hit.z > throughTheFloor.z + 1f,
                "the old answer carried on under the room and landed " + throughTheFloor.z
                        + " where the true one is " + hit.z);
    }

    /** A stair is between two storeys, and a click on one lands on the slope. */
    @Test
    void aClickOnAStairLandsOnTheSlope() {
        var hit = DukeRtsApp.groundHit(new Vector3f(150f, 40f, 100f), DOWNWARD, STOREYS, STEP,
                (x, z) -> 4f); // a ramp, halfway up

        assertEquals(4f, hit.y, 0.001f, "on the step it is actually standing on");
    }

    /** A click on an open deck lands on the deck, at its height; closed, or beside it, on the ground. */
    @Test
    void aClickOnADeckLandsOnTheDeck() {
        var grid = new uz.dukeengine.core.pathfind.PathGrid(40, 40);
        int deck = grid.addDeck(new uz.dukeengine.core.math.Coord3D(130f, 180f, 20f),
                new uz.dukeengine.core.math.Coord3D(130f, 220f, 20f),
                new uz.dukeengine.core.math.Coord3D(270f, 220f, 20f),
                new uz.dukeengine.core.math.Coord3D(270f, 180f, 20f));
        var over = new Vector3f(200f, 100f, 275f); // the scene's z is the map's y
        var ground = DukeRtsApp.groundHit(over, DOWNWARD, FLAT, STEP, (x, z) -> 0f);

        var hit = DukeRtsApp.deckHit(over, DOWNWARD, ground, grid.decks());
        assertEquals(20f, hit.y, 1e-3f, "on the deck, at its height");
        assertEquals(215f, hit.z, 1e-3f, "where the ray meets it");

        var beside = new Vector3f(200f, 100f, 350f);
        var groundBeside = DukeRtsApp.groundHit(beside, DOWNWARD, FLAT, STEP, (x, z) -> 0f);
        assertEquals(0f, DukeRtsApp.deckHit(beside, DOWNWARD, groundBeside, grid.decks()).y, 1e-3f, "beside it");

        grid.setDeckOpen(deck, false);
        assertEquals(0f, DukeRtsApp.deckHit(over, DOWNWARD, ground, grid.decks()).y, 1e-3f, "closed, the ground");
    }

    /** A game with no height in it is answered exactly as it always was. */
    @Test
    void aFlatMapIsPickedAtZero() {
        var hit = DukeRtsApp.groundHit(new Vector3f(150f, 40f, 100f), DOWNWARD, FLAT, STEP,
                (x, z) -> 0f);

        assertEquals(0f, hit.y, 0.001f);
    }

    /**
     * A ray that never meets the ground is followed a long way instead of being
     * reported as nothing — the projection clamps it to the map, which is what
     * the player sees anyway.
     */
    @Test
    void aRayIntoTheSkyStillAnswers() {
        var hit = DukeRtsApp.groundHit(new Vector3f(50f, 40f, 100f),
                new Vector3f(0f, 0.8f, -0.6f).normalizeLocal(), STOREYS, STEP, (x, z) -> 0f);

        assertTrue(hit.length() > 1000f, "it should run off toward the edge of the world");
    }

    /**
     * A ridge 40 high along x, its middle at z = 215, falling 2 a unit either side: the ray from (0, 90, 280) meets its
     * near face at t = 90, where 90 - 0.8t = 40 - 2(280 - 0.6t - 215). The plane at zero is met under the ridge, 35
     * below its ground, and settling from there went back and forth between the ridge and the ground before it.
     */
    @Test
    void aClickOnAHillsNearFaceLandsOnIt() {
        var hit = DukeRtsApp.groundHit(new Vector3f(0f, 90f, 280f), DOWNWARD, new DukeRtsApp.GroundSpan(0f, 40f), STEP,
                (x, z) -> 40f * Math.max(0f, 1f - Math.abs(z - 215f) / 20f));

        assertEquals(226f, hit.z, 0.05f, "on the face the cursor is over: " + hit);
        assertEquals(18f, hit.y, 0.05f);
    }

    /**
     * A narrow hill with level ground behind it: the plane at zero is met on that ground, which is where the old answer
     * stopped — past the hill. The first meeting is on its near face, at t = 325 / 3.8.
     */
    @Test
    void aClickOnANarrowHillLandsOnItAndNotOnTheGroundBehindIt() {
        var hit = DukeRtsApp.groundHit(new Vector3f(0f, 90f, 280f), DOWNWARD, new DukeRtsApp.GroundSpan(0f, 40f), STEP,
                (x, z) -> 40f * Math.max(0f, 1f - Math.abs(z - 225f) / 8f));

        assertEquals(280f - 0.6f * 325f / 3.8f, hit.z, 0.05f, "on the hill: " + hit);
        assertEquals(90f - 0.8f * 325f / 3.8f, hit.y, 0.05f);
    }

    /** How low and high a map's ground stands: its storeys, a stair up from the top one, and its relief's corners. */
    @Test
    void theGroundsSpanTakesInItsStoreysAndItsRelief() {
        var grid = new uz.dukeengine.core.pathfind.PathGrid(3, 1);
        grid.setLevelHeight(STOREY);
        grid.setLevel(2, 0, 1);
        grid.setRelief(uz.dukeengine.core.pathfind.HeightMap.parse(java.util.List.of("0 0 8 24", "0 0 8 24")));

        assertEquals(new DukeRtsApp.GroundSpan(0f, 2 * STOREY + 15f), DukeRtsApp.GroundSpan.of(grid),
                "24 sixteenths of a cell of 10 over a stair above the upper storey");
    }
}
