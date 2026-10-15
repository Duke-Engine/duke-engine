package uz.dukeengine.client3d;

import uz.dukeengine.game.CreditsRoll;

/**
 * The credits as the client rolls them, over everything on the game's canvas: the screen darkened and the lines
 * rising from below its foot until the last has passed its top, over the roll's seconds — see {@code
 * DukeGame.creditsRoll}.
 */
final class CreditsRollShow {

    private final CreditsRoll roll;
    private float elapsed;
    private boolean skipped;

    CreditsRollShow(CreditsRoll roll) {
        this.roll = roll;
    }

    CreditsRoll roll() {
        return roll;
    }

    /** Esc or a click: over at the next frame. */
    void skip() {
        skipped = true;
    }

    /** One frame of it, {@code seconds} on; whether it is still up after it. */
    boolean paint(Canvas canvas, float seconds) {
        elapsed += seconds;
        if (skipped || elapsed >= roll.seconds()) {
            return false;
        }
        canvas.fillRect(0f, 0f, canvas.width(), canvas.height(), 0xD8000000);
        var heading = Canvas.Font.of("Georgia", Math.max(18, Math.round(canvas.height() * 0.04f))).bold(true);
        var name = Canvas.Font.of("Georgia", Math.max(14, Math.round(canvas.height() * 0.03f)));
        float spacing = canvas.height() * 0.06f;
        float tall = roll.lines().size() * spacing;
        // From just under the foot of the screen to the last line just past its top.
        float top = canvas.height() - (canvas.height() + tall) * (elapsed / roll.seconds());
        for (int at = 0; at < roll.lines().size(); at++) {
            var line = roll.lines().get(at);
            float y = top + at * spacing;
            if (y < -spacing || y > canvas.height()) {
                continue;
            }
            var font = line.heading() ? heading : name;
            var size = canvas.measure(font, line.text());
            canvas.drawText(font, line.text(), (canvas.width() - size.width()) / 2f, y,
                    line.heading() ? 0xFFF0D890 : 0xFFE6E0D0);
        }
        return true;
    }
}
