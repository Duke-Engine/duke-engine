package uz.dukeengine.client3d.desktop;

import com.jme3.system.JmeContext;
import com.jme3.system.lwjgl.LwjglWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;
import uz.dukeengine.client3d.platform.Window;

/**
 * The window as GLFW keeps it, reached through jME's {@link LwjglWindow}. With no window to ask (no context yet, or a
 * headless one) it answers as a desktop with none: nothing to move, and the AWT's word for the monitor.
 */
final class GlfwWindow implements Window {

    private final Supplier<JmeContext> context;
    /** Where the window was before it filled the screen: x, y, width, height. */
    private int[] windowedBounds;

    GlfwWindow(Supplier<JmeContext> context) {
        this.context = context;
    }

    /** The window's own handle, to ask it what jME does not keep; 0 where there is none. */
    static long handle(Supplier<JmeContext> context) {
        return context.get() instanceof LwjglWindow display ? display.getWindowHandle() : 0L;
    }

    @Override
    public boolean open() {
        return handle(context) != 0L;
    }

    @Override
    public boolean fullscreen() {
        long window = handle(context);
        return window != 0L && GLFW.glfwGetWindowMonitor(window) != 0L;
    }

    @Override
    public boolean fillTheScreen(boolean fill) {
        long window = handle(context);
        if (window == 0L) {
            return false;
        }
        if (fill) {
            windowedBounds = boundsOf(window);
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor == 0L) {
                return false;
            }
            GLFW.glfwSetWindowMonitor(window, monitor, 0, 0, windowedBounds[2], windowedBounds[3], GLFW.GLFW_DONT_CARE);
            return true;
        }
        var back = windowedBounds != null ? windowedBounds : startingBounds();
        GLFW.glfwSetWindowMonitor(window, 0L, back[0], back[1], back[2], back[3], GLFW.GLFW_DONT_CARE);
        return true;
    }

    @Override
    public boolean resize(int w, int h, boolean fullscreen) {
        long window = handle(context);
        long monitor = window == 0L ? 0L : GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            return false;
        }
        boolean filling = GLFW.glfwGetWindowMonitor(window) != 0L;
        if (fullscreen) {
            if (!filling) {
                windowedBounds = boundsOf(window);
            }
            GLFW.glfwSetWindowMonitor(window, monitor, 0, 0, w, h, GLFW.GLFW_DONT_CARE);
        } else {
            var at = filling && windowedBounds != null ? windowedBounds : boundsOf(window);
            GLFW.glfwSetWindowMonitor(window, 0L, at[0], at[1], w, h, GLFW.GLFW_DONT_CARE);
        }
        return true;
    }

    @Override
    public List<int[]> modes() {
        var modes = new ArrayList<int[]>();
        long monitor = open() ? GLFW.glfwGetPrimaryMonitor() : 0L;
        if (monitor != 0L) {
            var shown = GLFW.glfwGetVideoModes(monitor);
            if (shown != null) {
                for (int at = 0; at < shown.limit(); at++) {
                    modes.add(new int[] {shown.get(at).width(), shown.get(at).height()});
                }
            }
            return modes;
        }
        for (var mode : awtDevice().getDisplayModes()) {
            modes.add(new int[] {mode.getWidth(), mode.getHeight()});
        }
        return modes;
    }

    @Override
    public int[] desktopSize() {
        long monitor = open() ? GLFW.glfwGetPrimaryMonitor() : 0L;
        var now = monitor == 0L ? null : GLFW.glfwGetVideoMode(monitor);
        if (now != null) {
            return new int[] {now.width(), now.height()};
        }
        try {
            var mode = awtDevice().getDisplayMode();
            return mode.getWidth() > 0 && mode.getHeight() > 0 ? new int[] {mode.getWidth(), mode.getHeight()} : null;
        } catch (RuntimeException _) {
            return null; // headless, or a platform that will not say
        }
    }

    private static java.awt.GraphicsDevice awtDevice() {
        return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    private static int[] boundsOf(long window) {
        var x = new int[1];
        var y = new int[1];
        var width = new int[1];
        var height = new int[1];
        GLFW.glfwGetWindowPos(window, x, y);
        GLFW.glfwGetWindowSize(window, width, height);
        return new int[] {x[0], y[0], width[0], height[0]};
    }

    /** Where a window that has never been anywhere else should go back to: the size it was made at. */
    private int[] startingBounds() {
        var made = context.get().getSettings();
        return new int[] {60, 60, made.getWidth(), made.getHeight()};
    }
}
