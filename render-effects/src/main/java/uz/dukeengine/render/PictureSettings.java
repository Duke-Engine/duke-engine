package uz.dukeengine.render;

/**
 * What the finished picture goes through, and where the light of the sky comes from. Each effect may be left out, so a
 * game can pay for only what it wants and the bench can price each one.
 *
 * @param ambientOcclusion darkens the creases light barely reaches
 * @param bloom            bright things spill over their edges
 * @param fxaa             smooths stair-stepped edges
 * @param exposure         brightness of the picture before the tone curve bends it
 * @param atmosphere       the sky is the {@link Atmosphere} under the sun's direction and colour, and the ambient
 *                         the game asks for is no longer read for the sky's probe; off, the sky is the ambient's gradient
 * @param softShadows      the sun's shadows grow softer the further they fall; off, one narrow filter
 * @param contactShadows   a short ray over the screen's depth finds the shadow at a foot or in a crease
 * @param localShadows     how many spot and point lights may have a shadow at once, the ones that matter most to the
 *                         view picked each frame ({@link LocalShadowFilter}); the others stay lit, without one. 0, none
 */
public record PictureSettings(boolean ambientOcclusion, boolean bloom, boolean fxaa, float exposure, boolean atmosphere,
        boolean softShadows, boolean contactShadows, int localShadows) {

    /**
     * Every filter on, the sky left as the game's own sun and ambient make it: a game's colours keep their look.
     * At this exposure the curve leaves a mid-tone where it was.
     */
    public static final PictureSettings FULL = new PictureSettings(true, true, true, 0.7f, false, true, true, 4);

    /** These settings with the atmosphere on or off. */
    public PictureSettings withAtmosphere(boolean on) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, on, softShadows, contactShadows, localShadows);
    }

    /** These settings with this many lights allowed a shadow, none for 0. */
    public PictureSettings withLocalShadows(int budget) {
        return new PictureSettings(ambientOcclusion, bloom, fxaa, exposure, atmosphere, softShadows, contactShadows,
                Math.max(0, budget));
    }
}
