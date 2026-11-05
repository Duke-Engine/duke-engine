package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Plane;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.ShadowUtil;
import com.jme3.util.TempVars;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Four shadow maps over the stretch of the view that has something to fall on, each drawn so that a moving camera does
 * not make its edges crawl: a map covers the sphere round its slice of the view, whose size does not turn with the
 * camera, and moves over the world in whole texels. The fork's own fit crops to casters and receivers, so its texels change
 * size each frame; its splits are kept for the games drawn as before (see {@link #splits}).
 *
 * <p>A map is placed by the sphere but drawn only with the casters whose shadow can reach its slice, which the sphere much
 * exceeds: a caster elsewhere has no pixel to shade in this map, and a crowd drawn four times costs four crowds.
 */
final class SunCascades extends DirectionalLightShadowRenderer {

    static final int CASCADES = 4;
    /** The share of a cascade that fades into the next: as {@code BLEND} in SunShadows.frag. */
    static final float BLEND = 0.15f;
    /** Nearly all logarithmic: an arm's length from the eye needs the texels, the distance does not. */
    private static final float LAMBDA = 0.95f;
    /** Further than any caster's bound could be asked to reach toward the sun. */
    private static final float SUNWARD = 1e6f;
    /** How far past its slice a map keeps casters, in texels: past the filter's widest reach, {@code WIDEST} in the shader. */
    private static final float MARGIN = 14f;

    private final int size;
    private final Predicate<Geometry> casts;
    private final Vector3f[] corners = new Vector3f[8];
    private final Vector3f centre = new Vector3f();
    private final Matrix4f lightSpace = new Matrix4f();
    private final Vector4f texels = new Vector4f();
    private final Vector4f depths = new Vector4f();
    private final Vector4f viewDepth = new Vector4f();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f toSun = new Vector3f();
    private final Vector3f sunInView = new Vector3f();
    private BoundingBox seen;
    private Vector3f sun;

    SunCascades(AssetManager assets, int size, Predicate<Geometry> casts) {
        super(assets, size, CASCADES);
        this.size = size;
        this.casts = casts;
        for (int i = 0; i < corners.length; i++) {
            corners[i] = new Vector3f();
        }
    }

    void wear(Material material) {
        setPostShadowMaterial(material);
    }

    /** The sun that lights the scene, where this light is another: a look's raised one. */
    void sun(Vector3f direction) {
        sun = direction;
    }

    @Override
    protected void updateShadowCams(Camera view) {
        var direction = light.getDirection();
        // From no last frame's up: a rotation built on itself drifts, and every texel would drift with it.
        shadowCam.lookAtDirection(direction, Math.abs(direction.y) > 0.99f ? Vector3f.UNIT_Z : Vector3f.UNIT_Y);
        shadowCam.update();

        for (var scene : viewPort.getScenes()) {
            ShadowUtil.getGeometriesInCamFrustum(scene, view, RenderQueue.ShadowMode.Receive, lightReceivers);
        }
        seen = boundOf(lightReceivers);
        float near = Math.max(view.getFrustumNear(), 0.001f);
        float far = zFarOverride > 0f ? Math.min(view.getFrustumFar(), zFarOverride) : view.getFrustumFar();
        var range = seenBetween(view, seen, near, far, corners);
        splits(range[0], range[1], LAMBDA, splitsArray);
        splits.set(splitsArray[1], splitsArray[2], splitsArray[3], splitsArray[4]);
    }

    @Override
    protected GeometryList getOccludersToRender(int index, GeometryList occluders) {
        var view = viewPort.getCamera();
        // This map also serves the end of the last one, where that fades into this.
        float from = index == 0 ? splitsArray[0]
                : splitsArray[index] - BLEND * (splitsArray[index] - splitsArray[index - 1]);
        float to = splitsArray[index + 1];
        float radius = sphere(view, from, to, centre);
        lightSpace.set(shadowCam.getViewMatrix());
        // Depth as distance along the light, the way the fork's extractor expects it to grow.
        lightSpace.m20 = -lightSpace.m20;
        lightSpace.m21 = -lightSpace.m21;
        lightSpace.m22 = -lightSpace.m22;
        lightSpace.m23 = -lightSpace.m23;
        var at = lightSpace.mult(centre);
        float texel = 2f * radius / size;
        float x = snap(at.x, texel);
        float y = snap(at.y, texel);
        float furthest = at.z + radius;

        var reached = new BoundingBox(new Vector3f(x, y, at.z), radius, radius, radius);
        var overTheMap = new BoundingBox(new Vector3f(x, y, furthest - SUNWARD), radius, radius, SUNWARD);
        if (seen != null) {
            var vars = TempVars.get();
            var extractor = new ShadowUtil.OccludersExtractor(lightSpace, 0, overTheMap, reached, occluders, vars);
            viewPort.getScenes().forEach(extractor::addOccluders);
            vars.release();
            keepCasters(occluders, slicePlanes(view, from, to), texel * MARGIN);
        }

        float nearest = reached.getCenter().z - reached.getZExtent();
        shadowCam.setProjectionMatrix(ortho(x, y, radius, nearest, furthest));
        setComponent(texels, index, texel);
        setComponent(depths, index, furthest - nearest);
        return occluders;
    }

    private static void setComponent(Vector4f vector, int index, float value) {
        switch (index) {
            case 0 -> vector.x = value;
            case 1 -> vector.y = value;
            case 2 -> vector.z = value;
            default -> vector.w = value;
        }
    }

    private void keepCasters(GeometryList occluders, Plane[] slice, float margin) {
        var all = new ArrayList<Geometry>(occluders.size());
        for (var geometry : occluders) {
            all.add(geometry);
        }
        occluders.clear();
        for (var geometry : all) {
            if (casts.test(geometry) && reaches(geometry.getWorldBound(), light.getDirection(), seen, slice, margin)) {
                occluders.add(geometry);
            }
        }
    }

    @Override
    protected void setMaterialParameters(Material material) {
        super.setMaterialParameters(material);
        var view = viewPort.getCamera().getViewMatrix();
        viewDepth.set(-view.m20, -view.m21, -view.m22, -view.m23);
        material.setVector4("ViewDepth", viewDepth);
        material.setFloat("SplitStart", splitsArray[0]);
        material.setMatrix4("ViewProjection", viewPort.getCamera().getViewProjectionMatrix());
        material.setVector4("CascadeTexels", texels);
        material.setVector4("CascadeDepths", depths);
        var rotation = shadowCam.getViewMatrix();
        material.setVector3("LightRight", right.set(rotation.m00, rotation.m01, rotation.m02));
        material.setVector3("LightUp", up.set(rotation.m10, rotation.m11, rotation.m12));
        material.setVector3("ToSun", toSun.set(sun == null ? light.getDirection() : sun).negateLocal());
        material.setVector3("SunInView", view.multNormal(toSun, sunInView));
    }

    /**
     * Where the slices of the view end, from {@code near} to {@code far}, in {@code out}: Parallel Split Shadow Maps' mix of
     * log and even. {@code PssmShadowUtil} divides by one more slice than there are, which leaves the last far the longest;
     * left as it is, since the games drawn as before stand on it.
     */
    static void splits(float near, float far, float lambda, float[] out) {
        int count = out.length - 1;
        for (int i = 0; i <= count; i++) {
            float share = i / (float) count;
            float log = near * FastMath.pow(far / near, share);
            float even = near + (far - near) * share;
            out[i] = log * lambda + even * (1f - lambda);
        }
        out[0] = near;
        out[count] = far;
    }

    /**
     * The smallest sphere round the view between {@code near} and {@code far}: its radius, and its centre in
     * {@code centre}. Worked from the frustum's shape alone, so it is the same to the last bit for as long as the slice is,
     * wherever the camera stands and however it turns. shortcut: a frustum is taken as symmetric, as every camera here is;
     * use the corners when a game draws an off-axis one.
     */
    static float sphere(Camera view, float near, float far, Vector3f centre) {
        float top = view.getFrustumTop();
        float right = view.getFrustumRight();
        float wide = FastMath.sqrt(top * top + right * right);
        boolean parallel = view.isParallelProjection();
        float nearWide = parallel ? wide : wide / view.getFrustumNear() * near;
        float farWide = parallel ? wide : wide / view.getFrustumNear() * far;
        float along = (far * far - near * near + farWide * farWide - nearWide * nearWide) / (2f * (far - near));
        along = FastMath.clamp(along, near, far);
        view.getDirection().mult(along, centre).addLocal(view.getLocation());
        float toNear = (along - near) * (along - near) + nearWide * nearWide;
        float toFar = (far - along) * (far - along) + farWide * farWide;
        return FastMath.sqrt(Math.max(toNear, toFar));
    }

    /**
     * Whether a caster's shadow can fall inside the {@code slice} of the view: its bound, swept along the light for as long
     * as any of it stays in the box {@code seen} of all that can receive, is not wholly outside one of the planes.
     */
    static boolean reaches(BoundingVolume caster, Vector3f light, BoundingBox seen, Plane[] slice, float margin) {
        if (!(caster instanceof BoundingBox box)) {
            return true;
        }
        var from = box.getCenter();
        float xWide = box.getXExtent();
        float yWide = box.getYExtent();
        float zWide = box.getZExtent();
        float sweep = Math.min(Math.min(leaving(from.x, xWide, seen.getCenter().x, seen.getXExtent(), light.x),
                leaving(from.y, yWide, seen.getCenter().y, seen.getYExtent(), light.y)),
                leaving(from.z, zWide, seen.getCenter().z, seen.getZExtent(), light.z));
        sweep = Math.max(sweep, 0f);
        for (var plane : slice) {
            var normal = plane.getNormal();
            float furthest = normal.dot(from) - plane.getConstant() + margin
                    + Math.abs(normal.x) * xWide + Math.abs(normal.y) * yWide + Math.abs(normal.z) * zWide
                    + Math.max(normal.dot(light) * sweep, 0f);
            if (furthest < 0f) {
                return false;
            }
        }
        return true;
    }

    /** How long, moving along {@code direction}, until the last of a span has passed the far face of a box's span. */
    private static float leaving(float centre, float wide, float boxCentre, float boxWide, float direction) {
        if (direction > 1e-5f) {
            return (boxCentre + boxWide - (centre - wide)) / direction;
        }
        return direction < -1e-5f ? (boxCentre - boxWide - (centre + wide)) / direction : Float.POSITIVE_INFINITY;
    }

    /** {@code value} on the nearest whole multiple of {@code texel}. */
    static float snap(float value, float texel) {
        return Math.round(value / texel) * texel;
    }

    /** Light space (x and y across the map, z along the light from the sun) into a shadow map's clip space. */
    static Matrix4f ortho(float x, float y, float radius, float nearest, float furthest) {
        float across = 1f / radius;
        float along = 2f / (furthest - nearest);
        // A view matrix looks down -z, so what the light calls its depth is -z there.
        return new Matrix4f(across, 0f, 0f, -x * across,
                0f, across, 0f, -y * across,
                0f, 0f, -along, -nearest * along - 1f,
                0f, 0f, 0f, 1f);
    }

    /** The bound of all {@code receivers} but a sky, whose bound is everywhere; null when there are none. */
    private static BoundingBox boundOf(GeometryList receivers) {
        BoundingBox all = null;
        for (var geometry : receivers) {
            var bound = geometry.getWorldBound();
            if (bound == null || geometry.getQueueBucket() == RenderQueue.Bucket.Sky) {
                continue;
            }
            if (all == null) {
                all = new BoundingBox(bound.getCenter().clone(), 0f, 0f, 0f);
            }
            all.mergeLocal(bound);
        }
        return all;
    }

    /**
     * The stretch of the view from {@code near} to {@code far} in which it meets {@code seen}, rounded out to an eighth of
     * its octave so a walking camera leaves the slices as they are for many frames at a time. A camera high over a field
     * sees it only from far off: four maps spent on the air under its near plane would leave one for the field.
     */
    static float[] seenBetween(Camera view, BoundingBox seen, float near, float far, Vector3f[] corners) {
        var span = new float[] {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        var eye = view.getLocation();
        var forward = view.getDirection();
        clippedEnds(view, seen, near, far, corners, end -> {
            float depth = forward.dot(end) - forward.dot(eye);
            span[0] = Math.min(span[0], depth);
            span[1] = Math.max(span[1], depth);
        });
        if (span[0] > span[1]) {
            return new float[] {near, far};
        }
        float from = Math.max(near, roundOut(span[0], false));
        float to = Math.min(far, roundOut(span[1], true));
        return to > from ? new float[] {from, to} : new float[] {near, far};
    }

    /**
     * Every point at which an edge of the view's slice from {@code near} to {@code far} and of the box {@code seen} enters
     * the other: the outermost points of the shape the two share lie among them or are their own corners inside the other.
     * A box that is not finite, or none, shares nothing to narrow the view by.
     */
    private static void clippedEnds(Camera view, BoundingBox seen, float near, float far, Vector3f[] corners,
            Consumer<Vector3f> end) {
        if (seen == null || !Float.isFinite(seen.getXExtent()) || !Float.isFinite(seen.getYExtent())
                || !Float.isFinite(seen.getZExtent())) {
            return;
        }
        var low = seen.getMin(new Vector3f());
        var high = seen.getMax(new Vector3f());
        var box = new Plane[] {new Plane(Vector3f.UNIT_X, low.x), new Plane(Vector3f.UNIT_X.negate(), -high.x),
            new Plane(Vector3f.UNIT_Y, low.y), new Plane(Vector3f.UNIT_Y.negate(), -high.y),
            new Plane(Vector3f.UNIT_Z, low.z), new Plane(Vector3f.UNIT_Z.negate(), -high.z)};
        var slice = slicePlanes(view, near, far);
        ShadowUtil.updateFrustumPoints(view, near, far, 1f, corners);
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            clip(corners[i], corners[next], box, end);
            clip(corners[i + 4], corners[next + 4], box, end);
            clip(corners[i], corners[i + 4], box, end);
        }
        var a = new Vector3f();
        var b = new Vector3f();
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) == 0) {
                    corner(i, low, high, a);
                    corner(i | bit, low, high, b);
                    clip(a, b, slice, end);
                }
            }
        }
    }

    /** The view from {@code near} to {@code far}, each normal inward: the camera's left, right, bottom and top, and its own ends. */
    private static Plane[] slicePlanes(Camera view, float near, float far) {
        var forward = view.getDirection();
        float eye = forward.dot(view.getLocation());
        return new Plane[] {view.getWorldPlane(0), view.getWorldPlane(1), view.getWorldPlane(2), view.getWorldPlane(3),
            new Plane(forward, eye + near), new Plane(forward.negate(), -(eye + far))};
    }

    private static void corner(int index, Vector3f low, Vector3f high, Vector3f store) {
        store.set((index & 1) == 0 ? low.x : high.x, (index & 2) == 0 ? low.y : high.y, (index & 4) == 0 ? low.z : high.z);
    }

    /** The two ends of the piece of a segment inside all {@code planes}, if it has one. */
    private static void clip(Vector3f a, Vector3f b, Plane[] planes, Consumer<Vector3f> end) {
        float from = 0f;
        float to = 1f;
        for (var plane : planes) {
            float atA = plane.pseudoDistance(a);
            float atB = plane.pseudoDistance(b);
            if (atA < 0f && atB < 0f) {
                return;
            }
            if (atA < 0f) {
                from = Math.max(from, atA / (atA - atB));
            } else if (atB < 0f) {
                to = Math.min(to, atA / (atA - atB));
            }
        }
        if (from <= to) {
            var point = new Vector3f();
            end.accept(point.interpolateLocal(a, b, from));
            end.accept(point.interpolateLocal(a, b, to));
        }
    }

    private static float roundOut(float value, boolean up) {
        if (value <= 0f) {
            return value;
        }
        float step = Math.scalb(1f, Math.getExponent(value) - 3);
        return (up ? FastMath.ceil(value / step) : FastMath.floor(value / step)) * step;
    }
}
