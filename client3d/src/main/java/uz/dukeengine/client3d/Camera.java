package uz.dukeengine.client3d;

/**
 * How the mouse moves the camera: a shove when the cursor comes near the edge of the screen.
 *
 * @param edgeMargin       how close to the edge the cursor has to be to shove the camera; 0 is off
 * @param edgeSpeedPercent how fast it shoves, as a percentage of what the keys move the camera at
 */
public record Camera(float edgeMargin, int edgeSpeedPercent) {

    /** What a block leaves out. */
    public static final Camera DEFAULTS = new Camera(0f, 100);
}
