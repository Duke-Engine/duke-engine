package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.AimMark;

/**
 * What an armed button puts down, drawn where it would stand, and the ground the answer about its place marks. As the
 * game names it ({@link Visuals.GhostLook}): the thing as it is drawn, in its placer's colours, at the look's opacity,
 * a colour added to its light while the answer is no — the reference's {@code InGameUI::handleBuildPlacements}, its
 * {@code placementOpacity} and {@code illegalBuildColor} — and each rectangle of the answer laid with the look's
 * picture ({@code addFactionBib}). Where the game names none, one flat translucent colour, green or red.
 */
final class Ghost {

    static final ColorRGBA FITS = new ColorRGBA(0.35f, 1f, 0.35f, 0.45f);
    static final ColorRGBA REFUSED = new ColorRGBA(1f, 0.3f, 0.3f, 0.45f);

    private final AssetManager assets;
    private final Node parent;
    private final Visuals.GhostLook look;
    private final Node node = new Node("ghost");
    private final AmbientLight tint = new AmbientLight(ColorRGBA.Black.clone());
    private final List<GroundDecal> marks = new ArrayList<>();
    private Material flat;

    /** {@code body}, already in its placer's colours, drawn as a ghost under {@code parent}. */
    Ghost(AssetManager assets, Node parent, Spatial body, Visuals.GhostLook look) {
        this.assets = assets;
        this.parent = parent;
        this.look = look;
        if (look == null) {
            flat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            flat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            flat.getAdditionalRenderState().setDepthWrite(false);
            flat.setColor("Color", FITS);
            body.depthFirstTraversal(spatial -> {
                if (spatial instanceof Geometry geometry) {
                    geometry.setMaterial(flat);
                }
            });
        } else {
            body.depthFirstTraversal(spatial -> {
                if (spatial instanceof Geometry geometry && geometry.getMaterial() != null) {
                    geometry.setMaterial(geometry.getMaterial().clone()); // its own: the things it stands for untouched
                }
            });
            StealthLook.fade(body, look.opacity());
            node.addLight(tint);
        }
        node.attachChild(body);
        node.setQueueBucket(RenderQueue.Bucket.Transparent);
        parent.attachChild(node);
    }

    /** Where it would stand, and turned which way. */
    Node node() {
        return node;
    }

    /**
     * The answer about the place it stands on now: green or red, or its light tinted while it is no; and the answer's
     * rectangles laid over the ground until the next.
     */
    void answer(boolean fits, List<AimMark> rectangles, BiFunction<Float, Float, Float> floorAt) {
        if (look == null) {
            flat.setColor("Color", fits ? FITS : REFUSED);
            return;
        }
        var refused = look.refused();
        tint.setColor(fits || refused == null ? ColorRGBA.Black.clone()
                : new ColorRGBA(refused.getRed() / 255f, refused.getGreen() / 255f, refused.getBlue() / 255f, 1f));
        int laid = 0;
        if (look.marks() != null) {
            for (var rectangle : rectangles) {
                if (laid == marks.size()) {
                    marks.add(new GroundDecal(assets, parent));
                }
                lay(marks.get(laid++), rectangle, look.marks(), floorAt);
            }
        }
        for (int left = laid; left < marks.size(); left++) {
            marks.get(left).hide();
        }
    }

    /** A rectangle laid: from {@code behind} back to {@code ahead} along its facing, {@code side} to either side. */
    static void lay(GroundDecal decal, AimMark rectangle, String picture, BiFunction<Float, Float, Float> floorAt) {
        float facing = rectangle.facing() * FastMath.DEG_TO_RAD;
        float forward = (rectangle.ahead() - rectangle.behind()) / 2f;
        var middle = new Coord3D(rectangle.x() + forward * FastMath.cos(facing),
                rectangle.y() + forward * FastMath.sin(facing), 0f);
        decal.lay(middle, rectangle.ahead() + rectangle.behind(), 2f * rectangle.side(), facing, picture, 0xFFFFFF,
                1f, floorAt);
    }

    /** Taken down: the ghost and every mark. */
    void remove() {
        node.removeFromParent();
        marks.forEach(GroundDecal::remove);
        marks.clear();
    }

    /** The colour added to its light now, for a test. */
    ColorRGBA tint() {
        return tint.getColor();
    }

    /** How many of its marks lie on the ground now, for a test. */
    long marksShowing() {
        return marks.stream().filter(GroundDecal::showing).count();
    }
}
