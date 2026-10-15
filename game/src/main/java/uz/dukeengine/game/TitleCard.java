package uz.dukeengine.game;

/**
 * Big lettering in the middle of a darkened screen — an act's end, a chapter's name — faded in, held and faded out, in
 * seconds; drawn only, out of the checksum. See {@link DukeGame#titleCard}.
 *
 * @param takesInput whether the player's keys and clicks go to nothing while it shows
 */
public record TitleCard(String text, String subtext, float fadeIn, float hold, float fadeOut, boolean takesInput) {

    public TitleCard {
        text = text == null ? "" : text;
        subtext = subtext == null ? "" : subtext;
        fadeIn = Math.max(0f, fadeIn);
        hold = Math.max(0f, hold);
        fadeOut = Math.max(0f, fadeOut);
    }

    /** How long it shows, from the first of its fade to the last. */
    public float seconds() {
        return fadeIn + hold + fadeOut;
    }

    /** How much of it shows {@code elapsed} seconds in, from 0 to 1. */
    public float shownAt(float elapsed) {
        if (elapsed < fadeIn) {
            return fadeIn <= 0f ? 1f : elapsed / fadeIn;
        }
        float left = seconds() - elapsed;
        return left >= fadeOut ? 1f : fadeOut <= 0f ? 0f : Math.max(0f, left / fadeOut);
    }
}
