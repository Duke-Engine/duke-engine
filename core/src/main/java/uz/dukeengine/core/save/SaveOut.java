package uz.dukeengine.core.save;

import java.util.Collection;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;

/**
 * The values a {@link Saveable} writes, in turn, as one line of text a {@link SaveIn} reads back in the same order.
 *
 * <p>Each value is one word, the words parted by a space: a whole number in decimal, a float by {@link Float#toString}
 * and a double by {@link Double#toString} (which both read back to the same bits), a yes or no as {@code 1} or {@code 0},
 * a place as its three floats parted by commas, an enum by its name, a thing by its id, a text after a {@code '} with
 * {@code %}, space, {@code |} and line breaks written as {@code %25}, {@code %20}, {@code %7C}, {@code %0A} and
 * {@code %0D}, and null as {@code ~}. The line holds no {@code |} and no line break, so a save may part modules by the
 * one and lines by the other.
 */
public final class SaveOut {

    static final String NULL = "~";

    private final StringBuilder text = new StringBuilder();

    private SaveOut word(String word) {
        if (!text.isEmpty()) {
            text.append(' ');
        }
        text.append(word);
        return this;
    }

    public SaveOut writeInt(int value) {
        return word(Integer.toString(value));
    }

    public SaveOut writeLong(long value) {
        return word(Long.toString(value));
    }

    public SaveOut writeFloat(float value) {
        return word(Float.toString(value));
    }

    public SaveOut writeDouble(double value) {
        return word(Double.toString(value));
    }

    public SaveOut writeBoolean(boolean value) {
        return word(value ? "1" : "0");
    }

    public SaveOut writeString(String value) {
        return word(value == null ? NULL : "'" + escape(value));
    }

    public SaveOut writeEnum(Enum<?> value) {
        return word(value == null ? NULL : value.name());
    }

    public SaveOut writeId(ObjectId value) {
        return word(value == null ? NULL : Integer.toString(value.value()));
    }

    /** A thing by its id; it is found again by {@link SaveIn#readThing}, once every thing of the save is back. */
    public SaveOut writeThing(GameObject value) {
        return writeId(value == null ? null : value.getId());
    }

    public SaveOut writePlace(Coord3D value) {
        return word(value == null ? NULL
                : Float.toString(value.x()) + ',' + Float.toString(value.y()) + ',' + Float.toString(value.z()));
    }

    /** How many ids, then each in turn. */
    public SaveOut writeIds(Collection<ObjectId> ids) {
        writeInt(ids.size());
        for (var id : ids) {
            writeId(id);
        }
        return this;
    }

    /** How many places, then each in turn. */
    public SaveOut writePlaces(Collection<Coord3D> places) {
        writeInt(places.size());
        for (var place : places) {
            writePlace(place);
        }
        return this;
    }

    /** How many texts, then each in turn. */
    public SaveOut writeStrings(Collection<String> strings) {
        writeInt(strings.size());
        for (var string : strings) {
            writeString(string);
        }
        return this;
    }

    /** The words written so far, as one line. */
    public String text() {
        return text.toString();
    }

    private static String escape(String value) {
        var escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '%' -> escaped.append("%25");
                case ' ' -> escaped.append("%20");
                case '|' -> escaped.append("%7C");
                case '\n' -> escaped.append("%0A");
                case '\r' -> escaped.append("%0D");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
