package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Flakes falling round the camera, as the reference's snow falls ({@code W3DSnowManager::render}). */
class WeatherTest {

    /** The reference's snow, without its sway, so a flake's column can be read off where it stands. */
    private static final WeatherLook STILL_SNOW = new WeatherLook("weather/flake.png", 100f, 1f, 3f, 0f, 0.0533f,
            0.0275f, 0.16f, 0f, 10f);

    /** Each flake's height, by its column. */
    private static Map<Long, Float> heights(float[] places) {
        var byColumn = new HashMap<Long, Float>();
        for (int at = 0; at < places.length; at += 3) {
            byColumn.put(Math.round(places[at]) * 1_000_000L + Math.round(places[at + 2]), places[at + 1]);
        }
        return byColumn;
    }

    @Test
    void aBoxOf100AtSpacing1Holds10000FlakesAndPanningByAHalfMovesNoneStillInIt() {
        var weather = new Weather(STILL_SNOW);
        assertEquals(10000, weather.count());
        var before = heights(weather.lay(500.2f, 60f, 500.2f).clone());
        var after = heights(weather.lay(500.7f, 60f, 500.2f));
        int kept = 0;
        for (var column : after.entrySet()) {
            var was = before.get(column.getKey());
            if (was != null) {
                assertEquals(was, column.getValue(), "a flake still in the box stands where it stood");
                kept++;
            }
        }
        assertTrue(kept >= 9900, "most of them still in it: " + kept);
    }

    @Test
    void atSpeed3AFlakeFalls0099AFrameIsBackAtTheTopEvery10101FramesAndStandsStillWhilePaused() {
        var weather = new Weather(STILL_SNOW);
        float first = weather.lay(500f, 60f, 500f)[1];
        weather.step(1);
        float next = weather.lay(500f, 60f, 500f)[1];
        float fell = first - next;
        assertEquals(0.099f, fell < 0f ? fell + 100f : fell, 1e-3f, "0.099 a frame");
        weather.step(0);
        assertEquals(next, weather.lay(500f, 60f, 500f)[1], "paused: it stands still");
        weather.step(1009);
        float apart = Math.abs(first - weather.lay(500f, 60f, 500f)[1]) % 100f;
        assertEquals(0f, Math.min(apart, 100f - apart), 0.02f, "back where it was after 1010 of 1010.1 frames");
        assertEquals(1010.1f, 100f / (3f * Weather.FRAME_SECONDS), 0.05f);
    }

    @Test
    void theEyeRaisedBy20LeavesEveryFlakeStillInTheBoxAtItsHeightInTheWorld() {
        var weather = new Weather(STILL_SNOW);
        var low = heights(weather.lay(500f, 30f, 500f).clone());
        var high = heights(weather.lay(500f, 50f, 500f));
        for (var column : low.entrySet()) {
            float height = column.getValue();
            if (height > 0.01f && height <= 80f) {
                assertEquals(height, high.get(column.getKey()), 1e-3f, "in both boxes: where it was");
            }
        }
    }

    @Test
    void aSizeOf016InAView480HighIs768PixelsAt10AndTheMostAt5() {
        assertEquals(7.68f, Weather.pixels(STILL_SNOW, 480f, 10f), 1e-4f);
        assertEquals(10f, Weather.pixels(STILL_SNOW, 480f, 5f), "the most");
    }

    @Test
    void itsMaterialTakesThePictureAndTheSizes() {
        var flakes = new com.jme3.material.Material(new com.jme3.asset.DesktopAssetManager(true),
                "MatDefs/duke/Flakes.j3md").getMaterialDef();
        assertTrue(flakes.getMaterialParam("Picture") != null && flakes.getMaterialParam("ViewHeight") != null);
    }

    // ---- set for a match ----

    private static final uz.dukeengine.game.view.FallingWeather SNOW = new uz.dukeengine.game.view.FallingWeather(
            "weather/flake.png", 100f, 1f, 3f, 4f, 0.0533f, 0.0275f, 0.5f);

    @Test
    void aMatchsSnowFallsRoundTheCameraAsSquaresOfItsSizeAndClearedIsNone() {
        var look = WeatherLook.of(SNOW);
        assertEquals(1f, look.spacing(), "one every 1/density");
        assertEquals(0.5f, look.square());
        var weather = new Weather(look);
        assertEquals(10000, weather.count(), "round the camera");
        float first = weather.lay(0f, 50f, 0f)[1];
        weather.step(30);
        float fell = first - weather.lay(0f, 50f, 0f)[1];
        assertEquals(3f * 30 * Weather.FRAME_SECONDS, fell < 0f ? fell + 100f : fell, 1e-3f, "speed times time");

        float tanHalf = (float) Math.tan(Math.toRadians(22.5));
        assertEquals(720f * 0.5f / (2f * 10f * tanHalf), Weather.scale(look, tanHalf) * 720f / 10f, 1e-3f,
                "a square of 0.5 at 10 from the eye spans as many pixels as the view shows it");
        assertEquals(null, WeatherLook.of(null), "cleared: none");
    }
}
