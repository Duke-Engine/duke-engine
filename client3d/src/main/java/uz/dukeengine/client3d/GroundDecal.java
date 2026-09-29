package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import uz.dukeengine.core.math.Coord3D;

/**
 * An {@link AimDecal} drawn: its picture on the ground round a spot, blended over it as a scorch mark is, and laid
 * over the ground's rise and fall on a grid rather than flat at the height under its middle — a reticle two hundred
 * across on a hillside would otherwise stand in the air on one side and under the hill on the other. The grid's cells
 * are {@link GroundRing#STEP} across however large the picture, up to {@link #MOST_CELLS} a side: a grid of a few
 * cells laid over a long cone let a hill rise through it between them.
 */
final class GroundDecal {

    private static final Logger LOG = Logger.getLogger(GroundDecal.class.getName());
    /** Cells a side of the grid it is laid on, at the least and at the most. */
    private static final int LEAST_CELLS = 8;
    private static final int MOST_CELLS = 64;
    /** Just clear of the ground, as a scorch mark is. */
    static final float LIFT = 0.05f;

    private final AssetManager assets;
    private final Node node = new Node("aim decal");
    private final Geometry picture;
    private FloatBuffer corners = BufferUtils.createFloatBuffer(0);
    private int cells = -1;
    private final java.util.Map<String, Material> materials = new java.util.HashMap<>();
    private final float lift;
    private float across;

    GroundDecal(AssetManager assets, Node parent) {
        this(assets, parent, LIFT);
    }

    /** One laid {@code lift} over the ground: a picture over another, drawn over it. */
    GroundDecal(AssetManager assets, Node parent, float lift) {
        this.assets = assets;
        this.lift = lift;
        picture = new Geometry("aim decal", new Mesh());
        picture.setQueueBucket(RenderQueue.Bucket.Transparent);
        node.attachChild(picture);
        parent.attachChild(node);
        hide();
    }

    /**
     * Lay it round {@code at}, {@code radius} each way, at its opacity in the game's frame {@code frame}; hidden where
     * its picture will not load.
     */
    void show(Coord3D at, float radius, AimDecal decal, int frame, BiFunction<Float, Float, Float> floorAt) {
        if (decal == null || radius <= 0f) {
            hide();
            return;
        }
        lay(at, 2f * radius, 2f * radius, 0f, decal.picture(), decal.colour(), decal.opacity(frame), floorAt);
    }

    /**
     * Lay a picture {@code width} along {@code facing} — the way a thing faces, in the simulation's radians — and
     * {@code depth} across it, centred on {@code at}, over the ground's rise and fall, in {@code colour} at {@code
     * opacity}; hidden where its picture will not load.
     */
    void lay(Coord3D at, float width, float depth, float facing, String path, int colour, float opacity,
            BiFunction<Float, Float, Float> floorAt) {
        var material = width <= 0f || depth <= 0f ? null : materialFor(path);
        if (material == null) {
            hide();
            return;
        }
        float base = floorAt.apply(at.x(), at.y());
        node.setLocalTranslation(at.x(), base, at.y());
        grid(Math.clamp((int) Math.ceil(Math.max(width, depth) / GroundRing.STEP), LEAST_CELLS, MOST_CELLS));
        float cos = (float) Math.cos(facing);
        float sin = (float) Math.sin(facing);
        for (int row = 0; row <= cells; row++) {
            for (int column = 0; column <= cells; column++) {
                float along = ((float) column / cells - 0.5f) * width;
                float across = ((float) row / cells - 0.5f) * depth;
                // Turned as a thing is drawn turned: the map's y is the scene's z.
                float x = along * cos - across * sin;
                float z = along * sin + across * cos;
                int corner = (row * (cells + 1) + column) * 3;
                corners.put(corner, x).put(corner + 1, floorAt.apply(at.x() + x, at.y() + z) - base + lift)
                        .put(corner + 2, z);
            }
        }
        var mesh = picture.getMesh();
        mesh.getBuffer(VertexBuffer.Type.Position).updateData(corners);
        picture.updateModelBound();
        material.setColor("Color", new ColorRGBA(((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f,
                (colour & 0xFF) / 255f, opacity));
        picture.setMaterial(material);
        this.across = Math.max(width, depth);
        node.setCullHint(Spatial.CullHint.Inherit);
    }

    /** Taken out of the scene for good. */
    void remove() {
        node.removeFromParent();
    }

    void hide() {
        node.setCullHint(Spatial.CullHint.Always);
    }

    boolean showing() {
        return node.getLocalCullHint() != Spatial.CullHint.Always;
    }

    /** How wide it was last laid, for a test. */
    float across() {
        return across;
    }

    /** Its node, for a test: where it stands, and its picture under it. */
    Node node() {
        return node;
    }

    /** The picture's colour as last painted, alpha its opacity, for a test. */
    ColorRGBA colour() {
        var material = picture.getMaterial();
        return material == null ? null : (ColorRGBA) material.getParam("Color").getValue();
    }

    private Material materialFor(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (materials.containsKey(path)) {
            return materials.get(path);
        }
        Material material = null;
        try {
            material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            material.setTexture("ColorMap", assets.loadTexture(path));
            var state = material.getAdditionalRenderState();
            state.setBlendMode(RenderState.BlendMode.Alpha);
            state.setDepthWrite(false);
            state.setFaceCullMode(RenderState.FaceCullMode.Off);
            state.setPolyOffset(-1f, -1f);
        } catch (RuntimeException notThere) {
            material = null;
            LOG.warning(() -> "a picture for the ground will not load: " + path);
        }
        materials.put(path, material);
        return material;
    }

    /**
     * The grid at {@code wanted} cells a side, its picture's top to the far side of the ground as the camera looks at
     * it; made again only when the count changes.
     */
    private void grid(int wanted) {
        if (wanted == cells) {
            return;
        }
        cells = wanted;
        corners = BufferUtils.createFloatBuffer((cells + 1) * (cells + 1) * 3);
        var uvs = BufferUtils.createFloatBuffer((cells + 1) * (cells + 1) * 2);
        for (int row = 0; row <= cells; row++) {
            for (int column = 0; column <= cells; column++) {
                uvs.put((float) column / cells).put(1f - (float) row / cells);
            }
        }
        var indices = BufferUtils.createShortBuffer(cells * cells * 6);
        for (int row = 0; row < cells; row++) {
            for (int column = 0; column < cells; column++) {
                short near = (short) (row * (cells + 1) + column);
                short far = (short) (near + cells + 1);
                indices.put(near).put(far).put((short) (near + 1)).put((short) (near + 1)).put(far)
                        .put((short) (far + 1));
            }
        }
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, corners);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs.flip());
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices.flip());
        picture.setMesh(mesh);
    }
}
