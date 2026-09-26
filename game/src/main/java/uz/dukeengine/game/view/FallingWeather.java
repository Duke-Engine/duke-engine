package uz.dukeengine.game.view;

/**
 * A weather falling over the view that a game sets for a match — the reference's snow and rain ({@code
 * WeatherSetting}, {@code W3DSnowManager}): flakes on a square grid round the camera, one every one-over-density units
 * of ground, falling through a box centred on the camera's height and wrapping from its bottom to its top, swayed
 * sideways, drawn as camera-facing squares of a picture. Drawing only: nothing in the simulation or the checksum.
 *
 * @param picture    the flake's picture, by its whole path
 * @param box        the side of the box round the camera — 100
 * @param density    flakes a unit of ground each way — 1
 * @param speed      how fast a flake falls, in units a second — snow 3, rain 25
 * @param amplitude  how far it sways — snow 4, rain 0.1
 * @param frequencyX how fast it sways across x with its height — snow 0.0533, rain 1
 * @param frequencyY and across the map's y — snow 0.0275, rain 1
 * @param size       the side of its square, in units — snow 0.5, rain 0.1
 */
public record FallingWeather(String picture, float box, float density, float speed, float amplitude,
        float frequencyX, float frequencyY, float size) {

    public FallingWeather {
        box = Math.max(1f, box);
        density = density <= 0f ? 1f : density;
        speed = Math.max(0f, speed);
        size = Math.max(0f, size);
    }
}
