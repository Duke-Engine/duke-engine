package uz.dukeengine.render;

/**
 * What a camera does to the picture the way a real one does: blur what stands off the plane it is focused on
 * ({@link DepthOfFieldFilter}) and what moves while its shutter is open ({@link MotionBlurFilter}).
 *
 * @param focusDistance  how far from the camera, along its view, the plane of sharp focus is, in world units taken as metres;
 *                       0, no depth of field and no filter
 * @param fStop          focal length over the aperture's diameter: the smaller, the shallower the focus
 * @param focalLengthMm  the lens's focal length in millimetres, which with the f-stop gives the aperture's size
 * @param shutter        how much of the time between two frames the shutter is open, 0 to 1 (0.5 is the film's 180 degrees);
 *                       0, no motion blur and no filter
 */
public record Lens(float focusDistance, float fStop, float focalLengthMm, float shutter) {

    /** No depth of field, no motion blur. */
    public static final Lens OFF = new Lens(0f, 2.8f, 50f, 0f);

    public Lens {
        if (!(fStop > 0f) || !(focalLengthMm > 0f)) {
            throw new IllegalArgumentException("f-stop and focal length must be above 0: " + fStop + ", " + focalLengthMm);
        }
        if (!(shutter >= 0f && shutter <= 1f)) {
            throw new IllegalArgumentException("shutter must be from 0 to 1: " + shutter);
        }
        // A lens cannot focus nearer than its own focal length.
        if (!(focusDistance == 0f || focusDistance > focalLengthMm / 1000f)) {
            throw new IllegalArgumentException("focusDistance must be 0 or beyond the focal length: " + focusDistance);
        }
    }

    public boolean depthOfField() {
        return focusDistance > 0f;
    }

    public boolean motionBlur() {
        return shutter > 0f;
    }

    /** The same lens focused on a plane this far away. */
    public Lens withFocus(float distance) {
        return new Lens(distance, fStop, focalLengthMm, shutter);
    }

    /** The same lens with the shutter open this much. */
    public Lens withShutter(float open) {
        return new Lens(focusDistance, fStop, focalLengthMm, open);
    }

    /**
     * The diameter in pixels of the disc a point far off the focus plane spreads to, for a picture {@code widthPixels} wide drawn
     * by a projection whose first element is {@code projection00} (1 over the tangent of half the horizontal field of view).
     * The thin lens: a point at {@code distance} is spread over the focus plane by {@code aperture * |distance - focus| / distance},
     * which is that many metres of a plane {@code 2 * focus / projection00} metres across. Keep DofCoc.glsllib the same.
     */
    public float reachPixels(float projection00, int widthPixels) {
        float aperture = focalLengthMm / 1000f / fStop;
        return aperture * projection00 / (2f * focusDistance) * widthPixels;
    }

    /** {@link #reachPixels} scaled by how far {@code distance} is from the plane: negative nearer than it, positive beyond. */
    public float confusionPixels(float distance, float projection00, int widthPixels) {
        return reachPixels(projection00, widthPixels) * (distance - focusDistance) / distance;
    }
}
