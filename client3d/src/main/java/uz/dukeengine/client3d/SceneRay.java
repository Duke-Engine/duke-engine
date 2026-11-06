package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingBox;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

/**
 * A line through the map's standing scenery — its walls, towers and relief, whatever the ground is built of — asked how
 * far it runs before something stops it: what a camera backing away from the hero needs to know. Done on the meshes
 * themselves, so an arched gate is open under its arch and a wall of eighty units is a wall.
 */
final class SceneRay {

    private SceneRay() {
    }

    /**
     * How far from {@code from} along the unit {@code way} the line runs, up to {@code length}, before a thing in {@code
     * scenery} at least {@code minHeight} high is in the way. Things hidden (a cell the fog has put away) and things
     * lower than that are passed through.
     */
    static float run(Spatial scenery, Vector3f from, Vector3f way, float length, float minHeight) {
        var ray = new Ray(from, way);
        ray.setLimit(length);
        var hits = new CollisionResults();
        scenery.collideWith(ray, hits);
        for (var hit : hits) {
            var geometry = hit.getGeometry();
            if (shown(geometry) && high(geometry) >= minHeight) {
                return Math.min(length, hit.getDistance());
            }
        }
        return length;
    }

    /** Whether nothing from the geometry up to the root has been put away. */
    private static boolean shown(Spatial spatial) {
        for (var up = spatial; up != null; up = up.getParent()) {
            if (up.getCullHint() == Spatial.CullHint.Always) {
                return false;
            }
        }
        return true;
    }

    /** How high the geometry stands, by its bounds in the world. */
    private static float high(Spatial geometry) {
        return geometry.getWorldBound() instanceof BoundingBox box ? box.getYExtent() * 2f : Float.MAX_VALUE;
    }
}
