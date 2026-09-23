package uz.dukeengine.game.view;

/**
 * One button of the command bar: what the player may do with whatever is selected.
 *
 * <p>The engine has never had a word for this. A dungeon's hero panel is fed a line of text and parses
 * figures out of it, which serves a game where the thing selected is always the same hero; an RTS selects
 * a barracks and then a tank and has a different set of orders each time, and there was nothing to hand
 * one over with. So nothing could be built, trained or ordered except from a keyboard.
 *
 * <p><b>The engine does not know what any of these mean.</b> It draws a picture, a word and a key, reports
 * which one was pressed, and the game does the rest — the same bargain {@code WorldSnapshot.status}
 * strikes. Building a power plant, casting a spell and calling an airstrike are one thing from here.
 *
 * <p>Carried in the {@link WorldSnapshot}, which is to say computed on the simulation thread from the
 * selection the client last reported. That is what keeps it out of a race: a button is worked out where
 * the state it is about lives, and crosses to the window as a copy with everything else.
 *
 * @param id        what is sent back when it is pressed — the game's own word, never read here
 * @param picture   the whole path to its icon, as the game wrote it, or null for a button of words
 * @param label     what it is called, for the button and whatever the game wants read off it
 * @param hotkey    the key that presses it, drawn in the corner of the button so the player can learn it.
 *                  What the key actually <em>does</em> is claimed through the client's {@code Hotkeys} —
 *                  a game has been able to take a key for years — so this is the label for one, never a
 *                  second way of pressing it
 * @param available whether it may be pressed now — a button that cannot is drawn dim rather than hidden,
 *                  because a bar whose buttons move around as money comes and goes cannot be learned
 */
public record CommandButton(String id, String picture, String label, String hotkey, boolean available) {

    public CommandButton {
        label = label == null ? "" : label;
    }

    /** A button that is simply there and may be pressed. */
    public static CommandButton of(String id, String picture, String label, String hotkey) {
        return new CommandButton(id, picture, label, hotkey, true);
    }
}
