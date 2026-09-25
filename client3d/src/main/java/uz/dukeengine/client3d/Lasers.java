package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
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
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import uz.dukeengine.core.content.Laser;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.view.BeamView;

/**
 * The beams the simulation owns, drawn as the game's {@link Laser} looks say — the reference's {@code W3DLaserDraw}:
 * each beam its nested lines, added to what is behind them, facing the camera, a picture tiled and scrolled along them,
 * laid in segments along an arc. Laid again from every snapshot, so a beam moved is drawn where it went that frame, and
 * one the snapshot no longer holds is gone.
 */
final class Lasers {

    private static final Logger LOG = Logger.getLogger(Lasers.class.getName());

    /** How far over the ground a segment of an arc is kept: the reference's 2, so it skims rather than sinks. */
    private static final float SKIM = 2f;

    /**
     * One line of a beam, in the client's frame (y up).
     *
     * @param colour what it adds, the reference's arithmetic done
     * @param tiles  how many times its picture is laid along it; 1 where it is stretched
     */
    record Line(Vector3f from, Vector3f to, float width, ColorRGBA colour, float tiles) {
    }

    /**
     * The lines a beam is drawn as: each nested beam across each segment, the outermost first as the reference lays
     * them. None where its width is nothing.
     *
     * @param share  the share of its full width the simulation gives it
     * @param aspect its picture's width over its height, for tiling
     */
    static List<Line> lay(Laser look, Coord3D from, Coord3D to, float share, float aspect, WorldMoments.Floor floor) {
        if (share <= 0f) {
            return List.of();
        }
        var lines = new ArrayList<Line>();
        var start = new Vector3f(from.x(), from.z(), from.y());
        var end = new Vector3f(to.x(), to.z(), to.y());
        boolean arced = look.arcHeight() > 0f && look.segments() > 1;
        int segments = arced ? look.segments() : 1;
        for (int segment = 0; segment < segments; segment++) {
            Vector3f a = start;
            Vector3f b = end;
            if (arced) {
                a = onArc(look, start, end, Math.max(0f, (float) segment / segments
                        - (segment > 0 ? look.segmentOverlapRatio() : 0f)), floor);
                b = onArc(look, start, end, Math.min(1f, (segment + 1f) / segments
                        + (segment < segments - 1 ? look.segmentOverlapRatio() : 0f)), floor);
            }
            for (int beam = look.numBeams() - 1; beam >= 0; beam--) {
                float width = widthOf(look, beam) * share;
                float tiles = look.texture() != null && look.tile() && width > 0f
                        ? tiles(a.distance(b) / width * aspect * look.tilingScalar()) : 1f;
                lines.add(new Line(a, b, width, colourOf(look, beam), tiles));
            }
        }
        return lines;
    }

    /** {@code SegLineRendererClass::Set_Texture_Tile_Factor}'s {@code MAX_LINE_TILING_FACTOR}. */
    static final float MOST_TILES = 50f;

    /**
     * How many times a line's picture is tiled along it, as the reference's line holds it: never more than {@link
     * #MOST_TILES}, never fewer than none — a picture tiled no times is stretched untiled along it.
     */
    static float tiles(float count) {
        return Math.clamp(count, 0f, MOST_TILES);
    }

    /** A point {@code along} the line, raised by the arc's cosine — highest in the middle — and never under the floor. */
    private static Vector3f onArc(Laser look, Vector3f start, Vector3f end, float along, WorldMoments.Floor floor) {
        var point = start.add(end.subtract(start).multLocal(along));
        float half = start.distance(end) / 2f;
        float fromMiddle = point.distance(start.add(end).multLocal(0.5f));
        if (half > 0f) {
            point.y += FastMath.cos(fromMiddle / half * FastMath.HALF_PI) * look.arcHeight();
        }
        point.y = Math.max(point.y, SKIM + floor.at(point.x, point.z));
        return point;
    }

    /** {@code W3DLaserDraw}: the innermost the inner width, the outermost the outer, those between their share. */
    private static float widthOf(Laser look, int beam) {
        if (look.numBeams() == 1) {
            return look.innerBeamWidth();
        }
        float scale = beam / (look.numBeams() - 1f);
        return look.innerBeamWidth() + scale * (look.outerBeamWidth() - look.innerBeamWidth());
    }

    /**
     * The colour a line adds, as {@code W3DLaserDraw}'s constructor works it out — quirk kept: past the first line,
     * the step toward the outer colour is scaled by the inner alpha and the inner colour is not.
     */
    static ColorRGBA colourOf(Laser look, int beam) {
        float innerAlpha = (look.innerColour() >>> 24) / 255f;
        float[] inner = rgb(look.innerColour());
        float[] outer = rgb(look.outerColour());
        if (look.numBeams() == 1) {
            return new ColorRGBA(inner[0] * innerAlpha, inner[1] * innerAlpha, inner[2] * innerAlpha, 1f);
        }
        float scale = beam / (look.numBeams() - 1f);
        return new ColorRGBA(inner[0] + scale * (outer[0] - inner[0]) * innerAlpha,
                inner[1] + scale * (outer[1] - inner[1]) * innerAlpha,
                inner[2] + scale * (outer[2] - inner[2]) * innerAlpha, 1f);
    }

