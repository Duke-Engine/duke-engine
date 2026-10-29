package uz.dukeengine.core.save;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;

/**
 * The values a {@link SaveOut} wrote, read back in the order they were written. A value of the wrong kind, one asked
 * for past the end, or one left unread ({@link #expectEnd}) is refused by the name of what wrote it: a save read
 * differently from how it was written is a world gone wrong, and is said at once.
 */
public final class SaveIn {

    private final String[] words;
    private final World world;
    private final String writer;
    private int next;

    /** The words of {@code text}, as {@code writer} — a module's class, say — wrote them in {@code world}. */
    public SaveIn(String text, World world, String writer) {
        this.words = text.isEmpty() ? new String[0] : text.split(" ", -1);
        this.world = world;
        this.writer = writer;
    }

    /** The world the save is loaded into: every thing of the save is in it, and its rules are set. */
    public World world() {
        return world;
    }

    private String word() {
        if (next >= words.length) {
            throw refused("asked for value " + (next + 1) + " of " + words.length);
        }
        return words[next++];
    }

    private String wordOrNull() {
        var word = word();
        return SaveOut.NULL.equals(word) ? null : word;
    }

    public int readInt() {
        var word = word();
        try {
            return Integer.parseInt(word);
        } catch (NumberFormatException e) {
            throw refused("value " + next + " '" + word + "' is not a whole number");
        }
    }

    public long readLong() {
        var word = word();
        try {
            return Long.parseLong(word);
        } catch (NumberFormatException e) {
            throw refused("value " + next + " '" + word + "' is not a whole number");
        }
    }

    public float readFloat() {
        var word = word();
        try {
            return Float.parseFloat(word);
        } catch (NumberFormatException e) {
            throw refused("value " + next + " '" + word + "' is not a number");
        }
    }

    public double readDouble() {
        var word = word();
        try {
            return Double.parseDouble(word);
        } catch (NumberFormatException e) {
            throw refused("value " + next + " '" + word + "' is not a number");
        }
    }

    public boolean readBoolean() {
        return switch (word()) {
            case "1" -> true;
            case "0" -> false;
            default -> throw refused("value " + next + " '" + words[next - 1] + "' is not 1 or 0");
        };
    }

    public String readString() {
        var word = wordOrNull();
        if (word == null) {
            return null;
        }
        if (word.isEmpty() || word.charAt(0) != '\'') {
            throw refused("value " + next + " '" + word + "' is not a text");
        }
        return unescape(word.substring(1));
    }

    public <E extends Enum<E>> E readEnum(Class<E> type) {
        var word = wordOrNull();
        if (word == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, word);
        } catch (IllegalArgumentException e) {
            throw refused("value " + next + " '" + word + "' is no " + type.getSimpleName());
        }
    }

    public ObjectId readId() {
        var word = wordOrNull();
        if (word == null) {
            return null;
        }
        try {
            return new ObjectId(Integer.parseInt(word));
        } catch (NumberFormatException e) {
            throw refused("value " + next + " '" + word + "' is not an id");
        }
    }

    /** The thing whose id was written, or null where none was or it is not in the world. */
    public GameObject readThing() {
        var id = readId();
        return id == null ? null : world.findObject(id);
    }

    public Coord3D readPlace() {
        var word = wordOrNull();
        if (word == null) {
            return null;
        }
        var xyz = word.split(",");
        try {
            if (xyz.length != 3) {
                throw new NumberFormatException();
            }
            return new Coord3D(Float.parseFloat(xyz[0]), Float.parseFloat(xyz[1]), Float.parseFloat(xyz[2]));
        } catch (NumberFormatException e) {
            throw refused("value " + next + " '" + word + "' is not a place");
        }
    }

    public List<ObjectId> readIds() {
        int count = readInt();
        var ids = new ArrayList<ObjectId>(count);
        for (int i = 0; i < count; i++) {
            ids.add(readId());
        }
        return ids;
    }

    public List<Coord3D> readPlaces() {
        int count = readInt();
        var places = new ArrayList<Coord3D>(count);
        for (int i = 0; i < count; i++) {
            places.add(readPlace());
        }
        return places;
    }

    public List<String> readStrings() {
        int count = readInt();
        var strings = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            strings.add(readString());
        }
        return strings;
    }

    /** Refused unless every value written has been read. */
    public void expectEnd() {
        if (next != words.length) {
            throw refused("read " + next + " of its " + words.length + " values");
        }
    }

    private UnsavedModuleException refused(String why) {
        return new UnsavedModuleException(writer, writer + " was saved differently from how it is read: " + why);
    }

    private String unescape(String word) {
        if (word.indexOf('%') < 0) {
            return word;
        }
        var text = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c != '%') {
                text.append(c);
                continue;
            }
            try {
                text.append((char) Integer.parseInt(word.substring(i + 1, i + 3), 16));
            } catch (RuntimeException e) {
                throw refused("value " + next + " has a broken escape at " + i);
            }
            i += 2;
        }
        return text.toString();
    }
}
