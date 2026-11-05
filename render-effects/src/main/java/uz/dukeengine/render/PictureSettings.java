package uz.dukeengine.render;

/**
 * What the finished picture goes through, and where the light of the sky comes from. Each effect may be left out, so a
 * game can pay for only what it wants and the bench can price each one.
 *
 * @param ambientOcclusion darkens the creases light barely reaches ({@link GtaoFilter})
 * @param bloom            bright things spill over their edges
 * @param fxaa             smooths stair-stepped edges
 * @param exposure         brightness of the picture before the tone curve bends it
 * @param atmosphere       the sky is the {@link Atmosphere} under the sun's direction and colour, and the ambient
 *                         the game asks for is no longer read for the sky's probe; off, the sky is the ambient's gradient
 * @param softShadows      the sun's shadows grow softer the further they fall; off, one narrow filter
 * @param contactShadows   a short ray over the screen's depth finds the shadow at a foot or in a crease
 * @param localShadows     how many spot and point lights may have a shadow at once, the ones that matter most to the
 *                         view picked each frame ({@link LocalShadowFilter}); the others stay lit, without one. 0, none
 * @param occlusionRadius  how far in world units something can stand and still darken a crease; above 0
 */
public record PictureSettings(boolean ambientOcclusion, boolean bloom, boolean fxaa, float exposure, boolean atmosphere,
        boolean softShadows, boolean contactShadows, int localShadows, float occlusionRadius) {

    public PictureSettings {
        if (!(occlusionRadius > 0f)) {
            throw new IllegalArgumentException("occlusionRadius must be above 0: " + occlusionRadius);
        }
    }

    /**
     * Every filter on, the sky left as the game's own sun and ambient make it: a game's colours keep their look.
     * At this exposure the curve leaves a mid-tone where it was. Three quarters of a metre of reach is a human-scale world's crease
     * between a wall and a floor, a doorway, the underside of a table.
     */
    public static final PictureSettings FULL = new PictureSettings(true, true, true, 0.7f, false, true, true, 4, 0.75f);

    /** These settings with the atmosphere on or off. */
    public PictureSettings withAtmosphere(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, on, softShadows, contactShadows, localShadows,
                occlusionRadius);
    }

    /** These settings with this many lights allowed a shadow, none for 0. */
    public PictureSettings withLocalShadows(int budget) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                Math.max(0, budget), occlusionRadius);
    }

    /** These settings with creases darkened by what stands this far (world units) from them. */
    public PictureSettings withOcclusionRadius(float radius) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                localShadows, radius);
    }
}
