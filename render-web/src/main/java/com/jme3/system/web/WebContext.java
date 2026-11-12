package com.jme3.system.web;

import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.web.WebKeyInput;
import com.jme3.input.web.WebMouseInput;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.opengl.GLExt;
import com.jme3.renderer.opengl.GLFbo;
import com.jme3.renderer.opengl.GLRenderer;
import com.jme3.renderer.web.WebGL;
import com.jme3.system.NanoTimer;
import com.jme3.system.NullContext;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * A canvas of the page (id "duke") as jME's display. The loop is a green thread that waits for each animation frame
 * and then updates: a frame callback cannot itself wait on a monitor, which the app's code may do.
 */
public class WebContext extends NullContext {
    private final JSObject canvas = canvas("duke");
    private GLRenderer gl;
    private WebMouseInput mouse;
    private WebKeyInput keys;

    @JSBody(params = "id", script = "return document.getElementById(id);")
    private static native JSObject canvas(String id);

    @JSBody(params = "c", script = "return c.getContext('webgl2', {antialias: true});")
    private static native JSObject context(JSObject canvas);

    /** Sizes the canvas's drawing buffer to its laid-out size in device pixels; answers whether it changed. */
    @JSBody(params = "c", script = "var r = window.devicePixelRatio || 1;"
            + " var w = Math.max(1, Math.round(c.clientWidth * r)), h = Math.max(1, Math.round(c.clientHeight * r));"
            + " if (c.width === w && c.height === h) return false; c.width = w; c.height = h; return true;")
    private static native boolean fit(JSObject canvas);

    @JSBody(params = "c", script = "return c.width;")
    private static native int width(JSObject canvas);

    @JSBody(params = "c", script = "return c.height;")
    private static native int height(JSObject canvas);

    @JSFunctor
    interface Frame extends JSObject {
        void run();
    }

    @JSBody(params = "f", script = "requestAnimationFrame(function() { f(); });")
    private static native void requestFrame(Frame f);

    @Async
    private static native Void nextFrame();

    private static void nextFrame(AsyncCallback<Void> callback) {
        requestFrame(() -> callback.complete(null));
    }

    @Override
    public Type getType() {
        return Type.Display;
    }

    @Override
    protected void initInThread() {
        Thread.setDefaultUncaughtExceptionHandler((thread, thrown) ->
                listener.handleError("Uncaught exception thrown in " + thread, thrown));
        JSObject context = canvas == null ? null : context(canvas);
        if (context == null) {
            listener.handleError("No canvas with id \"duke\" or no WebGL2 in this browser", null);
            return;
        }
        fit(canvas);
        settings.setResolution(width(canvas), height(canvas));
        timer = new NanoTimer();
        WebGL webGl = new WebGL(context);
        gl = new GLRenderer(webGl, webGl, webGl);
        gl.initialize();
        synchronized (createdLock) {
            created.set(true);
            createdLock.notifyAll();
        }
        listener.initialize();
    }

    @Override
    public void run() {
        initInThread();
        while (gl != null && !needClose.get()) {
            nextFrame();
            if (fit(canvas)) {
                settings.setResolution(width(canvas), height(canvas));
                listener.reshape(width(canvas), height(canvas));
            }
            listener.update();
            gl.postFrame();
        }
        deinitInThread();
    }

    @Override
    public Renderer getRenderer() {
        return gl;
    }

    @Override
    public MouseInput getMouseInput() {
        if (mouse == null) {
            mouse = new WebMouseInput(canvas);
        }
        return mouse;
    }

    @Override
    public KeyInput getKeyInput() {
        if (keys == null) {
            keys = new WebKeyInput(canvas);
        }
        return keys;
    }

    @Override
    public boolean isRenderable() {
        return gl != null;
    }

    @Override
    public int getFramebufferWidth() {
        return width(canvas);
    }

    @Override
    public int getFramebufferHeight() {
        return height(canvas);
    }

    /** The canvas is the window, and it sits where the page puts it: at the origin, as far as jME can tell. */
    @Override
    public int getWindowXPosition() {
        return 0;
    }

    @Override
    public int getWindowYPosition() {
        return 0;
    }
}
