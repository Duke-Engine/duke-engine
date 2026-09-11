package uz.duke.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The arrowheads that close on a spot the player clicked.
 *
 * <p>Three of them, set a third of a turn apart, points inward — see
 * {@link OrderMark} for why they move the way they do. This class is the other
 * half of that: the part that knows about jME, and nothing about what looks good.
 *
 * <p><b>Nothing here is built while the game is running.</b> The old marker
 * emptied its node and made a fresh cylinder and a fresh material every frame for
 * every mark — cheap enough at one mark and exactly the shape of a leak at
 * twenty. Here one triangle mesh is shared by every arrowhead that will ever be
 * drawn, and each mark's node, geometries and material are made once and then
 * lent out again. A mark that has finished is hidden rather than detached, so the
 * scene settles at however many were on screen at once and stops growing there.
 *
 * <p>Drawn <b>additively</b>, which is what makes it legible in a dark room
 * without being a lamp in a lit one: the colour is added to what is behind it, so
 * it glows over stone and disappears cleanly as its alpha falls. Depth
 * <em>writing</em> is off, because a translucent thing has no business occluding
 * what comes after it; depth <em>testing</em> stays on, which is what keeps a
 * mark on the floor of the next room behind the wall in front of it.
 */
final class Chevrons {

    /** Three, a third of a turn apart. The whole shape of the thing. */
    private static final int POINTS = 3;

    private final AssetManager assets;
    private final Node root;
    private final OrderMark look;
    private final Mesh arrowhead;
    private final List<Mark> pool = new ArrayList<>();

    /** One mark's furniture, kept and lent out again. */
    private record Mark(Node node, List<Geometry> heads, Material material) {
    }

    Chevrons(AssetManager assets, Node root, OrderMark look) {
        this.assets = assets;
        this.root = root;
        this.look = look == null ? OrderMark.DEFAULT : look;
        this.arrowhead = arrowhead(this.look.size(), this.look.width());
    }

    /**
     * Draw every mark that is still alive, and hide the rest.
     *
     * @param floorAt how high the floor is under a point — a dungeon has storeys,
     *                so a mark is not simply at zero
     */
    void show(List<OrderMarkers.Marker> marks, float now, BiFunction<Float, Float, Float> floorAt) {
        int used = 0;
        for (var marker : marks) {
            float age = now - marker.bornAt();
            if (age < 0f || look.spent(age)) {
                continue;
            }
            place(borrow(used++), marker, look.at(age), floorAt);
        }
        for (int spare = used; spare < pool.size(); spare++) {
            pool.get(spare).node().setCullHint(Spatial.CullHint.Always);
        }
    }

    /** Hide everything — a new world has no orders outstanding in it. */
    void clear() {
        for (var mark : pool) {
            mark.node().setCullHint(Spatial.CullHint.Always);
        }
    }

    /** How many marks the pool has had to make. Package-private so it can be checked. */
    int madeSoFar() {
        return pool.size();
    }

    private void place(Mark mark, OrderMarkers.Marker marker, OrderMark.Step step,
            BiFunction<Float, Float, Float> floorAt) {
        mark.node().setCullHint(Spatial.CullHint.Inherit);
        mark.node().setLocalTranslation(marker.x(),
                floorAt.apply(marker.x(), marker.y()) + look.height(), marker.y());
        mark.material().setColor("Color", colourOf(marker.kind(), step.alpha()));
        for (int point = 0; point < POINTS; point++) {
            float around = step.spinRadians() + point * FastMath.TWO_PI / POINTS;
            var head = mark.heads().get(point);
            head.setLocalTranslation(FastMath.cos(around) * step.radius(), 0f,
                    FastMath.sin(around) * step.radius());
            // Turning by -around points the mesh's own +X outward, which puts its
            // point -- sitting at the origin -- nearest the middle. Inward-facing
            // is the whole reading of the shape: three things aiming at one spot.
            head.setLocalRotation(new Quaternion().fromAngleAxis(-around, Vector3f.UNIT_Y));
        }
    }

    private ColorRGBA colourOf(OrderMarkers.Kind kind, float alpha) {
        int packed = kind == OrderMarkers.Kind.ATTACK ? look.attackColour() : look.moveColour();
        float scale = look.brightness() / 255f;
        return new ColorRGBA(
                ((packed >> 16) & 0xFF) * scale,
                ((packed >> 8) & 0xFF) * scale,
                (packed & 0xFF) * scale,
                alpha);
    }

    private Mark borrow(int index) {
        while (pool.size() <= index) {
            pool.add(build());
        }
        return pool.get(index);
    }

    private Mark build() {
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", ColorRGBA.White);
        var state = material.getAdditionalRenderState();
        state.setBlendMode(RenderState.BlendMode.AlphaAdditive);
        state.setDepthWrite(false);
        // Which way the triangle was wound is not worth caring about for a flat
        // shape seen from one side of the map.
        state.setFaceCullMode(RenderState.FaceCullMode.Off);

        var node = new Node("order-mark");
        var heads = new ArrayList<Geometry>(POINTS);
        for (int point = 0; point < POINTS; point++) {
            var head = new Geometry("order-head", arrowhead);
            head.setMaterial(material);
            head.setQueueBucket(RenderQueue.Bucket.Transparent);
            node.attachChild(head);
            heads.add(head);
        }
        node.setCullHint(Spatial.CullHint.Always);
        root.attachChild(node);
        return new Mark(node, List.copyOf(heads), material);
    }

    /**
     * One arrowhead, lying flat with its point at the origin and its back edge
     * out along {@code +X}.
     *
     * <p>Built once and shared by every mark on screen. Its point is at the
     * origin so that placing it is placing its tip: the thing the player's eye
     * follows is the sharp end, and it is the sharp end that has to land on the
     * spot he clicked.
     */
    private static Mesh arrowhead(float size, float width) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(
                0f, 0f, 0f,
                size, 0f, width * 0.5f,
                size, 0f, -width * 0.5f));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(
                (short) 0, (short) 1, (short) 2));
        mesh.updateBound();
        return mesh;
    }
}
