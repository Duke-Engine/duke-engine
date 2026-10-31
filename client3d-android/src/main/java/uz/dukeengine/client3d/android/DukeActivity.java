package uz.dukeengine.client3d.android;

import android.os.Bundle;
import android.view.MotionEvent;
import com.jme3.app.AndroidHarness;
import com.jme3.app.LegacyApplication;

/**
 * The activity a game's Android launcher extends: jME's harness, running the client the game makes. It hands this
 * activity to the platform first, so the client finds its fonts, settings and screen as it is made.
 */
public abstract class DukeActivity extends AndroidHarness {

    protected DukeActivity() {
        // Select and order act at the cursor, so the first finger moves it and is the left button; the touch schemes
        // read every finger's own events beside it.
        mouseEventsEnabled = true;
    }

    /** The game's client, configured and not started: {@code Duke3D.of(game, visuals)...application()}. */
    protected abstract LegacyApplication application();

    /**
     * How many of the screen's pixels one of the interface's design is: the display's density, so a HUD drawn for a
     * desktop is as large to a finger. A game that lays its own screens out by the window's resolution, as Generals'
     * do, says 1.
     */
    protected float uiScale() {
        return getResources().getDisplayMetrics().density;
    }

    /**
     * jME's buffers in native memory: ART makes a direct ByteBuffer in the Java heap's non-moving space, about 64 MB on
     * the Vivo V2247, which a game's worth of models fills ("malloc_space fragmentation"). Before jME's BufferUtils is
     * first loaded, which reads it once; a property already set is kept.
     *
     * <p>shortcut: a buffer let go is never freed here, only one destroyed — code that makes buffers per frame leaks
     * (the canvas's pictures did, 8 MB a second); free on collection with a Cleaner if another such leak turns up.
     */
    static void nativeBuffers() {
        if (System.getProperty("com.jme3.BufferAllocatorImplementation") == null) {
            System.setProperty("com.jme3.BufferAllocatorImplementation",
                    com.jme3.util.AndroidNativeBufferAllocator.class.getName());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        nativeBuffers();
        AndroidPlatform.attach(this);
        ContentPack.mount(getExternalFilesDir(null), getClassLoader()); // a downloaded pack's files, where there is one
        if (getLastNonConfigurationInstance() == null) { // else the harness keeps the client it already runs
            app = application();
        }
        // The harness asks for 16 bits of depth, the desktop has 24: a model's layers laid one on another (a house
        // colour over its body) fought in stripes at 16.
        eglDepthBits = 24;
        super.onCreate(savedInstanceState);
        // The whole screen, past a camera's notch too: the game draws its own edges.
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setDecorFitsSystemWindows(false);
        // A game is watched, not touched, for minutes at a time: the screen stays on while it is in front.
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            immersive(); // back from a dialog or another app the bars came back
        }
    }

    /** The status and navigation bars hidden: a swipe from the edge shows them a while, and they hide again. */
    private void immersive() {
        var bars = getWindow().getInsetsController();
        if (bars != null) {
            bars.hide(android.view.WindowInsets.Type.systemBars());
            bars.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        TouchHeldInput.touched(event.getActionMasked());
        return super.dispatchTouchEvent(event);
    }
}
