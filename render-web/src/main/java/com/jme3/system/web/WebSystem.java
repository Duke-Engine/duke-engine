package com.jme3.system.web;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.audio.AudioRenderer;
import com.jme3.font.plugins.BitmapFontLoader;
import com.jme3.material.plugins.J3MLoader;
import com.jme3.shader.plugins.GLSLLoader;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.system.JmeSystem;
import com.jme3.system.JmeSystemDelegate;
import com.jme3.system.Platform;
import com.jme3.util.res.Resources;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;

/**
 * jME's system in a browser. Under TeaVM a class named in a file or a property is not found, so what jME reads from its
 * config elsewhere is set here by code: call {@link #install()} before the app is made.
 */
public class WebSystem extends JmeSystemDelegate {
    public static void install() {
        JmeSystem.setSystemDelegate(new WebSystem());
        Resources.setResourceLoader(new WebFiles());
    }

    /** An asset manager that reads from the page's server, with the loaders a material and a bitmap font need. */
    public static DesktopAssetManager assetManager() {
        DesktopAssetManager assets = new DesktopAssetManager();
        assets.registerLocator("/", WebFiles.class);
        assets.registerLoader(J3MLoader.class, "j3m", "j3md");
        assets.registerLoader(GLSLLoader.class, "vert", "frag", "geom", "tsctrl", "tseval", "glsl", "glsllib");
        assets.registerLoader(BitmapFontLoader.class, "fnt");
        return assets;
    }

    /** Settings for a browser: no audio, and frames as fast as the page's animation frames come. */
    public static AppSettings settings() {
        AppSettings settings = new AppSettings(true);
        settings.setAudioRenderer(null);
        settings.setFrameRate(-1);
        return settings;
    }

    @Override
    public void writeImageFile(OutputStream outStream, String format, ByteBuffer imageData, int width, int height) {
        throw new UnsupportedOperationException("No image files in a browser");
    }

    @Override
    public URL getPlatformAssetConfigURL() {
        return null;
    }

    @Override
    public JmeContext newContext(AppSettings settings, JmeContext.Type contextType) {
        JmeContext context = new WebContext();
        context.setSettings(settings);
        return context;
    }

    @Override
    public AudioRenderer newAudioRenderer(AppSettings settings) {
        return null;
    }

    @Override
    public Platform getPlatform() {
        return Platform.Web;
    }

    @Override
    public void initialize(AppSettings settings) {
    }

    @Override
    public void showSoftKeyboard(boolean show) {
    }
}
