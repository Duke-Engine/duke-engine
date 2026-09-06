package uz.duke.core;

/**
 * The presentation layer, ported from SAGE's {@code GameClient}.
 *
 * <p>Unlike {@link GameLogic}, the client runs every engine loop and may tick
 * faster than the simulation: it handles input, audio and rendering, and
 * interpolates between logic frames for smooth visuals. It must never mutate
 * simulation state — it only observes {@link GameLogic} and draws it.
 */
public abstract class GameClient extends SubsystemInterface {

    private int frame;

    @Override
    public void init() {
        frame = 0;
    }

    @Override
    public void reset() {
        frame = 0;
    }

    @Override
    public final void update() {
        render();
        frame++;
    }

    /** Process input and draw one display frame. */
    protected abstract void render();

    /** The number of display frames serviced so far. */
    public final int getFrame() {
        return frame;
    }
}
