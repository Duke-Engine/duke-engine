package uz.dukeengine.client3d;

import com.jme3.anim.AnimComposer;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.RallyView;

/**
 * The rally points of what is selected, shown as the reference shows them ({@code W3DWaypointBuffer::drawWaypoints},
 * {@code ControlBar::showRallyPoint}): a line for each selected building of the player's that has one, with its nodes,
 * and the flag on its rally point while it alone is selected. Laid again every frame from the snapshot, so they go with
 * the selection and follow a rally point moved.
 */
final class RallyMarks {

    private static final Logger LOG = Logger.getLogger(RallyMarks.class.getName());

    private final AssetManager assets;
    private final Node node;
    private final Function<String, Spatial> load;
    private final List<Geometry> legs = new ArrayList<>();
    private final List<Spatial> nodes = new ArrayList<>();
    private final Map<String, com.jme3.texture.Texture> textures = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private String nodeModel;
    private Spatial flag;
    private String flagModel;

    RallyMarks(AssetManager assets, Node node, Function<String, Spatial> load) {
        this.assets = assets;
        this.node = node;
        this.load = load;
    }

    /** The one whose flag stands: the only rally point shown, while only one thing is selected. */
    static RallyView flagOf(List<RallyView> shown, int selected) {
        return selected == 1 && shown.size() == 1 ? shown.getFirst() : null;
    }

    /**
     * This frame's: the lines of {@code shown} — the selected things of the player's that have a rally point — and the
     * flag on {@code flagged}'s, or none, painted {@code owner} on its parts named {@code housePrefix}; the lines turned
     * to face {@code eye}.
     */
    void show(RallyLook look, List<RallyView> shown, RallyView flagged, ColorRGBA owner, String housePrefix,
            WorldMoments.Floor floor, Vector3f eye) {
        if (!Objects.equals(nodeModel, look.node())) {
            nodes.forEach(Spatial::removeFromParent);
            nodes.clear();
            nodeModel = look.node();
        }
        var texture = texture(look.texture());
        var colour = new ColorRGBA((look.colour() >> 16 & 0xFF) / 255f, (look.colour() >> 8 & 0xFF) / 255f,
                (look.colour() & 0xFF) / 255f, 1f);
        int leg = 0;
        int nodeCount = 0;
        for (var rally : shown) {
            var points = rally.line().stream().map(point -> at(point, floor)).toList();
            for (int i = 1; i < points.size() && look.width() > 0f; i++, leg++) {
                if (leg == legs.size()) {
                    legs.add(leg());
                }
                shape(legs.get(leg), points.get(i - 1), points.get(i), look.width(), eye, texture, colour);
            }
            for (var point : look.node() == null ? List.<Coord3D>of() : rally.nodes()) {
                if (nodeCount == nodes.size()) {
                    var model = load.apply(look.node());
                    if (model == null) {
                        break;
                    }
                    node.attachChild(model);
                    nodes.add(model);
                }
                nodes.get(nodeCount).setLocalTranslation(at(point, floor));
                nodes.get(nodeCount++).setCullHint(Spatial.CullHint.Inherit);
            }
        }
        for (int i = leg; i < legs.size(); i++) {
            legs.get(i).setCullHint(Spatial.CullHint.Always);
        }
        for (int i = nodeCount; i < nodes.size(); i++) {
            nodes.get(i).setCullHint(Spatial.CullHint.Always);
        }
        showFlag(look, flagged, owner, housePrefix, floor);
    }

    private void showFlag(RallyLook look, RallyView flagged, ColorRGBA owner, String housePrefix,
            WorldMoments.Floor floor) {
        if (flag != null && !Objects.equals(flagModel, look.flag())) {
            flag.removeFromParent();
            flag = null;
        }
        if (flagged == null || look.flag() == null) {
            if (flag != null) {
                flag.setCullHint(Spatial.CullHint.Always);
            }
            return;
        }
        if (flag == null) {
            flag = load.apply(look.flag());
            flagModel = look.flag();
            if (flag == null) {
                return;
            }
            node.attachChild(flag);
            var composer = AnimationLibrary.findControl(flag, AnimComposer.class);
            if (composer != null && look.flagClip() != null && composer.getAnimClip(look.flagClip()) != null) {
                composer.setCurrentAction(look.flagClip(), AnimComposer.DEFAULT_LAYER, true);
            }
        }
        if (housePrefix != null) {
            DukeRtsApp.paintOwner(flag, housePrefix, owner);
        }
        flag.setLocalTranslation(at(flagged.rallyPoint(), floor));
        flag.setLocalRotation(new Quaternion().fromAngles(0f, -look.flagFacing(), 0f));
        flag.setCullHint(Spatial.CullHint.Inherit);
    }

