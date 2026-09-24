package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A model's materials drawn as another engine's shaders drew them: added, multiplied, blended, cut out. */
class ModelBlendsTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    /** A glb holding nothing but its JSON, as a converter writes one. */
    private static byte[] glb(String json) {
        var text = json.getBytes(StandardCharsets.UTF_8);
        int padded = (text.length + 3) / 4 * 4;
        var file = ByteBuffer.allocate(20 + padded).order(ByteOrder.LITTLE_ENDIAN);
        file.putInt(0x46546C67).putInt(2).putInt(20 + padded).putInt(padded).putInt(0x4E4F534A).put(text);
        while (file.hasRemaining()) {
            file.put((byte) ' ');
        }
        return file.array();
    }

    private static Material loaded(String definition, String name) {
        var material = new Material(ASSETS, definition);
        material.setName(name);
        return material;
    }

    private static Material dressed() {
        return new Material(ASSETS, "Common/MatDefs/Light/Lighting.j3md");
    }

    @Test
    void theMaterialsAGlbMarksAreReadByName() throws Exception {
        var file = glb("""
                {"asset": {"version": "2.0"}, "materials": [
                  {"name": "searchlight", "extras": {"blend": "ADDITIVE"}},
                  {"name": "shadow", "extras": {"blend": "multiply"}},
                  {"name": "glass", "alphaMode": "BLEND"},
                  {"name": "hull"}]}
                """);

        var marked = ModelBlends.read(new ByteArrayInputStream(file));

        assertEquals(Map.of("searchlight", ModelBlends.Blend.ADDITIVE, "shadow", ModelBlends.Blend.MULTIPLY), marked);
    }

    @Test
    void anAddedLightBrightensWhatIsBehindItAndWritesNoDepth() {
        var cone = new Geometry("HEADLIGHT01", new Quad(1f, 1f));
        var material = dressed();

        ModelBlends.carryOver(loaded("Common/MatDefs/Light/PBRLighting.j3md", "searchlight"), material, cone,
                ModelBlends.Blend.ADDITIVE);

        assertEquals(RenderState.BlendMode.Additive, material.getAdditionalRenderState().getBlendMode(),
                "one plus one: added to the mid-grey ground, never taken from it");
        assertFalse(material.getAdditionalRenderState().isDepthWrite());
        assertEquals(RenderQueue.Bucket.Transparent, cone.getQueueBucket());
    }

    @Test
    void aShadowDecalMultipliesWhatIsBehindIt() {
        var decal = new Geometry("SHADOW", new Quad(1f, 1f));
        var material = dressed();

        ModelBlends.carryOver(loaded("Common/MatDefs/Light/PBRLighting.j3md", "shadow"), material, decal,
                ModelBlends.Blend.MULTIPLY);

        assertEquals(RenderState.BlendMode.Modulate, material.getAdditionalRenderState().getBlendMode());
        assertFalse(material.getAdditionalRenderState().isDepthWrite());
    }

    @Test
    void glTFsBlendIsKeptAndWritesNoDepth() {
        var glass = new Geometry("GLASS", new Quad(1f, 1f));
        var fromTheLoader = loaded("Common/MatDefs/Light/PBRLighting.j3md", "glass");
        fromTheLoader.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha); // alphaMode BLEND
        var material = dressed();

        ModelBlends.carryOver(fromTheLoader, material, glass, null);

        assertEquals(RenderState.BlendMode.Alpha, material.getAdditionalRenderState().getBlendMode());
        assertFalse(material.getAdditionalRenderState().isDepthWrite(), "a blended surface writes no depth");
        assertEquals(RenderQueue.Bucket.Transparent, glass.getQueueBucket());
    }

    @Test
    void glTFsMaskCutsOutAtItsCutoffAndStaysSolid() {
        var leaves = new Geometry("LEAVES", new Quad(1f, 1f));
        var fromTheLoader = loaded("Common/MatDefs/Misc/Unshaded.j3md", "leaves");
        fromTheLoader.setFloat("AlphaDiscardThreshold", 0.5f); // alphaMode MASK, alphaCutoff 0.5
        fromTheLoader.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha); // as the unlit loader does
        var material = dressed();

        ModelBlends.carryOver(fromTheLoader, material, leaves, null);

        assertEquals(0.5f, (Float) material.getParam("AlphaDiscardThreshold").getValue(),
                "a texel half transparent, under 0.5, is not drawn");
        assertEquals(RenderState.BlendMode.Off, material.getAdditionalRenderState().getBlendMode(), "and it is solid");
        assertTrue(material.getAdditionalRenderState().isDepthWrite());
        assertTrue(ModelBlends.unlit(fromTheLoader));
    }
}
