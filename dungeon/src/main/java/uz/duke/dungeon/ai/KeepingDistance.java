package uz.duke.dungeon.ai;

import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.World;

/**
 * Where a monster that keeps its distance steps back to.
 *
 * <p>Straight away from whatever is too near, if the stone allows. Otherwise it turns
 * a little further aside each try, left before right, and takes the first spot that
 * is open ground with nothing but air between here and there. Nowhere is an answer
 * too: cornered, it stands and fights where it is.
 *
 * <p>Deterministic. The tries come in a fixed order and the angles are
 * {@code StrictMath}'s, so two machines back the same skeleton into the same corner.
 */
final class KeepingDistance {

    private KeepingDistance() {
    }

    /**
     * The spot to back away to, or {@code null} when every try is shut.
     *
     * @param from        where it stands
     * @param threat      what it is backing away from
     * @param far         how far from the threat the spot should be
     * @param turnDegrees how much further aside each try turns
     * @param turns       how many tries each side of straight back
     */
    static Coord3D stepBack(World world, Coord3D from, Coord3D threat, float far,
            float turnDegrees, int turns) {
        double away = StrictMath.atan2(from.y() - threat.y(), from.x() - threat.x());
        double turn = StrictMath.toRadians(turnDegrees);
        for (int attempt = 0; attempt <= 2 * turns; attempt++) {
            int aside = (attempt + 1) / 2;
            int side = attempt % 2 == 1 ? 1 : -1;
            double angle = away + side * aside * turn;
            var spot = new Coord3D(threat.x() + (float) (StrictMath.cos(angle) * far),
                    threat.y() + (float) (StrictMath.sin(angle) * far), from.z());
            if (!world.isGroundBlocked(spot) && SightLine.clear(world, from, spot)) {
                return spot;
            }
        }
        return null;
    }
}
