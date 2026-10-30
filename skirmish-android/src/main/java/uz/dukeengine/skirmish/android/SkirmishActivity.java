package uz.dukeengine.skirmish.android;

import com.jme3.app.LegacyApplication;
import uz.dukeengine.client3d.android.DukeActivity;
import uz.dukeengine.client3d.touch.TouchRts;
import uz.dukeengine.skirmish.Main;

/** The skirmish on Android: the desktop's game, played by finger. */
public final class SkirmishActivity extends DukeActivity {

    @Override
    protected LegacyApplication application() {
        return Main.client(null).controls(new TouchRts()).application();
    }
}
