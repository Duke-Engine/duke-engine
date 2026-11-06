package uz.dukeengine.client3d;

import uz.dukeengine.render.PictureSettings;
import uz.dukeengine.render.Quality;

/**
 * How the picture is finished, as a game's {@code Picture} block in its {@code .duke} files writes it: a {@link Quality}
 * step and the few things a map changes over it. Look and nothing else, like the sun: the simulation never reads it.
 *
 * @param quality      the step of picture against cost; left out, every effect ({@link PictureSettings#FULL})
 * @param exposure     brightness before the tone curve
 * @param atmosphere   the sky is the air under the sun, and the sun's light reddens as it sets
 * @param autoExposure the eye adapts to the picture's brightness
 * @param fogDensity   how much light the air takes per world unit at height 0; 0, no fog
 * @param fogFalloff   how fast the fog thins with height, per world unit
 * @param water        the scene's water is drawn
 */
public record PictureLook(Quality quality, float exposure, boolean atmosphere, boolean autoExposure, float fogDensity,
        float fogFalloff, boolean water) {

    /** What a block leaves out. */
    public static final PictureLook DEFAULTS = new PictureLook(null, PictureSettings.FULL.exposure(),
            PictureSettings.FULL.atmosphere(), PictureSettings.FULL.autoExposure(), PictureSettings.FULL.fogDensity(),
            PictureSettings.FULL.fogFalloff(), PictureSettings.FULL.water());

    /** The settings the client draws by. */
    public PictureSettings settings() {
        var step = quality == null ? PictureSettings.FULL : quality.settings();
        return step.withExposure(exposure).withAtmosphere(atmosphere).withAutoExposure(autoExposure)
                .withFog(fogDensity, fogFalloff).withWater(water);
    }
}
