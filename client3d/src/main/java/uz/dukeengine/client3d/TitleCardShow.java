package uz.dukeengine.client3d;

import uz.dukeengine.game.TitleCard;

/**
 * A title card as the client draws it, over everything on the game's canvas: the screen darkened, the words large in
 * its middle and the line under them smaller, all faded by the card's own clock — see {@code DukeGame.titleCard}.
 */
final class TitleCardShow {

    private final TitleCard card;
    private float elapsed;

    TitleCardShow(TitleCard card) {
        this.card = card;
    }

    TitleCard card() {
        return card;
    }

    /** Whether the player's input goes to nothing while it shows. */
    boolean takesInput() {
        return card.takesInput();
    }

    /** One frame of it, {@code seconds} on; whether it is still up after it. */
    boolean paint(Canvas canvas, float seconds) {
        elapsed += seconds;
        if (elapsed >= card.seconds()) {
            return false;
        }
        float shown = card.shownAt(elapsed);
        int alpha = Math.round(shown * 255f) << 24;
        canvas.fillRect(0f, 0f, canvas.width(), canvas.height(), Math.round(shown * 180f) << 24);
        var big = Canvas.Font.of("Georgia", Math.max(24, Math.round(canvas.height() * 0.07f))).bold(true);
        var small = Canvas.Font.of("Georgia", Math.max(14, Math.round(canvas.height() * 0.03f)));
        var words = canvas.measure(big, card.text());
        float top = canvas.height() * 0.42f - words.lineHeight() / 2f;
        canvas.drawText(big, card.text(), (canvas.width() - words.width()) / 2f, top, alpha | 0xF0E6C8);
        if (!card.subtext().isEmpty()) {
            var under = canvas.measure(small, card.subtext());
            canvas.drawText(small, card.subtext(), (canvas.width() - under.width()) / 2f,
                    top + words.lineHeight() * 1.2f, alpha | 0xC8BEA0);
        }
        return true;
    }
}