    /** Everything taken away, for a new world. */
    void clear() {
        legs.forEach(Spatial::removeFromParent);
        legs.clear();
        nodes.forEach(Spatial::removeFromParent);
        nodes.clear();
        if (flag != null) {
            flag.removeFromParent();
            flag = null;
        }
    }

    /** The legs shown now; for checking. */
    List<Geometry> shownLegs() {
        return legs.stream().filter(leg -> leg.getCullHint() != Spatial.CullHint.Always).toList();
    }

    /** The nodes shown now; for checking. */
    List<Spatial> shownNodes() {
        return nodes.stream().filter(each -> each.getCullHint() != Spatial.CullHint.Always).toList();
    }

    /** The flag, where it stands shown, or null; for checking. */
    Spatial shownFlag() {
        return flag == null || flag.getCullHint() == Spatial.CullHint.Always ? null : flag;
    }

    /** A point of the world in the scene's frame, no lower than the floor under it. */
    private static Vector3f at(Coord3D point, WorldMoments.Floor floor) {
        return new Vector3f(point.x(), Math.max(point.z(), floor.at(point.x(), point.y())), point.y());
    }

    private Geometry leg() {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[12]);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f});
        mesh.setBuffer(VertexBuffer.Type.Index, 3, new short[] {0, 1, 2, 0, 2, 3});
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        var state = material.getAdditionalRenderState();
        state.setBlendMode(RenderState.BlendMode.Additive); // the reference's _PresetAdditiveShader
        state.setDepthTest(false); // and PASS_ALWAYS: over everything
        state.setDepthWrite(false);
        state.setFaceCullMode(RenderState.FaceCullMode.Off);
        var geometry = new Geometry("rally line", mesh);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        node.attachChild(geometry);
        return geometry;
    }

    /** A leg as a ribbon from end to end, turned about its length to face the eye, its picture stretched along it. */
    private static void shape(Geometry geometry, Vector3f from, Vector3f to, float width, Vector3f eye,
            com.jme3.texture.Texture texture, ColorRGBA colour) {
        var along = to.subtract(from);
        var side = along.cross(eye.subtract(from.add(to).multLocal(0.5f)));
        if (side.lengthSquared() < 1e-8f) {
            side = along.cross(Vector3f.UNIT_Y);
            if (side.lengthSquared() < 1e-8f) {
                side.set(Vector3f.UNIT_X);
            }
        }
        side.normalizeLocal().multLocal(width / 2f);
        var a = from.subtract(side);
        var b = from.add(side);
        var c = to.add(side);
        var d = to.subtract(side);
        var mesh = geometry.getMesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
            a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z});
        mesh.updateBound();
        geometry.updateModelBound();
        var material = geometry.getMaterial();
        if (texture != null) {
            material.setTexture("ColorMap", texture);
        } else if (material.getParam("ColorMap") != null) {
            material.clearParam("ColorMap");
        }
        material.setColor("Color", colour);
        geometry.setCullHint(Spatial.CullHint.Inherit);
    }

    private com.jme3.texture.Texture texture(String path) {
        if (path == null || missing.contains(path)) {
            return null;
        }
        var known = textures.get(path);
        if (known != null) {
            return known;
        }
        try {
            var texture = assets.loadTexture(path);
            textures.put(path, texture);
            return texture;
        } catch (RuntimeException notThere) {
            missing.add(path);
            LOG.warning(() -> "the rally line names a picture that will not load: " + path);
            return null;
        }
    }
}
