package uz.dukeengine.core.event;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A short text floated up from a point of the world — money earned there, a bounty, a word the game wants seen where it
 * happened: the reference's {@code InGameUI::addFloatingText}. Posted from the simulation, it reaches a client only where
 * that client's player can see the point, as every event with a place does; the client draws it rising and fading.
 *
 * <p>It may say who sees it instead: the players it is shown to ({@link #to}), or the thing it is about ({@link
 * #about}) — shown to that thing's owner and to whoever may see the thing, so a hidden building's income tells no enemy
 * where it stands, as the reference's floats none for a stealthed thing that is neither the player's nor detected.
 *
 * @param where   the point it rises from: x and y the ground, z the height
 * @param argb    its colour, alpha included — the reference's are 230 or 255 opaque
 * @param shownTo the players it is shown to, or empty for whoever sees the point, or sees what it is about
 * @param about   the thing it is about, or null
 */
public record TextFloated(int frame, Coord3D where, String text, int argb, List<Integer> shownTo, ObjectId about)
        implements WorldEvent {

    public TextFloated {
        shownTo = shownTo == null ? List.of() : List.copyOf(shownTo);
    }

    /** A text for whoever sees the point, as every one was before one could say more. */
    public TextFloated(int frame, Coord3D where, String text, int argb) {
        this(frame, where, text, argb, List.of(), null);
    }

    /** A text shown to these players alone, wherever they look. */
    public static TextFloated to(int frame, Coord3D where, String text, int argb, List<Integer> players) {
        return new TextFloated(frame, where, text, argb, players, null);
    }

    /** A text about a thing, rising from it: shown to its owner and to whoever may see it. */
    public static TextFloated about(int frame, GameObject thing, String text, int argb) {
        return new TextFloated(frame, thing.getPosition(), text, argb, List.of(), thing.getId());
    }
}
