package com.jme3.scene.plugins.gltf;

import static com.jme3.scene.plugins.gltf.GltfUtils.getAsColor;
import static com.jme3.scene.plugins.gltf.GltfUtils.getAsFloat;

import com.jme3.math.ColorRGBA;
import com.jme3.plugins.json.JsonElement;
import java.io.IOException;

/**
 * Extension loader for "KHR_materials_sheen": the colour and roughness become PBRLighting's SheenColor and
 * SheenRoughness. shortcut: the sheen colour and sheen roughness textures are left out; read them here when a model
 * paints its cloth by texture.
 */
public class PBRSheenExtensionLoader implements ExtensionLoader {

    @Override
    public Object handleExtension(GltfLoader loader, String parentName, JsonElement parent, JsonElement extension, Object input) throws IOException {
        if (input instanceof PBRMaterialAdapter) {
            var layer = extension.getAsJsonObject();
            var colour = getAsColor(layer, "sheenColorFactor", ColorRGBA.Black);
            // A black sheen is none: the material keeps the shader path without the layer.
            if (colour.r + colour.g + colour.b > 0f) {
                var adapter = (PBRMaterialAdapter) input;
                adapter.setParam("sheenColorFactor", colour);
                adapter.setParam("sheenRoughnessFactor", getAsFloat(layer, "sheenRoughnessFactor", 0f));
            }
        }
        return input;
    }
}
