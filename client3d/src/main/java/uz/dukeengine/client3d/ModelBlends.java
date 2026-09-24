package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.plugins.gltf.GltfUtils;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How a model's materials are drawn beyond plain and opaque. The client dresses every loaded model in a material of
 * its own it can light, and the loader's way of drawing each one is carried onto it: glTF's {@code BLEND}, glTF's
 * {@code MASK} with its cutoff, and what glTF has no word for — a material the game marks
 * {@code extras.blend = "ADDITIVE"} or {@code "MULTIPLY"}, as another engine's shaders add a headlight's cone or
 * multiply a shadow decal (the reference's W3D shaders, {@code SrcBlend} and {@code DestBlend}).
 */
final class ModelBlends {

    /** What a material the game marks is drawn with. */
    enum Blend {
        /** Added to what is behind it: light — a headlight's cone, a muzzle's flash, a lamp's halo. */
        ADDITIVE,
        /** Multiplied with what is behind it: a shadow decal. */
        MULTIPLY
    }

    private static final int GLB_MAGIC = 0x46546C67; // "glTF", little-endian
    private static final int JSON_CHUNK = 0x4E4F534A; // "JSON"

    private ModelBlends() {
    }

    /**
     * The materials of a {@code .glb} the game marked, by material name: {@code "materials": [{"name": "searchlight",
     * "extras": {"blend": "ADDITIVE"}}]}. Only the file's JSON is read. Empty for a file that marks none, and for
     * anything that is not a glb.
     */
    static Map<String, Blend> read(InputStream glb) throws IOException {
        var marked = new HashMap<String, Blend>();
        var in = new DataInputStream(glb);
        var header = little(in, 20);
        if (header.getInt(0) != GLB_MAGIC || header.getInt(16) != JSON_CHUNK) {
            return marked;
        }
        var json = in.readNBytes(header.getInt(12));
        var root = GltfUtils.parse(new ByteArrayInputStream(json));
        var materials = root.getAsJsonArray("materials");
        if (materials == null) {
            return marked;
        }
        for (int i = 0; i < materials.size(); i++) {
            var material = materials.get(i).getAsJsonObject();
            var extras = material.getAsJsonObject("extras");
            if (extras == null || !extras.has("blend") || !material.has("name")) {
                continue;
            }
            var blend = switch (extras.get("blend").getAsString().toUpperCase(Locale.ROOT)) {
                case "ADDITIVE" -> Blend.ADDITIVE;
                case "MULTIPLY" -> Blend.MULTIPLY;
                default -> null; // a word this client does not know: drawn as glTF says
            };
            if (blend != null) {
                marked.put(material.get("name").getAsString(), blend);
            }
        }
        return marked;
    }

    private static ByteBuffer little(DataInputStream in, int bytes) throws IOException {
        return ByteBuffer.wrap(in.readNBytes(bytes)).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Whether the loader made the material unlit: glTF's {@code KHR_materials_unlit}. */
    static boolean unlit(Material loaded) {
        return loaded != null && loaded.getMaterialDef().getAssetName().endsWith("Unshaded.j3md");
    }

    /**
     * The loaded material's way of being drawn, carried onto the material the client dresses its geometry in: the
     * game's {@code marked} blend when it gave one, else glTF's {@code BLEND} or {@code MASK} as the loader read it.
     * A translucent one — added, multiplied or blended — writes no depth and is drawn in the translucent bucket, after
     * what is solid.
     */
    static void carryOver(Material loaded, Material dressed, Geometry geometry, Blend marked) {
        var blend = marked == Blend.ADDITIVE ? RenderState.BlendMode.Additive
                : marked == Blend.MULTIPLY ? RenderState.BlendMode.Modulate
                : loaded == null ? RenderState.BlendMode.Off
                : loaded.getAdditionalRenderState().getBlendMode();
        var cutoff = loaded == null ? null : loaded.getParam("AlphaDiscardThreshold");
        if (cutoff != null && dressed.getMaterialDef().getMaterialParam("AlphaDiscardThreshold") != null) {
            dressed.setFloat("AlphaDiscardThreshold", (Float) cutoff.getValue());
        }
        // A cutoff is glTF's MASK: cut out and drawn solid, though an unlit one comes from the loader blended.
        if (blend == RenderState.BlendMode.Off || cutoff != null && marked == null) {
            return;
        }
        dressed.getAdditionalRenderState().setBlendMode(blend);
        dressed.getAdditionalRenderState().setDepthWrite(false);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
    }
}
