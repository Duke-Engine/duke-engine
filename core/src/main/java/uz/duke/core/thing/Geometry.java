package uz.duke.core.thing;

/**
 * The physical shape an object occupies in the world, ported from SAGE's
 * {@code GeometryInfo}.
 *
 * <p>Without this an object is a dimensionless point: units walk through each
 * other and through buildings, and "is this spot free?" cannot be asked. The
 * shape is authored in INI alongside the rest of a template:
 *
 * <pre>{@code
 * Geometry = BOX
 * GeometryMajorRadius = 20   ; half-length along the object's facing
 * GeometryMinorRadius = 12   ; half-width across it
 * GeometryHeight = 15
 * }</pre>
 *
 * <p>Shapes are immutable and shared by every instance of a template; where an
 * instance actually stands is a {@link Footprint}.
 *
 * <p>The default is {@link #POINT} — zero extent, collides with nothing — so
 * data written before geometry existed keeps its old behaviour until it opts in.
 */
public sealed interface Geometry {

    /** No extent: collides with nothing. The default for templates that declare no geometry. */
    Geometry POINT = new Sphere(0f);

    /** A ball: the same in every direction, so facing does not matter. */
    record Sphere(float radius) implements Geometry {
        public Sphere {
            requireNonNegative(radius, "radius");
        }

        @Override
        public float footprintRadius() {
            return radius;
        }

        @Override
        public float height() {
            return radius * 2f;
        }
    }

    /** An upright cylinder — the usual shape for a unit standing on the ground. */
    record Cylinder(float radius, float height) implements Geometry {
        public Cylinder {
            requireNonNegative(radius, "radius");
            requireNonNegative(height, "height");
        }

        @Override
        public float footprintRadius() {
            return radius;
        }
    }

    /**
     * An upright box that turns with the object: {@code majorRadius} is its
     * half-length along the facing, {@code minorRadius} its half-width across.
     * The shape most buildings want.
     */
    record Box(float majorRadius, float minorRadius, float height) implements Geometry {
        public Box {
            requireNonNegative(majorRadius, "majorRadius");
            requireNonNegative(minorRadius, "minorRadius");
            requireNonNegative(height, "height");
        }

        @Override
        public float footprintRadius() {
            // Math.sqrt is correctly rounded, so this is identical on every machine.
            return (float) Math.sqrt(majorRadius * majorRadius + minorRadius * minorRadius);
        }
    }

    /**
     * The radius of the circle on the ground that encloses this shape — the cheap
     * broad-phase test, and the exact one for spheres and cylinders.
     */
    float footprintRadius();

    /** How tall the shape stands. */
    float height();

    /** True when the shape has no extent and therefore never collides. */
    default boolean isPoint() {
        return footprintRadius() <= 0f;
    }

    private static void requireNonNegative(float value, String field) {
        if (value < 0f) {
            throw new IllegalArgumentException("geometry " + field + " cannot be negative: " + value);
        }
    }
}
