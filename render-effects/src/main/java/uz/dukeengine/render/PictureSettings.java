package uz.dukeengine.render;

/**
 * What the finished picture goes through. Each effect may be left out, so a game can pay for only what it wants and the
 * bench can price each one.
 *
 * @param ambientOcclusion darkens the creases light barely reaches
 * @param bloom            bright things spill over their edges
 * @param fxaa             smooths stair-stepped edges
 * @param exposure         brightness of the picture before the tone curve bends it
 */
public record PictureSettings(boolean ambientOcclusion, boolean bloom, boolean fxaa, float exposure) {

    /** Every effect on, with a mid-tone colour kept where it was: {@link com.jme3.post.filters.ToneMapFilter#setExposure}. */
    public static final PictureSettings FULL = new PictureSettings(true, true, true, 1.47f);
}
