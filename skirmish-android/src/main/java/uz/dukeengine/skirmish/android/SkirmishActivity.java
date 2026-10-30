package uz.dukeengine.skirmish.android;

import com.jme3.app.LegacyApplication;
import uz.dukeengine.client3d.android.DukeActivity;
import uz.dukeengine.client3d.touch.TouchRts;
import uz.dukeengine.skirmish.Main;

/** The skirmish on Android: the desktop's game, played by finger. */
public final class SkirmishActivity extends DukeActivity {

    @Override
    protected LegacyApplication application() {
        // `am start --es stats 5`: a stats line every 5 s in logcat (FrameStats); read before the client is made
        var stats = getIntent().getStringExtra("stats");
        if (stats != null) {
            System.setProperty("duke.stats", stats);
        }
        return Main.client(null).controls(new TouchRts()).application();
    }
}
