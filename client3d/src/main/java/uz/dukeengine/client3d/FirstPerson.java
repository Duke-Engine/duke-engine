package uz.dukeengine.client3d;

/**
 * How a {@link FirstPersonCamera} walks and looks, in the scene's metres.
 *
 * @param eyeHeight   how high the eye stands over the floor
 * @param speed       how fast it walks, a second
 * @param sensitivity how far the view turns for each pixel the held mouse moves, in radians
 * @param reach       how far away a thing may be and still be pointed at
 * @param minX        the walls it walks within, on the floor's x and z
 */
public record FirstPerson(float eyeHeight, float speed, float sensitivity, float reach, float minX, float minZ,
        float maxX, float maxZ) {

    /** A person of the usual height, pace and reach, walking within these walls. */
    public static FirstPerson within(float minX, float minZ, float maxX, float maxZ) {
        return new FirstPerson(1.65f, 3f, 0.0025f, 2.5f, minX, minZ, maxX, maxZ);
    }
}
