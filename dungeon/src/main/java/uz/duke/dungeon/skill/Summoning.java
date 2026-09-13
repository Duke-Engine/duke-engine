package uz.duke.dungeon.skill;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;
import uz.duke.dungeon.ai.SightLine;

/**
 * Where what a caster calls up rises: {@code distance} from it, first toward where it
 * was aimed and then turned aside a step at a time, one way and then the other.
 *
 * <p>Only on open floor. Not stone, and not the solid things baked into the floor with
 * it -- the rule the furniture is held to. No step up or down from where the caster
 * stands, which keeps a stair and another storey out. Nobody's body in the way,
 * nothing but air between the caster and the spot, and each spot at least
 * {@code apart} from the ones already taken, so two that rise together do not rise
 * inside each other.
 *
 * <p>No dice: the angles are tried in a fixed order and worked with {@link StrictMath},
 * so a room calls its skeletons up in the same places on every machine.
 */
public final class Summoning {

    /** How far the ground may stand from the caster's and still be the floor it is on. */
    private static final float SAME_FLOOR = 0.01f;

    private Summoning() {
    }

    /** Up to {@code count} spots, in the order they were found; fewer, or none, if the floor has no more. */
    public static List<Coord3D> spots(World world, GameObject caster, Coord3D towards,
            float distance, int count, float apart, float turnDegrees, int turns) {
        var from = caster.getPosition();
        double ahead = towards == null ? caster.getOrientation()
                : StrictMath.atan2(towards.y() - from.y(), towards.x() - from.x());
        double turn = StrictMath.toRadians(turnDegrees);
        float floor = world.groundHeight(from);
        var found = new ArrayList<Coord3D>(count);
        for (int attempt = 0; attempt <= 2 * turns && found.size() < count; attempt++) {
            int aside = (attempt + 1) / 2;
            int side = attempt % 2 == 1 ? 1 : -1;
            double angle = ahead + side * aside * turn;
            var flat = new Coord3D(from.x() + (float) (StrictMath.cos(angle) * distance),
                    from.y() + (float) (StrictMath.sin(angle) * distance), from.z());
            var spot = new Coord3D(flat.x(), flat.y(), world.groundHeight(flat));
            if (open(world, caster, spot, floor) && clearOf(found, spot, apart)) {
                found.add(spot);
            }
        }
        return found;
    }

    private static boolean open(World world, GameObject caster, Coord3D spot, float floor) {
        return !world.isGroundBlocked(spot)
                && Math.abs(spot.z() - floor) <= SAME_FLOOR
                && world.findBlocker(caster, spot) == null
                && SightLine.clear(world, caster.getPosition(), spot);
    }

    private static boolean clearOf(List<Coord3D> taken, Coord3D spot, float apart) {
        for (var other : taken) {
            if (other.distance(spot) < apart) {
                return false;
            }
        }
        return true;
    }
}
