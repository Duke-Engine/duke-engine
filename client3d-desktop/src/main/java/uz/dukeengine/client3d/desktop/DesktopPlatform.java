package uz.dukeengine.client3d.desktop;

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

/** The desktop's platform: AWT's fonts and pictures, Swing's prompt, GLFW's window. Filled in a seam at a time. */
public final class DesktopPlatform implements ClientPlatform {

    @Override
    public GlyphRasterizer glyphs() {
        throw new UnsupportedOperationException("not yet: GlyphRasterizer");
    }

    @Override
    public ImageDecoder images() {
        throw new UnsupportedOperationException("not yet: ImageDecoder");
    }

    @Override
    public SettingsStore settings() {
        throw new UnsupportedOperationException("not yet: SettingsStore");
    }

    @Override
    public TextPrompt prompt() {
        throw new UnsupportedOperationException("not yet: TextPrompt");
    }

    @Override
    public Window window(Supplier<JmeContext> context) {
        return new GlfwWindow(context);
    }

    @Override
    public HeldInput held(Supplier<JmeContext> context) {
        return new GlfwHeldInput(context);
    }

    @Override
    public ScreenshotAppState screenshots(String folder, String pattern, Consumer<String> saved) {
        throw new UnsupportedOperationException("not yet: Screenshots");
    }
}
