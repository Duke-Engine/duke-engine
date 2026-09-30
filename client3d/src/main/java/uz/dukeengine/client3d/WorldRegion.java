package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.ViewRays;

/**
 * The part of the window the world is drawn in, in shares of the window from its top left — the whole of it, or the
 * top 80% where an RTS's bar takes the foot of the screen (the reference's {@code W3DView::setHeight(height * 0.80)}).
 * The camera renders into it with its aspect, its vertical angle kept and the region's shape making the horizontal
 * one, as the reference's does; picking a unit, the drag box, placing a building and scrolling at the edge are all
 * measured inside it, and the world takes nothing from outside it.
 */
record WorldRegion(float left, float top, float width, float height) {

    static final WorldRegion WHOLE = new WorldRegion(0f, 0f, 1f, 1f);

    WorldRegion {
        left = Math.clamp(left, 0f, 1f);
        top = Math.clamp(top, 0f, 1f);
        width = Math.clamp(width, 0f, 1f - left);
        height = Math.clamp(height, 0f, 1f - top);
        if (width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("a world drawn in nothing: " + width + " by " + height);
        }
    }

    boolean isWhole() {
        return equals(WHOLE);
    }

    /** Draw the camera into this part of its window, with this part's shape — nowhere in the world moved. */
    void applyTo(Camera camera) {
        applyTo(camera, Float.NaN);
    }

    /**
     * The same, seeing {@code fieldOfView} degrees across the whole window — the part's height following its shape, as
     * the reference keeps its horizontal half-width — or, for NaN, keeping the camera's vertical angle.
     */
    void applyTo(Camera camera, float fieldOfView) {
        camera.setViewPort(left, left + width, 1f - top - height, 1f - top);
        float fovY = Float.isNaN(fieldOfView)
                ? FastMath.RAD_TO_DEG * 2f * FastMath.atan(camera.getFrustumTop() / camera.getFrustumNear())
                : (float) Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(fieldOfView) / 2.0)
                        * camera.getHeight() * height / camera.getWidth()));
        camera.setFrustumPerspective(fovY, camera.getWidth() * width / (camera.getHeight() * height),
                camera.getFrustumNear(), camera.getFrustumFar());
    }

    /**
     * The eye of a camera laid into it, and the rays through its four corners, in the map's axes — its y the scene's
     * z, z up — see {@link ViewRays}.
     */
    ViewRays raysThrough(Camera camera) {
        int wide = camera.getWidth();
        int tall = camera.getHeight();
        return new ViewRays(onTheMap(camera.getLocation()),
                ray(camera, leftPixel(wide), topPixel(tall)), ray(camera, rightPixel(wide), topPixel(tall)),
                ray(camera, rightPixel(wide), bottomPixel(tall)), ray(camera, leftPixel(wide), bottomPixel(tall)));
    }

    /** The corners of it, as the input manager counts the window: top left, top right, bottom right, bottom left. */
    float[][] corners(int windowWidth, int windowHeight) {
        return new float[][] {
            {leftPixel(windowWidth), topPixel(windowHeight)}, {rightPixel(windowWidth), topPixel(windowHeight)},
            {rightPixel(windowWidth), bottomPixel(windowHeight)}, {leftPixel(windowWidth), bottomPixel(windowHeight)}};
    }

    private static Coord3D ray(Camera camera, float x, float y) {
        var near = camera.getWorldCoordinates(new Vector2f(x, y), 0f);
        return onTheMap(camera.getWorldCoordinates(new Vector2f(x, y), 1f).subtractLocal(near).normalizeLocal());
    }

    private static Coord3D onTheMap(Vector3f scene) {
        return new Coord3D(scene.x, scene.z, scene.y);
    }

    /** Whether a point of the window, counted as the input manager counts it — up from the bottom — is in it. */
    boolean contains(float x, float yFromBottom, int windowWidth, int windowHeight) {
        return x >= leftPixel(windowWidth) && x < rightPixel(windowWidth)
                && yFromBottom >= bottomPixel(windowHeight) && yFromBottom < topPixel(windowHeight);
    }

    float leftPixel(int windowWidth) {
        return left * windowWidth;
    }

    float rightPixel(int windowWidth) {
        return (left + width) * windowWidth;
    }

    /** Its bottom edge, up from the window's bottom. */
    float bottomPixel(int windowHeight) {
        return (1f - top - height) * windowHeight;
    }

    /** Its top edge, up from the window's bottom. */
    float topPixel(int windowHeight) {
        return (1f - top) * windowHeight;
    }
}
