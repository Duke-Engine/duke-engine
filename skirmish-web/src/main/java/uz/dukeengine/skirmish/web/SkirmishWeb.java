package uz.dukeengine.skirmish.web;

import com.jme3.app.LegacyApplication;
import com.jme3.system.web.WebSystem;
import uz.dukeengine.client3d.web.WebAssets;
import uz.dukeengine.skirmish.Main;

/** The skirmish in a browser page: the same client the desktop opens, on the page's canvas. */
public final class SkirmishWeb {

    private SkirmishWeb() {
    }

    public static void main(String[] args) {
        WebSystem.install();
        LegacyApplication app = Main.client(null).application();
        app.setAssetManager(WebAssets.assetManager());
        app.setSettings(WebSystem.settings());
        app.start();
    }
}
