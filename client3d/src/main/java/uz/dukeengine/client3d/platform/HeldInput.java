package uz.dukeengine.client3d.platform;

/** What is held down right now, which jME's events say only as they change. */
public interface HeldInput {

    boolean pointerInside();

    boolean focused();

    /** Whether the button is down; numbered as {@code MouseInput.BUTTON_*}. */
    boolean buttonHeld(int button);

    boolean keyHeld(char key);
}
