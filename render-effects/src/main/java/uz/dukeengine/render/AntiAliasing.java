package uz.dukeengine.render;

/** How the stair steps on edges are smoothed. */
public enum AntiAliasing {

    /** Left as drawn. */
    NONE,

    /** One filter over the finished picture: cheap, soft, and it cannot gather what a frame is too coarse to hold. */
    FXAA,

    /**
     * Frames summed over time ({@link TaaFilter}): the camera moves a fraction of a pixel each frame and what the last
     * frames saw is carried along by the motion, so edges, thin lines and the grain of the noisy effects settle to a
     * smooth picture, and a light sharpen ({@link SharpenFilter}) brings back the bite the summing takes.
     */
    TAA
}
