package uz.duke.core.client;

import uz.duke.core.GameLogic;

/**
 * Turns the simulation state into a frame for display — the seam between the
 * deterministic logic and any presentation backend.
 *
 * <p>SAGE's client renders {@code Drawable}s with the W3D engine; this interface
 * keeps rendering swappable. {@link AsciiRenderer} is a dependency-free, testable
 * text backend; a real 3D backend (e.g. jMonkeyEngine) would be another
 * implementation, with no change to the logic core.
 *
 * <p>A renderer only ever reads the world and must respect fog of war — it shows
 * a single viewing player what that player can see, never the whole map.
 */
public interface Renderer {

    /** Produce a frame of the world as seen by {@code viewerPlayer}. */
    String render(GameLogic logic, int viewerPlayer);
}
