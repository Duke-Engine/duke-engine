package uz.dukeengine.client3d.web;

import com.jme3.app.state.ScreenshotAppState;
import com.jme3.system.JmeContext;
import java.util.function.Consumer;
import java.util.function.Supplier;
import uz.dukeengine.client3d.platform.ClientPlatform;
import uz.dukeengine.client3d.platform.GlyphRasterizer;
import uz.dukeengine.client3d.platform.HeldInput;
import uz.dukeengine.client3d.platform.ImageDecoder;
import uz.dukeengine.client3d.platform.SettingsStore;
import uz.dukeengine.client3d.platform.TextPrompt;
import uz.dukeengine.client3d.platform.Window;

/** The client's platform in a browser page. */
public final class WebPlatform implements ClientPlatform {

    @Override
    public GlyphRasterizer glyphs() {
        throw new UnsupportedOperationException("glyphs");
    }

    @Override
    public ImageDecoder images() {
        throw new UnsupportedOperationException("images");
    }

    @Override
    public SettingsStore settings() {
        throw new UnsupportedOperationException("settings");
    }

    @Override
    public TextPrompt prompt() {
        throw new UnsupportedOperationException("prompt");
    }

    @Override
    public Window window(Supplier<JmeContext> context) {
        throw new UnsupportedOperationException("window");
    }

    @Override
    public HeldInput held(Supplier<JmeContext> context) {
        throw new UnsupportedOperationException("held");
    }

    @Override
    public ScreenshotAppState screenshots(String folder, String pattern, Consumer<String> saved) {
        throw new UnsupportedOperationException("screenshots");
    }
}
