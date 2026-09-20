package uz.dukeengine.core.thing;

/** A thing that takes up room: what collision, footprints and path clearance read. */
public interface Solid extends ThingTemplate {

    Geometry geometry();

    /** The shape of any template: its own if it is solid, a point that collides with nothing if not. */
    static Geometry of(ThingTemplate template) {
        return template instanceof Solid solid && solid.geometry() != null ? solid.geometry() : Geometry.POINT;
    }
}
