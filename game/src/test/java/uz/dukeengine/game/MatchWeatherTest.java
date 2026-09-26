package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.FallingWeather;

/** A weather set for a match: kept by a game with no window, and nothing in the simulation. */
class MatchWeatherTest {

    private static final FallingWeather RAIN = new FallingWeather("weather/rain.png", 100f, 1f, 25f, 0.1f, 1f, 1f,
            0.1f);

    private static DukeGame game() {
        var game = DukeGame.create("weather").loadUnits(DukeGame.STARTER_UNITS).map(20, 20);
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        return game;
    }

    @Test
    void aHeadlessGameSetsAndClearsItWithoutErrorAndItsWorldSumsAsWithNone() {
        var wet = game().weather(RAIN);
        var dry = game();
        wet.runHeadless(5);
        dry.runHeadless(5);
        assertEquals(RAIN, wet.getWeather());
        assertEquals(dry.getLogic().checksum(), wet.getLogic().checksum(), "drawing only");
        wet.weather(null);
        assertNull(wet.getWeather(), "cleared");
    }
}
