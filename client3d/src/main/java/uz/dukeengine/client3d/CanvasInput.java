package uz.dukeengine.client3d;

/**
 * The raw input, offered to the game before the client does anything with it: the pointer, its buttons and wheel,
 * the keys, and the characters they type. What the game takes goes no further — a click on a button the game drew
 * does not also select the unit under it — and what it leaves goes on to the client's own controls as it always did.
 *
 * <p>On the window's thread, in screen pixels from the top left, the same pixels {@link Canvas} draws in.
 */
@FunctionalInterface
public interface CanvasInput {

    /** Whether the game took this; if it did, nothing else is told of it. */
    boolean take(Event event);

    /** One thing the hand did. */
    sealed interface Event permits Pointer, Button, Wheel, Key, Typed {
    }

    enum Mouse { LEFT, RIGHT, MIDDLE }

    /** The pointer moved to here. */
    record Pointer(int x, int y) implements Event {
    }

    /**
     * A button went down or came up, here. {@code doubleClick} on the down that makes two of the same button close
     * together — within half a second and four pixels, the defaults of the system the reference ran on.
     */
    record Button(int x, int y, Mouse button, boolean down, boolean doubleClick) implements Event {
    }

    /** The wheel turned so many notches here; positive is away from the player. */
    record Wheel(int x, int y, int notches) implements Event {
    }

    /**
     * A key went down or came up, by its code — {@link com.jme3.input.KeyInput}'s, which is the keyboard's scan code
     * — or went down again while held.
     */
    record Key(int code, boolean down, boolean repeat) implements Event {
    }

    /** A character was typed, as a Unicode code point: what text is entered from, in any script. */
    record Typed(int codePoint) implements Event {
    }
}
