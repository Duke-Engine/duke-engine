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

    private SettingsStore settings;

    @Override
    public GlyphRasterizer glyphs() {
        throw new UnsupportedOperationException("not yet: GlyphRasterizer");
    }

    @Override
    public ImageDecoder images() {
        return new ImageIoDecoder();
    }

    @Override
    public SettingsStore settings() {
        if (settings == null) {
            settings = new FileSettingsStore(); // takes over the registry on first use, not when the platform is found
        }
        return settings;
    }

    @Override
    public TextPrompt prompt() {
        return new SwingTextPrompt();
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
        return new DesktopScreenshots(folder, pattern, saved);
    }
}
