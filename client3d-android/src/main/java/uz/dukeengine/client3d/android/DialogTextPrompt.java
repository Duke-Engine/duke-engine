package uz.dukeengine.client3d.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.widget.EditText;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import uz.dukeengine.client3d.platform.TextPrompt;

/** An AlertDialog with a line to type in, put up on the UI thread while the caller waits. */
final class DialogTextPrompt implements TextPrompt {

    private final Activity activity;

    DialogTextPrompt(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String ask(String prompt, String initial) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("asked on the UI thread, which the dialog needs to come up");
        }
        var answer = new AtomicReference<String>();
        var answered = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            var line = new EditText(activity);
            line.setSingleLine(true);
            line.setText(initial);
            new AlertDialog.Builder(activity)
                    .setMessage(prompt)
                    .setView(line)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> answer.set(line.getText().toString()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener(dialog -> answered.countDown()) // after OK's answer, and on cancel or back
                    .show();
        });
        try {
            answered.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return null;
        }
        return answer.get();
    }
}
