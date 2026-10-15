package uz.dukeengine.game;

import java.util.List;

/**
 * The credits, rolled up a darkened screen over {@code seconds}: headings and names, in order; drawn only, out of the
 * checksum, and skipped by Esc or a click. See {@link DukeGame#creditsRoll}.
 *
 * @param ended told on the window's thread when it has rolled out or been skipped
 */
public record CreditsRoll(List<Line> lines, float seconds, Runnable ended) {

    /** One line of the roll: a heading, drawn larger, or a name under one. */
    public record Line(String text, boolean heading) {

        public Line {
            text = text == null ? "" : text;
        }

        public static Line heading(String text) {
            return new Line(text, true);
        }

        public static Line name(String text) {
            return new Line(text, false);
        }
    }

    public CreditsRoll {
        lines = List.copyOf(lines);
        seconds = Math.max(0.1f, seconds);
        ended = ended == null ? () -> { } : ended;
    }
}
