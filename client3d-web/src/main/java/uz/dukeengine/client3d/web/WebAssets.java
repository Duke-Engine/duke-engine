package uz.dukeengine.client3d.web;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.plugins.gltf.BinLoader;
import com.jme3.scene.plugins.gltf.GlbLoader;
import com.jme3.scene.plugins.gltf.GltfLoader;
import com.jme3.system.web.WebSystem;

/** The client's asset manager in a browser: the page's files, with the loaders of a game's models and pictures. */
public final class WebAssets {

    private WebAssets() {
    }

    public static DesktopAssetManager assetManager() {
        var assets = WebSystem.assetManager();
        assets.registerLoader(GltfLoader.class, "gltf");
        assets.registerLoader(GlbLoader.class, "glb");
        assets.registerLoader(BinLoader.class, "bin");
        assets.registerLoader(CanvasTextureLoader.class, "png", "jpg", "jpeg", "gif", "bmp", "webp");
        return assets;
    }
}
