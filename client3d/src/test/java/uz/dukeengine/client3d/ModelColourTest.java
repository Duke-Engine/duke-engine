package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A model's own colour under the look's tint: a glTF piece with no picture drawn in its {@code baseColorFactor}, as a
 * textured one is drawn in its picture — read off models the glTF loader itself loaded.
 */
class ModelColourTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    @TempDir
    static Path folder;

    @BeforeAll
    static void lookInTheFolder() {
        ASSETS.registerLocator(folder.toString(), FileLocator.class);
    }

    /** One triangle, its positions as a buffer written into the file. */
    private static final String TRIANGLE = """
            "scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"mesh": 0}],
            "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "material": 0}]}],
            "buffers": [{"byteLength": 36, "uri": "data:application/octet-stream;base64,%s"}],
            "bufferViews": [{"buffer": 0, "byteLength": 36}],
            "accessors": [{"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
              "min": [0, 0, 0], "max": [1, 1, 0]}]
            """;

    private static String positions() {
        var bytes = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f}) {
            bytes.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(bytes.array());
    }

    private static String onePixel() throws IOException {
        var picture = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        picture.setRGB(0, 0, 0x8B4513);
        var png = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", png);
        return Base64.getEncoder().encodeToString(png.toByteArray());
    }

    /** The material the loader gave the one piece of a glTF file written with {@code rest} beside its triangle. */
    private static Material loaded(String file, String rest) throws IOException {
        Files.writeString(folder.resolve(file), "{\"asset\": {\"version\": \"2.0\"}, "
                + TRIANGLE.formatted(positions()) + ", " + rest + "}");
        var model = ASSETS.loadModel(file);
        var found = new Material[1];
        model.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry && found[0] == null) {
                found[0] = geometry.getMaterial();
            }
        });
        return found[0];
    }

    private static void assertColour(float r, float g, float b, ColorRGBA colour) {
        assertEquals(r, colour.r, 1e-6f);
        assertEquals(g, colour.g, 1e-6f);
        assertEquals(b, colour.b, 1e-6f);
    }

    /** The dungeon's key: one silver material, no texture — drawn silver under a white tint, not white. */
    @Test
    void anUntexturedPieceIsDrawnInItsOwnBaseColour() throws IOException {
        var silver = loaded("key.gltf", """
                "materials": [{"name": "silver mat", "pbrMetallicRoughness": {
                  "baseColorFactor": [0.24, 0.24, 0.25, 1.0], "metallicFactor": 1.0, "roughnessFactor": 0.16}}]
                """);
        assertNull(DukeRtsApp.skinOf(silver), "no picture of its own");

        var colour = DukeRtsApp.ownColour(silver, null, "key.gltf").mult(ColorRGBA.White);
        assertColour(0.24f, 0.24f, 0.25f, colour);

        var tinted = DukeRtsApp.ownColour(silver, null, "key.gltf").mult(new ColorRGBA(0.5f, 1f, 1f, 1f));
        assertColour(0.12f, 0.24f, 0.25f, tinted);
    }

    /** An unlit surface keeps its own colour the same way: the loader holds it as the unshaded material's colour. */
    @Test
    void anUnlitUntexturedPieceIsDrawnInItsOwnBaseColour() throws IOException {
        var lamp = loaded("lamp.gltf", """
                "extensionsUsed": ["KHR_materials_unlit"],
                "materials": [{"name": "glow", "extensions": {"KHR_materials_unlit": {}},
                  "pbrMetallicRoughness": {"baseColorFactor": [1.0, 0.8, 0.2, 1.0]}}]
                """);
        assertColour(1f, 0.8f, 0.2f, DukeRtsApp.ownColour(lamp, null, "lamp.gltf"));
    }

    /** A textured piece draws as it did: its picture carries its colours, and the tint is laid over it alone. */
    @Test
    void aTexturedPieceIsDrawnInItsPictureAsBefore() throws IOException {
        var gate = loaded("gate.gltf", """
                "materials": [{"name": "stone", "pbrMetallicRoughness": {
                  "baseColorFactor": [0.5, 0.5, 0.5, 1.0], "baseColorTexture": {"index": 0}}}],
                "textures": [{"source": 0}],
                "images": [{"uri": "data:image/png;base64,%s"}]
                """.formatted(onePixel()));
        var picture = DukeRtsApp.skinOf(gate);
        assertNotNull(picture, "the picture it came with");
        assertEquals(ColorRGBA.White, DukeRtsApp.ownColour(gate, picture, "gate.gltf"));
    }

    /** A piece given the look's own picture is drawn in that picture, whatever colour its file gives it. */
    @Test
    void aPieceWearingTheLooksPictureIsDrawnInIt() throws IOException {
        var silver = loaded("amulet.gltf", """
                "materials": [{"name": "silver", "pbrMetallicRoughness": {"baseColorFactor": [0.24, 0.24, 0.24, 1.0]}}]
                """);
        var named = new com.jme3.texture.Texture2D(1, 1, com.jme3.texture.Image.Format.RGBA8);
        assertEquals(ColorRGBA.White, DukeRtsApp.ownColour(silver, named, "amulet.gltf"));
    }

    /** Any other file is dressed as every piece was: white under the tint. */
    @Test
    void aPieceOfAnotherKindOfFileIsWhiteAsBefore() {
        var material = new Material(ASSETS, "Common/MatDefs/Light/PBRLighting.j3md");
        material.setColor("BaseColor", new ColorRGBA(0.2f, 0.4f, 0.6f, 1f));
        assertEquals(ColorRGBA.White, DukeRtsApp.ownColour(material, null, "barrel.j3o"));
        assertEquals(ColorRGBA.White, DukeRtsApp.ownColour(null, null, "key.glb"));
    }
}
