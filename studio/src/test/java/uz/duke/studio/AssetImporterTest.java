package uz.duke.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.duke.studio.io.AssetImporter;

class AssetImporterTest {

    @TempDir
    Path temp;

    @Test
    void selfContainedModelCopiesAlone() throws Exception {
        var source = temp.resolve("downloads");
        Files.createDirectories(source);
        Files.writeString(source.resolve("tank.glb"), "glb-bytes");
        Files.writeString(source.resolve("unrelated.txt"), "junk");
        var assets = temp.resolve("assets");

        var relative = AssetImporter.importAsset(assets, source.resolve("tank.glb"), "Models");

        assertEquals("Models/tank.glb", relative);
        assertTrue(Files.exists(assets.resolve("Models/tank.glb")));
        assertFalse(Files.exists(assets.resolve("Models/unrelated.txt")),
                ".glb is self-contained — no siblings copied");
    }

    @Test
    void gltfBringsItsBuffersAndTextures() throws Exception {
        var source = temp.resolve("downloads");
        Files.createDirectories(source);
        Files.writeString(source.resolve("hero.gltf"), "{}");
        Files.writeString(source.resolve("hero.bin"), "buffer");
        Files.writeString(source.resolve("skin.png"), "img");
        Files.writeString(source.resolve("readme.txt"), "junk");
        var assets = temp.resolve("assets");

        var relative = AssetImporter.importAsset(assets, source.resolve("hero.gltf"), "Models");

        assertEquals("Models/hero.gltf", relative);
        assertTrue(Files.exists(assets.resolve("Models/hero.bin")), "same-name buffer comes along");
        assertTrue(Files.exists(assets.resolve("Models/skin.png")), "textures come along");
        assertFalse(Files.exists(assets.resolve("Models/readme.txt")), "junk stays behind");
    }

    @Test
    void soundGoesToItsCategory() throws Exception {
        var source = temp.resolve("downloads");
        Files.createDirectories(source);
        Files.writeString(source.resolve("boom.ogg"), "audio");

        var relative = AssetImporter.importAsset(temp.resolve("assets"), source.resolve("boom.ogg"), "Sounds");

        assertEquals("Sounds/boom.ogg", relative);
        assertTrue(Files.exists(temp.resolve("assets/Sounds/boom.ogg")));
    }
}
