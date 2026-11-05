package com.jme3.scene.plugins.gltf;

import static com.jme3.scene.plugins.gltf.GltfUtils.getAsFloat;

import com.jme3.plugins.json.JsonElement;
import java.io.IOException;

/**
 * Extension loader for "KHR_materials_clearcoat": the layer's amount and roughness become PBRLighting's ClearCoat and
 * ClearCoatRoughness. shortcut: the clearcoat, clearcoat roughness and clearcoat normal textures are left out; read them
 * here (R of the first, G of the second) when a model paints its coat by texture.
 */
public class PBRClearCoatExtensionLoader implements ExtensionLoader {

    @Override
    public Object handleExtension(GltfLoader loader, String parentName, JsonElement parent, JsonElement extension, Object input) throws IOException {
        if (input instanceof PBRMaterialAdapter) {
            var layer = extension.getAsJsonObject();
            // A coat of 0 is no coat: the material keeps the shader path without the layer.
            if (getAsFloat(layer, "clearcoatFactor", 0f) > 0f) {
                var adapter = (PBRMaterialAdapter) input;
                adapter.setParam("clearcoatFactor", getAsFloat(layer, "clearcoatFactor"));
                adapter.setParam("clearcoatRoughnessFactor", getAsFloat(layer, "clearcoatRoughnessFactor", 0f));
            }
        }
        return input;
    }
}
