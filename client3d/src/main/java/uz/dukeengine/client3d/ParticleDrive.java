package uz.dukeengine.client3d;

import uz.dukeengine.core.view.UnitView;

/**
 * How a thing's motion drives the particle system it carries at a bone — the reference's {@code DirtSpray} and {@code
 * PowerslideSpray}, a puff scaled by velocity, turn and acceleration: asked each time the thing is drawn for how much
 * larger and how much more often the system lets its particles out. Drawing only.
 */
@FunctionalInterface
public interface ParticleDrive {

    /**
     * How the thing moves between this draw and the last.
     *
     * @param view         the thing as drawn now
     * @param speed        how fast it moved, world units a second
     * @param turn         how fast it turned, radians a second, its drawn yaw counted with its facing: positive one
     *                     way round, negative the other
     * @param acceleration how much its speed changed, world units a second each second; negative braking
     */
    record Motion(UnitView view, float speed, float turn, float acceleration) {
    }

    /**
     * What to multiply the system by.
     *
     * @param size how large its particles are born; 1 as its data has them
     * @param rate how many it lets out; 1 as its data has it, 0 for none
     */
    record Scales(float size, float rate) {
        /** Letting out nothing. */
        public static final Scales OFF = new Scales(1f, 0f);
    }

    Scales scales(Motion motion);
}
