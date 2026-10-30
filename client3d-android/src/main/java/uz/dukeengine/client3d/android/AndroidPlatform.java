package uz.dukeengine.client3d.android;

import android.app.Activity;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.system.JmeContext;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;
import uz.dukeengine.client3d.platform.ClientPlatform;
import uz.dukeengine.client3d.platform.GlyphRasterizer;
import uz.dukeengine.client3d.platform.HeldInput;
import uz.dukeengine.client3d.platform.ImageDecoder;
import uz.dukeengine.client3d.platform.SettingsStore;
import uz.dukeengine.client3d.platform.TextPrompt;
import uz.dukeengine.client3d.platform.Window;

/**
 * Android's platform: android.graphics' fonts and pictures, a dialog's prompt, the screen the surface fills, the app's
 * own files. Everything it answers is the activity's, which {@link DukeActivity} hands it before the client is made.
 */
public final class AndroidPlatform implements ClientPlatform {

    /** Kept here because ServiceLoader makes the platform, with nothing to hand it. */
    private static volatile Activity activity;

    static void attach(Activity running) {
        activity = running;
    }

    private static Activity activity() {
        var running = activity;
        if (running == null) {
            throw new IllegalStateException("the Android platform was asked before its activity started: start the "
                    + "game from a subclass of " + DukeActivity.class.getName());
        }
        return running;
    }

    @Override
    public float uiScale() {
        return activity().getResources().getDisplayMetrics().density;
    }

    @Override
    public GlyphRasterizer glyphs() {
        return new PaintGlyphs();
    }

    @Override
    public ImageDecoder images() {
        return new BitmapFactoryDecoder();
    }

    @Override
    public SettingsStore settings() {
        return new AppFileSettingsStore(new File(activity().getFilesDir(), "settings.properties"));
    }

    @Override
    public TextPrompt prompt() {
        return new DialogTextPrompt(activity());
    }

    @Override
    public Window window(Supplier<JmeContext> context) {
        return new SurfaceWindow(context, activity());
    }

    @Override
    public HeldInput held(Supplier<JmeContext> context) {
        return new TouchHeldInput(activity());
    }

    /** Always in the app's own files: {@code folder} is taken inside them, the files themselves where it is null. */
    @Override
    public ScreenshotAppState screenshots(String folder, String pattern, Consumer<String> saved) {
        var files = activity().getFilesDir();
        return new AndroidScreenshots(folder == null ? files : new File(files, folder), pattern, saved);
    }
}
