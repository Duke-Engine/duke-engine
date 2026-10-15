package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;

/** The hour's sun eased over its seconds, the ground told its share, and rain in a colour of its own (E86). */
class HourOfDayTest {

    @Test
    void aSunSetAtRuntimeIsReachedOverItsSecondsAndTheGroundToldItsShare() {
        var noon = HourOfDay.Sun.of(Sunlight.DEFAULT);
        var dusk = HourOfDay.Sun.of(new Sunlight(10f, 270f, 0.6f, 0.3f, 0xFF8040, 0x403060));
        var hour = new HourOfDay(noon);
        hour.toward(dusk, 2f);
        for (int frame = 0; frame < 30; frame++) {
            hour.step(1f / 30f);
        }
        var half = hour.now();
        assertEquals((noon.colour().r + dusk.colour().r) / 2f, half.colour().r, 1e-3f, "half way at one second");
        for (int frame = 0; frame < 40; frame++) {
            hour.step(1f / 30f);
        }
        assertEquals(dusk.colour(), hour.now().colour(), "there at two");
        assertEquals(dusk.direction().x, hour.now().direction().x, 1e-4f);

        var shift = HourOfDay.shift(new ColorRGBA(0.2f, 0.4f, 0.6f, 1f), new ColorRGBA(0.4f, 0.4f, 0f, 1f));
        assertEquals(0.5f, shift.r, 1e-6f, "the ground's ambient at half what it was built with");
        assertEquals(1f, shift.b, 1e-6f, "and a channel built dark left as it was");
    }

    @Test
    void rainInAColourHandsItToTheFlakes() {
        var assets = new DesktopAssetManager(true);
        var rain = new WeatherLook("Common/Textures/MissingTexture.png", 100f, 1f, 30f, 0f, 0f, 0f, 2f, 1f, 8f, 0f)
                .rain(0x806080FF, 6f);
        var weather = new Weather(rain);
        var parent = new Node("sky");
        weather.show(assets, parent, 0f, 50f, 0f, 900f, 0.4f);
        var flakes = (Geometry) parent.getChild("weather");
        assertNotNull(flakes.getMaterial().getParam("Colour"), "its colour on the flakes");
        assertEquals(6f, (float) flakes.getMaterial().getParam("Stretch").getValue(), 1e-6f, "streaks six long");
    }
}
