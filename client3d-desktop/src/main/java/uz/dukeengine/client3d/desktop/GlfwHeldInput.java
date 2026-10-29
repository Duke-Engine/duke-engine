package uz.dukeengine.client3d.desktop;

import com.jme3.system.JmeContext;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;
import uz.dukeengine.client3d.platform.HeldInput;

/** What GLFW says is down in the window right now. With no window: a button is held, the window is not focused. */
final class GlfwHeldInput implements HeldInput {

    private final Supplier<JmeContext> context;

    GlfwHeldInput(Supplier<JmeContext> context) {
        this.context = context;
    }

    /** The pointer is in the window: the window says so, where jME keeps only where it was last seen. */
    @Override
    public boolean pointerInside() {
        long window = GlfwWindow.handle(context);
        return window == 0L || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE;
    }

    @Override
    public boolean focused() {
        long window = GlfwWindow.handle(context);
        return window != 0L && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 0;
    }

    @Override
    public boolean buttonHeld(int button) {
        long window = GlfwWindow.handle(context);
        return window == 0L || GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    /** The letter or digit {@code key}: its GLFW code is the character itself. */
    @Override
    public boolean keyHeld(char key) {
        long window = GlfwWindow.handle(context);
        return window != 0L && GLFW.glfwGetKey(window, Character.toUpperCase(key)) == GLFW.GLFW_PRESS;
    }
}
