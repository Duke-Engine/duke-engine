package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A skin named for a model is read the way the model reads its own.
 *
 * <p>Found by looking rather than by a test. The Skeleton Healer's green robe came out
 * cream: a kit's colours are swatches laid out in rows, and a skin read the other way up
 * puts every face on the row below the one it was painted for.
 */
class UnitSkinTest {

    @Test
    void aGltfModelsSkinIsReadTopRowFirst() {
        assertFalse(DukeRtsApp.skinKey("models/monsters/skin.png", "models/monsters/mage.glb").isFlipY());
        assertFalse(DukeRtsApp.skinKey("models/heroes/skin.png", "models/heroes/rogue.gltf").isFlipY());
    }

    @Test
    void anythingElseIsReadAsATextureLoadedByNameAlwaysWas() {
        assertTrue(DukeRtsApp.skinKey("models/old/skin.png", "models/old/thing.j3o").isFlipY());
        assertTrue(DukeRtsApp.skinKey("models/old/skin.png", null).isFlipY());
    }

    @Test
    void itIsMippedAsATextureLoadedByNameIs() {
        assertTrue(DukeRtsApp.skinKey("models/monsters/skin.png", "models/monsters/mage.glb")
                .isGenerateMips());
    }
}
