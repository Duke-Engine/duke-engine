package uz.dukeengine.core.module;

/**
 * A module that quickens or slows its thing's walking, asked each frame by {@link MoveUpdate}: a mount's buff, a haste
 * of the legs. Every one on the thing is multiplied together; 1 changes nothing.
 */
public interface SpeedModifier {

    /** The share of its own speed it walks at now: 2 twice as fast, 0.5 half. */
    float speedShare();
}
