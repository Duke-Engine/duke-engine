package uz.dukeengine.core.pathfind;

import java.util.Set;
import uz.dukeengine.core.thing.Kind;

/**
 * What of the still things on the ground is in the way of a route — the reference's {@code
 * Pathfinder::classifyObjectFootprint}, which paths round structures alone: not a small one, not one standing more than
 * a cell over the ground, never a mine, a projectile or a bridge tower, and a fence along its line only (a template's
 * {@code FenceWidth}, which the engine reads off {@link uz.dukeengine.core.thing.Solid}).
 *
 * @param inTheWay    the kinds of thing in the way; none, every still thing with a shape — as every one was before a
 *                    game could say
 * @param outOfTheWay the kinds never in the way, whatever else they are: the reference's mines, projectiles, bridge
 *                    towers, and whatever the game marks small
 * @param aboveGround how far over the ground a thing may stand and still be in the way — the reference's cell, 10;
 *                    0 for any height: a bridge 80 over its valley leaves the valley open
 * @param laid        the things that lay a class of ground over their footprints instead of blocking them, the first
 *                    that matches: a structure lying in ruins laying rubble ({@code PathfindCell::setTypeAsObstacle},
 *                    {@code BODY_RUBBLE}), which infantry walk and vehicles go round — see {@link Laid}
 */
public record ObstacleRules(Set<Kind> inTheWay, Set<Kind> outOfTheWay, float aboveGround, java.util.List<Laid> laid) {

    /** Every still thing with a shape in the way, at any height: what the engine did before a game could say. */
    public static final ObstacleRules EVERYTHING = new ObstacleRules(Set.of(), Set.of(), 0f);

    public ObstacleRules {
        inTheWay = inTheWay == null ? Set.of() : Set.copyOf(inTheWay);
        outOfTheWay = outOfTheWay == null ? Set.of() : Set.copyOf(outOfTheWay);
        laid = laid == null ? java.util.List.of() : java.util.List.copyOf(laid);
    }

    /** Rules in which every thing in the way blocks: every game's from before a thing could lay a class. */
    public ObstacleRules(Set<Kind> inTheWay, Set<Kind> outOfTheWay, float aboveGround) {
        this(inTheWay, outOfTheWay, aboveGround, java.util.List.of());
    }

    /**
     * A thing that holds {@code word}, or is of {@code kind}, lays the class of ground {@code ground} over its
     * footprint instead of blocking it — laid again as that changes.
     */
    public record Laid(String word, Kind kind, String ground) {
    }
}
