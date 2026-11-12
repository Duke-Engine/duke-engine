package com.jme3.input.web;

import com.jme3.cursors.plugins.JmeCursor;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.InputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * The mouse over the canvas. The page's events are queued as they come and handed to jME on its update; positions are
 * in the canvas's drawing pixels, y up, as jME counts.
 */
public class WebMouseInput implements MouseInput {
    private final JSObject canvas;
    private final List<InputEvent> events = new ArrayList<>();
    private RawInputListener listener;
    private boolean initialized;
    private int x;
    private int y;
    private int wheel;

    public WebMouseInput(JSObject canvas) {
        this.canvas = canvas;
    }

    @JSFunctor
    interface Handler extends JSObject {
        /** kind: 0 move, 1 press, 2 release, 3 wheel; x, y in drawing pixels from the bottom left. */
        void handle(int kind, int x, int y, int button, int wheel);
    }

    @JSBody(params = {"c", "h"}, script = """
            function at(e, k, b, d) { var r = c.getBoundingClientRect();
              var sx = c.width / Math.max(1, r.width), sy = c.height / Math.max(1, r.height);
              h(k, Math.round((e.clientX - r.left) * sx), Math.round(c.height - (e.clientY - r.top) * sy), b, d); }
            c.addEventListener('mousemove', function(e) { at(e, 0, 0, 0); });
            c.addEventListener('mousedown', function(e) { at(e, 1, e.button, 0); });
            window.addEventListener('mouseup', function(e) { at(e, 2, e.button, 0); });
            c.addEventListener('wheel', function(e) { e.preventDefault();
              at(e, 3, 0, e.deltaY < 0 ? 120 : e.deltaY > 0 ? -120 : 0); }, {passive: false});
            c.addEventListener('contextmenu', function(e) { e.preventDefault(); });
            """)
    private static native void listen(JSObject canvas, Handler handler);

    @JSBody(params = {"c", "v"}, script = "c.style.cursor = v ? '' : 'none';")
    private static native void cursor(JSObject canvas, boolean visible);

    @Override
    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        listen(canvas, this::handle);
    }

    private void handle(int kind, int nx, int ny, int button, int dwheel) {
        int dx = nx - x;
        int dy = ny - y;
        x = nx;
        y = ny;
        wheel += dwheel;
        MouseMotionEvent motion = new MouseMotionEvent(x, y, dx, dy, wheel, dwheel);
        switch (kind) {
            case 1, 2 -> {
                if (dx != 0 || dy != 0) {
                    events.add(motion);
                }
                // The page counts left, middle, right; jME left, right, middle.
                int index = button == 1 ? MouseInput.BUTTON_MIDDLE : button == 2 ? MouseInput.BUTTON_RIGHT : button;
                events.add(new MouseButtonEvent(index, kind == 1, x, y));
            }
            default -> events.add(motion);
        }
    }

    @Override
    public void update() {
        if (listener == null) {
            events.clear();
            return;
        }
        for (InputEvent event : events) {
            if (event instanceof MouseMotionEvent motion) {
                listener.onMouseMotionEvent(motion);
            } else {
                listener.onMouseButtonEvent((MouseButtonEvent) event);
            }
        }
        events.clear();
    }

    @Override
    public void destroy() {
        initialized = false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void setInputListener(RawInputListener listener) {
        this.listener = listener;
    }

    @Override
    public long getInputTimeNanos() {
        return System.nanoTime();
    }

    @Override
    public void setCursorVisible(boolean visible) {
        cursor(canvas, visible);
    }

    @Override
    public int getButtonCount() {
        return 3;
    }

    @Override
    public void setNativeCursor(JmeCursor cursor) {
    }
}
