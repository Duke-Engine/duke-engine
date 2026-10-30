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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AndroidPlatform.attach(this);
        if (getLastNonConfigurationInstance() == null) { // else the harness keeps the client it already runs
            app = application();
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        TouchHeldInput.touched(event.getActionMasked());
        return super.dispatchTouchEvent(event);
    }
}
