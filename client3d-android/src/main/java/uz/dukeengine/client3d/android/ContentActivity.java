package uz.dukeengine.client3d.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Locale;

/**
 * The screen a game with a {@link ContentPack} starts on. When the pack is not unpacked yet it downloads it, with a bar,
 * the megabytes and the speed, keeps the screen on and offers Retry when it fails; then it puts the pack on the class
 * path, runs the game's {@link #prepare} and starts the game's {@link DukeActivity}. The work runs on its own thread for
 * the whole process, so turning the phone or leaving the screen does not stop it; a killed process resumes the download
 * on the next start.
 */
public abstract class ContentActivity extends Activity {

    /** The game's pack: {@code new ContentPack(getExternalFilesDir(null), url, version, size, sha256)}. */
    protected abstract ContentPack pack();

    /** The game's activity, started once the pack is there and {@link #prepare} is done. */
    protected abstract Class<? extends DukeActivity> game();

    /**
     * Runs off the UI thread once the pack is on the class path, before {@link #game} starts: a game reads its data here,
     * where the screen still answers, rather than in {@link DukeActivity#application}, which Android runs on the UI
     * thread (a touch during a long load there is an "app not responding").
     */
    protected void prepare() {
    }

    // The one worker of the process, read by whichever screen shows it.
    private static Thread worker;
    private static volatile String step = "download";
    private static volatile long done;
    private static volatile long total;
    private static volatile long startedAt;
    private static volatile long startedFrom;
    private static volatile String failed;
    private static volatile boolean prepared;

    private final Handler ticks = new Handler(Looper.getMainLooper());
    private ProgressBar bar;
    private TextView title;
    private TextView said;
    private Button retry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DukeActivity.nativeBuffers(); // before prepare() loads anything of jME
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        var screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(pad, pad, pad, pad);
        title = new TextView(this);
        title.setTextSize(20);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        said = new TextView(this);
        retry = new Button(this);
        retry.setText("Retry");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(click -> start());
        screen.addView(title);
        screen.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        screen.addView(said);
        screen.addView(retry);
        setContentView(screen);
        start();
        ticks.post(this::tick);
    }

    @Override
    protected void onDestroy() {
        ticks.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void start() {
        synchronized (ContentActivity.class) {
            if (worker != null && worker.isAlive()) {
                return;
            }
            failed = null;
            prepared = false;
            step = "download";
            done = 0;
            total = 0;
            startedAt = System.nanoTime();
            startedFrom = -1;
            var pack = pack();
            var base = getExternalFilesDir(null);
            var loader = getClassLoader();
            // A deep stack, as the main thread's: a game's loading may recurse through its data as it does there.
            worker = new Thread(null, () -> {
                try {
                    if (!pack.ready()) {
                        pack.fetch((what, at, of) -> {
                            if (startedFrom < 0) {
                                startedFrom = at;
                            }
                            step = what;
                            done = at;
                            total = of;
                        });
                    }
                    ContentPack.mount(base, loader);
                    step = "load";
                    prepare();
                    prepared = true;
                } catch (Exception | Error e) {
                    android.util.Log.e("ContentActivity", "the game's data could not be made ready", e);
                    failed = e.getMessage() != null ? e.getMessage() : e.toString();
                }
            }, "content", 64L << 20);
            worker.start();
        }
        retry.setVisibility(View.GONE);
    }

    private void tick() {
        if (prepared) {
            ticks.removeCallbacksAndMessages(null);
            startActivity(new Intent(this, game()));
            finish();
            return;
        }
        var problem = failed;
        var now = step;
        title.setText(now.equals("load") ? "Loading the game" : "Downloading the game's data");
        bar.setIndeterminate(now.equals("load") && problem == null);
        if (problem != null) {
            said.setText("Failed: " + problem);
            retry.setVisibility(View.VISIBLE);
        } else {
            long at = done;
            long of = Math.max(1, total);
            bar.setProgress((int) (1000 * at / of));
            double seconds = (System.nanoTime() - startedAt) / 1e9;
            said.setText(switch (now) {
                case "load" -> "";
                case "check" -> "Checking the download...";
                case "unpack" -> String.format(Locale.ROOT, "Unpacking: %d / %d MB", ContentPack.mb(at),
                        ContentPack.mb(of));
                default -> String.format(Locale.ROOT, "%d / %d MB, %.1f MB/s", ContentPack.mb(at),
                        ContentPack.mb(of), startedFrom < 0 ? 0 : (at - startedFrom) / 1e6 / Math.max(seconds, 0.001));
            });
        }
        ticks.postDelayed(this::tick, 250);
    }
}
