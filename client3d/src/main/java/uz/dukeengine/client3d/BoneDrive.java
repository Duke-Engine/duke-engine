package uz.dukeengine.client3d;

import uz.dukeengine.core.view.UnitView;

/**
 * A bone of a thing's model given its pose by the game each time it is drawn — the reference's {@code W3DTruckDraw}: a
 * trailer swung about its hitch by the turn, a dump bed lifted. See {@link Visuals.UnitVisual#driveBone}.
 */
@FunctionalInterface
public interface BoneDrive {

    /**
     * What the drive is told of its thing now.
     *
     * @param view     the thing as the picture has it
     * @param turnRate how fast it turns, degrees a second, positive as its facing grows
     */
    record Input(UnitView view, float turnRate) {
    }

    /**
     * The bone's pose, from the one the file gives it: turned {@code degrees} about the axis {@code (axisX, axisY,
     * axisZ)} in the model's frame (x its forward, y its up), moved by the offset, and shown or hidden.
     */
    record Pose(float axisX, float axisY, float axisZ, float degrees, float dx, float dy, float dz, boolean visible) {

        /** As the file has it. */
        public static final Pose REST = new Pose(0f, 1f, 0f, 0f, 0f, 0f, 0f, true);

        /** Turned about the model's up by {@code degrees}. */
        public static Pose turned(float degrees) {
            return new Pose(0f, 1f, 0f, degrees, 0f, 0f, 0f, true);
        }
    }

    Pose pose(Input input);
}
