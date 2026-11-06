package uz.dukeengine.render;

/**
 * What the finished picture goes through, and where the light of the sky comes from. Each effect may be left out, so a
 * game can pay for only what it wants and the bench can price each one.
 *
 * @param ambientOcclusion darkens the creases light barely reaches ({@link GtaoFilter})
 * @param bloom            bright things spill over their edges ({@link MipBloomFilter})
 * @param antiAliasing     how stair-stepped edges are smoothed ({@link AntiAliasing}); {@code TAA} also lets the noisy effects
 *                         turn their pattern each frame, for it to average
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
 * @param lens             the camera's focus and shutter ({@link Lens}); off, the picture is as sharp at any distance and as still
 *                         as it is drawn, and neither filter is in the chain
 * @param lut              the asset path of a colour grade strip ({@link ColorLut}) applied after the tone curve; null, none
 * @param vignette         how much the corners are darkened, 0 to 1 ({@link GradeFilter}); 0, none
 * @param clusteredLights  the physically based materials draw the spot and point lights of the scene's root in one pass,
 *                         from the clusters of the view they are sorted into ({@link ClusteredLights}); off, a pass for
 *                         each light on each thing it reaches
 * @param screenSpaceGi    the light that bounces once off what the screen shows lights the surfaces near it, in their own colour
 *                         ({@link SsgiFilter}): a red wall tints the floor by it, the shade side of a thing in low sun is not black
 * @param water            the {@link Water} in the scene is drawn over the picture, refracting it ({@link WaterFilter}); off, the chain has
 *                         no such filter and no pass for it, which is what a game without water wants
 * @param autoInstancing   the solid things that share a mesh and a material are drawn as one instanced call, each pass, whatever the game
 *                         does ({@link AutoInstancing}); off, a call for each
 * @param autoLod          what is small on the screen is drawn with fewer triangles, the levels made for each mesh off the frame
 *                         ({@link AutoLod}); off, every mesh as the game gave it
 */
public record PictureSettings(boolean ambientOcclusion, boolean bloom, AntiAliasing antiAliasing, float exposure, boolean atmosphere,
        boolean softShadows, boolean contactShadows, int localShadows, float occlusionRadius,
        boolean autoExposure, float fogDensity, float fogFalloff, boolean shafts, boolean reflections,
        Lens lens, String lut, float vignette, boolean clusteredLights, boolean screenSpaceGi, boolean water, boolean autoInstancing, boolean autoLod) {

    public PictureSettings {
        if (!(occlusionRadius > 0f)) {
            throw new IllegalArgumentException("occlusionRadius must be above 0: " + occlusionRadius);
        }
        if (!(fogDensity >= 0f) || !(fogFalloff >= 0f)) {
            throw new IllegalArgumentException("fog density and falloff must not be below 0: " + fogDensity + ", " + fogFalloff);
        }
        if (lens == null || !(vignette >= 0f && vignette <= 1f)) {
            throw new IllegalArgumentException("a lens is needed and the vignette is from 0 to 1: " + lens + ", " + vignette);
        }
    }

    /**
     * Every filter on but the camera's and the grade (the lens and the vignette are for a game to choose), the sky left as the game's own sun and ambient make it: a game's colours keep their look.
     * At this exposure the curve leaves a mid-tone where it was. Three quarters of a metre of reach is a human-scale world's crease
     * between a wall and a floor, a doorway, the underside of a table.
     */
    public static final PictureSettings FULL = new PictureSettings(true, true, AntiAliasing.TAA, 0.7f, false, true, true, 4, 0.75f, false, 0f, 0.06f, true, true,
            Lens.OFF, null, 0f, true, true, false, true, true);

    /** These settings with the atmosphere on or off. */
    public PictureSettings withAtmosphere(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, on, softShadows, contactShadows, localShadows,
                occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with this many lights allowed a shadow, none for 0. */
    public PictureSettings withLocalShadows(int budget) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                Math.max(0, budget), occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with creases darkened by what stands this far (world units) from them. */
    public PictureSettings withOcclusionRadius(float radius) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, radius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with the eye adapting to the picture, or not. */
    public PictureSettings withAutoExposure(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, on, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with fog of this density at height 0 that thins by {@code falloff} per unit of height; 0 density, none. */
    public PictureSettings withFog(float density, float falloff) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, density, falloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with the sun's shafts through the fog, or not. */
    public PictureSettings withShafts(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, on, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with the reflections of the picture in glossy surfaces, or not. */
    public PictureSettings withReflections(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, on, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with edges smoothed this way. */
    public PictureSettings withAntiAliasing(AntiAliasing mode) {
        return new PictureSettings(ambientOcclusion, bloom, mode, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with the camera focused and its shutter open as {@code camera} says; {@link Lens#OFF}, neither. */
    public PictureSettings withLens(Lens camera) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, camera, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with the colour grade at this asset path (see {@link ColorLut}) after the tone curve; null, none. */
    public PictureSettings withLut(String path) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, path, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with the corners darkened by this much, 0 to 1; 0, not at all. */
    public PictureSettings withVignette(float strength) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, strength, clusteredLights, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with one bounce of the picture's light lighting what is near it, or not. */
    public PictureSettings withScreenSpaceGi(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, on, water, autoInstancing, autoLod);
    }

    /** These settings with the spot and point lights drawn from clusters in one pass, or a pass for each. */
    public PictureSettings withClusteredLights(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, on, screenSpaceGi, water, autoInstancing, autoLod);
    }

    /** These settings with the water of the scene drawn, or not. */
    public PictureSettings withWater(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, on, autoInstancing, autoLod);
    }

    /** These settings with identical solid things drawn in one instanced call, or a call for each. */
    public PictureSettings withAutoInstancing(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, on, autoLod);
    }

    /** These settings with what is small on the screen drawn with fewer triangles, or every mesh as it was given. */
    public PictureSettings withAutoLod(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, antiAliasing, exposure, atmosphere, softShadows, contactShadows,
                localShadows, occlusionRadius, autoExposure, fogDensity, fogFalloff, shafts, reflections, lens, lut, vignette, clusteredLights, screenSpaceGi, water, autoInstancing, on);
    }
}
