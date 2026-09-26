package uz.dukeengine.client3d;

/**
 * A weather of flakes falling round the player's camera — the reference's {@code WeatherSetting} snow ({@code
 * SnowManager}, {@code W3DSnowManager::render}): snow at 3 a second swaying 4, rain at 25 swaying 0.1.
 *
 * @param picture    the flake's picture, by its whole path
 * @param box        the side of the box round the eye the flakes fall in — {@code SnowBoxDimensions}, 100
 * @param spacing    how far apart the columns of the grid fixed to the world are, one flake to each — one over {@code
 *                   SnowBoxDensity}, 1
 * @param speed      how fast a flake falls, in world units a second of the view's clock — {@code SnowVelocity}
 * @param amplitude  how far a flake sways — {@code SnowAmplitude}
 * @param frequencyX how fast it sways across x with its height — {@code SnowFrequencyScaleX}
 * @param frequencyY and across the map's y — {@code SnowFrequencyScaleY}
 * @param size       a flake's size — {@code SnowPointSize}: that times the view's height in pixels over its
 *                   distance from the eye, in pixels
 * @param leastPixels the fewest pixels a flake is drawn across — {@code SnowMinPointSize}
 * @param mostPixels  the most — {@code SnowMaxPointSize}
 * @param square      the side of the camera-facing square a flake is drawn as, in world units — {@code SnowQuadSize},
 *                    snow 0.5, rain 0.1 — in place of the size in pixels; 0 for the size in pixels
 */
public record WeatherLook(String picture, float box, float spacing, float speed, float amplitude, float frequencyX,
        float frequencyY, float size, float leastPixels, float mostPixels, float square) {

    /** The most pixels across a square flake is drawn, however near the eye. */
    static final float MOST_SQUARE_PIXELS = 256f;

    public WeatherLook {
        box = Math.max(1f, box);
        spacing = spacing <= 0f ? 1f : spacing;
        speed = Math.max(0f, speed);
        mostPixels = Math.max(leastPixels, mostPixels);
        square = Math.max(0f, square);
    }

    /** A flake drawn by its size in pixels. */
    public WeatherLook(String picture, float box, float spacing, float speed, float amplitude, float frequencyX,
            float frequencyY, float size, float leastPixels, float mostPixels) {
        this(picture, box, spacing, speed, amplitude, frequencyX, frequencyY, size, leastPixels, mostPixels, 0f);
    }

    /** The weather a game set for its match ({@code DukeGame.weather}): squares of its size, one every 1/density. */
    static WeatherLook of(uz.dukeengine.game.view.FallingWeather weather) {
        return weather == null ? null
                : new WeatherLook(weather.picture(), weather.box(), 1f / weather.density(), weather.speed(),
                        weather.amplitude(), weather.frequencyX(), weather.frequencyY(), 0f, 0f, MOST_SQUARE_PIXELS,
                        weather.size());
    }
}
