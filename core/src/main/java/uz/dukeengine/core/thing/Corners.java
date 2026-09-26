package uz.dukeengine.core.thing;

/**
 * How far a thing's wheels stand raised at each of its corners, along its model's up — lowered below 0 — as a
 * suspension holds them: the reference's {@code Drawable::calcPhysicsXformWheels}, each tyre bone raised or dropped by
 * its corner's height. How it is drawn only, as its pitch and roll are ({@link GameObject#setCorners}).
 */
public record Corners(float frontLeft, float frontRight, float backLeft, float backRight) {

    /** Every wheel where its model has it. */
    public static final Corners LEVEL = new Corners(0f, 0f, 0f, 0f);
}
