package uz.dukeengine.client3d;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import uz.dukeengine.core.view.UnitView;

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
        float height = view.ownHeight() ? view.z() : Math.max(ground, view.z());
        return new Vector3f(view.x(), height + view.lift(), view.y());
    }

    /**
     * How far a model {@code top} high is sunk into the ground when {@code built} of it is built: all of it at 0, its top
     * at the ground; half at a half; none once it is whole; more than all as a sold building goes below nothing.
     */
    static float sunk(float built, float top) {
        return Math.max(0f, 1f - built) * top;
    }

    /**
     * How high a body's top stands above the ground its thing is placed on: measured off the model as it would hang from
     * its node, whether it hangs there yet or not.
     *
     * <p>Measured taken down and hung back. Brought up to date in place, a body hung this frame from a thing that
     * appeared this frame worked out its lights from a node that had none yet, and was drawn black all match: jME does
     * not work a body's lights out again until it is hung anew.
     */
    static float topOf(com.jme3.scene.Spatial body) {
        var parent = body.getParent();
        int at = parent == null ? -1 : parent.detachChild(body);
        body.updateModelBound();
        body.updateGeometricState();
        float top = body.getWorldBound() instanceof com.jme3.bounding.BoundingBox box
                ? box.getCenter().y + box.getYExtent() : 0f;
        if (parent != null) {
            parent.attachChildAt(body, at);
        }
        return top;
    }

    /** How far a look that rises as it is built rises: the height the game named, or else its model's own top. */
    static float riseOf(Visuals.UnitVisual look, com.jme3.scene.Spatial body) {
        return look.riseHeight >= 0f ? look.riseHeight : topOf(body);
    }

    /** How it is turned: its facing and the turn the game gives it beside it, then its pitch, then its roll. */
    static Quaternion turn(UnitView view) {
        var facing = new Quaternion().fromAngles(0f, -(view.orientation() + view.yaw()), 0f);
        if (view.pitch() == 0f && view.roll() == 0f) {
            return facing; // level, as every unit was drawn before
        }
        var pitch = new Quaternion().fromAngleAxis(view.pitch(), Vector3f.UNIT_Z);
        var roll = new Quaternion().fromAngleAxis(view.roll(), Vector3f.UNIT_X);
        return facing.mult(pitch).multLocal(roll);
    }
}
