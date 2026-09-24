package uz.dukeengine.client3d;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import uz.dukeengine.game.view.UnitView;

/**
 * Where a thing is drawn and how it is turned: at its own height — a jet at cruising height, a shell at the top of
 * its arc — or on the ground where that is higher, so a ground unit whose height is stale is never drawn under the
 * map; turned by its facing, then its pitch, then its roll. The minimap and the fog go on reading its x and y.
 *
 * <p>Its forward is its x, as the simulation's facing is: pitch turns that up about its side, roll banks it about
 * its forward, to its right as drawn.
 */
final class UnitPlacement {

    private UnitPlacement() {
    }

    /** Where it stands, in the client's frame. */
    static Vector3f where(UnitView view, WorldMoments.Floor floor) {
        float ground = floor.at(view.x(), view.y());
        return new Vector3f(view.x(), view.ownHeight() ? view.z() : Math.max(ground, view.z()), view.y());
    }

    /** How it is turned: its facing, then its pitch, then its roll. */
    static Quaternion turn(UnitView view) {
        var facing = new Quaternion().fromAngles(0f, -view.orientation(), 0f);
        if (view.pitch() == 0f && view.roll() == 0f) {
            return facing; // level, as every unit was drawn before
        }
        var pitch = new Quaternion().fromAngleAxis(view.pitch(), Vector3f.UNIT_Z);
        var roll = new Quaternion().fromAngleAxis(view.roll(), Vector3f.UNIT_X);
        return facing.mult(pitch).multLocal(roll);
    }
}
