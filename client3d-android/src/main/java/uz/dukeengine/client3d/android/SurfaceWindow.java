package uz.dukeengine.client3d.android;

import android.app.Activity;
import com.jme3.system.JmeContext;
import java.util.List;
import java.util.function.Supplier;
import uz.dukeengine.client3d.platform.Window;

/**
 * The screen the GL surface fills. It always fills it: there is no other size to choose and no window to move, so a
 * resize is refused and the one mode is the screen's.
 */
final class SurfaceWindow implements Window {

    private final Supplier<JmeContext> context;
    private final Activity activity;

    SurfaceWindow(Supplier<JmeContext> context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    @Override
    public boolean open() {
        return context.get() != null;
    }

    @Override
    public boolean fullscreen() {
        return true;
    }

    @Override
    public boolean fillTheScreen(boolean fill) {
        return fill;
    }

    @Override
    public boolean resize(int w, int h, boolean fullscreen) {
        return false;
    }

    @Override
    public List<int[]> modes() {
        return List.of(desktopSize());
    }

    @Override
    public int[] desktopSize() {
        var screen = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        return new int[] {screen.width(), screen.height()};
    }
}
