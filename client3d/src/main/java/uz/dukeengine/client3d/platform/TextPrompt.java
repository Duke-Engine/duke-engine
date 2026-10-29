package uz.dukeengine.client3d.platform;

/** A box that asks the player for a line of text. */
public interface TextPrompt {

    /** Asks and waits for the answer, or {@code null} if cancelled. Never on the render thread. */
    String ask(String prompt, String initial);
}
