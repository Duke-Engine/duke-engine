package uz.dukeengine.skirmish.web;

import com.jme3.app.LegacyApplication;
import com.jme3.system.web.WebSystem;
import org.teavm.jso.JSBody;
import uz.dukeengine.client3d.web.WebAssets;
import uz.dukeengine.skirmish.Main;

/** The skirmish in a browser page: the same client the desktop opens, on the page's canvas. */
public final class SkirmishWeb {

    private SkirmishWeb() {
    }

    /** The page address's {@code stats} parameter, or null. */
    @JSBody(script = "return new URLSearchParams(location.search).get('stats');")
    private static native String statsParameter();

    public static void main(String[] args) {
        WebSystem.install();
        // `?stats=5`: a stats line every 5 s in the console (FrameStats); read before the client is made
        var stats = statsParameter();
        if (stats != null) {
            System.setProperty("duke.stats", stats);
        }
        LegacyApplication app = Main.client(null).application();
        app.setAssetManager(WebAssets.assetManager());
        app.setSettings(WebSystem.settings());
        app.start();
    }
}
