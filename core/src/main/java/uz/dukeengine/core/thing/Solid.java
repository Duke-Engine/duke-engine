package uz.dukeengine.core.thing;

/** A thing that takes up room: what collision, footprints and path clearance read. */
public interface Solid extends ThingTemplate {

    Geometry geometry();

    /**
     * How long a fence it is, laid along its facing: the reference's {@code FenceWidth}, a fence in a route's way along
     * its line alone, not over its whole shape; 0 for no fence.
     */
    default float fenceWidth() {
        return 0f;
    }

    /** How far behind its position along its facing its fence starts: the reference's {@code FenceXOffset}. */
    default float fenceOffset() {
        return 0f;
    }

    /**
     * Whether it hides what is behind it from sight while it stands — a shut gate, a door, a curtain, a thick hedge
     * ({@code BlocksSight = Yes}): the sight cells its footprint covers are stone to every looker's line of sight, as
     * the map's stone is, where the game has stone hide the ground at all ({@code GameLogic.setSightHiddenByStone}).
     * The cells themselves are seen, what is behind them not; gone, or another template in its place, they are open on
     * the next look. Pathing is untouched.
     */
    default boolean blocksSight() {
        return false;
    }

    /** The shape of any template: its own if it is solid, a point that collides with nothing if not. */
    static Geometry of(ThingTemplate template) {
        return template instanceof Solid solid && solid.geometry() != null ? solid.geometry() : Geometry.POINT;
    }
}
