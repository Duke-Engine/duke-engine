package uz.dukeengine.render;

/**
 * What the finished picture goes through, and where the light of the sky comes from. Each effect may be left out, so a
 * game can pay for only what it wants and the bench can price each one.
 *
 * @param ambientOcclusion darkens the creases light barely reaches ({@link GtaoFilter})
 * @param bloom            bright things spill over their edges ({@link MipBloomFilter})
 * @param fxaa             smooths stair-stepped edges
 * @param exposure         brightness of the picture before the tone curve bends it; with {@code autoExposure} the
 *                         compensation on top of what the eye has adapted to
 * @param atmosphere       the sky is the {@link Atmosphere} under the sun's direction and colour, and the ambient
 *                         the game asks for is no longer read for the sky's probe; off, the sky is the ambient's gradient
 * @param softShadows      the sun's shadows grow softer the further they fall; off, one narrow filter
 * @param contactShadows   a short ray over the screen's depth finds the shadow at a foot or in a crease
 * @param localShadows     how many spot and point lights may have a shadow at once, the ones that matter most to the
 *                         view picked each frame ({@link LocalShadowFilter}); the others stay lit, without one. 0, none
 * @param occlusionRadius  how far in world units something can stand and still darken a crease; above 0
 * @param autoExposure     the eye adapts to the picture's average brightness over time ({@link AcesToneMap}); off, the
 *                         exposure is fixed and a game's colours keep the look it gave them
 * @param fogDensity       how much light the air takes per world unit, at height 0 ({@link HeightFogFilter}); 0, no fog
 *                         and no filter, so a game's colours are the ones it gave them
 * @param fogFalloff       how fast the fog thins with height, per world unit: its density is {@code fogDensity *
 *                         e^(-fogFalloff * height)}; 0, as thick at any height
 * @param shafts           the sun's light through the fog is cut by what stands in it, from {@link SunShadowFilter}'s maps
 * @param reflections      glossy surfaces show the picture in themselves where it has their reflection, the light probe's
 *                         where it has not ({@link SsrFilter})
 */
public record PictureSettings(boolean ambientOcclusion, boolean bloom, boolean fxaa, float exposure, boolean atmosphere,
        boolean softShadows, boolean contactShadows, int localShadows, float occlusionRadius,
        boolean autoExposure, float fogDensity, float fogFalloff, boolean shafts, boolean reflections) {

    public PictureSettings {
        if (!(occlusionRadius > 0f)) {
            throw new IllegalArgumentException("occlusionRadius must be above 0: " + occlusionRadius);
        }
        if (!(fogDensity >= 0f) || !(fogFalloff >= 0f)) {
            throw new IllegalArgumentException("fog density and falloff must not be below 0: " + fogDensity + ", " + fogFalloff);
        }
    }

    /**
     * Every filter on, the sky left as the game's own sun and ambient make it: a game's colours keep their look.
     * At this exposure the curve leaves a mid-tone where it was. Three quarters of a metre of reach is a human-scale world's crease
     * between a wall and a floor, a doorway, the underside of a table.
     */
    public static final PictureSettings FULL = new PictureSettings(true, true, true, 0.7f, false, true, true, 4, 0.75f, false, 0f, 0.06f, true, true);

    /** These settings with the atmosphere on or off. */
    public PictureSettings withAtmosphere(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, on, softShadows, contactShadows, localShadows,
                occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections);
    }

    /** These settings with this many lights allowed a shadow, none for 0. */
    public PictureSettings withLocalShadows(int budget) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                Math.max(0, budget), occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections);
    }

    /** These settings with creases darkened by what stands this far (world units) from them. */
    public PictureSettings withOcclusionRadius(float radius) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                localShadows, radius, autoExposure, fogDensity, fogFalloff, shafts, reflections);
    }

    /** These settings with the eye adapting to the picture, or not. */
    public PictureSettings withAutoExposure(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, on, fogDensity, fogFalloff, shafts, reflections);
    }

    /** These settings with fog of this density at height 0 that thins by {@code falloff} per unit of height; 0 density, none. */
    public PictureSettings withFog(float density, float falloff) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, density, falloff, shafts, reflections);
    }

    /** These settings with the sun's shafts through the fog, or not. */
    public PictureSettings withShafts(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, on, reflections);
    }

    /** These settings with the reflections of the picture in glossy surfaces, or not. */
    public PictureSettings withReflections(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, on);
    }
}
