package uz.dukeengine.combat.message;

import java.util.List;
import java.util.Objects;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * An order the game defined — a special power fired at a place or a thing, a science bought with rank points, a skill
 * cast, a thing picked up: the reference's {@code MSG_DO_SPECIAL_POWER_AT_LOCATION}, {@code MSG_PURCHASE_SCIENCE} and
 * their kind. It travels as every order does: posted from any thread, sent to every machine, applied on a frame
 * boundary in the order it was given, written into a replay. The engine never reads {@code word} or {@code number}; the
 * game hears the order where it is applied ({@code DukeGame.onOrder}) and does what it means there. {@code units} may
 * be empty, and {@code place} and {@code target} null.
 */
public record GameOrder(int playerIndex, String word, List<ObjectId> units, Coord3D place, ObjectId target, long number)
        implements Command {

    public GameOrder {
        Objects.requireNonNull(word, "word");
        units = units == null ? List.of() : List.copyOf(units);
    }
}
