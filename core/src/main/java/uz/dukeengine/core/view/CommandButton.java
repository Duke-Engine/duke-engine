package uz.dukeengine.core.view;

/**
 * One button of the command bar: what the player may do with whatever is selected.
 *
 * <p>The engine has never had a word for this. A dungeon's hero panel is fed a line of text and parses
 * figures out of it, which serves a game where the thing selected is always the same hero; an RTS selects
 * a barracks and then a tank and has a different set of orders each time, and there was nothing to hand
 * one over with. So nothing could be built, trained or ordered except from a keyboard.
 *
 * <p><b>The engine does not know what any of these mean.</b> It draws a picture, a word and a key, reports
 * which one was pressed — and where, for one that {@link Aim aims} — and the game does the rest: the same
 * bargain {@code WorldSnapshot.status} strikes. Building a power plant, setting a rally point and calling
 * an airstrike are one thing from here.
 *
 * <p>Carried in the {@link WorldSnapshot}, which is to say computed on the simulation thread from the
 * selection the client last reported. That is what keeps it out of a race: a button is worked out where
 * the state it is about lives, and crosses to the window as a copy with everything else.
 *
 * @param id        what is sent back when it is pressed — the game's own word, never read here
 * @param picture   the whole path to its icon, as the game wrote it, or null for a button of words
 * @param label     what it is called, for the button and whatever the game wants read off it
 * @param hotkey    the key that presses it, drawn in the corner of the button so the player can learn it —
 *                  a letter. It presses the button as a click does, unless the game took that letter for
 *                  itself ({@code Hotkeys}) or put one of the client's own controls on it ({@code KeyMap}),
 *                  which come first
 * @param available whether it may be pressed now — a button that cannot is drawn dim rather than hidden,
 *                  because a bar whose buttons move around as money comes and goes cannot be learned
 * @param aim       what it needs before it is sent: nothing, a place, or a thing
 * @param ghost     for a button that aims at a place, what to draw at the cursor while it is armed — the
 *                  name of a template, drawn as that template is drawn, or the path to a model. Null for no
 *                  ghost at all
 * @param progress  how far along what the button stands for is, from 0 to 1, drawn as a shade over what is
 *                  still to do; {@link #NO_PROGRESS} for a button that shows none
 * @param facing    which way the ghost faces until the player turns it, in the simulation's degrees — the
 *                  unit {@code GameMessage.Construct} takes, 0 along +x. A game that puts its buildings down
 *                  turned says so here: one RTS measured puts 254 of its 271 turned things down at -45 or
 *                  -135, fronts to the camera, and a ghost drawn square-on showed a different footprint from
 *                  the one that was built. The reference game starts its placement icon at the thing's own
 *                  angle and deliberately not the camera's, so it faces the player until he turns it
 * @param answeredAs for a button that aims at a thing, the game's word its press there is answered as: marked and
 *                  voiced as a click giving that word on a thing is ({@code Visuals.wordMark}, {@code orderAnswer}) —
 *                  a key used on a gate is the game's own order, no attack on it. Null for an attack's answer, the
 *                  ring in the attack's colour and the button's own name voiced, as an ability aimed at an enemy is
 */
public record CommandButton(String id, String picture, String label, String hotkey, boolean available,
        Aim aim, String ghost, float facing, float progress, String answeredAs) {

    /** What {@link #progress} is for a button that shows none. */
    public static final float NO_PROGRESS = -1f;

    /**
     * What a button needs from the player between being pressed and being sent.
     *
     * <p>A place or a thing arrives the same way whatever it is for — a building's corner, a rally point,
     * where an airstrike lands — and the engine does not know which.
     */
    public enum Aim {
        /** Sent the moment it is pressed. */
        NOW,
        /** Pressing arms the cursor; the next click on the ground is the place — dragged, the facing too. */
        GROUND,
        /** Pressing arms the cursor; the next click on a thing is the target. */
        UNIT
    }

    public CommandButton {
        label = label == null ? "" : label;
        aim = aim == null ? Aim.NOW : aim;
    }

    /** A button whose press on a thing is answered as an attack's: every button from before the game could say. */
    public CommandButton(String id, String picture, String label, String hotkey, boolean available, Aim aim,
            String ghost, float facing, float progress) {
        this(id, picture, label, hotkey, available, aim, ghost, facing, progress, null);
    }

    /** A button with no progress to show: every button from before a queue could be drawn on the bar. */
    public CommandButton(String id, String picture, String label, String hotkey, boolean available, Aim aim,
            String ghost, float facing) {
        this(id, picture, label, hotkey, available, aim, ghost, facing, NO_PROGRESS, null);
    }

    /**
     * The same button showing how far along something is, from 0 to 1 — a unit or research at the head of a
     * factory's queue ({@code ProductionUpdate.getEntries}), drawn with what is still to do shaded over it.
     */
    public CommandButton withProgress(float share) {
        return new CommandButton(id, picture, label, hotkey, available, aim, ghost, facing,
                Math.clamp(share, 0f, 1f), answeredAs);
    }

    /**
     * The same button, its press on a thing answered as the game's order {@code word} is — see {@link #answeredAs()};
     * null for an attack's answer again.
     */
    public CommandButton answeredAs(String word) {
        return new CommandButton(id, picture, label, hotkey, available, aim, ghost, facing, progress, word);
    }

    /** A button sent the moment it is pressed. */
    public CommandButton(String id, String picture, String label, String hotkey, boolean available) {
        this(id, picture, label, hotkey, available, Aim.NOW, null, 0f);
    }

    /** A button that aims, its ghost facing along +x until it is turned. */
    public CommandButton(String id, String picture, String label, String hotkey, boolean available, Aim aim,
            String ghost) {
        this(id, picture, label, hotkey, available, aim, ghost, 0f);
    }

    /** A button that is simply there and may be pressed. */
    public static CommandButton of(String id, String picture, String label, String hotkey) {
        return new CommandButton(id, picture, label, hotkey, true);
    }
}