    private static float[] rgb(int argb) {
        return new float[] {(argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }

    private final AssetManager assets;
    private final Node node;
    private final Function<String, Laser> looks;
    private final WorldMoments.Floor floor;
    private final Map<Integer, List<Geometry>> drawn = new HashMap<>();
    private final Map<String, com.jme3.texture.Texture> textures = new HashMap<>();
    private final Set<String> missing = new HashSet<>();

    Lasers(AssetManager assets, Node node, Function<String, Laser> looks, WorldMoments.Floor floor) {
        this.assets = assets;
        this.node = node;
        this.looks = looks;
        this.floor = floor;
    }

    /**
     * The beams of this snapshot, drawn facing {@code eye}; {@code seconds} is the client's time, which scrolls their
     * pictures.
     */
    void show(List<BeamView> beams, Vector3f eye, float seconds) {
        var alive = new HashSet<Integer>();
        for (var beam : beams) {
            alive.add(beam.id());
            var look = looks.apply(beam.look());
            var texture = look == null ? null : texture(look.texture());
            var lines = look == null ? List.<Line>of() : lay(look, beam.from(), beam.to(), beam.width(),
                    aspectOf(texture), floor);
            var geometries = drawn.computeIfAbsent(beam.id(), id -> new ArrayList<>());
            while (geometries.size() < lines.size()) {
                geometries.add(line(texture));
            }
            for (int i = 0; i < geometries.size(); i++) {
                var geometry = geometries.get(i);
                if (i < lines.size() && lines.get(i).width() > 0f) {
                    shape(geometry, lines.get(i), eye, seconds * look.scrollRate());
                    geometry.setCullHint(Spatial.CullHint.Inherit);
                } else {
                    geometry.setCullHint(Spatial.CullHint.Always);
                }
            }
        }
        for (var each = drawn.entrySet().iterator(); each.hasNext();) {
            var entry = each.next();
            if (!alive.contains(entry.getKey())) {
                entry.getValue().forEach(Spatial::removeFromParent);
                each.remove();
            }
        }
    }

    /** Everything gone at once, for a new world. */
    void clear() {
        drawn.values().forEach(lines -> lines.forEach(Spatial::removeFromParent));
        drawn.clear();
    }

    /** The lines drawn for a beam now, shown or not; for checking. */
    List<Geometry> linesOf(int beam) {
        return drawn.getOrDefault(beam, List.of());
    }

    private Geometry line(com.jme3.texture.Texture texture) {
        var mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[12]);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[8]);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, new short[] {0, 1, 2, 0, 2, 3});
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        if (texture != null) {
            material.setTexture("ColorMap", texture);
        }
        var state = material.getAdditionalRenderState();
        state.setBlendMode(RenderState.BlendMode.Additive); // the reference's _PresetAdditiveShader
        state.setDepthWrite(false);
        state.setFaceCullMode(RenderState.FaceCullMode.Off);
        var geometry = new Geometry("laser", mesh);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        node.attachChild(geometry);
        return geometry;
    }

    /** A line as a ribbon from end to end, turned about its own length to face the eye. */
    private static void shape(Geometry geometry, Line line, Vector3f eye, float scrolled) {
        var along = line.to().subtract(line.from());
        var middle = line.from().add(line.to()).multLocal(0.5f);
        var side = along.cross(eye.subtract(middle));
        if (side.lengthSquared() < 1e-8f) {
            side = along.cross(Vector3f.UNIT_Y);
            if (side.lengthSquared() < 1e-8f) {
                side.set(Vector3f.UNIT_X);
            }
        }
        side.normalizeLocal().multLocal(line.width() / 2f);
        var a = line.from().subtract(side);
        var b = line.from().add(side);
        var c = line.to().add(side);
        var d = line.to().subtract(side);
        var mesh = geometry.getMesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
            a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z});
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {
            0f, scrolled, 1f, scrolled, 1f, scrolled + line.tiles(), 0f, scrolled + line.tiles()});
        mesh.updateBound();
        geometry.updateModelBound();
        geometry.getMaterial().setColor("Color", line.colour());
    }

    private com.jme3.texture.Texture texture(String path) {
        if (path == null || missing.contains(path)) {
            return null;
        }
        return textures.computeIfAbsent(path, key -> {
            try {
                var texture = assets.loadTexture(key);
                texture.setWrap(com.jme3.texture.Texture.WrapMode.Repeat);
                return texture;
            } catch (RuntimeException notThere) {
                missing.add(key);
                LOG.warning(() -> "a laser names a picture that will not load: " + key);
                return null;
            }
        });
    }

    private static float aspectOf(com.jme3.texture.Texture texture) {
        var image = texture == null ? null : texture.getImage();
        return image == null || image.getHeight() == 0 ? 1f : (float) image.getWidth() / image.getHeight();
    }
}
