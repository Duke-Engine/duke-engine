package uz.dukeengine.client3d;

import java.util.List;

/**
 * How a {@link FirstPersonCamera} walks and looks, in the scene's metres.
 *
 * @param eyeHeight   how high the eye stands over the floor
 * @param speed       how fast it walks, a second
 * @param sensitivity how far the view turns for each pixel the held mouse moves, in radians
 * @param reach       how far away a thing may be and still be pointed at
 * @param minX        the walls it walks within, on the floor's x and z
 * @param radius      how near it comes to a blocked footprint
 * @param blocked     what stands on the floor and is not walked through: benches, machines
 */
public record FirstPerson(float eyeHeight, float speed, float sensitivity, float reach, float minX, float minZ,
        float maxX, float maxZ, float radius, List<Footprint> blocked) {

    public FirstPerson {
        blocked = List.copyOf(blocked);
    }

    /** A person of the usual height, pace and reach, walking within these walls. */
    public static FirstPerson within(float minX, float minZ, float maxX, float maxZ) {
        return new FirstPerson(1.65f, 3f, 0.0025f, 2.5f, minX, minZ, maxX, maxZ, 0.3f, List.of());
    }

    /** The same person, kept off these footprints too. */
    public FirstPerson blocking(List<Footprint> footprints) {
        return new FirstPerson(eyeHeight, speed, sensitivity, reach, minX, minZ, maxX, maxZ, radius, footprints);
    }

    /** A rectangle on the floor's x and z. */
    public record Footprint(float minX, float minZ, float maxX, float maxZ) {

        boolean acrossX(float x, float margin) {
            return x > minX - margin && x < maxX + margin;
        }

        boolean acrossZ(float z, float margin) {
            return z > minZ - margin && z < maxZ + margin;
        }
    }
}
